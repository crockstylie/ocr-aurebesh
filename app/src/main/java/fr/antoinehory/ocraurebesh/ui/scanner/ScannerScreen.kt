package fr.antoinehory.ocraurebesh.ui.scanner

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.antoinehory.ocraurebesh.R
import fr.antoinehory.ocraurebesh.camera.CameraPreview
import fr.antoinehory.ocraurebesh.ui.components.CameraPermissionGate
import fr.antoinehory.ocraurebesh.ui.components.HudIcons
import fr.antoinehory.ocraurebesh.ui.components.HudTopSnackbarHost
import fr.antoinehory.ocraurebesh.ui.components.ScannerOverlay
import fr.antoinehory.ocraurebesh.ui.generator.AurebeshGeneratorActivity
import fr.antoinehory.ocraurebesh.ui.history.ScanHistorySheet
import fr.antoinehory.ocraurebesh.ui.info.InfoActivity
import fr.antoinehory.ocraurebesh.ui.theme.HudAmber
import fr.antoinehory.ocraurebesh.ui.theme.HudAmberDim
import fr.antoinehory.ocraurebesh.ui.theme.HudCyan
import fr.antoinehory.ocraurebesh.ui.theme.HudCyanDim
import fr.antoinehory.ocraurebesh.ui.theme.HudEmerald
import fr.antoinehory.ocraurebesh.ui.theme.HudGlassBorder
import fr.antoinehory.ocraurebesh.ui.theme.HudGlassButton
import fr.antoinehory.ocraurebesh.ui.theme.HudGlassSurface
import fr.antoinehory.ocraurebesh.ui.theme.HudSubtleBorder
import fr.antoinehory.ocraurebesh.ui.theme.HudTextPrimary
import fr.antoinehory.ocraurebesh.ui.theme.HudTextSecondary
import fr.antoinehory.ocraurebesh.ui.theme.OCRAurebeshTheme
import kotlin.math.roundToInt

/**
 * Route stateful reliant [ScannerViewModel], la permission caméra et [ScannerScreen].
 */
@Composable
fun ScannerRoute(
    viewModel: ScannerViewModel,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboardLabel = stringResource(R.string.clipboard_label_translation)

    CameraPermissionGate(modifier = modifier) {
        ScannerScreen(
            uiState = uiState,
            onEvent = viewModel::onEvent,
            onCopyText = { text -> copyToClipboard(context, clipboardLabel, text) },
            onOpenGenerator = { initialText ->
                context.startActivity(
                    AurebeshGeneratorActivity.createIntent(
                        context = context,
                        initialText = initialText,
                    )
                )
            },
            onOpenInfo = {
                context.startActivity(InfoActivity.createIntent(context))
            },
            cameraPreviewContent = { previewModifier ->
                CameraPreview(
                    analyzer = viewModel.imageAnalyzer,
                    isPaused = uiState.isPaused,
                    frozenFrameBitmap = uiState.frozenFrameBitmap,
                    isTorchEnabled = uiState.isTorchEnabled,
                    onFrozenFrameCaptured = { bitmap ->
                        viewModel.onEvent(ScannerUiEvent.OnFrozenFrameCaptured(bitmap))
                    },
                    onFlashAvailabilityChanged = { available ->
                        viewModel.onEvent(ScannerUiEvent.OnFlashAvailabilityChanged(available))
                    },
                    onCameraError = { message ->
                        viewModel.onEvent(ScannerUiEvent.OnCameraError(message))
                    },
                    modifier = previewModifier,
                )
            },
        )
    }
}

/**
 * Écran principal de scan Aurebesh (stateless), conçu en Edge-to-Edge et adaptatif
 * (Portrait vs Paysage). Un tap n'importe où sur le flux vidéo fige ou reprend l'image
 * (indiqué visuellement par le changement de couleur du viseur et du bandeau).
 */
