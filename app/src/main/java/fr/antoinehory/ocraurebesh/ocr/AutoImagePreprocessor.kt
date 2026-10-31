package fr.antoinehory.ocraurebesh.ocr

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import androidx.compose.runtime.Immutable
import androidx.core.graphics.createBitmap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Coordonnées relatives (entre 0f et 1f) de la zone de visée (ROI - Region of Interest).
 * Par défaut, couvre toute la surface utile de l'écran entre la barre supérieure et la carte de traduction.
 */
@Immutable
data class RoiRectFraction(
    val leftFraction: Float = 0.04f,
    val topFraction: Float = 0.10f,
    val widthFraction: Float = 0.92f,
    val heightFraction: Float = 0.66f,
)

/**
 * Paire de Bitmaps binarisés générés par [AutoImagePreprocessor] :
 * - [primaryBitmap] : polarité détectée automatiquement (texte noir sur fond blanc).
 * - [invertedBitmap] : polarité opposée pour la double-passe de sécurité.
 * - [wasAutoInverted] : `true` si [primaryBitmap] correspond à une inversion (texte clair sur fond sombre détecté).
 */
class PreprocessedImages(
    val primaryBitmap: Bitmap,
    val invertedBitmap: Bitmap,
    val wasAutoInverted: Boolean,
    val primaryInkRatio: Float,
    val invertedInkRatio: Float,
    val lineCandidates: List<OcrCandidate> = emptyList(),
) {
    fun recycleAll() {
        primaryBitmap.recycle()
        invertedBitmap.recycle()
        lineCandidates.forEach { it.bitmap.recycle() }
    }
}

/**
 * Image candidate « nettoyée » par [TextLineFilter] : seules les lignes de glyphes alignés sont conservées,
 * recadrées sur la zone de texte (le décor et les illustrations sont supprimés).
 *
 * @property wasInverted `true` si le texte était plus clair (ou plus « chaud ») que son fond.
 * @property alignedGlyphCount nombre de glyphes alignés détectés (indice de confiance structurel).
 */
class OcrCandidate(
    val bitmap: Bitmap,
    val wasInverted: Boolean,
    val alignedGlyphCount: Int,
)

/**
 * Région de texte détectée sur un canal donné, avant conversion en [Bitmap] (testable en JVM).
 */
internal class LineRegionCandidate(
    val region: TextRegion,
    val wasInverted: Boolean,
)

/**
 * Masques binaires (`true` = encre) issus du seuillage adaptatif multi-échelle d'un canal.
 */
internal class AdaptiveMasks(
    val darkMask: BooleanArray,
    val lightMask: BooleanArray,
    val darkCount: Int,
    val lightCount: Int,
)

/**
 * Résultat intermédiaire (buffers de pixels ARGB) de [AutoImagePreprocessor.preprocessPixels],
 * directement testable en tests unitaires JVM sans dépendance aux instances natives [Bitmap].
 */
internal class PreprocessedPixelBuffers(
    val primaryPixels: IntArray,
    val invertedPixels: IntArray,
    val threshold: Int,
    val wasAutoInverted: Boolean,
    val primaryInkRatio: Float,
    val invertedInkRatio: Float,
    val darkMask: BooleanArray = BooleanArray(0),
    val lightMask: BooleanArray = BooleanArray(0),
)

/**
 * Préprocesseur d'image temps réel 100 % automatique (zéro réglage utilisateur).
 *
 * Étapes appliquées sur la zone de visée (ROI) :
 * 1. Rotation et découpage strict de la ROI aligné sur le recadrage `FILL_CENTER` de l'écran,
 *    avec préservation de la haute définition (`1080p`) pour lire jusqu'à 20 lignes de 30 petits caractères.
 * 2. Conversion en niveaux de gris et étirement d'histogramme centré (percentiles `0.05% - 99.95%`)
 *    capable de capter aussi bien un mot isolé ou un texte en mode contour (`< 0.2%` des pixels)
 *    que des textes à très faible contraste.
 * 3. Lissage anti-bruit 3x3 suivi d'un rehaussement adaptatif des contours (Unsharp Mask 5x5)
 *    pour restaurer la netteté des textes flous et des lettres en mode contour.
 * 4. Binarisation adaptative multi-échelle par Image Intégrale (petit rayon pour les petits textes
 *    de 20 lignes et les lettres en mode contour + grand rayon pour les traits pleins épais).
 * 5. Détection automatique de la polarité (texte clair sur fond foncé vs texte foncé sur fond clair)
 *    et ajout d'une marge blanche de sécurité autour de la ROI pour Tesseract LSTM.
 */
