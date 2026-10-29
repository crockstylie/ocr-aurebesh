package fr.antoinehory.ocraurebesh.ui.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import fr.antoinehory.ocraurebesh.ocr.RoiRectFraction

/**
 * Superposition graphique épurée dessinant uniquement les 4 repères d'angle HUD
 * délimitant la zone plein écran analysée ([roiFraction]), sans assombrir l'image.
 */
@Composable
fun ScannerOverlay(
    roiFraction: RoiRectFraction,
    isPaused: Boolean,
    hasRecognizedText: Boolean,
    modifier: Modifier = Modifier,
) {
    val accentColor = when {
        isPaused -> Color(0xFFFFB300)          // Ambre : Image figée
        hasRecognizedText -> Color(0xFF00E676) // Vert émeraude : Texte Aurebesh détecté
        else -> Color(0xFF00E5FF)              // Cyan hologramme : Scan actif
    }

    Spacer(
        modifier = modifier
            .fillMaxSize()
            .drawWithCache {
                val roiLeft = size.width * roiFraction.leftFraction
                val roiTop = size.height * roiFraction.topFraction
                val roiWidth = size.width * roiFraction.widthFraction
                val roiHeight = size.height * roiFraction.heightFraction

                val bracketThicknessPx = 3.5.dp.toPx()
                val bracketLengthPx = 28.dp.toPx()

                val roiRect = Rect(
                    offset = Offset(roiLeft, roiTop),
                    size = Size(roiWidth, roiHeight),
                )

                onDrawBehind {
                    val left = roiRect.left
                    val top = roiRect.top
                    val right = roiRect.right
                    val bottom = roiRect.bottom

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