@Composable
fun ScannerScreen(
    uiState: ScannerUiState,
    onEvent: (ScannerUiEvent) -> Unit,
    onCopyText: (String) -> Unit,
    onOpenGenerator: (String) -> Unit,
    cameraPreviewContent: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
    onOpenInfo: () -> Unit = {},
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val activeMessage = uiState.errorMessage ?: uiState.statusMessage

    if (activeMessage != null) {
        LaunchedEffect(activeMessage) {
            snackbarHostState.showSnackbar(activeMessage)
            onEvent(ScannerUiEvent.DismissMessage)
        }
    }

    val density = LocalDensity.current.density
    var overlayPositionInRoot by remember { mutableStateOf(Offset.Zero) }
    var overlaySize by remember { mutableStateOf(Size.Zero) }
    var rootSize by remember { mutableStateOf(Size.Zero) }

    val onCropCommitted = remember(uiState.frozenFrameBitmap, uiState.cropRect, overlayPositionInRoot, overlaySize, rootSize, density) {
        {
            val bitmap = uiState.frozenFrameBitmap
            if (bitmap != null && !bitmap.isRecycled && overlaySize.width > 0f && overlaySize.height > 0f && rootSize.width > 0f && rootSize.height > 0f) {
                val crop = uiState.cropRect
                val insetPx = 4f * density
                val spanW = (overlaySize.width - insetPx * 2f).coerceAtLeast(1f)
                val spanH = (overlaySize.height - insetPx * 2f).coerceAtLeast(1f)

                val boxLeftInOverlay = insetPx + spanW * crop.left
                val boxTopInOverlay = insetPx + spanH * crop.top
                val boxWidthInOverlay = spanW * crop.width
                val boxHeightInOverlay = spanH * crop.height

                val boxLeftInRoot = overlayPositionInRoot.x + boxLeftInOverlay
                val boxTopInRoot = overlayPositionInRoot.y + boxTopInOverlay

                val scaleX = bitmap.width.toFloat() / rootSize.width
                val scaleY = bitmap.height.toFloat() / rootSize.height

                val bmpX = (boxLeftInRoot * scaleX).roundToInt().coerceIn(0, bitmap.width - 1)
                val bmpY = (boxTopInRoot * scaleY).roundToInt().coerceIn(0, bitmap.height - 1)
                val bmpW = (boxWidthInOverlay * scaleX).roundToInt().coerceIn(1, bitmap.width - bmpX)
                val bmpH = (boxHeightInOverlay * scaleY).roundToInt().coerceIn(1, bitmap.height - bmpY)

                onEvent(ScannerUiEvent.AnalyzeCrop(android.graphics.Rect(bmpX, bmpY, bmpX + bmpW, bmpY + bmpH)))
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = Color.Black,
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { coords ->
                    rootSize = Size(coords.size.width.toFloat(), coords.size.height.toFloat())
                }
                .pointerInput(uiState.isPaused) {
                    detectTapGestures(
                        onTap = {
                            if (!uiState.isPaused) {
                                onEvent(ScannerUiEvent.TogglePause)
                            }
                        }
                    )
                }
        ) {
            val isLandscape = maxWidth > maxHeight

            // 1. Flux caméra plein écran en arrière-plan
            cameraPreviewContent(Modifier.fillMaxSize())

            // 2. Interface HUD adaptative (Portrait vs Paysage) sans aucun chevauchement
            if (isLandscape) {
                ScannerLandscapeLayout(
                    uiState = uiState,
                    onEvent = onEvent,
                    onCopyText = onCopyText,
                    onOpenGenerator = { onOpenGenerator(uiState.recognizedText) },
                    onOpenInfo = onOpenInfo,
                    onCropCommitted = onCropCommitted,
                    onOverlayPositioned = { pos, sz ->
                        overlayPositionInRoot = pos
                        overlaySize = sz
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .consumeWindowInsets(innerPadding)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                )
            } else {
                ScannerPortraitLayout(
                    uiState = uiState,
                    onEvent = onEvent,
                    onCopyText = onCopyText,
                    onOpenGenerator = { onOpenGenerator(uiState.recognizedText) },
                    onOpenInfo = onOpenInfo,
                    onCropCommitted = onCropCommitted,
                    onOverlayPositioned = { pos, sz ->
                        overlayPositionInRoot = pos
                        overlaySize = sz
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .consumeWindowInsets(innerPadding)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }

            // 3. Toast HUD sombre positionné en haut pour ne jamais masquer les boutons du bas
            HudTopSnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(innerPadding)
                    .padding(top = 60.dp),
            )
        }

        if (uiState.isHistorySheetVisible) {
            ScanHistorySheet(
                records = uiState.historyRecords,
                showFavoritesOnly = uiState.showFavoritesOnly,
                onToggleFavoritesFilter = { favOnly ->
                    onEvent(ScannerUiEvent.SetShowFavoritesOnly(favOnly))
                },
                onToggleFavoriteItem = { id, isFav ->
                    onEvent(ScannerUiEvent.ToggleFavoriteRecord(id, isFav))
                },
                onCopyText = onCopyText,
                onEditInStudio = { text ->
                    onEvent(ScannerUiEvent.SetHistorySheetVisible(false))
                    onOpenGenerator(text)
                },
                onDeleteItem = { id ->
                    onEvent(ScannerUiEvent.DeleteHistoryRecord(id))
                },
                onClearAll = { onEvent(ScannerUiEvent.ClearAllHistory) },
                onDismiss = { onEvent(ScannerUiEvent.SetHistorySheetVisible(false)) },
            )
        }
    }
}

/**
 * Mise en page Portrait :
 * - En haut : bouton Info à gauche + boutons ronds à icônes à droite (Studio, Torche, Historique).
 * - Au centre (`weight(1f)`) : viseur HUD occupant tout l'espace libre sans toucher aux boutons.
 * - En bas : dock flottant épuré (sans bouton Play/Pause redondant).
 */
@Composable
private fun ScannerPortraitLayout(
    uiState: ScannerUiState,
    onEvent: (ScannerUiEvent) -> Unit,
    onCopyText: (String) -> Unit,
    onOpenGenerator: () -> Unit,
    onOpenInfo: () -> Unit,
    onCropCommitted: () -> Unit,
    onOverlayPositioned: (Offset, Size) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ScannerTopHudBar(
            isFlashAvailable = uiState.isFlashAvailable,
            isTorchEnabled = uiState.isTorchEnabled,
            historyCount = uiState.historyRecords.size,
            onOpenInfo = onOpenInfo,
            onToggleTorch = { onEvent(ScannerUiEvent.ToggleTorch) },
            onOpenGenerator = onOpenGenerator,
            onOpenHistory = { onEvent(ScannerUiEvent.SetHistorySheetVisible(true)) },
        )

        ScannerOverlay(
            isPaused = uiState.isPaused,
            hasRecognizedText = uiState.recognizedText.isNotEmpty(),
            cropRect = uiState.cropRect,
            onCropChanged = { onEvent(ScannerUiEvent.OnCropChanged(it)) },
            onCropCommitted = onCropCommitted,
            onOverlayPositionedInRoot = onOverlayPositioned,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        )

        ScannerContextualToolbar(
            isPaused = uiState.isPaused,
            hasRecognizedText = uiState.recognizedText.isNotEmpty(),
            isAnalyzingCrop = uiState.isAnalyzingCrop,
            onResumeScan = { onEvent(ScannerUiEvent.ResumeScanning) },
            onAnalyzeCrop = onCropCommitted,
            onCopyScan = { onCopyText(uiState.recognizedText) },
            onSaveScan = { onEvent(ScannerUiEvent.SaveCurrentScan) },
            onClearScan = { onEvent(ScannerUiEvent.ClearCurrentScan) },
        )

        TranslationTextCard(
            recognizedText = uiState.recognizedText,
            isPaused = uiState.isPaused,
        )
    }
}

/**
 * Mise en page Paysage :
 * - À gauche (`weight(1f)`) : grand viseur caméra totalement dégagé.
 * - À droite (`268.dp`) : colonne latérale regroupant les boutons d'action du haut, la barre contextuelle et la carte de texte traduit.
 */
@Composable
private fun ScannerLandscapeLayout(
    uiState: ScannerUiState,
    onEvent: (ScannerUiEvent) -> Unit,
    onCopyText: (String) -> Unit,
    onOpenGenerator: () -> Unit,
    onOpenInfo: () -> Unit,
    onCropCommitted: () -> Unit,
    onOverlayPositioned: (Offset, Size) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Zone de visée caméra plein cadre à gauche
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        ) {
            ScannerOverlay(
                isPaused = uiState.isPaused,
                hasRecognizedText = uiState.recognizedText.isNotEmpty(),
                cropRect = uiState.cropRect,
                onCropChanged = { onEvent(ScannerUiEvent.OnCropChanged(it)) },
                onCropCommitted = onCropCommitted,
                onOverlayPositionedInRoot = onOverlayPositioned,
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Panneau latéral HUD compact à droite
        Column(
            modifier = Modifier
                .width(268.dp)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ScannerTopHudBar(
                isFlashAvailable = uiState.isFlashAvailable,
                isTorchEnabled = uiState.isTorchEnabled,
                historyCount = uiState.historyRecords.size,
                onOpenInfo = onOpenInfo,
                onToggleTorch = { onEvent(ScannerUiEvent.ToggleTorch) },
                onOpenGenerator = onOpenGenerator,
                onOpenHistory = { onEvent(ScannerUiEvent.SetHistorySheetVisible(true)) },
            )

            ScannerContextualToolbar(
                isPaused = uiState.isPaused,
                hasRecognizedText = uiState.recognizedText.isNotEmpty(),
                isAnalyzingCrop = uiState.isAnalyzingCrop,
                onResumeScan = { onEvent(ScannerUiEvent.ResumeScanning) },
                onAnalyzeCrop = onCropCommitted,
                onCopyScan = { onCopyText(uiState.recognizedText) },
                onSaveScan = { onEvent(ScannerUiEvent.SaveCurrentScan) },
                onClearScan = { onEvent(ScannerUiEvent.ClearCurrentScan) },
            )

            TranslationTextCard(
                recognizedText = uiState.recognizedText,
                isPaused = uiState.isPaused,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ScannerTopHudBar(
    isFlashAvailable: Boolean,
    isTorchEnabled: Boolean,
    historyCount: Int,
    onOpenInfo: () -> Unit,
    onToggleTorch: () -> Unit,
    onOpenGenerator: () -> Unit,
    onOpenHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HudCircleIconButton(
            icon = HudIcons.Info,
            contentDescription = stringResource(R.string.cd_info),
            tint = HudTextPrimary,
            onClick = onOpenInfo,
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HudCircleIconButton(
                icon = HudIcons.Edit,
                contentDescription = stringResource(R.string.cd_studio_aurebesh),
                tint = HudCyan,
                onClick = onOpenGenerator,
            )

            if (isFlashAvailable) {
                HudCircleIconButton(
                    icon = if (isTorchEnabled) HudIcons.FlashOn else HudIcons.FlashOff,
                    contentDescription = stringResource(R.string.cd_torch),
                    isActive = isTorchEnabled,
                    activeColor = HudAmber,
                    activeBgColor = HudAmberDim,
                    onClick = onToggleTorch,
                )
            }

            HudHistoryButton(
                historyCount = historyCount,
                onClick = onOpenHistory,
            )
        }
    }
}

/**
 * Barre d'outils contextuelle flottante située juste au-dessus du bloc de texte traduit.
 * Évite toute redondance et adapte ses boutons à l'état du scan :
 * - En mode figé : bouton Play (tout à gauche pour reprendre le direct) + bouton Analyser.
 * - Quand du texte est reconnu : boutons Copier, Sauvegarder, Effacer (Croix).
 * - Quand aucun texte n'est reconnu et que le scan est en direct : masquée pour un minimalisme maximal.
 */
@Composable
private fun ScannerContextualToolbar(
    isPaused: Boolean,
    hasRecognizedText: Boolean,
    isAnalyzingCrop: Boolean,
    onResumeScan: () -> Unit,
    onAnalyzeCrop: () -> Unit,
    onCopyScan: () -> Unit,
    onSaveScan: () -> Unit,
    onClearScan: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasActions = isPaused || hasRecognizedText
    AnimatedVisibility(
        visible = hasActions,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier.fillMaxWidth(),
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = HudGlassSurface,
            border = BorderStroke(
                width = 1.dp,
                color = if (isPaused) HudAmber.copy(alpha = 0.55f) else HudGlassBorder,
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Contrôles spécifiques au mode figé (Play tout à gauche + Analyser)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (isPaused) {
                        HudCircleIconButton(
                            icon = HudIcons.Play,
                            contentDescription = stringResource(R.string.btn_resume_scan),
                            tint = HudAmber,
                            activeColor = HudAmber,
                            activeBgColor = HudAmberDim,
                            onClick = onResumeScan,
                        )
                        HudCircleIconButton(
                            icon = HudIcons.Search,
                            contentDescription = stringResource(R.string.btn_crop_reanalyze),
                            tint = HudCyan,
                            isLoading = isAnalyzingCrop,
                            onClick = onAnalyzeCrop,
                        )
                    }
                }

                // Actions contextuelles sur le texte reconnu (Copier, Sauvegarder, Croix)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (hasRecognizedText) {
                        HudCircleIconButton(
                            icon = HudIcons.Copy,
                            contentDescription = stringResource(R.string.cd_copy),
                            tint = HudCyan,
                            onClick = onCopyScan,
                        )
                        HudCircleIconButton(
                            icon = HudIcons.BookmarkAdd,
                            contentDescription = stringResource(R.string.cd_save),
                            tint = HudEmerald,
                            onClick = onSaveScan,
                        )
                        HudCircleIconButton(
                            icon = HudIcons.Close,
                            contentDescription = stringResource(R.string.cd_clear),
                            tint = HudTextSecondary,
                            onClick = onClearScan,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Carte d'affichage du texte traduit (ou placeholder quand rien n'est reconnu).
 * Totalement épurée : ne contient plus aucun bouton de contrôle (ceux-ci sont dans [ScannerContextualToolbar]).
 */
@Composable
private fun TranslationTextCard(
    recognizedText: String,
    isPaused: Boolean,
    modifier: Modifier = Modifier,
) {
    val hasText = recognizedText.isNotEmpty()
    val scrollState = rememberScrollState()

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = HudGlassSurface,
        border = BorderStroke(
            width = 1.dp,
            color = when {
                isPaused -> HudAmber.copy(alpha = 0.55f)
                hasText -> HudGlassBorder
                else -> HudSubtleBorder
            },
        ),
    ) {
        if (!hasText) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.scanner_placeholder),
                    style = MaterialTheme.typography.bodyMedium,
                    color = HudTextSecondary,
                )
            }
        } else {
            val isDenseMultiLine = recognizedText.count { it == '\n' } >= 2
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 28.dp, max = if (isDenseMultiLine) 160.dp else 90.dp)
                    .padding(horizontal = 16.dp, vertical = 14.dp)
                    .verticalScroll(scrollState),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = recognizedText,
                    style = if (isDenseMultiLine) {
                        MaterialTheme.typography.bodyMedium
                    } else {
                        MaterialTheme.typography.titleMedium
                    },
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = HudTextPrimary,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/**
 * Bouton rond translucide à icône style HUD Caméra.
 */
@Composable
private fun HudCircleIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isActive: Boolean = false,
    isLoading: Boolean = false,
    tint: Color = HudTextPrimary,
    activeColor: Color = HudCyan,
    activeBgColor: Color = HudCyanDim,
) {
    val bgColor = if (isActive) activeBgColor else HudGlassButton
    val borderColor = if (isActive) activeColor.copy(alpha = 0.7f) else HudSubtleBorder
    val iconTint = if (isActive) activeColor else tint

    Box(
        modifier = modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(color = bgColor, shape = CircleShape)
            .border(width = 1.dp, color = borderColor, shape = CircleShape)
            .clickable(enabled = !isLoading, onClick = onClick)
            .semantics(mergeDescendants = true) {
                this.contentDescription = contentDescription
                this.role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = iconTint,
            )
        } else {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/**
 * Bouton Historique compact avec icône et badge numérique intégré.
 */
@Composable
private fun HudHistoryButton(
    historyCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val historyDesc = stringResource(R.string.cd_history)
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = historyDesc
                role = Role.Button
            },
        shape = RoundedCornerShape(50),
        color = HudGlassButton,
        border = BorderStroke(1.dp, HudSubtleBorder),
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = if (historyCount > 0) 12.dp else 11.dp,
                vertical = 10.dp,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = HudIcons.History,
                contentDescription = null,
                tint = HudCyan,
                modifier = Modifier.size(20.dp),
            )
            if (historyCount > 0) {
                Text(
                    text = historyCount.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = HudTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                )
            }
        }
    }
}

private fun copyToClipboard(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    val clip = ClipData.newPlainText(label, text)
    clipboard?.setPrimaryClip(clip)
}

@Preview(name = "Portrait", showBackground = true, widthDp = 412, heightDp = 892)
@Composable
private fun ScannerScreenPortraitPreview() {
    OCRAurebeshTheme {
        ScannerScreen(
            uiState = ScannerUiState(
                recognizedText = "MAY THE FORCE BE WITH YOU",
                confidence = 96,
                wasAutoInverted = true,
                isPaused = false,
                isFlashAvailable = true,
                isTorchEnabled = true,
                isOcrReady = true,
            ),
            onEvent = {},
            onCopyText = {},
            onOpenGenerator = {},
            cameraPreviewContent = { mod ->
                Box(modifier = mod.background(Color(0xFF0B1520)))
            },
        )
    }
}

@Preview(name = "Landscape", showBackground = true, widthDp = 892, heightDp = 412)
@Composable
private fun ScannerScreenLandscapePreview() {
    OCRAurebeshTheme {
        ScannerScreen(
            uiState = ScannerUiState(
                recognizedText = "MAY THE FORCE BE WITH YOU",
                confidence = 96,
                wasAutoInverted = true,
                isPaused = true,
                isFlashAvailable = true,
                isTorchEnabled = false,
                isOcrReady = true,
            ),
            onEvent = {},
            onCopyText = {},
            onOpenGenerator = {},
            cameraPreviewContent = { mod ->
                Box(modifier = mod.background(Color(0xFF0B1520)))
            },
        )
    }
}