object AutoImagePreprocessor {

    internal const val COLOR_BLACK = -0x1000000 // 0xFF000000
    internal const val COLOR_WHITE = -0x1       // 0xFFFFFFFF
    private const val FULL_SCREEN_REFERENCE_HEIGHT_PX = 1080
    private const val MIN_TARGET_ROI_HEIGHT_PX = 180
    private const val PADDING_PX = 16
    private const val MAX_LINE_CANDIDATES = 3
    // Écart minimal (2e - 98e percentile) du canal chaud/froid pour justifier son analyse
    private const val MIN_WARM_COOL_SPREAD = 20
    // Écart minimal pour accepter un canal R, G ou B individuel
    private const val MIN_SINGLE_CHANNEL_SPREAD = 40

    /**
     * Applique la rotation du capteur et découpe la région d'intérêt (ROI) de la frame
     * en tenant compte du ratio d'affichage (`FILL_CENTER`) de la `PreviewView`.
     */
    fun cropAndRotateRoi(
        source: Bitmap,
        rotationDegrees: Int,
        roiFraction: RoiRectFraction = RoiRectFraction(),
        viewportAspectRatio: Float = 0f,
    ): Bitmap {
        val orientedBitmap = if (rotationDegrees != 0) {
            val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
            Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
        } else {
            source
        }

        val w = orientedBitmap.width
        val h = orientedBitmap.height

        // Calcul de la sous-zone effectivement visible à l'écran en mode FILL_CENTER
        val visibleLeft: Float
        val visibleTop: Float
        val visibleWidth: Float
        val visibleHeight: Float

        val imageAspect = w.toFloat() / max(1, h).toFloat()
        if (viewportAspectRatio > 0.05f && imageAspect > viewportAspectRatio) {
            visibleHeight = h.toFloat()
            visibleWidth = h * viewportAspectRatio
            visibleLeft = (w - visibleWidth) * 0.5f
            visibleTop = 0f
        } else if (viewportAspectRatio > 0.05f && imageAspect < viewportAspectRatio) {
            visibleWidth = w.toFloat()
            visibleHeight = w / viewportAspectRatio
            visibleLeft = 0f
            visibleTop = (h - visibleHeight) * 0.5f
        } else {
            visibleLeft = 0f
            visibleTop = 0f
            visibleWidth = w.toFloat()
            visibleHeight = h.toFloat()
        }

        val cropX = (visibleLeft + visibleWidth * roiFraction.leftFraction)
            .roundToInt()
            .coerceIn(0, max(0, w - 1))
        val cropY = (visibleTop + visibleHeight * roiFraction.topFraction)
            .roundToInt()
            .coerceIn(0, max(0, h - 1))
        val cropW = (visibleWidth * roiFraction.widthFraction)
            .roundToInt()
            .coerceIn(1, w - cropX)
        val cropH = (visibleHeight * roiFraction.heightFraction)
            .roundToInt()
            .coerceIn(1, h - cropY)

        val cropped = Bitmap.createBitmap(orientedBitmap, cropX, cropY, cropW, cropH)
        if (orientedBitmap !== source && orientedBitmap !== cropped) {
            orientedBitmap.recycle()
        }

        // Hauteur cible haute définition pour conserver >= 35 px par ligne sur un bloc de 20 lignes
        val targetHeightPx = (FULL_SCREEN_REFERENCE_HEIGHT_PX * roiFraction.heightFraction)
            .roundToInt()
            .coerceIn(MIN_TARGET_ROI_HEIGHT_PX, FULL_SCREEN_REFERENCE_HEIGHT_PX)

        if (cropped.height > targetHeightPx * 1.25f) {
            val scale = targetHeightPx.toFloat() / cropped.height.toFloat()
            val scaledW = max(32, (cropped.width * scale).roundToInt())
            val scaled = Bitmap.createScaledBitmap(cropped, scaledW, targetHeightPx, true)
            if (scaled !== cropped && cropped !== source) {
                cropped.recycle()
            }
            return scaled
        }

        return cropped
    }

