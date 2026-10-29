package fr.antoinehory.ocraurebesh.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests unitaires de [AutoImagePreprocessor] vérifiant :
 * - Le calcul du seuil optimal de binarisation d'Otsu.
 * - La détection automatique de polarité (texte sombre sur fond clair vs texte clair/néon sur fond sombre).
 * - La préservation des petits textes (< 1 % de la ROI) grâce aux percentiles 0.05 % - 99.95 %.
 * - L'extraction des lettres en mode contour (intérieur de la lettre de la même couleur que le fond).
 * - L'extraction des textes flous et à faible contraste via le rehaussement Unsharp Mask 5x5.
 * - Le rejet du bruit sur une surface de bureau uniforme.
 */
class AutoImagePreprocessorTest {

    @Test
    fun `computeOtsuThreshold separates bimodal histogram accurately`() {
        val histogram = IntArray(256)
        histogram[35] = 600
        histogram[220] = 400

        val threshold = AutoImagePreprocessor.computeOtsuThreshold(
            histogram = histogram,
            totalPixels = 1000,
        )

        assertTrue(
            "Le seuil d'Otsu ($threshold) doit se situer entre les deux pics [35..220[",
            threshold in 35 until 220,
        )
    }

    @Test
    fun `preprocessPixels keeps dark text on light background without inversion`() {
        val width = 24
        val height = 24
        val pixels = buildSyntheticRoi(
            width = width,
            height = height,
            backgroundRgb = 0xF5F5F5, // Fond papier blanc cassé
            glyphRgb = 0x181818,      // Encre noire
        )

        val result = AutoImagePreprocessor.preprocessPixels(pixels, width, height)

        assertFalse(
            "Un texte sombre sur fond clair ne doit pas être auto-inversé",
            result.wasAutoInverted,
        )
        val centerIndex = (height / 2) * width + (width / 2)
        assertEquals(AutoImagePreprocessor.COLOR_BLACK, result.primaryPixels[centerIndex])
        assertEquals(AutoImagePreprocessor.COLOR_WHITE, result.invertedPixels[centerIndex])
        assertEquals(AutoImagePreprocessor.COLOR_WHITE, result.primaryPixels[0])
    }

    @Test
    fun `preprocessPixels detects sparse black text covering less than 1 percent of white ROI`() {
        val width = 100
        val height = 100
        val bgArgb = (0xFF shl 24) or 0xF2F2F2
        val fgArgb = (0xFF shl 24) or 0x121212
        val pixels = IntArray(width * height) { bgArgb }

        // Trait fin couvrant seulement 0.6 % des pixels (60 pixels sur 10 000)
        for (y in 48..50) {
            for (x in 40 until 60) {
                pixels[y * width + x] = fgArgb
            }
        }

        val result = AutoImagePreprocessor.preprocessPixels(pixels, width, height)

        assertFalse(result.wasAutoInverted)
        assertEquals(AutoImagePreprocessor.COLOR_BLACK, result.primaryPixels[49 * width + 50])
        assertTrue(
            "Le ratio d'encre (${result.primaryInkRatio}) doit être > 0.002f",
            result.primaryInkRatio > 0.002f,
        )
    }

    @Test
    fun `preprocessPixels extracts outlined hollow letters where letter interior matches background`() {
        val width = 48
        val height = 48
        val bgArgb = (0xFF shl 24) or 0xEEEEEE
        val contourArgb = (0xFF shl 24) or 0x222222
        val pixels = IntArray(width * height) { bgArgb }

        // Lettre en mode contour (cadre creux de 2px d'épaisseur entre 14..33, intérieur 16..31 identique au fond)
        for (y in 14..33) {
            for (x in 14..33) {
                val isContour = y in 14..15 || y in 32..33 || x in 14..15 || x in 32..33
                if (isContour) {
                    pixels[y * width + x] = contourArgb
                }
            }
        }

        val result = AutoImagePreprocessor.preprocessPixels(pixels, width, height)

        // Le contour (x=15, y=24) doit être noir et l'intérieur creux (x=24, y=24) doit rester blanc
        assertEquals(AutoImagePreprocessor.COLOR_BLACK, result.primaryPixels[24 * width + 15])
        assertEquals(AutoImagePreprocessor.COLOR_WHITE, result.primaryPixels[24 * width + 24])
    }

