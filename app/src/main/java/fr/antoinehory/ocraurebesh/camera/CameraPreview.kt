package fr.antoinehory.ocraurebesh.camera

import android.view.ViewGroup
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.concurrent.Executors

/**
 * Composable affichant le flux caméra via [PreviewView] et attachant les cas d'utilisation
 * [Preview] et [ImageAnalysis] au [LocalLifecycleOwner].
 *
 * @param analyzer Instance de [ImageAnalysis.Analyzer] recevant les frames en mode `STRATEGY_KEEP_ONLY_LATEST`.
 * @param isTorchEnabled État souhaité de la lampe torche.
 * @param onFlashAvailabilityChanged Callback indiquant si l'appareil dispose d'un flash matériel.
 * @param onCameraError Callback invoqué si l'initialisation CameraX échoue.
 * @param modifier [Modifier] appliqué au conteneur [AndroidView].
 */
@Composable
fun CameraPreview(
    analyzer: ImageAnalysis.Analyzer,
    isTorchEnabled: Boolean,
    onFlashAvailabilityChanged: (Boolean) -> Unit,
    onCameraError: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val currentFlashCallback by rememberUpdatedState(onFlashAvailabilityChanged)
    val currentErrorCallback by rememberUpdatedState(onCameraError)

    val previewView = remember {
        PreviewView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    var boundCamera by remember { mutableStateOf<Camera?>(null) }

    DisposableEffect(lifecycleOwner, analyzer) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val mainExecutor = ContextCompat.getMainExecutor(context)

        val listener = Runnable {
            try {
                val cameraProvider = cameraProviderFuture.get()
                val previewUseCase = Preview.Builder()
                    .build()
                    .also { it.surfaceProvider = previewView.surfaceProvider }

                val imageAnalysisUseCase = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                    .build()
                    .also { it.setAnalyzer(analysisExecutor, analyzer) }

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                cameraProvider.unbindAll()
                val camera = cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    previewUseCase,
                    imageAnalysisUseCase,
                )
                boundCamera = camera
                val hasFlash = camera.cameraInfo.hasFlashUnit()
                currentFlashCallback(hasFlash)
                if (hasFlash) {
                    camera.cameraControl.enableTorch(isTorchEnabled)
                }
            } catch (e: Exception) {
                currentErrorCallback(
                    e.localizedMessage ?: "Impossible de démarrer la caméra"
                )
            }
        }

        cameraProviderFuture.addListener(listener, mainExecutor)

        onDispose {
            boundCamera = null
            runCatching {
                cameraProviderFuture.get().unbindAll()
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            analysisExecutor.shutdown()
        }
    }

    LaunchedEffect(boundCamera, isTorchEnabled) {
        val camera = boundCamera ?: return@LaunchedEffect
        if (camera.cameraInfo.hasFlashUnit()) {
            camera.cameraControl.enableTorch(isTorchEnabled)
        }
    }

    AndroidView(
        factory = { previewView },
        modifier = modifier,
    )
}
