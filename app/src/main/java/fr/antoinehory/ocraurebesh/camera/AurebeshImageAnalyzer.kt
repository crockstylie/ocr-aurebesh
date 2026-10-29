package fr.antoinehory.ocraurebesh.camera

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import fr.antoinehory.ocraurebesh.domain.OcrResult
import fr.antoinehory.ocraurebesh.ocr.AurebeshOcrEngine
import fr.antoinehory.ocraurebesh.ocr.AutoImagePreprocessor
import fr.antoinehory.ocraurebesh.ocr.RoiRectFraction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Analyseur de frames CameraX ([ImageAnalysis.Analyzer]) dédié à la détection Aurebesh.
 *
 * Respecte les garde-fous critiques :
 * - Ferme systématiquement chaque [ImageProxy] via `use {}` pour ne jamais bloquer le pipeline CameraX.
 * - Ignore immédiatement les frames lorsque le scan est en pause, qu'une analyse OCR est déjà en cours,
 *   ou que le délai minimal ([minIntervalMs]) entre deux analyses n'est pas écoulé.
 * - Découpe uniquement la région d'intérêt ([roiFraction]) avant d'invoquer [AurebeshOcrEngine].
 */
class AurebeshImageAnalyzer(
    private val ocrEngine: AurebeshOcrEngine,
    private val coroutineScope: CoroutineScope,
    private val isPausedProvider: () -> Boolean,
    private val roiFraction: RoiRectFraction = RoiRectFraction(),
    private val minIntervalMs: Long = DEFAULT_MIN_INTERVAL_MS,
    private val onResult: (OcrResult) -> Unit,
) : ImageAnalysis.Analyzer {

    private val isProcessing = AtomicBoolean(false)
    @Volatile
    private var lastAnalysisTimestampMs: Long = 0L

    override fun analyze(imageProxy: ImageProxy) {
        val now = SystemClock.elapsedRealtime()
        if (
            isPausedProvider() ||
            (now - lastAnalysisTimestampMs) < minIntervalMs ||
            !isProcessing.compareAndSet(false, true)
        ) {
            imageProxy.close()
            return
        }

        lastAnalysisTimestampMs = now

        val roiBitmap: Bitmap? = try {
            imageProxy.use { proxy ->
                val rotationDegrees = proxy.imageInfo.rotationDegrees
                val fullFrameBitmap = proxy.toBitmap()
                val cropped = AutoImagePreprocessor.cropAndRotateRoi(
                    source = fullFrameBitmap,
                    rotationDegrees = rotationDegrees,
                    roiFraction = roiFraction,
                )
                if (cropped !== fullFrameBitmap) {
                    fullFrameBitmap.recycle()
                }
                cropped
            }
        } catch (e: Exception) {
            isProcessing.set(false)
            onResult(
                OcrResult.Error(
                    message = e.localizedMessage ?: "Erreur lors de l'extraction de l'image caméra",
                    cause = e,
                )
            )
            null
        }

        if (roiBitmap == null) return

        coroutineScope.launch {
            try {
                if (!isPausedProvider()) {
                    val result = ocrEngine.recognize(roiBitmap)
                    if (!isPausedProvider()) {
                        onResult(result)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onResult(
                    OcrResult.Error(
                        message = e.localizedMessage ?: "Erreur lors de l'analyse OCR",
                        cause = e,
                    )
                )
            } finally {
                if (!roiBitmap.isRecycled) {
                    roiBitmap.recycle()
                }
                isProcessing.set(false)
            }
        }
    }

    companion object {
        private const val DEFAULT_MIN_INTERVAL_MS = 300L
    }
}