    @Test
    fun `preprocessPixels automatically detects and inverts light text on dark background`() {
        val width = 24
        val height = 24
        val pixels = buildSyntheticRoi(
            width = width,
            height = height,
            backgroundRgb = 0x10141C, // Écran sombre
            glyphRgb = 0xFAFAFA,      // Texte lumineux blanc
        )

        val result = AutoImagePreprocessor.preprocessPixels(pixels, width, height)

        assertTrue(
            "Un texte clair sur fond sombre doit déclencher l'inversion automatique",
            result.wasAutoInverted,
        )
        val centerIndex = (height / 2) * width + (width / 2)
        assertEquals(AutoImagePreprocessor.COLOR_BLACK, result.primaryPixels[centerIndex])
        assertEquals(AutoImagePreprocessor.COLOR_WHITE, result.primaryPixels[0])
    }

    @Test
    fun `preprocessPixels normalizes low-contrast hologram cyan text on dark background`() {
        val width = 24
        val height = 24
        val pixels = buildSyntheticRoi(
            width = width,
            height = height,
            backgroundRgb = 0x1A222A, // Fond sombre bleuté
            glyphRgb = 0x00A8CC,      // Texte hologramme cyan à contraste modéré
        )

        val result = AutoImagePreprocessor.preprocessPixels(pixels, width, height)

        assertTrue(
            "Le texte hologramme cyan sur fond sombre doit être auto-inversé",
            result.wasAutoInverted,
        )
        assertTrue(
            "Le seuil Otsu (${result.threshold}) doit être valide",
            result.threshold in 10..245,
        )
        val centerIndex = (height / 2) * width + (width / 2)
        assertEquals(AutoImagePreprocessor.COLOR_BLACK, result.primaryPixels[centerIndex])
        assertEquals(AutoImagePreprocessor.COLOR_WHITE, result.primaryPixels[0])
    }

    @Test
    fun `preprocessPixels preserves glyph strokes under uneven horizontal lighting gradient`() {
        val width = 40
        val height = 20
        val pixels = IntArray(width * height)

        // Fond avec fort gradient d'éclairage de gauche (sombre: 130) à droite (clair: 245)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val bgLuma = 130 + (x * 115) / width
                val isGlyphStroke = (y in 7..12) && (x in 8..11 || x in 28..31)
                val luma = if (isGlyphStroke) bgLuma - 35 else bgLuma
                val clamped = luma.coerceIn(0, 255)
                pixels[y * width + x] = (0xFF shl 24) or (clamped shl 16) or (clamped shl 8) or clamped
            }
        }

        val result = AutoImagePreprocessor.preprocessPixels(pixels, width, height)

        // Le glyphe dans la zone ombragée (x=9, y=9) ET le glyphe dans la zone éclairée (x=29, y=9)
        // doivent tous deux être détectés en noir même avec un faible contraste local (-35)
        assertEquals(AutoImagePreprocessor.COLOR_BLACK, result.primaryPixels[9 * width + 9])
        assertEquals(AutoImagePreprocessor.COLOR_BLACK, result.primaryPixels[9 * width + 29])
    }

    @Test
    fun `preprocessPixels suppresses low-contrast uniform desk surface noise`() {
        val width = 32
        val height = 32
        val pixels = IntArray(width * height)

        // Simulation d'un bureau avec de légères variations de grain de bois (luminance entre 110 et 122)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val luma = 110 + ((x * 3 + y * 5) % 13)
                pixels[y * width + x] = (0xFF shl 24) or (luma shl 16) or (luma shl 8) or luma
            }
        }

        val result = AutoImagePreprocessor.preprocessPixels(pixels, width, height)

        assertEquals(0f, result.primaryInkRatio, 0.001f)
        assertEquals(0f, result.invertedInkRatio, 0.001f)
    }

    private fun buildSyntheticRoi(
        width: Int,
        height: Int,
        backgroundRgb: Int,
        glyphRgb: Int,
    ): IntArray {
        val bgArgb = (0xFF shl 24) or (backgroundRgb and 0xFFFFFF)
        val fgArgb = (0xFF shl 24) or (glyphRgb and 0xFFFFFF)
        val pixels = IntArray(width * height) { bgArgb }

        val startX = width / 4
        val endX = (width * 3) / 4
        val startY = height / 4
        val endY = (height * 3) / 4

        for (y in startY until endY) {
            for (x in startX until endX) {
                pixels[y * width + x] = fgArgb
            }
        }
        return pixels
    }
}
