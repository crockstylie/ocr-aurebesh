package fr.antoinehory.ocraurebesh.camera

import android.graphics.Bitmap
import android.util.Size
import android.view.Surface
import android.view.View
import android.view.ViewGroup
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import fr.antoinehory.ocraurebesh.R
import java.util.concurrent.Executors

/**
 * Composable affichant le flux caméra via [PreviewView] et attachant les cas d'utilisation
 * [Preview] et [ImageAnalysis] (en haute définition `1920x1080`) au [LocalLifecycleOwner].
 *
 * Gère également :
 * - Le maintien de l'écran allumé (`keepScreenOn`) tant que le scan est actif.
 * - Le gel visuel instantané de l'image caméra lorsque [isPaused] passe à `true`, avec conservation
 *   de l'image figée ([frozenFrameBitmap] dans le ViewModel) lors des rotations d'écran
 *   et relance immédiate d'une analyse OCR dédiée sur la capture figée.
 */
@Composable
fun CameraPreview(
    analyzer: AurebeshImageAnalyzer,
    isPaused: Boolean,
    frozenFrameBitmap: Bitmap?,
    isTorchEnabled: Boolean,
    onFrozenFrameCaptured: (Bitmap) -> Unit,
    onFlashAvailabilityChanged: (Boolean) -> Unit,
    onCameraError: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentView = LocalView.current

    val currentFrozenCallback by rememberUpdatedState(onFrozenFrameCaptured)
    val currentFlashCallback by rememberUpdatedState(onFlashAvailabilityChanged)
    val currentErrorCallback by rememberUpdatedState(onCameraError)
    val cameraStartErrorText by rememberUpdatedState(stringResource(R.string.camera_start_error))

    DisposableEffect(currentView) {
        currentView.keepScreenOn = true
        onDispose {
            currentView.keepScreenOn = false
        }
    }

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

    // Lorsque l'utilisateur fige le scan, on capture le Bitmap du PreviewView s'il n'est pas déjà stocké
    // dans le ViewModel (ce qui préserve l'image figée lors d'une rotation Portrait <-> Paysage).
    LaunchedEffect(isPaused, frozenFrameBitmap) {
        if (isPaused && (frozenFrameBitmap == null || frozenFrameBitmap.isRecycled)) {
            previewView.bitmap?.let { captured ->
                currentFrozenCallback(captured)
            }
        }
    }

    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    var boundCamera by remember { mutableStateOf<Camera?>(null) }

    DisposableEffect(lifecycleOwner, analyzer, previewView) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val mainExecutor = ContextCompat.getMainExecutor(context)
        var activeImageAnalysis: ImageAnalysis? = null
        var activePreview: Preview? = null

        val layoutListener = View.OnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
            if (v.width > 0 && v.height > 0) {
                analyzer.viewportAspectRatio = v.width.toFloat() / v.height.toFloat()
            }
            val rotation = v.display?.rotation ?: Surface.ROTATION_0
            activeImageAnalysis?.targetRotation = rotation
            activePreview?.targetRotation = rotation
        }
        previewView.addOnLayoutChangeListener(layoutListener)

        val listener = Runnable {
            try {
                val cameraProvider = cameraProviderFuture.get()
                val initialRotation = previewView.display?.rotation ?: Surface.ROTATION_0

                val resolutionSelector = ResolutionSelector.Builder()
                    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
                    .setResolutionStrategy(
                        ResolutionStrategy(
                            Size(1920, 1080),
                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                        )
                    )
                    .build()

                val previewUseCase = Preview.Builder()
                    .setResolutionSelector(resolutionSelector)
                    .setTargetRotation(initialRotation)
                    .build()
                    .also { it.surfaceProvider = previewView.surfaceProvider }

                val imageAnalysisUseCase = ImageAnalysis.Builder()
                    .setResolutionSelector(resolutionSelector)
                    .setTargetRotation(initialRotation)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                    .build()
                    .also { it.setAnalyzer(analysisExecutor, analyzer) }

                activePreview = previewUseCase
                activeImageAnalysis = imageAnalysisUseCase

                if (previewView.width > 0 && previewView.height > 0) {
                    analyzer.viewportAspectRatio =
                        previewView.width.toFloat() / previewView.height.toFloat()
                }

                cameraProvider.unbindAll()
                val camera = cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
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
                    e.localizedMessage ?: cameraStartErrorText
                )
            }
        }

        cameraProviderFuture.addListener(listener, mainExecutor)

        onDispose {
            previewView.removeOnLayoutChangeListener(layoutListener)
            boundCamera = null
            activeImageAnalysis?.clearAnalyzer()
            activeImageAnalysis = null
            activePreview = null
            if (cameraProviderFuture.isDone) {
                runCatching {
                    cameraProviderFuture.get().unbindAll()
                }
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

    BoxWithConstraints(modifier = modifier) {
        val isScreenLandscape = maxWidth > maxHeight

        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize(),
        )

        if (isPaused && frozenFrameBitmap != null && !frozenFrameBitmap.isRecycled) {
            val isBitmapLandscape = frozenFrameBitmap.width > frozenFrameBitmap.height
            val sameOrientation = isScreenLandscape == isBitmapLandscape
            Image(
                bitmap = frozenFrameBitmap.asImageBitmap(),
                contentDescription = stringResource(R.string.cd_frozen_frame),
                contentScale = if (sameOrientation) ContentScale.Crop else ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
            )
        }
    }
}
