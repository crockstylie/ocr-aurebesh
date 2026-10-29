package fr.antoinehory.ocraurebesh.ui.generator

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.style.TextAlign
import fr.antoinehory.ocraurebesh.R

/**
 * Polices Aurebesh disponibles dans `assets/fonts/`.
 */
enum class AurebeshFontOption(
    val displayName: String,
    val assetPath: String,
) {
    REGULAR("Regular", "fonts/aurebesh_regular.ttf"),
    BOLD("Bold", "fonts/aurebesh_bold.ttf"),
    ITALIC("Italic", "fonts/aurebesh_italic.ttf"),
    CONDENSED("Condensed", "fonts/aurebesh_condensed.ttf"),
    CANON_AF("Canon AF", "fonts/aurebesh_af_canon.otf"),
    CANTINA("Cantina", "fonts/aurebesh_cantina.otf"),
    DROID("Droid", "fonts/aurebesh_droid.ttf"),
    DROID_DEPOT("Droid Depot", "fonts/droidobesh_depot.otf"),
    HAND("Hand", "fonts/aurebesh_hand.ttf"),
}

/**
 * Alignement horizontal du texte Aurebesh.
 */
enum class AurebeshTextAlignment(
    @StringRes val labelResId: Int,
    val composeTextAlign: TextAlign,
) {
    LEFT(R.string.studio_align_left, TextAlign.Start),
    CENTER(R.string.studio_align_center, TextAlign.Center),
    RIGHT(R.string.studio_align_right, TextAlign.End),
}

/**
 * Formats d'export d'image supportés.
 */
enum class AurebeshExportFormat(
    val label: String,
    val extension: String,
    val mimeType: String,
) {
    SVG("SVG", "svg", "image/svg+xml"),
    PNG("PNG", "png", "image/png"),
    JPG("JPG", "jpg", "image/jpeg"),
}

/**
 * Pastille de couleur prédéfinie pour le texte ou l'arrière-plan.
 */
@Immutable
data class ColorPreset(
    val name: String,
    val argb: Int,
    val isTransparent: Boolean = false,
)

object AurebeshColorPresets {
    val textColors: List<ColorPreset> = listOf(
        ColorPreset("Cyan Holo", 0xFF00E5FF.toInt()),
        ColorPreset("White", 0xFFF0F6FC.toInt()),
        ColorPreset("Amber", 0xFFFFB300.toInt()),
        ColorPreset("Emerald", 0xFF00E676.toInt()),
        ColorPreset("Sith Red", 0xFFFF3D3D.toInt()),
        ColorPreset("Jedi Blue", 0xFF2979FF.toInt()),
        ColorPreset("Purple", 0xFFD500F9.toInt()),
        ColorPreset("Black", 0xFF0A0E14.toInt()),
    )

    val backgroundColors: List<ColorPreset> = listOf(
        ColorPreset("Space Black", 0xFF070B10.toInt()),
        ColorPreset("White", 0xFFFFFFFF.toInt()),
        ColorPreset("Imperial Gray", 0xFF1A2432.toInt()),
        ColorPreset("Dark Blue", 0xFF0B192C.toInt()),
        ColorPreset("Dark Amber", 0xFF261905.toInt()),
        ColorPreset("Dark Red", 0xFF280909.toInt()),
        ColorPreset("Transparent", 0x00000000, isTransparent = true),
    )
}
