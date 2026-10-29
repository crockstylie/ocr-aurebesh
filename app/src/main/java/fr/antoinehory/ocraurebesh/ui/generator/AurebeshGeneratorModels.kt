package fr.antoinehory.ocraurebesh.ui.generator

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.style.TextAlign

/**
 * Polices Aurebesh disponibles dans `assets/fonts/`.
 */
enum class AurebeshFontOption(
    val displayName: String,
    val assetPath: String,
) {
    REGULAR("Regular", "fonts/aurebesh_regular.ttf"),
    BOLD("Bold", "fonts/aurebesh_bold.ttf"),
    CONDENSED("Condensed", "fonts/aurebesh_condensed.ttf"),
}

/**
 * Alignement horizontal du texte Aurebesh.
 */
enum class AurebeshTextAlignment(
    val label: String,
    val composeTextAlign: TextAlign,
) {
    LEFT("Gauche", TextAlign.Start),
    CENTER("Centre", TextAlign.Center),
    RIGHT("Droite", TextAlign.End),
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
        ColorPreset("Blanc", 0xFFF0F6FC.toInt()),
        ColorPreset("Ambre", 0xFFFFB300.toInt()),
        ColorPreset("Émeraude", 0xFF00E676.toInt()),
        ColorPreset("Rouge Sith", 0xFFFF3D3D.toInt()),
        ColorPreset("Bleu Jedi", 0xFF2979FF.toInt()),
        ColorPreset("Violet", 0xFFD500F9.toInt()),
        ColorPreset("Noir", 0xFF0A0E14.toInt()),
    )

    val backgroundColors: List<ColorPreset> = listOf(
        ColorPreset("Noir Spatial", 0xFF070B10.toInt()),
        ColorPreset("Blanc", 0xFFFFFFFF.toInt()),
        ColorPreset("Gris Impérial", 0xFF1A2432.toInt()),
        ColorPreset("Bleu Sombre", 0xFF0B192C.toInt()),
        ColorPreset("Ambre Sombre", 0xFF261905.toInt()),
        ColorPreset("Rouge Sombre", 0xFF280909.toInt()),
        ColorPreset("Transparent", 0x00000000, isTransparent = true),
    )
}
