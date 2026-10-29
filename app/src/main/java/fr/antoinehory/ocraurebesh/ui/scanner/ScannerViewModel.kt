package fr.antoinehory.ocraurebesh.ui.scanner

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import fr.antoinehory.ocraurebesh.camera.AurebeshImageAnalyzer
import fr.antoinehory.ocraurebesh.data.local.AppDatabase
import fr.antoinehory.ocraurebesh.data.repository.ScanHistoryRepository
import fr.antoinehory.ocraurebesh.domain.OcrResult
import fr.antoinehory.ocraurebesh.ocr.AurebeshOcrEngine
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
 * ViewModel principal orchestrant le flux caméra, le moteur OCR Aurebesh et l'historique Room.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ScannerViewModel(
    private val ocrEngine: OcrRecognizer,
    private val historyRepository: ScanHistoryRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScannerUiState())
    val uiState: StateFlow<ScannerUiState> = _uiState.asStateFlow()

    val imageAnalyzer = AurebeshImageAnalyzer(
        ocrEngine = ocrEngine,
        coroutineScope = viewModelScope,
        isPausedProvider = { _uiState.value.isPaused },
        roiFraction = _uiState.value.roiFraction,
        onResult = ::onOcrResult,
    )

    init {
        initializeOcrEngine()
        observeHistory()
    }

    fun onEvent(event: ScannerUiEvent) {
        when (event) {
            ScannerUiEvent.TogglePause -> {
                _uiState.update { state ->
                    state.copy(isPaused = !state.isPaused)
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
                _uiState.update {
                    it.copy(
                        recognizedText = "",
                        confidence = 0,
                        wasAutoInverted = false,
                        isPaused = false,
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
                    _uiState.update { it.copy(statusMessage = "Historique effacé") }
                }
            }

            ScannerUiEvent.DismissMessage -> {
                _uiState.update { it.copy(statusMessage = null, errorMessage = null) }
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
                            errorMessage = err.localizedMessage ?: "Erreur d'initialisation OCR",
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

    internal fun onOcrResult(result: OcrResult) {
        when (result) {
            is OcrResult.Success -> {
                _uiState.update { state ->
                    if (state.isPaused) {
                        state
                    } else {
                        state.copy(
                            recognizedText = result.text,
                            confidence = result.confidence,
                            wasAutoInverted = result.wasInverted,
                        )
                    }
                }
            }

            OcrResult.Empty -> {
                // On conserve la dernière lecture valide affichée à l'écran pour éviter le clignotement
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
                it.copy(statusMessage = "Traduction enregistrée dans l'historique")
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
                    return ScannerViewModel(
                        ocrEngine = ocrEngine,
                        historyRepository = repository,
                    ) as T
                }
            }
        }
    }
}
