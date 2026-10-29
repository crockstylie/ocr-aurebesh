package fr.antoinehory.ocraurebesh.ui.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import fr.antoinehory.ocraurebesh.ui.theme.HudAmber
import fr.antoinehory.ocraurebesh.ui.theme.HudCyan
import fr.antoinehory.ocraurebesh.ui.theme.HudEmerald

/**
 * Superposition graphique dessinant les 4 repères d'angle HUD aux extrémités
 * de son conteneur (placé dans l'espace libre entre les barres d'interface afin
 * de garantir zéro chevauchement en Portrait comme en Paysage).
 */
@Composable
fun ScannerOverlay(
    isPaused: Boolean,
    hasRecognizedText: Boolean,
    modifier: Modifier = Modifier,
) {
    val accentColor = when {
        isPaused -> HudAmber
        hasRecognizedText -> HudEmerald
        else -> HudCyan
    }

    Spacer(
        modifier = modifier
            .fillMaxSize()
            .drawWithCache {
                val insetPx = 4.dp.toPx()
                val bracketThicknessPx = 3.dp.toPx()
                val bracketLengthPx = 24.dp.toPx()

                val left = insetPx
                val top = insetPx
                val right = (size.width - insetPx).coerceAtLeast(left + 1f)
                val bottom = (size.height - insetPx).coerceAtLeast(top + 1f)

                onDrawBehind {
                    // Coin supérieur gauche
                    drawLine(
                        color = accentColor,
                        start = Offset(left, top + bracketLengthPx),
                        end = Offset(left, top),
                        strokeWidth = bracketThicknessPx,
                        cap = StrokeCap.Round,
                    )
                    drawLine(
                        color = accentColor,
                        start = Offset(left, top),
                        end = Offset(left + bracketLengthPx, top),
                        strokeWidth = bracketThicknessPx,
                        cap = StrokeCap.Round,
                    )

                    // Coin supérieur droit
                    drawLine(
                        color = accentColor,
                        start = Offset(right - bracketLengthPx, top),
                        end = Offset(right, top),
                        strokeWidth = bracketThicknessPx,
                        cap = StrokeCap.Round,
                    )
                    drawLine(
                        color = accentColor,
                        start = Offset(right, top),
                        end = Offset(right, top + bracketLengthPx),
                        strokeWidth = bracketThicknessPx,
                        cap = StrokeCap.Round,
                    )

                    // Coin inférieur gauche
                    drawLine(
                        color = accentColor,
                        start = Offset(left, bottom - bracketLengthPx),
                        end = Offset(left, bottom),
                        strokeWidth = bracketThicknessPx,
                        cap = StrokeCap.Round,
                    )
                    drawLine(
                        color = accentColor,
                        start = Offset(left, bottom),
                        end = Offset(left + bracketLengthPx, bottom),
                        strokeWidth = bracketThicknessPx,
                        cap = StrokeCap.Round,
                    )

                    // Coin inférieur droit
                    drawLine(
                        color = accentColor,
                        start = Offset(right - bracketLengthPx, bottom),
                        end = Offset(right, bottom),
                        strokeWidth = bracketThicknessPx,
                        cap = StrokeCap.Round,
                    )
                    drawLine(
                        color = accentColor,
                        start = Offset(right, bottom - bracketLengthPx),
                        end = Offset(right, bottom),
                        strokeWidth = bracketThicknessPx,
                        cap = StrokeCap.Round,
                    )
                }
            }
    )
}