    /**
     * Normalise, rehausse la netteté, binarise en multi-échelle et détecte automatiquement la polarité
     * d'un [Bitmap] découpé, en entourant le résultat d'une marge blanche propre pour Tesseract.
     */
    fun preprocessForOcr(roiBitmap: Bitmap): PreprocessedImages {
        val width = roiBitmap.width
        val height = roiBitmap.height
        val totalPixels = width * height

        val argbPixels = IntArray(totalPixels)
        roiBitmap.getPixels(argbPixels, 0, width, 0, 0, width, height)

        val buffers = preprocessPixels(argbPixels, width, height)

        val primaryBitmap = createPaddedBitmap(buffers.primaryPixels, width, height)
        val invertedBitmap = createPaddedBitmap(buffers.invertedPixels, width, height)

        val lineCandidates = extractLineRegions(argbPixels, width, height, buffers).map { candidate ->
            val region = candidate.region
            OcrCandidate(
                bitmap = createPaddedBitmap(maskToArgb(region.mask), region.width, region.height),
                wasInverted = candidate.wasInverted,
                alignedGlyphCount = region.alignedGlyphCount,
            )
        }

        return PreprocessedImages(
            primaryBitmap = primaryBitmap,
            invertedBitmap = invertedBitmap,
            wasAutoInverted = buffers.wasAutoInverted,
            primaryInkRatio = buffers.primaryInkRatio,
            invertedInkRatio = buffers.invertedInkRatio,
            lineCandidates = lineCandidates,
        )
    }

    /**
     * Recherche des lignes de texte sur plusieurs sources complémentaires :
     * - **Luminance** : texte sombre sur fond clair / texte clair sur fond sombre (y compris néons).
     * - **Canal chaud/froid** `(R+G)/2 - B` : texte doré/jaune/orange sur fond bleu et inversement.
     * - **Canaux R, G, B individuels** : texte néon coloré sur fond chromaturellement différent
     *   (ex. bleu néon sur anthracite, rouge néon sur noir, vert sur magenta).
     *   Chaque canal est traité si son écart percentile dépasse [MIN_SINGLE_CHANNEL_SPREAD].
     * - **Canal B-R** `B - R` : texte bleu/cyan sur fond rouge/orange et inversement.
     *
     * Seules les [MAX_LINE_CANDIDATES] sources présentant le plus de glyphes alignés sont conservées.
     */
    internal fun extractLineRegions(
        argbPixels: IntArray,
        width: Int,
        height: Int,
        lumaBuffers: PreprocessedPixelBuffers,
    ): List<LineRegionCandidate> {
        val sources = mutableListOf<Pair<BooleanArray, Boolean>>()

        // Source 1 & 2 : luminance (sombre sur clair + clair sur sombre)
        if (lumaBuffers.darkMask.size == width * height) {
            sources.add(lumaBuffers.darkMask to false)
            sources.add(lumaBuffers.lightMask to true)
        }

        // Source 3 & 4 : canal chaud/froid (doré sur bleu, bleu sur doré)
        computeWarmCoolMasks(argbPixels, width, height)?.let { warmCool ->
            sources.add(warmCool.lightMask to true)
            sources.add(warmCool.darkMask to false)
        }

        // Source 5 & 6 : canaux R, G, B individuels — couvre les néons à iso-luminance
        computeSingleChannelMasks(argbPixels, width, height)?.let { single ->
            sources.add(single.lightMask to true)
            sources.add(single.darkMask to false)
        }

        return sources
            .mapNotNull { (mask, inverted) ->
                TextLineFilter.extract(mask, width, height)?.let { LineRegionCandidate(it, inverted) }
            }
            .sortedByDescending { it.region.alignedGlyphCount }
            .take(MAX_LINE_CANDIDATES)
    }

