package dev.vanta.smartlock

import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import android.widget.FrameLayout
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import java.io.Closeable
import java.util.ArrayDeque
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

internal enum class HandGesture {
    FIST,
    OPEN_HAND,
    WAVE
}

/**
 * Optional, on-device camera gesture controller.
 * Frames are analysed in memory only and are never written to disk.
 */
internal class HandGestureController(
    private val activity: ComponentActivity,
    private val previewContainer: FrameLayout,
    private val onGesture: (HandGesture) -> Unit,
    private val onStatus: (String) -> Unit
) : Closeable {
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    @Volatile private var stopped = false
    @Volatile private var landmarker: HandLandmarker? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var previewView: PreviewView? = null
    private var candidatePose = HandPose.UNKNOWN
    private var candidateFrames = 0
    private var emittedPose = HandPose.UNKNOWN
    private val waveSamples = ArrayDeque<Pair<Long, Float>>()
    private var lastWaveAt = 0L
    private var lastErrorAt = 0L

    fun start() {
        val baseOptions = BaseOptions.builder()
            .setModelAssetPath("hand_landmarker.task")
            .build()
        val options = HandLandmarker.HandLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setRunningMode(RunningMode.VIDEO)
            .setNumHands(1)
            .setMinHandDetectionConfidence(0.55f)
            .setMinHandPresenceConfidence(0.55f)
            .setMinTrackingConfidence(0.55f)
            .build()
        landmarker = HandLandmarker.createFromOptions(activity, options)

        val view = PreviewView(activity).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
        previewView = view
        previewContainer.addView(
            view,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        val providerFuture = ProcessCameraProvider.getInstance(activity)
        providerFuture.addListener({
            if (stopped) return@addListener
            try {
                val provider = providerFuture.get()
                val selector = if (provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) {
                    CameraSelector.DEFAULT_FRONT_CAMERA
                } else {
                    CameraSelector.DEFAULT_BACK_CAMERA
                }
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(view.surfaceProvider)
                }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(cameraExecutor) { proxy -> analyseFrame(proxy) }
                provider.unbindAll()
                provider.bindToLifecycle(activity, selector, preview, analysis)
                cameraProvider = provider
                report("Camera active • hand recognition runs on this phone • frames are not saved.")
            } catch (error: Exception) {
                report("Could not start hand camera: ${error.message ?: "camera unavailable"}")
                close()
            }
        }, ContextCompat.getMainExecutor(activity))
    }

    private fun analyseFrame(proxy: ImageProxy) {
        var source: Bitmap? = null
        var rotated: Bitmap? = null
        var mpImage: com.google.mediapipe.framework.image.MPImage? = null
        try {
            if (stopped) return
            source = proxy.toBitmap()
            rotated = rotate(source, proxy.imageInfo.rotationDegrees)
            mpImage = BitmapImageBuilder(rotated).build()
            val result = landmarker?.detectForVideo(mpImage, SystemClock.uptimeMillis())
            val hand = result?.landmarks()?.firstOrNull()
            if (hand == null || hand.size < 21) {
                resetPose()
                return
            }
            val points = hand.map { HandPoint(it.x(), it.y()) }
            val pose = HandPoseClassifier.classify(points)
            updatePose(pose, points[0].x)
        } catch (error: Exception) {
            val now = SystemClock.uptimeMillis()
            if (now - lastErrorAt > 3_000L) {
                lastErrorAt = now
                report("Hand recognition paused: ${error.message ?: "frame processing error"}")
            }
        } finally {
            try { mpImage?.close() } catch (_: Exception) {}
            if (rotated != null && rotated !== source) rotated.recycle()
            source?.recycle()
            proxy.close()
        }
    }

    private fun rotate(bitmap: Bitmap, degrees: Int): Bitmap {
        if (degrees == 0) return bitmap
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun updatePose(pose: HandPose, wristX: Float) {
        if (pose == HandPose.UNKNOWN) {
            resetPose()
            return
        }
        if (pose == candidatePose) {
            candidateFrames++
        } else {
            candidatePose = pose
            candidateFrames = 1
        }

        if (candidateFrames >= 4 && emittedPose != pose) {
            emittedPose = pose
            when (pose) {
                HandPose.FIST -> emit(HandGesture.FIST)
                HandPose.OPEN_HAND -> emit(HandGesture.OPEN_HAND)
                HandPose.UNKNOWN -> Unit
            }
        }

        if (pose == HandPose.OPEN_HAND && candidateFrames >= 4) {
            trackWave(wristX)
        } else {
            waveSamples.clear()
        }
    }

    private fun trackWave(wristX: Float) {
        val now = SystemClock.uptimeMillis()
        waveSamples.addLast(now to wristX)
        while (waveSamples.isNotEmpty() && now - waveSamples.first().first > 1_200L) {
            waveSamples.removeFirst()
        }
        if (waveSamples.size < 6 || now - lastWaveAt < 1_800L) return

        val values = waveSamples.map { it.second }
        val amplitude = (values.maxOrNull() ?: 0f) - (values.minOrNull() ?: 0f)
        var previousDirection = 0
        var directionChanges = 0
        val samples = waveSamples.toList()
        for (index in 1 until samples.size) {
            val delta = samples[index].second - samples[index - 1].second
            if (kotlin.math.abs(delta) < 0.035f) continue
            val direction = if (delta > 0f) 1 else -1
            if (previousDirection != 0 && direction != previousDirection) directionChanges++
            previousDirection = direction
        }
        if (amplitude >= 0.20f && directionChanges >= 2) {
            lastWaveAt = now
            waveSamples.clear()
            emit(HandGesture.WAVE)
        }
    }

    private fun resetPose() {
        candidatePose = HandPose.UNKNOWN
        candidateFrames = 0
        emittedPose = HandPose.UNKNOWN
        waveSamples.clear()
    }

    private fun emit(gesture: HandGesture) {
        activity.runOnUiThread {
            if (!stopped) onGesture(gesture)
        }
    }

    private fun report(message: String) {
        activity.runOnUiThread {
            if (!stopped) onStatus(message)
        }
    }

    override fun close() {
        if (stopped) return
        stopped = true
        try { cameraProvider?.unbindAll() } catch (_: Exception) {}
        cameraProvider = null
        try { landmarker?.close() } catch (_: Exception) {}
        landmarker = null
        cameraExecutor.shutdownNow()
        previewContainer.removeAllViews()
        previewView = null
    }
}
