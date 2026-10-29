package fr.antoinehory.ocraurebesh.ui.scanner

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
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

    CameraPermissionGate(modifier = modifier) {
        ScannerScreen(
            uiState = uiState,
            onEvent = viewModel::onEvent,
            onCopyText = { text -> copyToClipboard(context, text) },
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
    onOpenInfo: () -> Unit = {},
    cameraPreviewContent: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val activeMessage = uiState.errorMessage ?: uiState.statusMessage

    if (activeMessage != null) {
        LaunchedEffect(activeMessage) {
            snackbarHostState.showSnackbar(activeMessage)
            onEvent(ScannerUiEvent.DismissMessage)
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
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { onEvent(ScannerUiEvent.TogglePause) }
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
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
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
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        )

        TranslationHudDock(
            recognizedText = uiState.recognizedText,
            isPaused = uiState.isPaused,
            onCopyScan = {
                if (uiState.recognizedText.isNotEmpty()) {
                    onCopyText(uiState.recognizedText)
                }
            },
            onSaveScan = { onEvent(ScannerUiEvent.SaveCurrentScan) },
            onClearScan = { onEvent(ScannerUiEvent.ClearCurrentScan) },
        )
    }
}

/**
 * Mise en page Paysage :
 * - À gauche (`weight(1f)`) : grand viseur caméra totalement dégagé.
 * - À droite (`268.dp`) : colonne latérale regroupant les boutons d'action ronds et la carte de texte traduit.
 */
@Composable
private fun ScannerLandscapeLayout(
    uiState: ScannerUiState,
    onEvent: (ScannerUiEvent) -> Unit,
    onCopyText: (String) -> Unit,
    onOpenGenerator: () -> Unit,
    onOpenInfo: () -> Unit,
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HudCircleIconButton(
                    icon = HudIcons.Info,
                    contentDescription = "Informations",
                    tint = HudTextPrimary,
                    onClick = onOpenInfo,
                )

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    HudCircleIconButton(
                        icon = HudIcons.Edit,
                        contentDescription = "Studio Aurebesh",
                        tint = HudCyan,
                        onClick = onOpenGenerator,
                    )

                    if (uiState.isFlashAvailable) {
                        HudCircleIconButton(
                            icon = if (uiState.isTorchEnabled) HudIcons.FlashOn else HudIcons.FlashOff,
                            contentDescription = "Torche",
                            isActive = uiState.isTorchEnabled,
                            activeColor = HudAmber,
                            activeBgColor = HudAmberDim,
                            onClick = { onEvent(ScannerUiEvent.ToggleTorch) },
                        )
                    }

                    HudHistoryButton(
                        historyCount = uiState.historyRecords.size,
                        onClick = { onEvent(ScannerUiEvent.SetHistorySheetVisible(true)) },
                    )
                }
            }

            // Carte latérale contenant le texte reconnu et la barre d'actions en bas
            val hasText = uiState.recognizedText.isNotEmpty()
            val scrollState = rememberScrollState()

            Surface(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                color = HudGlassSurface,
                border = BorderStroke(
                    width = 1.dp,
                    color = when {
                        uiState.isPaused -> HudAmber.copy(alpha = 0.55f)
                        hasText -> HudGlassBorder
                        else -> HudSubtleBorder
                    },
                ),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(scrollState),
                        contentAlignment = if (hasText) Alignment.TopStart else Alignment.Center,
                    ) {
                        Text(
                            text = if (hasText) uiState.recognizedText else "Scannez un texte Aurebesh...",
                            style = if (hasText) {
                                MaterialTheme.typography.titleMedium
                            } else {
                                MaterialTheme.typography.bodyMedium
                            },
                            fontFamily = if (hasText) FontFamily.Monospace else FontFamily.Default,
                            fontWeight = if (hasText) FontWeight.Bold else FontWeight.Normal,
                            color = if (hasText) HudTextPrimary else HudTextSecondary,
                        )
                    }

                    if (hasText) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            HudCircleIconButton(
                                icon = HudIcons.Copy,
                                contentDescription = "Copier",
                                tint = HudCyan,
                                onClick = { onCopyText(uiState.recognizedText) },
                            )
                            HudCircleIconButton(
                                icon = HudIcons.BookmarkAdd,
                                contentDescription = "Sauvegarder",
                                tint = HudEmerald,
                                onClick = { onEvent(ScannerUiEvent.SaveCurrentScan) },
                            )
                            HudCircleIconButton(
                                icon = HudIcons.Close,
                                contentDescription = "Effacer",
                                tint = HudTextSecondary,
                                onClick = { onEvent(ScannerUiEvent.ClearCurrentScan) },
                            )
                        }
                    }
                }
            }
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
            contentDescription = "Informations",
            tint = HudTextPrimary,
            onClick = onOpenInfo,
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HudCircleIconButton(
                icon = HudIcons.Edit,
                contentDescription = "Studio Aurebesh",
                tint = HudCyan,
                onClick = onOpenGenerator,
            )

            if (isFlashAvailable) {
                HudCircleIconButton(
                    icon = if (isTorchEnabled) HudIcons.FlashOn else HudIcons.FlashOff,
                    contentDescription = "Torche",
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
 * Bandeau inférieur ("Dock") épuré en mode Portrait :
 * - Affiche le texte traduit et les icônes d'action (Copier, Sauvegarder, Effacer).
 * - L'état figé est reflété par la bordure ambrée (et les coins du viseur), sans bouton Play/Pause.
 */
@Composable
private fun TranslationHudDock(
    recognizedText: String,
    isPaused: Boolean,
    onCopyScan: () -> Unit,
    onSaveScan: () -> Unit,
    onClearScan: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasText = recognizedText.isNotEmpty()
    val scrollState = rememberScrollState()

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
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
                    text = "Scannez un texte Aurebesh...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = HudTextSecondary,
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val isDenseMultiLine = recognizedText.count { it == '\n' } >= 2
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 28.dp, max = if (isDenseMultiLine) 180.dp else 96.dp)
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

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    HudCircleIconButton(
                        icon = HudIcons.Copy,
                        contentDescription = "Copier",
                        tint = HudCyan,
                        onClick = onCopyScan,
                    )
                    HudCircleIconButton(
                        icon = HudIcons.BookmarkAdd,
                        contentDescription = "Sauvegarder",
                        tint = HudEmerald,
                        onClick = onSaveScan,
                    )
                    HudCircleIconButton(
                        icon = HudIcons.Close,
                        contentDescription = "Effacer",
                        tint = HudTextSecondary,
                        onClick = onClearScan,
                    )
                }
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
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                this.contentDescription = contentDescription
                this.role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(20.dp),
        )
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
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = "Historique"
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

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    val clip = ClipData.newPlainText("Traduction Aurebesh", text)
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
