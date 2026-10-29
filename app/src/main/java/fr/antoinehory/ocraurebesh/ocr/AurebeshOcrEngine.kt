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
import kotlin.math.min

/**
 * Contrat d'abstraction du moteur de reconnaissance OCR Aurebesh,
 * facilitant l'injection de Fakes lors des tests unitaires de [fr.antoinehory.ocraurebesh.ui.scanner.ScannerViewModel].
 */
interface OcrRecognizer {
    suspend fun initialize(): Result<Unit>
    suspend fun recognize(roiBitmap: Bitmap): OcrResult
    suspend fun close()
}

/**
 * Moteur OCR Aurebesh encapsulant [TessBaseAPI] de manière thread-safe.
 *
 * Caractéristiques clés :
 * - Toutes les opérations natives sont sérialisées via un [Mutex] sur un [CoroutineDispatcher] dédié.
 * - Utilise `PSM_SINGLE_BLOCK` pour reconnaître aussi bien un mot court, une ligne complète ou deux lignes.
 * - Intègre le prétraitement automatique [AutoImagePreprocessor] et une **double-passe intelligente** :
 *   compare la polarité principale et la polarité inversée via un score de qualité composite
 *   (confiance pondérée par la longueur utile) afin d'éliminer les hallucinations d'un seul caractère.
 */
class AurebeshOcrEngine(
    private val tessDataManager: TessDataManager,
    private val ocrDispatcher: CoroutineDispatcher = Dispatchers.Default.limitedParallelism(1),
) : OcrRecognizer {

    private val mutex = Mutex()
    private var tessBaseApi: TessBaseAPI? = null
    private var isInitialized = false

    /**
     * Initialise le moteur Tesseract avec le modèle `aurebesh.traineddata`.
     */
    override suspend fun initialize(): Result<Unit> = withContext(ocrDispatcher) {
        mutex.withLock {
            if (isInitialized && tessBaseApi != null) {
                return@withLock Result.success(Unit)
            }
            initializeInternalUnlocked()
        }
    }

    /**
     * Analyse un [Bitmap] correspondant à la zone de visée (ROI) et retourne un [OcrResult].
     *
     * @param roiBitmap Bitmap découpé de la zone de visée.
     */
    override suspend fun recognize(roiBitmap: Bitmap): OcrResult = withContext(ocrDispatcher) {
        mutex.withLock {
            if (!isInitialized || tessBaseApi == null) {
                val initResult = initializeInternalUnlocked()
                if (initResult.isFailure) {
                    val err = initResult.exceptionOrNull()
                    return@withLock OcrResult.Error(
                        message = err?.localizedMessage ?: "Moteur OCR non initialisé",
                        cause = err,
                    )
                }
            }

            val api = tessBaseApi ?: return@withLock OcrResult.Error("Instance TessBaseAPI indisponible")
            val preprocessed = AutoImagePreprocessor.preprocessForOcr(roiBitmap)

            try {
                // Passe 1 : sur la polarité détectée automatiquement
                val primaryPass = runSinglePass(api, preprocessed.primaryBitmap)

                // On ne court-circuite la Passe 2 que si la Passe 1 est très fiable (>= 80% et >= 3 caractères)
                if (
                    primaryPass.nonSpaceLength >= 3 &&
                    primaryPass.confidence >= HIGH_CONFIDENCE_SHORT_CIRCUIT
                ) {
                    return@withLock OcrResult.Success(
                        text = primaryPass.text,
                        confidence = primaryPass.confidence,
                        wasInverted = preprocessed.wasAutoInverted,
                    )
                }

                // Passe 2 : test systématique de la polarité opposée (texte clair sur fond sombre vs sombre sur clair)
                val invertedPass = runSinglePass(api, preprocessed.invertedBitmap)

                val bestPass: PassResult
                val wasInvertedUsed: Boolean

                if (invertedPass.qualityScore > primaryPass.qualityScore) {
                    bestPass = invertedPass
                    wasInvertedUsed = !preprocessed.wasAutoInverted
                } else {
                    bestPass = primaryPass
                    wasInvertedUsed = preprocessed.wasAutoInverted
                }

                val minRequiredConfidence = if (bestPass.nonSpaceLength == 1) {
                    SINGLE_CHAR_MIN_CONFIDENCE
                } else {
                    MIN_ACCEPTABLE_CONFIDENCE
                }

                if (bestPass.text.isEmpty() || bestPass.confidence < minRequiredConfidence) {
                    OcrResult.Empty
                } else {
                    OcrResult.Success(
                        text = bestPass.text,
                        confidence = bestPass.confidence,
                        wasInverted = wasInvertedUsed,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                OcrResult.Error(
                    message = e.localizedMessage ?: "Erreur lors de l'analyse OCR",
                    cause = e,
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
    override suspend fun close() = withContext(ocrDispatcher) {
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
                throw IllegalStateException(
                    "Échec de l'initialisation de TessBaseAPI avec le modèle ${TessDataManager.LANGUAGE_CODE}"
                )
            }
            api.pageSegMode = TessBaseAPI.PageSegMode.PSM_SINGLE_BLOCK
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
            .replace(DISALLOWED_CHARS_REGEX, " ")
            .replace(MULTIPLE_SPACES_REGEX, " ")
            .trim()

        val nonSpaceLength = cleanedText.count { it != ' ' }
        // Pondération favorisant les vraies séquences de lettres par rapport à un bruit d'un seul caractère
        val lengthWeight = min(4, nonSpaceLength)
        val qualityScore = if (nonSpaceLength == 0 || confidence < MIN_ACCEPTABLE_CONFIDENCE) {
            0
        } else {
            confidence * (2 + lengthWeight)
        }

        return PassResult(
            text = cleanedText,
            confidence = confidence,
            nonSpaceLength = nonSpaceLength,
            qualityScore = qualityScore,
        )
    }

    private data class PassResult(
        val text: String,
        val confidence: Int,
        val nonSpaceLength: Int,
        val qualityScore: Int,
    )

    companion object {
        private const val ALLOWED_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789 "
        private const val HIGH_CONFIDENCE_SHORT_CIRCUIT = 80
        private const val MIN_ACCEPTABLE_CONFIDENCE = 45
        private const val SINGLE_CHAR_MIN_CONFIDENCE = 72
        private val DISALLOWED_CHARS_REGEX = Regex("[^A-Z0-9 ]")
        private val MULTIPLE_SPACES_REGEX = Regex("\\s+")
    }
}
