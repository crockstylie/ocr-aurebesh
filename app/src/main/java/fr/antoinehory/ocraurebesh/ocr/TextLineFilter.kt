package fr.antoinehory.ocraurebesh.ocr

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Région de texte isolée par [TextLineFilter] : masque binaire recadré (`true` = encre)
 * ne contenant QUE les glyphes alignés en lignes de texte (le décor, les illustrations
 * et le bruit sont supprimés).
 */
internal class TextRegion(
    val mask: BooleanArray,
    val width: Int,
    val height: Int,
    val alignedGlyphCount: Int,
)

/**
 * Filtre « lignes de texte » basé sur les composantes connexes.
 *
 * Principe : un vrai texte Aurebesh est une suite d'au moins [MIN_CHAIN_LENGTH] formes
 * de même hauteur, alignées sur une même ligne de base et régulièrement espacées.
 * Une illustration (personnage, paysage, objets sur un bureau) ne respecte pas cette structure.
 *
 * Étapes :
 * 1. Fermeture morphologique **verticale uniquement** pour fusionner les glyphes en plusieurs
 *    morceaux empilés (ex. les 3 barres de « H ») sans coller deux lettres voisines.
 * 2. Étiquetage des composantes connexes (8-connexité) et filtrage par taille / proportions.
 * 3. Recherche de chaînes horizontales de glyphes alignés (haut et bas à ±25 % de la hauteur).
 * 4. Reconstruction d'un masque propre, recadré sur la zone de texte détectée.
 */
internal object TextLineFilter {

    private const val VERTICAL_CLOSING_RADIUS = 3
    private const val MIN_GLYPH_HEIGHT_PX = 8
    private const val MAX_GLYPH_HEIGHT_FRACTION = 0.30f
    private const val MAX_GLYPH_WIDTH_FRACTION = 0.25f
    private const val MAX_GLYPH_ASPECT = 2.4f
    private const val MIN_CHAIN_LENGTH = 3
    private const val MAX_CANDIDATE_GLYPHS = 1200
    private const val BORDER_MARGIN_PX = 2
    private const val CROP_MARGIN_PX = 6

    private class Box(
        val label: Int,
        var x1: Int,
        var y1: Int,
        var x2: Int,
        var y2: Int,
    ) {
        val w: Int get() = x2 - x1 + 1
        val h: Int get() = y2 - y1 + 1
    }

    /**
     * @return la [TextRegion] contenant les lignes de texte détectées, ou `null` si aucune
     * chaîne d'au moins [MIN_CHAIN_LENGTH] glyphes alignés n'est trouvée.
     */
    fun extract(mask: BooleanArray, width: Int, height: Int): TextRegion? {
        if (width < 16 || height < 16 || mask.size < width * height) return null

        val closed = closeVertically(mask, width, height, VERTICAL_CLOSING_RADIUS)
        val labels = IntArray(width * height)
        val boxes = labelComponents(closed, width, height, labels)

        val maxGlyphH = height * MAX_GLYPH_HEIGHT_FRACTION
        val maxGlyphW = width * MAX_GLYPH_WIDTH_FRACTION
        val glyphs = boxes.filter { b ->
            b.x1 > BORDER_MARGIN_PX && b.y1 > BORDER_MARGIN_PX &&
                b.x2 < width - 1 - BORDER_MARGIN_PX && b.y2 < height - 1 - BORDER_MARGIN_PX &&
                b.h >= MIN_GLYPH_HEIGHT_PX && b.h <= maxGlyphH &&
                b.w >= 2 && b.w <= maxGlyphW &&
                b.w <= b.h * MAX_GLYPH_ASPECT
        }
        if (glyphs.size < MIN_CHAIN_LENGTH || glyphs.size > MAX_CANDIDATE_GLYPHS) return null

        val sortedByX = glyphs.sortedBy { it.x1 }
        val keptLabels = BooleanArray(boxes.size + 1)
        var keptCount = 0

        for (seed in glyphs) {
            if (keptLabels[seed.label]) continue
            val sh = seed.h
            val tol = max(3, (sh * 0.25f).roundToInt())
            val minH = sh * 0.72f
            val maxH = sh * 1.38f

            var chain = ArrayList<Box>()
            var best: List<Box> = emptyList()
            for (b in sortedByX) {
                if (abs(b.y1 - seed.y1) > tol || abs(b.y2 - seed.y2) > tol) continue
                if (b.h < minH || b.h > maxH) continue
                if (chain.isEmpty()) {
                    chain.add(b)
                } else {
                    val gap = b.x1 - chain.last().x2 - 1
                    when {
                        gap > sh * 1.8f -> {
                            chain = arrayListOf(b)
                        }
                        gap >= -sh * 0.25f -> chain.add(b)
                        else -> Unit // Chevauchement trop important : on ignore ce morceau
                    }
                }
                if (chain.size > best.size) best = ArrayList(chain)
            }

            if (best.size >= MIN_CHAIN_LENGTH && best.any { it.label == seed.label }) {
                for (b in best) {
                    if (!keptLabels[b.label]) {
                        keptLabels[b.label] = true
                        keptCount++
                    }
                }
            }
        }

        if (keptCount < MIN_CHAIN_LENGTH) return null

        var minX = width
        var minY = height
        var maxX = -1
        var maxY = -1
        for (b in glyphs) {
            if (!keptLabels[b.label]) continue
            minX = min(minX, b.x1)
            minY = min(minY, b.y1)
            maxX = max(maxX, b.x2)
            maxY = max(maxY, b.y2)
        }
        val left = max(0, minX - CROP_MARGIN_PX)
        val top = max(0, minY - CROP_MARGIN_PX)
        val right = min(width - 1, maxX + CROP_MARGIN_PX)
        val bottom = min(height - 1, maxY + CROP_MARGIN_PX)
        val cropW = right - left + 1
        val cropH = bottom - top + 1

        val cropped = BooleanArray(cropW * cropH)
        for (y in 0 until cropH) {
            val srcRow = (top + y) * width
            val dstRow = y * cropW
            for (x in 0 until cropW) {
                val idx = srcRow + left + x
                cropped[dstRow + x] = mask[idx] && keptLabels[labels[idx]]
            }
        }

        return TextRegion(
            mask = cropped,
            width = cropW,
            height = cropH,
            alignedGlyphCount = keptCount,
        )
    }

