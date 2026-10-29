package fr.antoinehory.ocraurebesh.ocr

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import androidx.compose.runtime.Immutable
import androidx.core.graphics.createBitmap
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Coordonnées relatives (entre 0f et 1f) de la zone de visée (ROI - Region of Interest).
 */
@Immutable
data class RoiRectFraction(
    val leftFraction: Float = 0.08f,
    val topFraction: Float = 0.38f,
    val widthFraction: Float = 0.84f,
    val heightFraction: Float = 0.18f,
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
)

/**
 * Préprocesseur d'image temps réel 100 % automatique (zéro réglage utilisateur).
 *
 * Étapes appliquées sur la zone de visée (ROI) :
 * 1. Rotation et découpage strict de la ROI aligné sur le recadrage `FILL_CENTER` de l'écran,
 *    suivi d'une mise à l'échelle optimale pour le réseau LSTM de Tesseract.
 * 2. Conversion en niveaux de gris et étirement d'histogramme robuste (percentiles 2% - 98%)
 *    pour gérer les textes colorés (néons, hologrammes bleus/rouges/jaunes) et les faibles contrastes.
 * 3. Lissage anti-Moiré 3x3 (suppression de la grille de sous-pixels des écrans filmés).
 * 4. Binarisation hybride (Otsu global + Seuillage adaptatif local par Image Intégrale de Bradley-Roth)
 *    pour éliminer les ombres, reflets d'écran et halos lumineux non uniformes.
 * 5. Détection automatique de la polarité (texte clair sur fond foncé vs texte foncé sur fond clair)
 *    et ajout d'une marge blanche de sécurité autour de la ROI pour Tesseract LSTM.
 */
object AutoImagePreprocessor {

    internal const val COLOR_BLACK = -0x1000000 // 0xFF000000
    internal const val COLOR_WHITE = -0x1       // 0xFFFFFFFF
    private const val TARGET_ROI_HEIGHT_PX = 112
    private const val PADDING_PX = 16

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
            // L'image capteur est plus large que l'écran : FILL_CENTER coupe à gauche et à droite
            visibleHeight = h.toFloat()
            visibleWidth = h * viewportAspectRatio
            visibleLeft = (w - visibleWidth) * 0.5f
            visibleTop = 0f
        } else if (viewportAspectRatio > 0.05f && imageAspect < viewportAspectRatio) {
            // L'image capteur est plus haute que l'écran : FILL_CENTER coupe en haut et en bas
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

        // Normalisation de la hauteur vers ~112px pour stabiliser la taille des glyphes pour Tesseract LSTM
        if (cropped.height > TARGET_ROI_HEIGHT_PX * 1.35f) {
            val scale = TARGET_ROI_HEIGHT_PX.toFloat() / cropped.height.toFloat()
            val scaledW = max(32, (cropped.width * scale).roundToInt())
            val scaled = Bitmap.createScaledBitmap(cropped, scaledW, TARGET_ROI_HEIGHT_PX, true)
            if (scaled !== cropped && cropped !== source) {
                cropped.recycle()
            }
            return scaled
        }

        return cropped
    }

    /**
     * Normalise, binarise (hybride Otsu + adaptatif local) et détecte automatiquement la polarité d'un [Bitmap] découpé,
     * en entourant le résultat d'une marge blanche propre pour Tesseract.
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

        return PreprocessedImages(
            primaryBitmap = primaryBitmap,
            invertedBitmap = invertedBitmap,
            wasAutoInverted = buffers.wasAutoInverted,
        )
    }

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

        // 2. Étirement d'histogramme entre le 2e et le 98e percentile
        val lowCutCount = (totalPixels * 0.02f).roundToInt()
        val highCutCount = (totalPixels * 0.98f).roundToInt()

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
        val range = max(1, maxLuma - minLuma)
        for (i in 0 until totalPixels) {
            stretchedGray[i] = ((rawGray[i] - minLuma) * 255 / range).coerceIn(0, 255)
        }

        // 3. Lissage 3x3 rapide pour atténuer le bruit capteur et la trame Moiré des écrans
        val grayValues = smooth3x3(stretchedGray, width, height)
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

        // 6. Construction de l'Image Intégrale pour le seuillage adaptatif local (Bradley-Roth)
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

        val windowRadius = max(6, min(width, height) / 4)
        val localContrastDelta = 11

        val darkOnLightPixels = IntArray(totalPixels)
        val lightOnDarkPixels = IntArray(totalPixels)

        for (y in 0 until height) {
            val y1 = max(0, y - windowRadius)
            val y2 = min(height - 1, y + windowRadius)
            val rowOffset = y * width

            for (x in 0 until width) {
                val x1 = max(0, x - windowRadius)
                val x2 = min(width - 1, x + windowRadius)
                val count = (x2 - x1 + 1) * (y2 - y1 + 1)

                val sum = integral[(y2 + 1) * stride + (x2 + 1)] -
                    integral[y1 * stride + (x2 + 1)] -
                    integral[(y2 + 1) * stride + x1] +
                    integral[y1 * stride + x1]

                val localMean = (sum / count).toInt()
                val g = grayValues[rowOffset + x]

                // Candidat Texte Sombre sur Fond Clair :
                // Le pixel est sombre localement (sous la moyenne locale) OU nettement sous le seuil d'Otsu
                val isDarkGlyph =
                    (g <= localMean - localContrastDelta && g <= threshold + 25) ||
                        (g <= threshold - 15)

                // Candidat Texte Clair sur Fond Sombre (écran, hologramme, néon) :
                // Le pixel est lumineux localement (au-dessus de la moyenne locale) OU nettement au-dessus d'Otsu
                val isLightGlyph =
                    (g >= localMean + localContrastDelta && g >= threshold - 25) ||
                        (g >= threshold + 15)

                darkOnLightPixels[rowOffset + x] = if (isDarkGlyph) COLOR_BLACK else COLOR_WHITE
                lightOnDarkPixels[rowOffset + x] = if (isLightGlyph) COLOR_BLACK else COLOR_WHITE
            }
        }

        val primaryPixels = if (shouldInvert) lightOnDarkPixels else darkOnLightPixels
        val invertedPixels = if (shouldInvert) darkOnLightPixels else lightOnDarkPixels

        return PreprocessedPixelBuffers(
            primaryPixels = primaryPixels,
            invertedPixels = invertedPixels,
            threshold = threshold,
            wasAutoInverted = shouldInvert,
        )
    }

    /**
     * Lissage 3x3 rapide préservant les bords tout en supprimant le bruit haute fréquence.
     */
    private fun smooth3x3(input: IntArray, width: Int, height: Int): IntArray {
        if (width < 5 || height < 5) return input.copyOf()
        val output = input.copyOf()
        for (y in 1 until height - 1) {
            val row = y * width
            val prevRow = (y - 1) * width
            val nextRow = (y + 1) * width
            for (x in 1 until width - 1) {
                // Noyau pondéré (centre poids 4, adjacents directs poids 2, coins poids 1 -> somme = 16)
                val sum =
                    input[prevRow + x - 1] + (input[prevRow + x] shl 1) + input[prevRow + x + 1] +
                        (input[row + x - 1] shl 1) + (input[row + x] shl 2) + (input[row + x + 1] shl 1) +
                        input[nextRow + x - 1] + (input[nextRow + x] shl 1) + input[nextRow + x + 1]
                output[row + x] = sum shr 4
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
