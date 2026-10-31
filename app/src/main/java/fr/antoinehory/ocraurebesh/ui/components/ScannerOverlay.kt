package fr.antoinehory.ocraurebesh.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
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
 *    - Cadre de recadrage interactif ultra réactif (suivi tactile immédiat via `awaitEachGesture`).
 *    - Redessin optimisé en phase Draw (zéro recomposition, zéro lag, zéro temps de latence au relâchement).
 *    - Déclenchement automatique d'une analyse OCR sur la zone recadrée lors du relâchement.
 */
@Composable
fun ScannerOverlay(
    isPaused: Boolean,
    hasRecognizedText: Boolean,
    modifier: Modifier = Modifier,
    cropRect: NormalizedCropRect = NormalizedCropRect(),
    onCropChanged: (NormalizedCropRect) -> Unit = {},
    onCropCommitted: (NormalizedCropRect) -> Unit = {},
    onOverlayPositionedInRoot: (Offset, Size) -> Unit = { _, _ -> },
) {
    val accentColor = when {
        isPaused -> HudAmber
        hasRecognizedText -> HudEmerald
        else -> HudCyan
    }

    // Animation sabre laser active uniquement en direct
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

    var activeCrop by remember { mutableStateOf(cropRect) }

    LaunchedEffect(isPaused, cropRect) {
        if (isPaused) {
            activeCrop = cropRect
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { coordinates ->
                onOverlayPositionedInRoot(
                    coordinates.positionInWindow(),
                    Size(coordinates.size.width.toFloat(), coordinates.size.height.toFloat()),
                )
            }
    ) {
        val gestureModifier = if (isPaused) {
            Modifier.pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val width = size.width.toFloat()
                    val height = size.height.toFloat()

                    val insetPx = 4.dp.toPx()
                    val left = insetPx
                    val top = insetPx
                    val right = (width - insetPx).coerceAtLeast(left + 1f)
                    val bottom = (height - insetPx).coerceAtLeast(top + 1f)
                    val spanW = (right - left).coerceAtLeast(1f)
                    val spanH = (bottom - top).coerceAtLeast(1f)

                    var current = activeCrop
                    val bL = left + spanW * current.left
                    val bT = top + spanH * current.top
                    val bR = left + spanW * current.right
                    val bB = top + spanH * current.bottom

                    val handleRadius = 40.dp.toPx()
                    val edgeTol = 28.dp.toPx()
                    val offset = down.position

                    val target = when {
                        (offset - Offset(bL, bT)).getDistance() <= handleRadius -> DragTarget.TOP_LEFT
                        (offset - Offset(bR, bT)).getDistance() <= handleRadius -> DragTarget.TOP_RIGHT
                        (offset - Offset(bL, bB)).getDistance() <= handleRadius -> DragTarget.BOTTOM_LEFT
                        (offset - Offset(bR, bB)).getDistance() <= handleRadius -> DragTarget.BOTTOM_RIGHT
                        abs(offset.y - bT) <= edgeTol && offset.x in (bL - 12f)..(bR + 12f) -> DragTarget.TOP
                        abs(offset.y - bB) <= edgeTol && offset.x in (bL - 12f)..(bR + 12f) -> DragTarget.BOTTOM
                        abs(offset.x - bL) <= edgeTol && offset.y in (bT - 12f)..(bB + 12f) -> DragTarget.LEFT
                        abs(offset.x - bR) <= edgeTol && offset.y in (bT - 12f)..(bB + 12f) -> DragTarget.RIGHT
                        // Tap strictement à l'intérieur du cadre (hors bordures) : déplacement
                        offset.x in (bL + edgeTol)..(bR - edgeTol) && offset.y in (bT + edgeTol)..(bB - edgeTol) -> DragTarget.CENTER
                        // Tap en dehors du cadre ou sur la bordure sans poignée : ignore
                        else -> DragTarget.NONE
                    }

                    down.consume()

                    val minSpan = 0.06f
                    val pointerId = down.id

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                        if (!change.pressed) {
                            change.consume()
                            break
                        }

                        val dragAmount = change.positionChange()
                        if (dragAmount != Offset.Zero) {
                            change.consume()
                            val dx = dragAmount.x / spanW
                            val dy = dragAmount.y / spanH

                            current = when (target) {
                                DragTarget.TOP_LEFT -> {
                                    val nL = (current.left + dx).coerceIn(0.01f, current.right - minSpan)
                                    val nT = (current.top + dy).coerceIn(0.01f, current.bottom - minSpan)
                                    current.copy(left = nL, top = nT)
                                }
                                DragTarget.TOP_RIGHT -> {
                                    val nR = (current.right + dx).coerceIn(current.left + minSpan, 0.99f)
                                    val nT = (current.top + dy).coerceIn(0.01f, current.bottom - minSpan)
                                    current.copy(right = nR, top = nT)
                                }
                                DragTarget.BOTTOM_LEFT -> {
                                    val nL = (current.left + dx).coerceIn(0.01f, current.right - minSpan)
                                    val nB = (current.bottom + dy).coerceIn(current.top + minSpan, 0.99f)
                                    current.copy(left = nL, bottom = nB)
                                }
                                DragTarget.BOTTOM_RIGHT -> {
                                    val nR = (current.right + dx).coerceIn(current.left + minSpan, 0.99f)
                                    val nB = (current.bottom + dy).coerceIn(current.top + minSpan, 0.99f)
                                    current.copy(right = nR, bottom = nB)
                                }
                                DragTarget.TOP -> {
                                    val nT = (current.top + dy).coerceIn(0.01f, current.bottom - minSpan)
                                    current.copy(top = nT)
                                }
                                DragTarget.BOTTOM -> {
                                    val nB = (current.bottom + dy).coerceIn(current.top + minSpan, 0.99f)
                                    current.copy(bottom = nB)
                                }
                                DragTarget.LEFT -> {
                                    val nL = (current.left + dx).coerceIn(0.01f, current.right - minSpan)
                                    current.copy(left = nL)
                                }
                                DragTarget.RIGHT -> {
                                    val nR = (current.right + dx).coerceIn(current.left + minSpan, 0.99f)
                                    current.copy(right = nR)
                                }
                                DragTarget.CENTER -> {
                                    val w = current.width
                                    val h = current.height
                                    val nL = (current.left + dx).coerceIn(0.01f, 0.99f - w)
                                    val nT = (current.top + dy).coerceIn(0.01f, 0.99f - h)
                                    NormalizedCropRect(nL, nT, nL + w, nT + h)
                                }
                                DragTarget.NONE -> current
                            }
                            activeCrop = current
                        }
                    }

                    onCropChanged(current)
                    onCropCommitted(current)
                }
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
                val crop = activeCrop
                val bL = left + spanW * crop.left
                val bT = top + spanH * crop.top
                val bR = left + spanW * crop.right
                val bB = top + spanH * crop.bottom

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
