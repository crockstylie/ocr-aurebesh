package fr.antoinehory.ocraurebesh.ui.scanner

import fr.antoinehory.ocraurebesh.domain.ScanRecord
import fr.antoinehory.ocraurebesh.ocr.RoiRectFraction

/**
 * État UI complet de l'écran de scan Aurebesh.
 * Note : pas d'annotation `@Immutable` forcée car il contient `List<ScanRecord>` (Strong Skipping Mode).
 */
data class ScannerUiState(
    val recognizedText: String = "",
    val confidence: Int = 0,
    val wasAutoInverted: Boolean = false,
    val isPaused: Boolean = false,
    val isTorchEnabled: Boolean = false,
    val isFlashAvailable: Boolean = false,
    val isOcrReady: Boolean = false,
    val isHistorySheetVisible: Boolean = false,
    val showFavoritesOnly: Boolean = false,
    val historyRecords: List<ScanRecord> = emptyList(),
    val statusMessage: String? = null,
    val errorMessage: String? = null,
    val roiFraction: RoiRectFraction = RoiRectFraction(),
)

/**
 * Événements remontés par l'interface utilisateur vers [ScannerViewModel] (pattern UDF).
 */
sealed interface ScannerUiEvent {
    data object TogglePause : ScannerUiEvent
    data object ToggleTorch : ScannerUiEvent
    data class OnFlashAvailabilityChanged(val available: Boolean) : ScannerUiEvent
    data class OnCameraError(val message: String) : ScannerUiEvent
    data object SaveCurrentScan : ScannerUiEvent
    data object ClearCurrentScan : ScannerUiEvent
    data class SetHistorySheetVisible(val visible: Boolean) : ScannerUiEvent
    data class SetShowFavoritesOnly(val favoritesOnly: Boolean) : ScannerUiEvent
    data class ToggleFavoriteRecord(val id: Long, val isFavorite: Boolean) : ScannerUiEvent
    data class DeleteHistoryRecord(val id: Long) : ScannerUiEvent
    data object ClearAllHistory : ScannerUiEvent
    data object DismissMessage : ScannerUiEvent
}