    /**
     * Fermeture morphologique verticale (dilatation puis érosion sur une fenêtre `2r+1` x 1).
     */
    internal fun closeVertically(mask: BooleanArray, width: Int, height: Int, radius: Int): BooleanArray {
        val dilated = BooleanArray(width * height)
        val closed = BooleanArray(width * height)
        val prefix = IntArray(height + 1)

        for (x in 0 until width) {
            for (y in 0 until height) {
                prefix[y + 1] = prefix[y] + if (mask[y * width + x]) 1 else 0
            }
            for (y in 0 until height) {
                val y1 = max(0, y - radius)
                val y2 = min(height - 1, y + radius)
                dilated[y * width + x] = prefix[y2 + 1] - prefix[y1] > 0
            }
        }

        for (x in 0 until width) {
            for (y in 0 until height) {
                prefix[y + 1] = prefix[y] + if (dilated[y * width + x]) 1 else 0
            }
            for (y in 0 until height) {
                val y1 = max(0, y - radius)
                val y2 = min(height - 1, y + radius)
                closed[y * width + x] = (prefix[y2 + 1] - prefix[y1]) == (y2 - y1 + 1)
            }
        }
        return closed
    }

    /**
     * Étiquetage itératif (sans récursion) des composantes connexes en 8-connexité.
     * Les labels commencent à 1 ; `labels[i] == 0` signifie « fond ».
     */
    private fun labelComponents(
        mask: BooleanArray,
        width: Int,
        height: Int,
        labels: IntArray,
    ): List<Box> {
        val boxes = ArrayList<Box>()
        val stack = IntArray(width * height)
        var nextLabel = 1

        for (start in 0 until width * height) {
            if (!mask[start] || labels[start] != 0) continue
            val label = nextLabel++
            val box = Box(label, start % width, start / width, start % width, start / width)
            var sp = 0
            stack[sp++] = start
            labels[start] = label

            while (sp > 0) {
                val idx = stack[--sp]
                val x = idx % width
                val y = idx / width
                if (x < box.x1) box.x1 = x
                if (x > box.x2) box.x2 = x
                if (y < box.y1) box.y1 = y
                if (y > box.y2) box.y2 = y

                for (dy in -1..1) {
                    val ny = y + dy
                    if (ny < 0 || ny >= height) continue
                    for (dx in -1..1) {
                        if (dx == 0 && dy == 0) continue
                        val nx = x + dx
                        if (nx < 0 || nx >= width) continue
                        val nIdx = ny * width + nx
                        if (mask[nIdx] && labels[nIdx] == 0) {
                            labels[nIdx] = label
                            stack[sp++] = nIdx
                        }
                    }
                }
            }
            boxes.add(box)
        }
        return boxes
    }
}
