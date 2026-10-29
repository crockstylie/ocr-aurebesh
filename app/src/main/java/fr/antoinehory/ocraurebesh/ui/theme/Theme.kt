package fr.antoinehory.ocraurebesh.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val HudDarkColorScheme = darkColorScheme(
    primary = HudCyan,
    onPrimary = Color(0xFF00262C),
    primaryContainer = HudCyanDim,
    onPrimaryContainer = HudTextPrimary,
    secondary = HudAmber,
    onSecondary = Color(0xFF261900),
    secondaryContainer = Color(0xFF2B2312),
    onSecondaryContainer = HudAmber,
    tertiary = HudEmerald,
    background = HudBackground,
    onBackground = HudTextPrimary,
    surface = HudBackground,
    onSurface = HudTextPrimary,
    surfaceVariant = HudSurfaceVariant,
    onSurfaceVariant = HudTextSecondary,
    error = HudCoral,
)

/**
 * Thème unifié sombre type "HUD Caméra / Holo-Scanner" garantissant
 * un contraste élevé et homogène par-dessus le flux vidéo CameraX.
 */
@Composable
fun OCRAurebeshTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = HudDarkColorScheme,
        typography = Typography,
        content = content,
    )
}
