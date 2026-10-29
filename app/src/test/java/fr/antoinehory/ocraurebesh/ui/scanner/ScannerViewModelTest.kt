package fr.antoinehory.ocraurebesh.ui.scanner

import android.graphics.Bitmap
import fr.antoinehory.ocraurebesh.data.FakeScanHistoryDao
import fr.antoinehory.ocraurebesh.data.repository.ScanHistoryRepository
import fr.antoinehory.ocraurebesh.domain.OcrResult
import fr.antoinehory.ocraurebesh.ocr.OcrRecognizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Tests unitaires de [ScannerViewModel] couvrant l'initialisation OCR, la réception des traductions,
 * la pause/gel du scan, la lampe torche et la gestion de l'historique/favoris Room.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ScannerViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `initializes OCR engine and updates uiState when ready`() = runTest {
        val fakeOcr = FakeOcrRecognizer(initResult = Result.success(Unit))
        val viewModel = createViewModel(fakeOcr)

        assertTrue(viewModel.uiState.value.isOcrReady)
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `surfaces error message when OCR initialization fails`() = runTest {
        val fakeOcr = FakeOcrRecognizer(
            initResult = Result.failure(IllegalStateException("Fichier traineddata manquant"))
        )
        val viewModel = createViewModel(fakeOcr)

        assertFalse(viewModel.uiState.value.isOcrReady)
        assertEquals("Fichier traineddata manquant", viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `updates recognized text on OcrResult Success and ignores updates while paused`() = runTest {
        val viewModel = createViewModel()

        viewModel.onOcrResult(
            OcrResult.Success(
                text = "REBEL ALLIANCE",
                confidence = 91,
                wasInverted = true,
            )
        )

        assertEquals("REBEL ALLIANCE", viewModel.uiState.value.recognizedText)
        assertEquals(91, viewModel.uiState.value.confidence)
        assertTrue(viewModel.uiState.value.wasAutoInverted)

        // Un résultat vide conserve le dernier texte affiché pour éviter le clignotement
        viewModel.onOcrResult(OcrResult.Empty)
        assertEquals("REBEL ALLIANCE", viewModel.uiState.value.recognizedText)

        // Mise en pause (gel de l'écran)
        viewModel.onEvent(ScannerUiEvent.TogglePause)
        assertTrue(viewModel.uiState.value.isPaused)

        // Les nouveaux résultats OCR sont ignorés tant que le scan est figé
        viewModel.onOcrResult(
            OcrResult.Success(
                text = "GALACTIC EMPIRE",
                confidence = 99,
                wasInverted = false,
            )
        )
        assertEquals("REBEL ALLIANCE", viewModel.uiState.value.recognizedText)
    }

    @Test
    fun `toggles torch only when hardware flash is available`() = runTest {
        val viewModel = createViewModel()

        // Sans flash disponible, ToggleTorch ne doit pas activer la torche
        viewModel.onEvent(ScannerUiEvent.ToggleTorch)
        assertFalse(viewModel.uiState.value.isTorchEnabled)

        // Détection du flash matériel
        viewModel.onEvent(ScannerUiEvent.OnFlashAvailabilityChanged(true))
        assertTrue(viewModel.uiState.value.isFlashAvailable)

        viewModel.onEvent(ScannerUiEvent.ToggleTorch)
        assertTrue(viewModel.uiState.value.isTorchEnabled)

        // Si le flash devient indisponible, la torche repasse automatiquement à false
        viewModel.onEvent(ScannerUiEvent.OnFlashAvailabilityChanged(false))
        assertFalse(viewModel.uiState.value.isTorchEnabled)
    }

    @Test
    fun `saves current scan and filters favorites in history`() = runTest {
        val viewModel = createViewModel()

        viewModel.onOcrResult(
            OcrResult.Success(text = "TATOOINE", confidence = 85, wasInverted = false)
        )
        viewModel.onEvent(ScannerUiEvent.SaveCurrentScan)

        assertEquals(1, viewModel.uiState.value.historyRecords.size)
        val savedRecord = viewModel.uiState.value.historyRecords.first()
        assertEquals("TATOOINE", savedRecord.translatedText)
        assertEquals("Traduction enregistrée dans l'historique", viewModel.uiState.value.statusMessage)

        // Activation du filtre Favoris uniquement (aucun favori pour l'instant)
        viewModel.onEvent(ScannerUiEvent.SetShowFavoritesOnly(true))
        assertTrue(viewModel.uiState.value.historyRecords.isEmpty())

        // Marquage en favori
        viewModel.onEvent(ScannerUiEvent.ToggleFavoriteRecord(savedRecord.id, true))
        assertEquals(1, viewModel.uiState.value.historyRecords.size)

        // Effacement de l'historique
        viewModel.onEvent(ScannerUiEvent.ClearAllHistory)
        assertTrue(viewModel.uiState.value.historyRecords.isEmpty())
    }

    private fun createViewModel(
        fakeOcr: FakeOcrRecognizer = FakeOcrRecognizer(),
    ): ScannerViewModel {
        val fakeDao = FakeScanHistoryDao()
        val repository = ScanHistoryRepository(
            scanHistoryDao = fakeDao,
            ioDispatcher = UnconfinedTestDispatcher(),
        )
        return ScannerViewModel(
            ocrEngine = fakeOcr,
            historyRepository = repository,
        )
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    private val testDispatcher: TestDispatcher = UnconfinedTestDispatcher(),
) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(testDispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}

private class FakeOcrRecognizer(
    private val initResult: Result<Unit> = Result.success(Unit),
    private val nextResult: OcrResult = OcrResult.Empty,
) : OcrRecognizer {
    var isClosed = false
        private set

    override suspend fun initialize(): Result<Unit> = initResult

    override suspend fun recognize(roiBitmap: Bitmap): OcrResult = nextResult

    override suspend fun close() {
        isClosed = true
    }
}
