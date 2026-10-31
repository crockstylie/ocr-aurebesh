package fr.antoinehory.ocraurebesh.ui.scanner

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import fr.antoinehory.ocraurebesh.R
import fr.antoinehory.ocraurebesh.camera.AurebeshImageAnalyzer
import fr.antoinehory.ocraurebesh.data.local.AppDatabase
import fr.antoinehory.ocraurebesh.data.repository.ScanHistoryRepository
import fr.antoinehory.ocraurebesh.domain.OcrResult
import fr.antoinehory.ocraurebesh.ocr.AurebeshOcrEngine
import fr.antoinehory.ocraurebesh.ocr.AutoImagePreprocessor
import fr.antoinehory.ocraurebesh.ocr.OcrRecognizer
import fr.antoinehory.ocraurebesh.ocr.TessDataManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Chaînes localisées utilisées par [ScannerViewModel] (avec valeurs par défaut pour les tests JVM).
 */
data class ScannerMessages(
    val translationSaved: String = "Traduction enregistrée dans l'historique",
    val historyCleared: String = "Historique effacé",
    val ocrInitError: String = "Erreur d'initialisation OCR",
)

/**
 * ViewModel principal orchestrant le flux caméra, le moteur OCR Aurebesh et l'historique Room.
 *
 * Inclut un stabilisateur temporel anti-troncature et anti-clignotement ainsi qu'une passe
 * d'analyse OCR dédiée lorsque l'utilisateur fige l'image caméra.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ScannerViewModel(
    private val ocrEngine: OcrRecognizer,
    private val historyRepository: ScanHistoryRepository,
    private val messages: ScannerMessages = ScannerMessages(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScannerUiState())
    val uiState: StateFlow<ScannerUiState> = _uiState.asStateFlow()

    private var consecutiveEmptyFrames = 0
    private var consecutiveShorterFrames = 0
    private var candidateText = ""
    private var candidateMatchCount = 0

    val imageAnalyzer = AurebeshImageAnalyzer(
        ocrEngine = ocrEngine,
        coroutineScope = viewModelScope,
        isPausedProvider = { _uiState.value.isPaused },
        roiFraction = _uiState.value.roiFraction,
        onResult = { result -> onOcrResult(result, isFromFrozenCapture = false) },
    )

    init {
        initializeOcrEngine()
        observeHistory()
    }

    fun onEvent(event: ScannerUiEvent) {
        when (event) {
            ScannerUiEvent.TogglePause -> {
                _uiState.update { state ->
                    val nextPaused = !state.isPaused
                    state.copy(
                        isPaused = nextPaused,
                        frozenFrameBitmap = if (nextPaused) state.frozenFrameBitmap else null,
                        cropRect = NormalizedCropRect(),
                        isAnalyzingCrop = false,
                    )
                }
            }

            ScannerUiEvent.ResumeScanning -> {
                _uiState.update { state ->
                    state.copy(
                        isPaused = false,
                        frozenFrameBitmap = null,
                        cropRect = NormalizedCropRect(),
                        isAnalyzingCrop = false,
                    )
                }
            }

            is ScannerUiEvent.OnCropChanged -> {
                _uiState.update { it.copy(cropRect = event.crop) }
            }

            is ScannerUiEvent.AnalyzeCrop -> {
                analyzeCropInBitmap(event.cropInBitmap)
            }

            is ScannerUiEvent.OnFrozenFrameCaptured -> {
                _uiState.update { state ->
                    if (state.isPaused) {
                        state.copy(frozenFrameBitmap = event.bitmap)
                    } else {
                        state
                    }
                }
                if (_uiState.value.isPaused) {
                    analyzeFrozenBitmap(event.bitmap)
                }
            }

            ScannerUiEvent.ToggleTorch -> {
                _uiState.update { state ->
                    if (state.isFlashAvailable) {
                        state.copy(isTorchEnabled = !state.isTorchEnabled)
                    } else {
                        state
                    }
                }
            }

            is ScannerUiEvent.OnFlashAvailabilityChanged -> {
                _uiState.update { state ->
                    state.copy(
                        isFlashAvailable = event.available,
                        isTorchEnabled = if (!event.available) false else state.isTorchEnabled,
                    )
                }
            }

            is ScannerUiEvent.OnCameraError -> {
                _uiState.update { it.copy(errorMessage = event.message) }
            }

            ScannerUiEvent.SaveCurrentScan -> saveCurrentScan()

            ScannerUiEvent.ClearCurrentScan -> {
                consecutiveEmptyFrames = 0
                consecutiveShorterFrames = 0
                candidateText = ""
                candidateMatchCount = 0
                _uiState.update {
                    it.copy(
                        recognizedText = "",
                        confidence = 0,
                        wasAutoInverted = false,
                        isPaused = false,
                        frozenFrameBitmap = null,
                    )
                }
            }

            is ScannerUiEvent.SetHistorySheetVisible -> {
                _uiState.update { it.copy(isHistorySheetVisible = event.visible) }
            }

            is ScannerUiEvent.SetShowFavoritesOnly -> {
                _uiState.update { it.copy(showFavoritesOnly = event.favoritesOnly) }
            }

            is ScannerUiEvent.ToggleFavoriteRecord -> {
                viewModelScope.launch {
                    historyRepository.setFavorite(event.id, event.isFavorite)
                }
            }

            is ScannerUiEvent.DeleteHistoryRecord -> {
                viewModelScope.launch {
                    historyRepository.deleteScan(event.id)
                }
            }

            ScannerUiEvent.ClearAllHistory -> {
                viewModelScope.launch {
                    historyRepository.clearHistory()
                    _uiState.update { it.copy(statusMessage = messages.historyCleared) }
                }
            }

            ScannerUiEvent.DismissMessage -> {
                _uiState.update { it.copy(statusMessage = null, errorMessage = null) }
            }
        }
    }

    /**
     * Déclenche une détection OCR dédiée sur le [Bitmap] capturé au moment précis où
     * l'utilisateur fige l'image caméra.
     */
    private fun analyzeFrozenBitmap(bitmap: Bitmap) {
        if (!_uiState.value.isOcrReady || bitmap.isRecycled) return
        viewModelScope.launch(Dispatchers.Default) {
            val roiBitmap = runCatching {
                AutoImagePreprocessor.cropAndRotateRoi(
                    source = bitmap,
                    rotationDegrees = 0,
                    roiFraction = _uiState.value.roiFraction,
                    viewportAspectRatio = 0f,
                )
            }.getOrNull() ?: return@launch

            try {
                val result = ocrEngine.recognize(roiBitmap)
                onOcrResult(result, isFromFrozenCapture = true)
            } finally {
                if (roiBitmap !== bitmap && !roiBitmap.isRecycled) {
                    roiBitmap.recycle()
                }
            }
        }
    }

    /**
     * Analyse un sous-rectangle spécifiquement recadré par l'utilisateur sur l'image figée.
     * On vide d'abord le texte reconnu pour forcer [onOcrResult] à accepter le nouveau résultat
     * même si la zone recadrée contient moins de lignes que l'analyse précédente.
     */
    private fun analyzeCropInBitmap(crop: android.graphics.Rect) {
        val bitmap = _uiState.value.frozenFrameBitmap ?: return
        if (!_uiState.value.isOcrReady || bitmap.isRecycled) return

        viewModelScope.launch(Dispatchers.Default) {
            // Vider le texte avant l'analyse pour garantir que le résultat recadré
            // (potentiellement moins de lignes) remplace toujours le texte précédent.
            _uiState.update { it.copy(isAnalyzingCrop = true, recognizedText = "", confidence = 0) }

            val cropX = crop.left.coerceIn(0, bitmap.width - 1)
            val cropY = crop.top.coerceIn(0, bitmap.height - 1)
            val cropW = crop.width().coerceIn(1, bitmap.width - cropX)
            val cropH = crop.height().coerceIn(1, bitmap.height - cropY)

            val cropped = runCatching {
                Bitmap.createBitmap(bitmap, cropX, cropY, cropW, cropH)
            }.getOrNull()

            if (cropped == null) {
                _uiState.update { it.copy(isAnalyzingCrop = false) }
                return@launch
            }

            try {
                val result = ocrEngine.recognize(cropped)
                onOcrResult(result, isFromFrozenCapture = true)
            } finally {
                _uiState.update { it.copy(isAnalyzingCrop = false) }
                if (cropped !== bitmap && !cropped.isRecycled) {
                    cropped.recycle()
                }
            }
        }
    }

    private fun initializeOcrEngine() {
        viewModelScope.launch {
            ocrEngine.initialize()
                .onSuccess {
                    _uiState.update { it.copy(isOcrReady = true) }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isOcrReady = false,
                            errorMessage = err.localizedMessage ?: messages.ocrInitError,
                        )
                    }
                }
        }
    }

    private fun observeHistory() {
        viewModelScope.launch {
            _uiState
                .map { it.showFavoritesOnly }
                .distinctUntilChanged()
                .flatMapLatest { favoritesOnly ->
                    if (favoritesOnly) {
                        historyRepository.favoriteScans
                    } else {
                        historyRepository.allScans
                    }
                }
                .collect { records ->
                    _uiState.update { it.copy(historyRecords = records) }
                }
        }
    }

    internal fun onOcrResult(
        result: OcrResult,
        isFromFrozenCapture: Boolean = false,
    ) {
        when (result) {
            is OcrResult.Success -> {
                consecutiveEmptyFrames = 0
                _uiState.update { state ->
                    val currentText = state.recognizedText
                    val currentLen = currentText.count { !it.isWhitespace() }
                    val newLen = result.text.count { !it.isWhitespace() }
                    val currentLines = if (currentText.isEmpty()) 0 else currentText.count { it == '\n' } + 1
                    val newLines = result.text.count { it == '\n' } + 1

                    if (state.isPaused) {
                        if (!isFromFrozenCapture) {
                            return@update state
                        }
                        val shouldUpdateFrozen =
                            currentText.isEmpty() ||
                                newLines > currentLines ||
                                newLen > currentLen ||
                                (newLines == currentLines &&
                                    newLen >= (currentLen * 85) / 100 &&
                                    result.confidence >= state.confidence)
                        return@update if (shouldUpdateFrozen) {
                            state.copy(
                                recognizedText = result.text,
                                confidence = result.confidence,
                                wasAutoInverted = result.wasInverted,
                            )
                        } else {
                            state
                        }
                    }

                    if (currentText.isEmpty()) {
                        consecutiveShorterFrames = 0
                        candidateText = result.text
                        candidateMatchCount = 1
                        return@update state.copy(
                            recognizedText = result.text,
                            confidence = result.confidence,
                            wasAutoInverted = result.wasInverted,
                        )
                    }

                    if (result.text == currentText) {
                        consecutiveShorterFrames = 0
                        candidateText = result.text
                        candidateMatchCount = 1
                        return@update state.copy(
                            confidence = maxOf(state.confidence, result.confidence),
                            wasAutoInverted = result.wasInverted,
                        )
                    }

                    val isTruncatedOrShorter =
                        newLines < currentLines || newLen < (currentLen * 85) / 100
                    if (isTruncatedOrShorter) {
                        consecutiveShorterFrames++
                        if (consecutiveShorterFrames < SHORTER_TEXT_HOLD_FRAMES) {
                            return@update state
                        }
                        consecutiveShorterFrames = 0
                        candidateText = result.text
                        candidateMatchCount = 1
                        return@update state.copy(
                            recognizedText = result.text,
                            confidence = result.confidence,
                            wasAutoInverted = result.wasInverted,
                        )
                    }

                    consecutiveShorterFrames = 0

                    val isMoreComplete =
                        (newLines > currentLines && newLen >= currentLen) ||
                            (newLines == currentLines && newLen > currentLen && result.confidence >= state.confidence - 8)
                    if (isMoreComplete) {
                        candidateText = result.text
                        candidateMatchCount = 1
                        return@update state.copy(
                            recognizedText = result.text,
                            confidence = result.confidence,
                            wasAutoInverted = result.wasInverted,
                        )
                    }

                    if (result.text == candidateText) {
                        candidateMatchCount++
                    } else {
                        candidateText = result.text
                        candidateMatchCount = 1
                    }

                    val isSignificantlyMoreConfident = result.confidence >= state.confidence + 5
                    val isConfirmedCandidate =
                        candidateMatchCount >= CANDIDATE_CONFIRM_FRAMES &&
                            result.confidence >= state.confidence - 5

                    if (isSignificantlyMoreConfident || isConfirmedCandidate) {
                        state.copy(
                            recognizedText = result.text,
                            confidence = result.confidence,
                            wasAutoInverted = result.wasInverted,
                        )
                    } else {
                        state
                    }
                }
            }

            OcrResult.Empty -> {
                if (!_uiState.value.isPaused) {
                    consecutiveEmptyFrames++
                    if (consecutiveEmptyFrames >= EMPTY_FRAMES_TO_CLEAR) {
                        consecutiveShorterFrames = 0
                        candidateText = ""
                        candidateMatchCount = 0
                        _uiState.update { state ->
                            if (state.isPaused || state.recognizedText.isEmpty()) {
                                state
                            } else {
                                state.copy(
                                    recognizedText = "",
                                    confidence = 0,
                                    wasAutoInverted = false,
                                )
                            }
                        }
                    }
                }
            }

            is OcrResult.Error -> {
                _uiState.update { it.copy(errorMessage = result.message) }
            }
        }
    }

    private fun saveCurrentScan() {
        val currentState = _uiState.value
        val textToSave = currentState.recognizedText.trim()
        if (textToSave.isEmpty()) return

        viewModelScope.launch {
            historyRepository.saveScan(
                translatedText = textToSave,
                confidence = currentState.confidence,
            )
            _uiState.update {
                it.copy(statusMessage = messages.translationSaved)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        cleanupScope.launch {
            ocrEngine.close()
        }
    }

    companion object {
        internal const val EMPTY_FRAMES_TO_CLEAR = 8
        internal const val SHORTER_TEXT_HOLD_FRAMES = 6
        private const val CANDIDATE_CONFIRM_FRAMES = 2
        private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        fun provideFactory(context: Context): ViewModelProvider.Factory {
            val appContext = context.applicationContext
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val tessDataManager = TessDataManager(appContext)
                    val ocrEngine = AurebeshOcrEngine(tessDataManager)
                    val database = AppDatabase.getInstance(appContext)
                    val repository = ScanHistoryRepository(database.scanHistoryDao())
                    val messages = ScannerMessages(
                        translationSaved = context.getString(R.string.msg_translation_saved),
                        historyCleared = context.getString(R.string.msg_history_cleared),
                        ocrInitError = context.getString(R.string.ocr_init_error),
                    )
                    return ScannerViewModel(
                        ocrEngine = ocrEngine,
                        historyRepository = repository,
                        messages = messages,
                    ) as T
                }
            }
        }
    }
}
