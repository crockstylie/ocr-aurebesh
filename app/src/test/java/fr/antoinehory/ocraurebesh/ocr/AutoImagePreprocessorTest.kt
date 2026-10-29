package fr.antoinehory.ocraurebesh.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests unitaires de [AutoImagePreprocessor] vérifiant :
 * - Le calcul du seuil optimal de binarisation d'Otsu.
 * - La détection automatique de polarité (texte sombre sur fond clair vs texte clair/néon sur fond sombre).
 * - L'étirement d'histogramme 2%-98% sur des textes à faible contraste ou colorés (hologrammes).
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
        val width = 20
        val height = 20
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
        // Le centre (glyphe) doit être noir dans primaryPixels et blanc dans invertedPixels
        val centerIndex = (height / 2) * width + (width / 2)
        assertEquals(AutoImagePreprocessor.COLOR_BLACK, result.primaryPixels[centerIndex])
        assertEquals(AutoImagePreprocessor.COLOR_WHITE, result.invertedPixels[centerIndex])

        // Le coin (fond) doit être blanc dans primaryPixels
        assertEquals(AutoImagePreprocessor.COLOR_WHITE, result.primaryPixels[0])
    }

    @Test
    fun `preprocessPixels automatically detects and inverts light text on dark background`() {
        val width = 20
        val height = 20
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
        // Après inversion automatique, le glyphe au centre doit être NOIR pour Tesseract
        val centerIndex = (height / 2) * width + (width / 2)
        assertEquals(AutoImagePreprocessor.COLOR_BLACK, result.primaryPixels[centerIndex])
        // Et la bordure (arrière-plan) doit être BLANCHE pour Tesseract
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

    /**
     * Génère une image synthétique avec un fond uniforme sur tout le cadre
     * et un bloc central représentant un glyphe Aurebesh.
     */
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
