package fr.antoinehory.ocraurebesh.ocr

import android.graphics.Bitmap
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
    val heightFraction: Float = 0.18f
)

/**
 * Paire de Bitmaps binarisés générés par [AutoImagePreprocessor] :
 * - [primaryBitmap] : polarité détectée automatiquement (texte noir sur fond blanc).
 * - [invertedBitmap] : polarité opposée pour la double-passe de sécurité en cas de faible confiance.
 * - [wasAutoInverted] : `true` si [primaryBitmap] correspond à une inversion (texte clair sur fond sombre détecté).
 */
class PreprocessedImages(
    val primaryBitmap: Bitmap,
    val invertedBitmap: Bitmap,
    val wasAutoInverted: Boolean
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
 * Préprocesseur d'image 100 % automatique (zéro réglage utilisateur).
 *
 * Étapes appliquées sur la zone de visée (ROI) :
 * 1. Rotation et découpage strict de la ROI pour minimiser le coût CPU/mémoire.
 * 2. Conversion en niveaux de gris et étirement d'histogramme robuste (percentiles 2% - 98%)
 *    pour gérer les textes colorés (néons, hologrammes bleus/rouges/jaunes) et les faibles contrastes.
 * 3. Calcul automatique du seuil optimal de binarisation via la méthode d'Otsu.
 * 4. Détection automatique de la polarité (texte clair sur fond foncé vs texte foncé sur fond clair)
 *    par analyse des pixels de bordure de la ROI.
 */
object AutoImagePreprocessor {

    internal const val COLOR_BLACK = -0x1000000 // 0xFF000000
    internal const val COLOR_WHITE = -0x1       // 0xFFFFFFFF

    /**
     * Applique la rotation du capteur et découpe la région d'intérêt (ROI) de la frame.
     */
    fun cropAndRotateRoi(
        source: Bitmap,
        rotationDegrees: Int,
        roiFraction: RoiRectFraction = RoiRectFraction()
    ): Bitmap {
        val orientedBitmap = if (rotationDegrees != 0) {
            val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
            Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
        } else {
            source
        }

        val w = orientedBitmap.width
        val h = orientedBitmap.height

        val cropX = (w * roiFraction.leftFraction).roundToInt().coerceIn(0, max(0, w - 1))
        val cropY = (h * roiFraction.topFraction).roundToInt().coerceIn(0, max(0, h - 1))
        val cropW = (w * roiFraction.widthFraction).roundToInt().coerceIn(1, w - cropX)
        val cropH = (h * roiFraction.heightFraction).roundToInt().coerceIn(1, h - cropY)

        val cropped = Bitmap.createBitmap(orientedBitmap, cropX, cropY, cropW, cropH)
        if (orientedBitmap !== source && orientedBitmap !== cropped) {
            orientedBitmap.recycle()
        }
        return cropped
    }

    /**
     * Normalise, binarise (Otsu) et détecte automatiquement la polarité d'un [Bitmap] découpé.
     */
    fun preprocessForOcr(roiBitmap: Bitmap): PreprocessedImages {
        val width = roiBitmap.width
        val height = roiBitmap.height
        val totalPixels = width * height

        val argbPixels = IntArray(totalPixels)
        roiBitmap.getPixels(argbPixels, 0, width, 0, 0, width, height)

        val buffers = preprocessPixels(argbPixels, width, height)

        val primaryBitmap = createBitmap(width, height, Bitmap.Config.ARGB_8888)
        primaryBitmap.setPixels(buffers.primaryPixels, 0, width, 0, 0, width, height)

        val invertedBitmap = createBitmap(width, height, Bitmap.Config.ARGB_8888)
        invertedBitmap.setPixels(buffers.invertedPixels, 0, width, 0, 0, width, height)

        return PreprocessedImages(
            primaryBitmap = primaryBitmap,
            invertedBitmap = invertedBitmap,
            wasAutoInverted = buffers.wasAutoInverted,
        )
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

        val grayValues = IntArray(totalPixels)
        val rawHistogram = IntArray(256)

        // 1. Conversion en luminance (formule ITU-R BT.601 en virgule fixe rapide)
        for (i in 0 until totalPixels) {
            val pixel = argbPixels[i]
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            val luma = (77 * r + 150 * g + 29 * b) shr 8
            grayValues[i] = luma
            rawHistogram[luma]++
        }

        // 2. Étirement d'histogramme entre le 2e et le 98e percentile pour ignorer les reflets parasites
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

        val normalizedHistogram = IntArray(256)
        val range = max(1, maxLuma - minLuma)
        for (i in 0 until totalPixels) {
            val stretched = ((grayValues[i] - minLuma) * 255 / range).coerceIn(0, 255)
            grayValues[i] = stretched
            normalizedHistogram[stretched]++
        }

        // 3. Calcul du seuil optimal par la méthode d'Otsu
        val threshold = computeOtsuThreshold(normalizedHistogram, totalPixels)

        // 4. Détection automatique de la polarité via les bordures de la ROI
        // Les bords du cadre appartiennent très majoritairement à l'arrière-plan.
        var darkBorderPixels = 0
        var totalBorderPixels = 0

        for (x in 0 until width) {
            if (grayValues[x] <= threshold) darkBorderPixels++
            if (grayValues[(height - 1) * width + x] <= threshold) darkBorderPixels++
            totalBorderPixels += 2
        }
        for (y in 1 until height - 1) {
            if (grayValues[y * width] <= threshold) darkBorderPixels++
            if (grayValues[y * width + (width - 1)] <= threshold) darkBorderPixels++
            totalBorderPixels += 2
        }

        // Si > 50 % du contour est sombre, le fond est foncé (texte clair sur fond noir) : on inverse !
        val shouldInvert = totalBorderPixels > 0 && (darkBorderPixels * 2 > totalBorderPixels)

        val primaryPixels = IntArray(totalPixels)
        val invertedPixels = IntArray(totalPixels)

        for (i in 0 until totalPixels) {
            val isDarkPixel = grayValues[i] <= threshold
            val makeBlackInPrimary = if (shouldInvert) !isDarkPixel else isDarkPixel
            if (makeBlackInPrimary) {
                primaryPixels[i] = COLOR_BLACK
                invertedPixels[i] = COLOR_WHITE
            } else {
                primaryPixels[i] = COLOR_WHITE
                invertedPixels[i] = COLOR_BLACK
            }
        }

        return PreprocessedPixelBuffers(
            primaryPixels = primaryPixels,
            invertedPixels = invertedPixels,
            threshold = threshold,
            wasAutoInverted = shouldInvert,
        )
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
