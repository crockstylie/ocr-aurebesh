package fr.antoinehory.ocraurebesh.ocr

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests unitaires de [TextLineFilter] vérifiant :
 * - L'extraction d'une chaîne alignée de glyphes réguliers (simulant un mot Aurebesh).
 * - Le rejet des bruits épars ou objets isolés (moins de 3 glyphes alignés).
 * - La fermeture morphologique verticale qui fusionne les barres empilées d'une lettre (ex: "H").
 */
class TextLineFilterTest {

    @Test
    fun `extract finds chain of aligned glyphs and computes tight crop`() {
        val width = 120
        val height = 60
        val mask = BooleanArray(width * height)

        // Dessin de 5 glyphes réguliers alignés horizontalement (y=24..35, hauteur 12px)
        // Espacés régulièrement en x : x=20..26, x=34..40, x=48..54, x=62..68, x=76..82
        for (g in 0 until 5) {
            val startX = 20 + g * 14
            for (y in 24..35) {
                for (x in startX..(startX + 6)) {
                    mask[y * width + x] = true
                }
            }
        }

        val region = TextLineFilter.extract(mask, width, height)

        assertNotNull("Une ligne de 5 glyphes alignés doit être détectée", region)
        assertTrue(
            "Le nombre de glyphes alignés (${region!!.alignedGlyphCount}) doit être >= 5",
            region.alignedGlyphCount >= 5,
        )
        // La région recadrée doit être plus compacte que la ROI d'origine
        assertTrue("La hauteur recadrée doit être < height", region.height < height)
        assertTrue("La largeur recadrée doit être < width", region.width < width)
    }

    @Test
    fun `extract rejects random isolated noise without aligned chain`() {
        val width = 80
        val height = 40
        val mask = BooleanArray(width * height)

        // Deux petits points isolés à des hauteurs totalement différentes
        mask[10 * width + 15] = true
        mask[30 * width + 60] = true

        val region = TextLineFilter.extract(mask, width, height)
        assertNull("Du bruit aléatoire non aligné ne doit pas produire de région de texte", region)
    }

    @Test
    fun `closeVertically connects stacked horizontal bars into single connected component`() {
        val width = 20
        val height = 20
        val mask = BooleanArray(width * height)

        // 3 barres horizontales séparées par 1px d'écart vertical (y=8, y=10, y=12)
        for (x in 6..14) {
            mask[8 * width + x] = true
            mask[10 * width + x] = true
            mask[12 * width + x] = true
        }

        val closed = TextLineFilter.closeVertically(mask, width, height, radius = 2)

        // Après fermeture verticale avec radius=2, les pixels intermédiaires (y=9 et y=11) doivent être reliés
        for (x in 6..14) {
            assertTrue("Le pixel intermédiaire (y=9) doit être comblé", closed[9 * width + x])
            assertTrue("Le pixel intermédiaire (y=11) doit être comblé", closed[11 * width + x])
        }
    }
}