    /**
     * Sélectionne parmi les canaux R, G, B et l'opposition B-R celui qui offre le plus grand
     * contraste (écart percentile 2 %-98 %) et génère les masques adaptatifs correspondants.
     *
     * Cible les textes néon (bleu, cyan, rouge, vert, jaune) sur fond sombre ou chromatiquement
     * neutre dont la **luminance seule ne suffit pas** à discriminer texte et fond.
     *
     * Retourne `null` si aucun canal ne présente un écart supérieur à [MIN_SINGLE_CHANNEL_SPREAD].
     */
    internal fun computeSingleChannelMasks(argbPixels: IntArray, width: Int, height: Int): AdaptiveMasks? {
        val totalPixels = width * height

        // Évalue jusqu'à 4 canaux : R, G, B et B-R (bleu néon sur rouge/orange)
        // On n'utilise pas `data class` pour éviter le warning Array-in-data-class
        class ChannelScore(val raw: IntArray, val histogram: IntArray, val spread: Int)

        fun evalChannel(extract: (Int) -> Int): ChannelScore {
            val raw = IntArray(totalPixels)
            val histogram = IntArray(256)
            for (i in 0 until totalPixels) {
                val v = extract(argbPixels[i]).coerceIn(0, 255)
                raw[i] = v
                histogram[v]++
            }
            val p2 = percentile(histogram, totalPixels, 0.02f)
            val p98 = percentile(histogram, totalPixels, 0.98f)
            return ChannelScore(raw, histogram, p98 - p2)
        }

        val candidates = listOf(
            // Canal R : texte rouge/orange néon sur fond vert/bleu/sombre
            evalChannel { pixel -> (pixel shr 16) and 0xFF },
            // Canal G : texte vert néon sur fond rouge/bleu/sombre
            evalChannel { pixel -> (pixel shr 8) and 0xFF },
            // Canal B : texte bleu/cyan néon sur fond rouge/orange/sombre
            evalChannel { pixel -> pixel and 0xFF },
            // Opposition B-R : bleu vif sur rouge/anthracite chaud (complémentaire du canal chaud/froid)
            evalChannel { pixel ->
                val r = (pixel shr 16) and 0xFF
                val b = pixel and 0xFF
                ((b - r + 255) shr 1)
            },
        )

        val best = candidates.maxByOrNull { it.spread } ?: return null
        if (best.spread < MIN_SINGLE_CHANNEL_SPREAD) return null

        val enhanced = enhanceChannel(best.raw, best.histogram, width, height)
        return computeAdaptiveMasks(enhanced, width, height)
    }

    /**
     * Calcule les masques adaptatifs sur le canal d'opposition chaud/froid `(R+G)/2 - B`.
     * Retourne `null` si l'image est quasi neutre (niveaux de gris) : ce canal n'apporterait rien.
     */
    internal fun computeWarmCoolMasks(argbPixels: IntArray, width: Int, height: Int): AdaptiveMasks? {
        val totalPixels = width * height
        val raw = IntArray(totalPixels)
        val histogram = IntArray(256)
        for (i in 0 until totalPixels) {
            val pixel = argbPixels[i]
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            val warmCool = ((((r + g) shr 1) - b) + 256) shr 1 // -255..255 -> 0..255
            val v = warmCool.coerceIn(0, 255)
            raw[i] = v
            histogram[v]++
        }

        val p2 = percentile(histogram, totalPixels, 0.02f)
        val p98 = percentile(histogram, totalPixels, 0.98f)
        if (p98 - p2 < MIN_WARM_COOL_SPREAD) return null

        val enhanced = enhanceChannel(raw, histogram, width, height)
        return computeAdaptiveMasks(enhanced, width, height)
    }

    // `percentile` est utilisé aussi par computeSingleChannelMasks — déjà déclaré plus bas.

    private fun percentile(histogram: IntArray, totalPixels: Int, fraction: Float): Int {
        val target = max(1, (totalPixels * fraction).roundToInt())
        var accumulated = 0
        for (i in 0..255) {
            accumulated += histogram[i]
            if (accumulated >= target) return i
        }
        return 255
    }

    private fun maskToArgb(mask: BooleanArray): IntArray =
        IntArray(mask.size) { i -> if (mask[i]) COLOR_BLACK else COLOR_WHITE }

