package fr.antoinehory.ocraurebesh.ui.scanner

import android.graphics.Bitmap
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
    val frozenFrameBitmap: Bitmap? = null,
    val isTorchEnabled: Boolean = false,
    val isFlashAvailable: Boolean = false,
    val isOcrReady: Boolean = false,
    val isHistorySheetVisible: Boolean = false,
    val showFavoritesOnly: Boolean = false,
    val historyRecords: List<ScanRecord> = emptyList(),
    val statusMessage: String? = null,
    val errorMessage: String? = null,
    val roiFraction: RoiRectFraction = RoiRectFraction(),
    val cropRect: NormalizedCropRect = NormalizedCropRect(),
    val isAnalyzingCrop: Boolean = false,
)

/**
 * Coordonnées relatives normalisées (0f..1f) du cadre de recadrage interactif dans le viseur.
 */
data class NormalizedCropRect(
    val left: Float = 0.05f,
    val top: Float = 0.30f,
    val right: Float = 0.95f,
    val bottom: Float = 0.70f,
) {
    val width: Float get() = (right - left).coerceAtLeast(0.04f)
    val height: Float get() = (bottom - top).coerceAtLeast(0.04f)

    fun coerceValid(): NormalizedCropRect {
        val minSpan = 0.06f
        val clampedL = left.coerceIn(0f, 1f - minSpan)
        val clampedT = top.coerceIn(0f, 1f - minSpan)
        val clampedR = right.coerceIn(clampedL + minSpan, 1f)
        val clampedB = bottom.coerceIn(clampedT + minSpan, 1f)
        return NormalizedCropRect(clampedL, clampedT, clampedR, clampedB)
    }
}

/**
 * Événements remontés par l'interface utilisateur vers [ScannerViewModel] (pattern UDF).
 */
sealed interface ScannerUiEvent {
    data object TogglePause : ScannerUiEvent
    data object ResumeScanning : ScannerUiEvent
    data class OnFrozenFrameCaptured(val bitmap: Bitmap) : ScannerUiEvent
    data class OnCropChanged(val crop: NormalizedCropRect) : ScannerUiEvent
    data class AnalyzeCrop(val cropInBitmap: android.graphics.Rect) : ScannerUiEvent
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
