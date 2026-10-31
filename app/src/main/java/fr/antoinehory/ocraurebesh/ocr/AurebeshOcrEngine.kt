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
 * - Support des textes courts ET des paragraphes multi-lignes denses (ex. 20 lignes de 30 caractères)
 *   avec conservation des retours à la ligne (`\n`).
 * - Support des textes légèrement flous, à faible contraste ou en mode contour, tout en filtrant
 *   les faux positifs sur un bureau vide.
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
                // 1. Passes prioritaires sur les lignes de texte candidates isolées et nettoyées
                var bestCandidatePass = PassResult.EMPTY
                var bestCandidateInverted = false

                for (candidate in preprocessed.lineCandidates) {
                    val psm = if (candidate.bitmap.width >= candidate.bitmap.height * 1.5f) {
                        TessBaseAPI.PageSegMode.PSM_SINGLE_LINE
                    } else {
                        TessBaseAPI.PageSegMode.PSM_SINGLE_BLOCK
                    }
                    val pass = runSinglePass(api, candidate.bitmap, psm)
                    if (pass.qualityScore > bestCandidatePass.qualityScore) {
                        bestCandidatePass = pass
                        bestCandidateInverted = candidate.wasInverted
                    }
                }

                // Si un candidat isolé présente un texte d'au moins 3 caractères et une très bonne confiance,
                // on le valide immédiatement (détection quasi certaine sans bruit de décor).
                if (
                    bestCandidatePass.nonSpaceLength >= 3 &&
                    bestCandidatePass.confidence >= HIGH_CONFIDENCE_SHORT_CIRCUIT
                ) {
                    return@withLock OcrResult.Success(
                        text = bestCandidatePass.text,
                        confidence = bestCandidatePass.confidence,
                        wasInverted = bestCandidateInverted,
                    )
                }

                // 2. Passes sur l'image entière (ROI complète) : utile pour les paragraphes denses
                val primaryPass = if (preprocessed.primaryInkRatio in MIN_INK_RATIO..MAX_INK_RATIO) {
                    runSinglePass(api, preprocessed.primaryBitmap, TessBaseAPI.PageSegMode.PSM_SINGLE_BLOCK)
                } else {
                    PassResult.EMPTY
                }

                // Court-circuit de la Passe 2 si la Passe 1 est déjà très fiable
                if (
                    primaryPass.nonSpaceLength >= 3 &&
                    primaryPass.confidence >= HIGH_CONFIDENCE_SHORT_CIRCUIT &&
                    primaryPass.qualityScore >= bestCandidatePass.qualityScore
                ) {
                    return@withLock OcrResult.Success(
                        text = primaryPass.text,
                        confidence = primaryPass.confidence,
                        wasInverted = preprocessed.wasAutoInverted,
                    )
                }

                // Passe 3 : polarité opposée de la ROI complète
                val invertedPass = if (preprocessed.invertedInkRatio in MIN_INK_RATIO..MAX_INK_RATIO) {
                    runSinglePass(api, preprocessed.invertedBitmap, TessBaseAPI.PageSegMode.PSM_SINGLE_BLOCK)
                } else {
                    PassResult.EMPTY
                }

                // Choix de la meilleure passe parmi : ligne candidate vs ROI primaire vs ROI inversée
                var bestPass = bestCandidatePass
                var wasInvertedUsed = bestCandidateInverted

                if (primaryPass.qualityScore > bestPass.qualityScore) {
                    bestPass = primaryPass
                    wasInvertedUsed = preprocessed.wasAutoInverted
                }

                if (invertedPass.qualityScore > bestPass.qualityScore) {
                    bestPass = invertedPass
                    wasInvertedUsed = !preprocessed.wasAutoInverted
                }

                val minRequiredConfidence = when {
                    bestPass.nonSpaceLength < 2 -> 101 // Rejette un caractère unique isolé
                    bestPass.nonSpaceLength <= 3 -> SHORT_TEXT_MIN_CONFIDENCE
                    else -> MIN_ACCEPTABLE_CONFIDENCE
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
                preprocessed.recycleAll()
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
            api.setVariable("load_system_dawg", "0")
            api.setVariable("load_freq_dawg", "0")
            tessBaseApi = api
            isInitialized = true
        }
    }

    private fun runSinglePass(
        api: TessBaseAPI,
        bitmap: Bitmap,
        pageSegMode: Int = TessBaseAPI.PageSegMode.PSM_SINGLE_BLOCK,
    ): PassResult {
        api.pageSegMode = pageSegMode
        api.setImage(bitmap)
        val rawText = api.utF8Text.orEmpty()
        val confidence = api.meanConfidence().coerceIn(0, 100)
        val wordConfidences = api.wordConfidences() ?: IntArray(0)
        api.clear()

        // Découpage ligne par ligne pour préserver la mise en page des textes multi-lignes (ex. 20 lignes)
        val rawLines = rawText
            .uppercase()
            .lines()
            .map { line ->
                line.replace(DISALLOWED_CHARS_REGEX, " ")
                    .trim()
                    .split(MULTIPLE_SPACES_REGEX)
                    .filter { it.isNotEmpty() }
            }
            .filter { it.isNotEmpty() }

        if (rawLines.isEmpty()) return PassResult.EMPTY

        val totalWordCount = rawLines.sumOf { it.size }
        val useWordConfidences = wordConfidences.size == totalWordCount
        var wordIdx = 0

        val filteredLines = mutableListOf<String>()
        val allKeptWords = mutableListOf<String>()

        for (lineWords in rawLines) {
            val keptLineWords = mutableListOf<String>()
            for (word in lineWords) {
                val keep = if (useWordConfidences) {
                    val wordConf = wordConfidences[wordIdx]
                    if (word.length == 1) {
                        wordConf >= SINGLE_LETTER_WORD_MIN_CONFIDENCE
                    } else {
                        wordConf >= MIN_WORD_CONFIDENCE
                    }
                } else {
                    true
                }
                wordIdx++
                if (keep) {
                    keptLineWords.add(word)
                    allKeptWords.add(word)
                }
            }
            if (keptLineWords.isNotEmpty()) {
                filteredLines.add(keptLineWords.joinToString(" "))
            }
        }

        if (allKeptWords.isEmpty()) return PassResult.EMPTY

        // Rejet si tous les mots ne sont que des lettres uniques éparpillées (bruit typique d'objets sur un bureau)
        if (allKeptWords.all { it.length == 1 }) {
            return PassResult.EMPTY
        }

        val cleanedText = filteredLines.joinToString("\n")
        val nonSpaceLength = cleanedText.count { it != ' ' && it != '\n' }

        if (nonSpaceLength < 2 || confidence < MIN_ACCEPTABLE_CONFIDENCE) {
            return PassResult.EMPTY
        }

        val lengthWeight = min(6, nonSpaceLength)
        val qualityScore = confidence * (2 + lengthWeight)

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
    ) {
        companion object {
            val EMPTY = PassResult(
                text = "",
                confidence = 0,
                nonSpaceLength = 0,
                qualityScore = 0,
            )
        }
    }

    companion object {
        private const val ALLOWED_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789 "
        // Permet aussi bien un mot court en mode contour (0.06% de la ROI) qu'un bloc dense de 20 lignes (45%)
        private const val MIN_INK_RATIO = 0.0006f
        private const val MAX_INK_RATIO = 0.45f
        private const val HIGH_CONFIDENCE_SHORT_CIRCUIT = 82
        private const val MIN_ACCEPTABLE_CONFIDENCE = 58
        private const val SHORT_TEXT_MIN_CONFIDENCE = 68
        private const val MIN_WORD_CONFIDENCE = 52
        private const val SINGLE_LETTER_WORD_MIN_CONFIDENCE = 72
        private val DISALLOWED_CHARS_REGEX = Regex("[^A-Z0-9 ]")
        private val MULTIPLE_SPACES_REGEX = Regex("\\s+")
    }
}
