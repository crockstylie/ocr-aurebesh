package fr.antoinehory.ocraurebesh.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.dp
import fr.antoinehory.ocraurebesh.ui.scanner.NormalizedCropRect
import fr.antoinehory.ocraurebesh.ui.theme.HudAmber
import fr.antoinehory.ocraurebesh.ui.theme.HudCyan
import fr.antoinehory.ocraurebesh.ui.theme.HudEmerald
import kotlin.math.abs

/**
 * Superposition graphique HUD du scanner Aurebesh :
 * 1. En direct ([isPaused] == false) :
 *    - 4 repères d'angle HUD (cyan ou émeraude si texte détecté).
 *    - Animation de balayage vertical type "lame de sabre laser" discrète avec aura luminescente.
 * 2. En mode figé ([isPaused] == true) :
 *    - 4 repères d'angle ambrés.
 *    - Cadre de recadrage interactif avec poignées d'angle et de bordures ajustables au doigt.
 *    - Déclenchement automatique d'une analyse OCR sur la zone recadrée lors du relâchement.
 *    - Zéro texte superflu et zéro bouton redondant dans le viseur (centralisés dans la barre contextuelle).
 */
@Composable
fun ScannerOverlay(
    isPaused: Boolean,
    hasRecognizedText: Boolean,
    modifier: Modifier = Modifier,
    cropRect: NormalizedCropRect = NormalizedCropRect(),
    onCropChanged: (NormalizedCropRect) -> Unit = {},
    onCropCommitted: () -> Unit = {},
    onOverlayPositionedInRoot: (Offset, Size) -> Unit = { _, _ -> },
) {
    val accentColor = when {
        isPaused -> HudAmber
        hasRecognizedText -> HudEmerald
        else -> HudCyan
    }

    val infiniteTransition = rememberInfiniteTransition(label = "lightsaber_scan")
    val scanProgress by infiniteTransition.animateFloat(
        initialValue = 0.04f,
        targetValue = 0.96f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "scan_progress",
    )

    var activeCrop by remember(cropRect, isPaused) { mutableStateOf(cropRect) }
    var currentDragTarget by remember { mutableStateOf(DragTarget.NONE) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { coordinates ->
                onOverlayPositionedInRoot(
                    coordinates.positionInRoot(),
                    Size(coordinates.size.width.toFloat(), coordinates.size.height.toFloat()),
                )
            }
    ) {
        val overlayWidthPx = constraints.maxWidth.toFloat()
        val overlayHeightPx = constraints.maxHeight.toFloat()

        val gestureModifier = if (isPaused) {
            Modifier.pointerInput(isPaused) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val insetPx = 4.dp.toPx()
                        val left = insetPx
                        val top = insetPx
                        val right = (overlayWidthPx - insetPx).coerceAtLeast(left + 1f)
                        val bottom = (overlayHeightPx - insetPx).coerceAtLeast(top + 1f)
                        val spanW = right - left
                        val spanH = bottom - top

                        val bL = left + spanW * activeCrop.left
                        val bT = top + spanH * activeCrop.top
                        val bR = left + spanW * activeCrop.right
                        val bB = top + spanH * activeCrop.bottom

                        val handleRadius = 36.dp.toPx()
                        val edgeTol = 24.dp.toPx()

                        currentDragTarget = when {
                            (offset - Offset(bL, bT)).getDistance() <= handleRadius -> DragTarget.TOP_LEFT
                            (offset - Offset(bR, bT)).getDistance() <= handleRadius -> DragTarget.TOP_RIGHT
                            (offset - Offset(bL, bB)).getDistance() <= handleRadius -> DragTarget.BOTTOM_LEFT
                            (offset - Offset(bR, bB)).getDistance() <= handleRadius -> DragTarget.BOTTOM_RIGHT
                            abs(offset.y - bT) <= edgeTol && offset.x in (bL - 10f)..(bR + 10f) -> DragTarget.TOP
                            abs(offset.y - bB) <= edgeTol && offset.x in (bL - 10f)..(bR + 10f) -> DragTarget.BOTTOM
                            abs(offset.x - bL) <= edgeTol && offset.y in (bT - 10f)..(bB + 10f) -> DragTarget.LEFT
                            abs(offset.x - bR) <= edgeTol && offset.y in (bT - 10f)..(bB + 10f) -> DragTarget.RIGHT
                            offset.x in bL..bR && offset.y in bT..bB -> DragTarget.CENTER
                            else -> DragTarget.CENTER
                        }
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val insetPx = 4.dp.toPx()
                        val left = insetPx
                        val top = insetPx
                        val right = (overlayWidthPx - insetPx).coerceAtLeast(left + 1f)
                        val bottom = (overlayHeightPx - insetPx).coerceAtLeast(top + 1f)
                        val spanW = (right - left).coerceAtLeast(1f)
                        val spanH = (bottom - top).coerceAtLeast(1f)

                        val dx = dragAmount.x / spanW
                        val dy = dragAmount.y / spanH
                        val minSpan = 0.06f

                        activeCrop = when (currentDragTarget) {
                            DragTarget.TOP_LEFT -> {
                                val nL = (activeCrop.left + dx).coerceIn(0.01f, activeCrop.right - minSpan)
                                val nT = (activeCrop.top + dy).coerceIn(0.01f, activeCrop.bottom - minSpan)
                                activeCrop.copy(left = nL, top = nT)
                            }
                            DragTarget.TOP_RIGHT -> {
                                val nR = (activeCrop.right + dx).coerceIn(activeCrop.left + minSpan, 0.99f)
                                val nT = (activeCrop.top + dy).coerceIn(0.01f, activeCrop.bottom - minSpan)
                                activeCrop.copy(right = nR, top = nT)
                            }
                            DragTarget.BOTTOM_LEFT -> {
                                val nL = (activeCrop.left + dx).coerceIn(0.01f, activeCrop.right - minSpan)
                                val nB = (activeCrop.bottom + dy).coerceIn(activeCrop.top + minSpan, 0.99f)
                                activeCrop.copy(left = nL, bottom = nB)
                            }
                            DragTarget.BOTTOM_RIGHT -> {
                                val nR = (activeCrop.right + dx).coerceIn(activeCrop.left + minSpan, 0.99f)
                                val nB = (activeCrop.bottom + dy).coerceIn(activeCrop.top + minSpan, 0.99f)
                                activeCrop.copy(right = nR, bottom = nB)
                            }
                            DragTarget.TOP -> {
                                val nT = (activeCrop.top + dy).coerceIn(0.01f, activeCrop.bottom - minSpan)
                                activeCrop.copy(top = nT)
                            }
                            DragTarget.BOTTOM -> {
                                val nB = (activeCrop.bottom + dy).coerceIn(activeCrop.top + minSpan, 0.99f)
                                activeCrop.copy(bottom = nB)
                            }
                            DragTarget.LEFT -> {
                                val nL = (activeCrop.left + dx).coerceIn(0.01f, activeCrop.right - minSpan)
                                activeCrop.copy(left = nL)
                            }
                            DragTarget.RIGHT -> {
                                val nR = (activeCrop.right + dx).coerceIn(activeCrop.left + minSpan, 0.99f)
                                activeCrop.copy(right = nR)
                            }
                            DragTarget.CENTER -> {
                                val w = activeCrop.width
                                val h = activeCrop.height
                                val nL = (activeCrop.left + dx).coerceIn(0.01f, 0.99f - w)
                                val nT = (activeCrop.top + dy).coerceIn(0.01f, 0.99f - h)
                                NormalizedCropRect(nL, nT, nL + w, nT + h)
                            }
                            DragTarget.NONE -> activeCrop
                        }
                    },
                    onDragEnd = {
                        currentDragTarget = DragTarget.NONE
                        onCropChanged(activeCrop)
                        onCropCommitted()
                    },
                    onDragCancel = {
                        currentDragTarget = DragTarget.NONE
                    },
                )
            }
        } else {
            Modifier
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .then(gestureModifier)
        ) {
            val insetPx = 4.dp.toPx()
            val bracketThicknessPx = 3.dp.toPx()
            val bracketLengthPx = 24.dp.toPx()

            val left = insetPx
            val top = insetPx
            val right = (size.width - insetPx).coerceAtLeast(left + 1f)
            val bottom = (size.height - insetPx).coerceAtLeast(top + 1f)
            val spanW = right - left
            val spanH = bottom - top

            // 1. Repères d'angle principaux du viseur
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

            // 2. Animation de la lame de sabre laser (en direct uniquement)
            if (!isPaused) {
                val scanY = top + spanH * scanProgress
                val auraHalfHeight = 22.dp.toPx()

                // Halo lumineux / aura du sabre laser
                val auraBrush = Brush.verticalGradient(
                    colors = listOf(
                        accentColor.copy(alpha = 0f),
                        accentColor.copy(alpha = 0.22f),
                        accentColor.copy(alpha = 0f),
                    ),
                    startY = scanY - auraHalfHeight,
                    endY = scanY + auraHalfHeight,
                )
                drawRect(
                    brush = auraBrush,
                    topLeft = Offset(left + 2f, scanY - auraHalfHeight),
                    size = Size(spanW - 4f, auraHalfHeight * 2f),
                )

                // Faisceau laser extérieur coloré
                drawLine(
                    color = accentColor.copy(alpha = 0.88f),
                    start = Offset(left + 4.dp.toPx(), scanY),
                    end = Offset(right - 4.dp.toPx(), scanY),
                    strokeWidth = 2.4.dp.toPx(),
                    cap = StrokeCap.Round,
                )

                // Cœur blanc éclatant de la lame
                drawLine(
                    color = Color.White.copy(alpha = 0.90f),
                    start = Offset(left + 14.dp.toPx(), scanY),
                    end = Offset(right - 14.dp.toPx(), scanY),
                    strokeWidth = 1.2.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            } else {
                // 3. Cadre de recadrage interactif (en mode figé)
                val bL = left + spanW * activeCrop.left
                val bT = top + spanH * activeCrop.top
                val bR = left + spanW * activeCrop.right
                val bB = top + spanH * activeCrop.bottom

                val scrimColor = Color.Black.copy(alpha = 0.42f)

                // Voile sombre sur les 4 zones extérieures au cadre sélectionné
                drawRect(scrimColor, Offset(0f, 0f), Size(size.width, bT))
                drawRect(scrimColor, Offset(0f, bB), Size(size.width, size.height - bB))
                drawRect(scrimColor, Offset(0f, bT), Size(bL, bB - bT))
                drawRect(scrimColor, Offset(bR, bT), Size(size.width - bR, bB - bT))

                // Contour du cadre de recadrage
                drawRect(
                    color = HudAmber.copy(alpha = 0.85f),
                    topLeft = Offset(bL, bT),
                    size = Size(bR - bL, bB - bT),
                    style = Stroke(width = 1.5.dp.toPx()),
                )

                // Repères d'angles du cadre de recadrage
                val cropCornerLength = 16.dp.toPx()
                val cropCornerThick = 3.dp.toPx()

                // Coin TL
                drawLine(HudAmber, Offset(bL, bT), Offset(bL + cropCornerLength, bT), cropCornerThick, StrokeCap.Round)
                drawLine(HudAmber, Offset(bL, bT), Offset(bL, bT + cropCornerLength), cropCornerThick, StrokeCap.Round)
                // Coin TR
                drawLine(HudAmber, Offset(bR - cropCornerLength, bT), Offset(bR, bT), cropCornerThick, StrokeCap.Round)
                drawLine(HudAmber, Offset(bR, bT), Offset(bR, bT + cropCornerLength), cropCornerThick, StrokeCap.Round)
                // Coin BL
                drawLine(HudAmber, Offset(bL, bB), Offset(bL + cropCornerLength, bB), cropCornerThick, StrokeCap.Round)
                drawLine(HudAmber, Offset(bL, bB - cropCornerLength), Offset(bL, bB), cropCornerThick, StrokeCap.Round)
                // Coin BR
                drawLine(HudAmber, Offset(bR - cropCornerLength, bB), Offset(bR, bB), cropCornerThick, StrokeCap.Round)
                drawLine(HudAmber, Offset(bR, bB - cropCornerLength), Offset(bR, bB), cropCornerThick, StrokeCap.Round)

                // Pastilles circulaires tactiles aux 4 coins
                val handleRadius = 4.5.dp.toPx()
                val whiteCoreRadius = 2.dp.toPx()
                listOf(
                    Offset(bL, bT),
                    Offset(bR, bT),
                    Offset(bL, bB),
                    Offset(bR, bB),
                ).forEach { pt ->
                    drawCircle(color = HudAmber, radius = handleRadius, center = pt)
                    drawCircle(color = Color.White, radius = whiteCoreRadius, center = pt)
                }

                // Barres de préhension au centre des 4 bordures
                val edgeBarHalf = 12.dp.toPx()
                drawLine(HudAmber, Offset((bL + bR) * 0.5f - edgeBarHalf, bT), Offset((bL + bR) * 0.5f + edgeBarHalf, bT), 3.dp.toPx(), StrokeCap.Round)
                drawLine(HudAmber, Offset((bL + bR) * 0.5f - edgeBarHalf, bB), Offset((bL + bR) * 0.5f + edgeBarHalf, bB), 3.dp.toPx(), StrokeCap.Round)
                drawLine(HudAmber, Offset(bL, (bT + bB) * 0.5f - edgeBarHalf), Offset(bL, (bT + bB) * 0.5f + edgeBarHalf), 3.dp.toPx(), StrokeCap.Round)
                drawLine(HudAmber, Offset(bR, (bT + bB) * 0.5f - edgeBarHalf), Offset(bR, (bT + bB) * 0.5f + edgeBarHalf), 3.dp.toPx(), StrokeCap.Round)
            }
        }
    }
}

private enum class DragTarget {
    NONE,
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT,
    TOP,
    BOTTOM,
    LEFT,
    RIGHT,
    CENTER,
}