    private fun createPaddedBitmap(pixels: IntArray, width: Int, height: Int): Bitmap {
        val paddedW = width + PADDING_PX * 2
        val paddedH = height + PADDING_PX * 2
        val bitmap = createBitmap(paddedW, paddedH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        bitmap.setPixels(pixels, 0, width, PADDING_PX, PADDING_PX, width, height)
        return bitmap
    }

    /**
     * Cœur algorithmique de prétraitement opérant directement sur un tableau de pixels ARGB.
     */
    internal fun preprocessPixels(
        argbPixels: IntArray,
        width: Int,
        height: Int,
    ): PreprocessedPixelBuffers {
        val totalPixels = width * height
        require(totalPixels > 0 && argbPixels.size >= totalPixels) {
            "Dimensions invalides pour preprocessPixels (width=$width, height=$height, size=${argbPixels.size})"
        }

        val rawGray = IntArray(totalPixels)
        val rawHistogram = IntArray(256)

        // 1. Conversion en luminance (formule ITU-R BT.601 en virgule fixe)
        for (i in 0 until totalPixels) {
            val pixel = argbPixels[i]
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            val luma = (77 * r + 150 * g + 29 * b) shr 8
            rawGray[i] = luma
            rawHistogram[luma]++
        }

        // 2-3. Étirement d'histogramme, lissage et rehaussement de netteté
        val grayValues = enhanceChannel(rawGray, rawHistogram, width, height)

        val normalizedHistogram = IntArray(256)
        for (i in 0 until totalPixels) {
            normalizedHistogram[grayValues[i]]++
        }

        // 4. Calcul du seuil global d'Otsu
        val threshold = computeOtsuThreshold(normalizedHistogram, totalPixels)

        // 5. Détection automatique de la polarité (analyse de la couronne périphérique de la ROI)
        var darkBorderPixels = 0
        var totalBorderPixels = 0
        val borderBandX = max(1, width / 12)
        val borderBandY = max(1, height / 8)

        for (y in 0 until height) {
            for (x in 0 until width) {
                val isInBorderBand =
                    x < borderBandX || x >= width - borderBandX ||
                        y < borderBandY || y >= height - borderBandY
                if (isInBorderBand) {
                    if (grayValues[y * width + x] <= threshold) {
                        darkBorderPixels++
                    }
                    totalBorderPixels++
                }
            }
        }

        val shouldInvert = totalBorderPixels > 0 && (darkBorderPixels * 2 > totalBorderPixels)

        // 6. Seuillage adaptatif multi-échelle (Image Intégrale)
        val masks = computeAdaptiveMasks(grayValues, width, height)

        val darkOnLightPixels = IntArray(totalPixels) { i -> if (masks.darkMask[i]) COLOR_BLACK else COLOR_WHITE }
        val lightOnDarkPixels = IntArray(totalPixels) { i -> if (masks.lightMask[i]) COLOR_BLACK else COLOR_WHITE }

        val darkInkRatio = masks.darkCount.toFloat() / totalPixels.toFloat()
        val lightInkRatio = masks.lightCount.toFloat() / totalPixels.toFloat()

        val primaryPixels = if (shouldInvert) lightOnDarkPixels else darkOnLightPixels
        val invertedPixels = if (shouldInvert) darkOnLightPixels else lightOnDarkPixels
        val primaryInkRatio = if (shouldInvert) lightInkRatio else darkInkRatio
        val invertedInkRatio = if (shouldInvert) darkInkRatio else lightInkRatio

        return PreprocessedPixelBuffers(
            primaryPixels = primaryPixels,
            invertedPixels = invertedPixels,
            threshold = threshold,
            wasAutoInverted = shouldInvert,
            primaryInkRatio = primaryInkRatio,
            invertedInkRatio = invertedInkRatio,
            darkMask = masks.darkMask,
            lightMask = masks.lightMask,
        )
    }

    /**
     * Étirement d'histogramme entre les percentiles 0.05 % et 99.95 % (plancher de dynamique à 48),
     * lissage 3x3 puis rehaussement de netteté 5x5 si l'image présente une dynamique minimale.
     */
    private fun enhanceChannel(
        rawGray: IntArray,
        rawHistogram: IntArray,
        width: Int,
        height: Int,
    ): IntArray {
        val totalPixels = width * height
        val lowCutCount = max(1, (totalPixels * 0.0005f).roundToInt())
        val highCutCount = min(totalPixels, (totalPixels * 0.9995f).roundToInt())

        var minLuma = 0
        var accumulated = 0
        for (i in 0..255) {
            accumulated += rawHistogram[i]
            if (accumulated >= lowCutCount) {
                minLuma = i
                break
            }
        }

        var maxLuma = 255
        accumulated = 0
        for (i in 0..255) {
            accumulated += rawHistogram[i]
            if (accumulated >= highCutCount) {
                maxLuma = i
                break
            }
        }

        val stretchedGray = IntArray(totalPixels)
        val actualRange = maxLuma - minLuma
        // Plancher à 48 pour étirer les textes à faible contraste tout en empêchant
        // le grain de bois/bureau (actualRange ~ 12) de dépasser le seuil local après lissage.
        val range = max(48, actualRange)
        val midLuma = (minLuma + maxLuma) shr 1
        for (i in 0 until totalPixels) {
            stretchedGray[i] = (128 + ((rawGray[i] - midLuma) * 255) / range).coerceIn(0, 255)
        }

        val smoothed = smooth3x3(stretchedGray, width, height)
        return if (actualRange >= 22) {
            sharpenEdges5x5(smoothed, width, height)
        } else {
            smoothed
        }
    }

    /**
     * Binarisation adaptative multi-échelle par Image Intégrale :
     * petit rayon (traits fins, contours évidés, petites lignes denses) + grand rayon (traits pleins épais).
     */
    private fun computeAdaptiveMasks(grayValues: IntArray, width: Int, height: Int): AdaptiveMasks {
        val totalPixels = width * height
        val stride = width + 1
        val integral = LongArray(stride * (height + 1))
        for (y in 0 until height) {
            var rowSum = 0L
            val srcOffset = y * width
            val prevIntegralOffset = y * stride
            val currIntegralOffset = (y + 1) * stride
            for (x in 0 until width) {
                rowSum += grayValues[srcOffset + x]
                integral[currIntegralOffset + x + 1] = integral[prevIntegralOffset + x + 1] + rowSum
            }
        }

        val minDim = min(width, height)
        val smallRadius = max(4, min(8, minDim / 24))
        val largeRadius = max(10, min(24, minDim / 2))
        val localContrastDelta = 18

        val darkMask = BooleanArray(totalPixels)
        val lightMask = BooleanArray(totalPixels)
        var darkGlyphCount = 0
        var lightGlyphCount = 0

        for (y in 0 until height) {
            val sy1 = max(0, y - smallRadius)
            val sy2 = min(height - 1, y + smallRadius)
            val ly1 = max(0, y - largeRadius)
            val ly2 = min(height - 1, y + largeRadius)
            val rowOffset = y * width

            for (x in 0 until width) {
                val sx1 = max(0, x - smallRadius)
                val sx2 = min(width - 1, x + smallRadius)
                val sCount = (sx2 - sx1 + 1) * (sy2 - sy1 + 1)
                val sSum = integral[(sy2 + 1) * stride + (sx2 + 1)] -
                    integral[sy1 * stride + (sx2 + 1)] -
                    integral[(sy2 + 1) * stride + sx1] +
                    integral[sy1 * stride + sx1]
                val smallMean = (sSum / sCount).toInt()

                val lx1 = max(0, x - largeRadius)
                val lx2 = min(width - 1, x + largeRadius)
                val lCount = (lx2 - lx1 + 1) * (ly2 - ly1 + 1)
                val lSum = integral[(ly2 + 1) * stride + (lx2 + 1)] -
                    integral[ly1 * stride + (lx2 + 1)] -
                    integral[(ly2 + 1) * stride + lx1] +
                    integral[ly1 * stride + lx1]
                val largeMean = (lSum / lCount).toInt()

                val g = grayValues[rowOffset + x]
                val isDarkGlyph =
                    (g <= smallMean - localContrastDelta) || (g <= largeMean - localContrastDelta)
                val isLightGlyph =
                    (g >= smallMean + localContrastDelta) || (g >= largeMean + localContrastDelta)

                if (isDarkGlyph) {
                    darkMask[rowOffset + x] = true
                    darkGlyphCount++
                }
                if (isLightGlyph) {
                    lightMask[rowOffset + x] = true
                    lightGlyphCount++
                }
            }
        }

        return AdaptiveMasks(
            darkMask = darkMask,
            lightMask = lightMask,
            darkCount = darkGlyphCount,
            lightCount = lightGlyphCount,
        )
    }

    /**
     * Lissage 3x3 rapide (incluant les bordures par répétition de bord) supprimant le bruit haute fréquence.
     */
    private fun smooth3x3(input: IntArray, width: Int, height: Int): IntArray {
        if (width < 3 || height < 3) return input.copyOf()
        val output = IntArray(width * height)
        for (y in 0 until height) {
            val row = y * width
            val prevRow = max(0, y - 1) * width
            val nextRow = min(height - 1, y + 1) * width
            for (x in 0 until width) {
                val xm1 = max(0, x - 1)
                val xp1 = min(width - 1, x + 1)
                val sum =
                    input[prevRow + xm1] + (input[prevRow + x] shl 1) + input[prevRow + xp1] +
                        (input[row + xm1] shl 1) + (input[row + x] shl 2) + (input[row + xp1] shl 1) +
                        input[nextRow + xm1] + (input[nextRow + x] shl 1) + input[nextRow + xp1]
                output[row + x] = sum shr 4
            }
        }
        return output
    }

    /**
     * Rehaussement de netteté (Unsharp Mask 5x5) avec seuil de bruit :
     * amplifie les contours flous, les faibles contrastes et les glyphes en mode contour
     * sans amplifier les surfaces uniformes.
     */
    private fun sharpenEdges5x5(input: IntArray, width: Int, height: Int): IntArray {
        if (width < 7 || height < 7) return input.copyOf()
        val output = input.copyOf()
        for (y in 2 until height - 2) {
            val row = y * width
            val rM2 = (y - 2) * width
            val rM1 = (y - 1) * width
            val rP1 = (y + 1) * width
            val rP2 = (y + 2) * width
            for (x in 2 until width - 2) {
                val center = input[row + x]
                // Moyenne sur l'anneau périphérique 5x5 (8 points cardinaux et diagonaux à distance 2)
                val ringSum =
                    input[rM2 + x - 2] + input[rM2 + x] + input[rM2 + x + 2] +
                        input[row + x - 2] + input[row + x + 2] +
                        input[rP2 + x - 2] + input[rP2 + x] + input[rP2 + x + 2] +
                        input[rM1 + x - 1] + input[rM1 + x + 1] +
                        input[rP1 + x - 1] + input[rP1 + x + 1]
                val surroundMean = ringSum / 12
                val diff = center - surroundMean
                // Seuil à 5 pour ignorer le bruit résiduel sur une surface plate tout en rehaussant le flou
                if (abs(diff) >= 5) {
                    output[row + x] = (center + (diff * 3) / 2).coerceIn(0, 255)
                }
            }
        }
        return output
    }

    /**
     * Calcule le seuil de binarisation d'Otsu maximisant la variance inter-classe.
     */
    internal fun computeOtsuThreshold(histogram: IntArray, totalPixels: Int): Int {
        if (totalPixels <= 0) return 128

        var sumAll = 0L
        for (t in 0..255) {
            sumAll += t.toLong() * histogram[t]
        }

        var sumBackground = 0L
        var weightBackground = 0
        var maxVariance = -1.0
        var bestThreshold = 128

        for (t in 0..255) {
            weightBackground += histogram[t]
            if (weightBackground == 0) continue

            val weightForeground = totalPixels - weightBackground
            if (weightForeground == 0) break

            sumBackground += t.toLong() * histogram[t]

            val meanBackground = sumBackground.toDouble() / weightBackground
            val meanForeground = (sumAll - sumBackground).toDouble() / weightForeground
            val meanDiff = meanBackground - meanForeground

            val betweenClassVariance =
                weightBackground.toDouble() * weightForeground.toDouble() * meanDiff * meanDiff

            if (betweenClassVariance > maxVariance) {
                maxVariance = betweenClassVariance
                bestThreshold = t
            }
        }

        return min(245, max(10, bestThreshold))
    }
}
