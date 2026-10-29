package fr.antoinehory.ocraurebesh.ocr

import android.graphics.Bitmap
import com.googlecode.tesseract.android.TessBaseAPI
import fr.antoinehory.ocraurebesh.domain.OcrResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Moteur OCR Aurebesh encapsulant [TessBaseAPI] de manière thread-safe.
 *
 * Caractéristiques clés :
 * - Toutes les opérations natives sont sérialisées via un [Mutex] sur un [CoroutineDispatcher] dédié.
 * - Intègre le prétraitement automatique [AutoImagePreprocessor] et une **double-passe transparente** :
 *   si la confiance sur la polarité estimée est inférieure à [FALLBACK_CONFIDENCE_THRESHOLD],
 *   l'image inversée est automatiquement testée et le meilleur résultat est retenu.
 */
class AurebeshOcrEngine(
    private val tessDataManager: TessDataManager,
    private val ocrDispatcher: CoroutineDispatcher = Dispatchers.Default.limitedParallelism(1)
) {

    private val mutex = Mutex()
    private var tessBaseApi: TessBaseAPI? = null
    private var isInitialized = false

    /**
     * Initialise le moteur Tesseract avec le modèle `aurebesh.traineddata`.
     */
    suspend fun initialize(): Result<Unit> = withContext(ocrDispatcher) {
        mutex.withLock {
            if (isInitialized && tessBaseApi != null) {
                return@withLock Result.success(Unit)
            }

            tessDataManager.ensureTessDataReady().mapCatching { dataPath ->
                val api = TessBaseAPI()
                val success = api.init(dataPath, TessDataManager.LANGUAGE_CODE)
                if (!success) {
                    api.recycle()
                    throw IllegalStateException("Échec de l'initialisation de TessBaseAPI avec le modèle ${TessDataManager.LANGUAGE_CODE}")
                }
                api.pageSegMode = TessBaseAPI.PageSegMode.PSM_SINGLE_LINE
                api.setVariable(TessBaseAPI.VAR_CHAR_WHITELIST, ALLOWED_CHARS)
                tessBaseApi = api
                isInitialized = true
            }
        }
    }

    /**
     * Analyse un [Bitmap] correspondant à la zone de visée (ROI) et retourne un [OcrResult].
     *
     * @param roiBitmap Bitmap découpé de la zone de visée.
     */
    suspend fun recognize(roiBitmap: Bitmap): OcrResult = withContext(ocrDispatcher) {
        mutex.withLock {
            if (!isInitialized || tessBaseApi == null) {
                val initResult = initializeInternalUnlocked()
                if (initResult.isFailure) {
                    val err = initResult.exceptionOrNull()
                    return@withLock OcrResult.Error(
                        message = err?.localizedMessage ?: "Moteur OCR non initialisé",
                        cause = err
                    )
                }
            }

            val api = tessBaseApi ?: return@withLock OcrResult.Error("Instance TessBaseAPI indisponible")
            val preprocessed = AutoImagePreprocessor.preprocessForOcr(roiBitmap)

            try {
                // Passe 1 : sur la polarité détectée automatiquement par analyse des bordures
                val primaryPass = runSinglePass(api, preprocessed.primaryBitmap)

                if (primaryPass.text.isNotEmpty() && primaryPass.confidence >= FALLBACK_CONFIDENCE_THRESHOLD) {
                    return@withLock OcrResult.Success(
                        text = primaryPass.text,
                        confidence = primaryPass.confidence,
                        wasInverted = preprocessed.wasAutoInverted
                    )
                }

                // Passe 2 (Fallback transparent) : test de la polarité opposée si confiance faible
                val invertedPass = runSinglePass(api, preprocessed.invertedBitmap)

                val bestPass: PassResult
                val wasInvertedUsed: Boolean

                if (invertedPass.text.isNotEmpty() && invertedPass.confidence > primaryPass.confidence) {
                    bestPass = invertedPass
                    wasInvertedUsed = !preprocessed.wasAutoInverted
                } else {
                    bestPass = primaryPass
                    wasInvertedUsed = preprocessed.wasAutoInverted
                }

                if (bestPass.text.isEmpty() || bestPass.confidence < MIN_ACCEPTABLE_CONFIDENCE) {
                    OcrResult.Empty
                } else {
                    OcrResult.Success(
                        text = bestPass.text,
                        confidence = bestPass.confidence,
                        wasInverted = wasInvertedUsed
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                OcrResult.Error(
                    message = e.localizedMessage ?: "Erreur lors de l'analyse OCR",
                    cause = e
                )
            } finally {
                preprocessed.primaryBitmap.recycle()
                preprocessed.invertedBitmap.recycle()
            }
        }
    }

    /**
     * Libère les ressources natives C++ de Tesseract.
     * Doit être appelé dans `ViewModel.onCleared()`.
     */
    suspend fun close() = withContext(ocrDispatcher) {
        mutex.withLock {
            tessBaseApi?.recycle()
            tessBaseApi = null
            isInitialized = false
        }
    }

    private suspend fun initializeInternalUnlocked(): Result<Unit> {
        return tessDataManager.ensureTessDataReady().mapCatching { dataPath ->
            val api = TessBaseAPI()
            val success = api.init(dataPath, TessDataManager.LANGUAGE_CODE)
            if (!success) {
                api.recycle()
                throw IllegalStateException("Échec de l'initialisation de TessBaseAPI")
            }
            api.pageSegMode = TessBaseAPI.PageSegMode.PSM_SINGLE_LINE
            api.setVariable(TessBaseAPI.VAR_CHAR_WHITELIST, ALLOWED_CHARS)
            tessBaseApi = api
            isInitialized = true
        }
    }

    private fun runSinglePass(api: TessBaseAPI, bitmap: Bitmap): PassResult {
        api.setImage(bitmap)
        val rawText = api.utF8Text.orEmpty()
        val confidence = api.meanConfidence().coerceIn(0, 100)
        api.clear()

        val cleanedText = rawText
            .uppercase()
            .replace( DISALLOWED_CHARS_REGEX, " ")
            .replace(MULTIPLE_SPACES_REGEX, " ")
            .trim()

        return PassResult(text = cleanedText, confidence = confidence)
    }

    private data class PassResult(
        val text: String,
        val confidence: Int
    )

    companion object {
        private const val ALLOWED_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789 "
        private const val FALLBACK_CONFIDENCE_THRESHOLD = 65
        private const val MIN_ACCEPTABLE_CONFIDENCE = 45
        private val DISALLOWED_CHARS_REGEX = Regex("[^A-Z0-9 ]")
        private val MULTIPLE_SPACES_REGEX = Regex("\\s+")
    }
}
