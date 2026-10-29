package fr.antoinehory.ocraurebesh.ui.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.dp
import fr.antoinehory.ocraurebesh.ocr.RoiRectFraction

/**
 * Superposition graphique (Overlay) dessinant le masque semi-transparent et le viseur HUD
 * délimitant la région d'intérêt ([roiFraction]).
 *
 * Utilise `Modifier.drawWithCache` (conformément au skill `jetpack-compose-performance`)
 * pour mettre en cache le [Path] de découpe et éviter toute allocation pendant la phase de dessin.
 */
@Composable
fun ScannerOverlay(
    roiFraction: RoiRectFraction,
    isPaused: Boolean,
    hasRecognizedText: Boolean,
    modifier: Modifier = Modifier,
) {
    val accentColor = when {
        isPaused -> Color(0xFFFFB300)          // Ambre : Scan figé / en pause
        hasRecognizedText -> Color(0xFF00E676) // Vert émeraude : Texte Aurebesh détecté
        else -> Color(0xFF00E5FF)              // Cyan hologramme : En attente / visée
    }

    Spacer(
        modifier = modifier
            .fillMaxSize()
            .drawWithCache {
                val roiLeft = size.width * roiFraction.leftFraction
                val roiTop = size.height * roiFraction.topFraction
                val roiWidth = size.width * roiFraction.widthFraction
                val roiHeight = size.height * roiFraction.heightFraction

                val cornerRadiusPx = 16.dp.toPx()
                val strokeWidthPx = 2.dp.toPx()
                val bracketThicknessPx = 4.dp.toPx()
                val bracketLengthPx = 28.dp.toPx()

                val roiRect = Rect(
                    offset = Offset(roiLeft, roiTop),
                    size = Size(roiWidth, roiHeight),
                )

                val cutoutPath = Path().apply {
                    addRoundRect(
                        RoundRect(
                            rect = roiRect,
                            cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx),
                        )
                    )
                }

                val borderStroke = Stroke(width = strokeWidthPx)

                onDrawBehind {
                    // 1. Voile sombre extérieur avec découpe transparente sur la ROI
                    clipPath(path = cutoutPath, clipOp = ClipOp.Difference) {
                        drawRect(color = Color.Black.copy(alpha = 0.58f))
                    }

                    // 2. Contour subtil de la zone de visée
                    drawRoundRect(
                        color = accentColor.copy(alpha = 0.45f),
                        topLeft = roiRect.topLeft,
                        size = roiRect.size,
                        cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx),
                        style = borderStroke,
                    )

                    // 3. Coins de visée HUD Star Wars
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
