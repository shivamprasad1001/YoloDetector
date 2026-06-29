package com.yolo.detector

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.yolo.detector.databinding.ActivityCameraBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

class CameraActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCameraBinding
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var detector: YoloDetector? = null
    private var camera: Camera? = null
    private var lensFacing = CameraSelector.LENS_FACING_BACK
    private var zoomPercent = 0
    private var frameCount = 0
    private var lastFpsTimestamp = System.currentTimeMillis()
    private lateinit var scaleGestureDetector: ScaleGestureDetector

    @Volatile
    private var isProcessing = false

    companion object {
        private const val TAG = "CameraActivity"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK

        binding = ActivityCameraBinding.inflate(layoutInflater)
        setContentView(binding.root)

        detector = YoloDetector(this)
        binding.tvModelName.text = "YOLO26n"
        binding.btnBack.setOnClickListener { finish() }
        binding.btnSwitchCamera.setOnClickListener { switchCamera() }
        binding.btnZoomOut.setOnClickListener { adjustZoomBy(-10) }
        binding.btnZoomIn.setOnClickListener { adjustZoomBy(10) }
        binding.zoomSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) setZoomPercent(progress)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })

        scaleGestureDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val currentZoom = camera?.cameraInfo?.zoomState?.value?.linearZoom ?: return false
                val zoomStep = (detector.scaleFactor - 1f) * 0.45f
                val nextZoom = (currentZoom + zoomStep).coerceIn(0f, 1f)
                setZoomPercent((nextZoom * 100f).toInt())
                return true
            }
        })
        binding.previewView.setOnTouchListener { _, event ->
            scaleGestureDetector.onTouchEvent(event)
            event.action == MotionEvent.ACTION_MOVE || event.pointerCount > 1
        }

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.previewView.surfaceProvider)
            }

            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, ::analyzeFrame)
                }

            try {
                val selector = CameraSelector.Builder()
                    .requireLensFacing(lensFacing)
                    .build()

                cameraProvider.unbindAll()
                camera?.cameraInfo?.zoomState?.removeObservers(this)
                camera = cameraProvider.bindToLifecycle(
                    this,
                    selector,
                    preview,
                    imageAnalyzer
                )
                observeZoom()
                Log.d(TAG, "Camera bound successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Camera binding failed: ${e.message}")
                Toast.makeText(this, "Unable to start camera", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun switchCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val nextLens = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                CameraSelector.LENS_FACING_FRONT
            } else {
                CameraSelector.LENS_FACING_BACK
            }
            val selector = CameraSelector.Builder().requireLensFacing(nextLens).build()

            if (cameraProvider.hasCamera(selector)) {
                lensFacing = nextLens
                zoomPercent = 0
                binding.zoomSeekBar.progress = 0
                binding.overlayView.setResults(emptyList())
                isProcessing = false
                startCamera()
            } else {
                Toast.makeText(this, "Camera not available", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun observeZoom() {
        val info = camera?.cameraInfo ?: return
        val control = camera?.cameraControl ?: return
        info.zoomState.observe(this) { state ->
            val percent = (state.linearZoom * 100f).toInt().coerceIn(0, 100)
            if (percent != zoomPercent) {
                zoomPercent = percent
                binding.zoomSeekBar.progress = percent
            }
            binding.tvZoomValue.text = String.format("%.1fx", state.zoomRatio)
        }
        control.setLinearZoom(zoomPercent / 100f)
    }

    private fun adjustZoomBy(delta: Int) {
        setZoomPercent((zoomPercent + delta).coerceIn(0, 100))
    }

    private fun setZoomPercent(percent: Int) {
        zoomPercent = percent.coerceIn(0, 100)
        binding.zoomSeekBar.progress = zoomPercent
        camera?.cameraControl?.setLinearZoom(zoomPercent / 100f)
    }

    private fun analyzeFrame(imageProxy: ImageProxy) {
        frameCount++
        val now = System.currentTimeMillis()
        if (now - lastFpsTimestamp >= 1000L) {
            val fps = frameCount
            frameCount = 0
            lastFpsTimestamp = now
            runOnUiThread { binding.tvFps.text = "FPS $fps" }
        }

        if (isProcessing) {
            imageProxy.close()
            return
        }
        isProcessing = true

        val bitmap = imageProxyToBitmap(imageProxy)
        val rotation = imageProxy.imageInfo.rotationDegrees
        imageProxy.close()

        val rotated = rotateBitmap(bitmap, rotation)

        scope.launch(Dispatchers.IO) {
            try {
                val (detections, inferenceMs) = detector?.detect(rotated) ?: Pair(emptyList(), 0L)

                withContext(Dispatchers.Main) {
                    binding.overlayView.setResults(detections)
                    binding.tvInferenceTime.text = "${inferenceMs}ms"
                    binding.tvObjectCount.text = "${detections.size} OBJ"

                    if (detections.isNotEmpty()) {
                        binding.edgeGlow.alpha = 0.8f
                        binding.edgeGlow.animate().alpha(0f).setDuration(300).start()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Inference error: ${e.message}")
            } finally {
                isProcessing = false
            }
        }
    }

    private fun rotateBitmap(source: Bitmap, degrees: Int): Bitmap {
        if (degrees == 0) return source
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap {
        val plane = imageProxy.planes[0]
        val buffer = plane.buffer
        buffer.rewind()

        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * imageProxy.width
        val bitmapWidth = imageProxy.width + rowPadding / pixelStride

        val paddedBitmap = Bitmap.createBitmap(bitmapWidth, imageProxy.height, Bitmap.Config.ARGB_8888)
        paddedBitmap.copyPixelsFromBuffer(buffer)

        return if (bitmapWidth == imageProxy.width) {
            paddedBitmap
        } else {
            Bitmap.createBitmap(paddedBitmap, 0, 0, imageProxy.width, imageProxy.height)
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this, "Camera permission required", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        cameraExecutor.shutdown()
        detector?.close()
    }
}
