package fr.antoinehory.ocraurebesh.ui.scanner

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.antoinehory.ocraurebesh.camera.CameraPreview
import fr.antoinehory.ocraurebesh.ui.components.CameraPermissionGate
import fr.antoinehory.ocraurebesh.ui.components.ScannerOverlay
import fr.antoinehory.ocraurebesh.ui.history.ScanHistorySheet
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
            cameraPreviewContent = { previewModifier ->
                CameraPreview(
                    analyzer = viewModel.imageAnalyzer,
                    isPaused = uiState.isPaused,
                    isTorchEnabled = uiState.isTorchEnabled,
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
 * Écran principal de scan Aurebesh (stateless), conçu en Edge-to-Edge.
 * Un tap n'importe où sur la vue caméra permet de figer ou reprendre l'image et la détection.
 */
@Composable
fun ScannerScreen(
    uiState: ScannerUiState,
    onEvent: (ScannerUiEvent) -> Unit,
    onCopyText: (String) -> Unit,
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
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        containerColor = Color.Black,
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { onEvent(ScannerUiEvent.TogglePause) }
                    )
                }
        ) {
            // 1. Flux caméra plein écran (se fige visuellement lorsque isPaused == true)
            cameraPreviewContent(Modifier.fillMaxSize())

            // 2. Coins de visée HUD plein écran dessinés avec drawWithCache
            ScannerOverlay(
                roiFraction = uiState.roiFraction,
                isPaused = uiState.isPaused,
                hasRecognizedText = uiState.recognizedText.isNotEmpty(),
                modifier = Modifier.fillMaxSize(),
            )

            // 3. Contrôles supérieurs et carte de traduction inférieure épurée
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                ScannerTopBar(
                    isOcrReady = uiState.isOcrReady,
                    isPaused = uiState.isPaused,
                    isFlashAvailable = uiState.isFlashAvailable,
                    isTorchEnabled = uiState.isTorchEnabled,
                    historyCount = uiState.historyRecords.size,
                    onToggleTorch = { onEvent(ScannerUiEvent.ToggleTorch) },
                    onOpenHistory = { onEvent(ScannerUiEvent.SetHistorySheetVisible(true)) },
                )

                TranslationResultCard(
                    recognizedText = uiState.recognizedText,
                    isPaused = uiState.isPaused,
                    onTogglePause = { onEvent(ScannerUiEvent.TogglePause) },
                    onSaveScan = { onEvent(ScannerUiEvent.SaveCurrentScan) },
                    onCopyScan = {
                        if (uiState.recognizedText.isNotEmpty()) {
                            onCopyText(uiState.recognizedText)
                        }
                    },
                    onClearScan = { onEvent(ScannerUiEvent.ClearCurrentScan) },
                )
            }
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
                onDeleteItem = { id ->
                    onEvent(ScannerUiEvent.DeleteHistoryRecord(id))
                },
                onClearAll = { onEvent(ScannerUiEvent.ClearAllHistory) },
                onDismiss = { onEvent(ScannerUiEvent.SetHistorySheetVisible(false)) },
            )
        }
    }
}

@Composable
private fun ScannerTopBar(
    isOcrReady: Boolean,
    isPaused: Boolean,
    isFlashAvailable: Boolean,
    isTorchEnabled: Boolean,
    historyCount: Int,
    onToggleTorch: () -> Unit,
    onOpenHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color.Black.copy(alpha = 0.65f),
        ) {
            val statusText = when {
                !isOcrReady -> "Initialisation OCR..."
                isPaused -> "Image figée ( toucher pour reprendre )"
                else -> "Scan Aurebesh actif"
            }
            Text(
                text = statusText,
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (isFlashAvailable) {
                FilledTonalButton(
                    onClick = onToggleTorch,
                    modifier = Modifier.semantics(mergeDescendants = true) {},
                ) {
                    Text(if (isTorchEnabled) "Torche ON" else "Torche OFF")
                }
            }

            FilledTonalButton(
                onClick = onOpenHistory,
                modifier = Modifier.semantics(mergeDescendants = true) {},
            ) {
                Text(
                    text = if (historyCount > 0) {
                        "Historique ($historyCount)"
                    } else {
                        "Historique"
                    }
                )
            }
        }
    }
}

@Composable
private fun TranslationResultCard(
    recognizedText: String,
    isPaused: Boolean,
    onTogglePause: () -> Unit,
    onSaveScan: () -> Unit,
    onCopyScan: () -> Unit,
    onClearScan: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasText = recognizedText.isNotEmpty()

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
        ) {
            Text(
                text = if (hasText) {
                    recognizedText
                } else {
                    "En attente d'un texte Aurebesh..."
                },
                style = if (hasText) {
                    MaterialTheme.typography.headlineSmall
                } else {
                    MaterialTheme.typography.bodyLarge
                },
                fontFamily = if (hasText) FontFamily.Monospace else FontFamily.Default,
                fontWeight = if (hasText) FontWeight.Bold else FontWeight.Normal,
                color = if (hasText) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onTogglePause,
                    modifier = Modifier
                        .weight(1f)
                        .semantics(mergeDescendants = true) {},
                ) {
                    Text(if (isPaused) "Reprendre" else "Figer")
                }

                FilledTonalButton(
                    onClick = onSaveScan,
                    enabled = hasText,
                    modifier = Modifier
                        .weight(1f)
                        .semantics(mergeDescendants = true) {},
                ) {
                    Text("Sauvegarder")
                }
            }

            if (hasText) {
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = onCopyScan,
                        modifier = Modifier
                            .weight(1f)
                            .semantics(mergeDescendants = true) {},
                    ) {
                        Text("Copier")
                    }

                    OutlinedButton(
                        onClick = onClearScan,
                        modifier = Modifier
                            .weight(1f)
                            .semantics(mergeDescendants = true) {},
                    ) {
                        Text("Effacer")
                    }
                }
            }
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    val clip = ClipData.newPlainText("Traduction Aurebesh", text)
    clipboard?.setPrimaryClip(clip)
}

@Preview(showBackground = true)
@Composable
private fun ScannerScreenPreview() {
    OCRAurebeshTheme {
        ScannerScreen(
            uiState = ScannerUiState(
                recognizedText = "MAY THE FORCE BE WITH YOU",
                confidence = 96,
                wasAutoInverted = true,
                isPaused = false,
                isFlashAvailable = true,
                isTorchEnabled = false,
                isOcrReady = true,
            ),
            onEvent = {},
            onCopyText = {},
            cameraPreviewContent = { mod ->
                Box(
                    modifier = mod.background(Color(0xFF101820))
                )
            },
        )
    }
}
