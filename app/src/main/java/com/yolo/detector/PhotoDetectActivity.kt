package com.yolo.detector

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.yolo.detector.databinding.ActivityPhotoDetectBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream

class PhotoDetectActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPhotoDetectBinding
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var detector: YoloDetector
    private var currentBitmap: Bitmap? = null

    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let(::loadImageFromUri)
        }
    }

    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val bitmap = result.data?.extras?.get("data") as? Bitmap
            if (bitmap != null) {
                setCurrentImage(bitmap)
            } else {
                Toast.makeText(this, "Unable to read captured image", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val classColors = listOf(
        "#00FF41", "#FF3131", "#00CFFF", "#FFD700",
        "#FF6EC7", "#BF5FFF", "#FF8C00", "#00FF99",
        "#FF4500", "#1E90FF", "#ADFF2F", "#FF1493",
        "#00FFFF", "#FF6347", "#7FFF00", "#FF00FF"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.parseColor("#E8EAF0")
        window.navigationBarColor = Color.parseColor("#E8EAF0")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR

        binding = ActivityPhotoDetectBinding.inflate(layoutInflater)
        setContentView(binding.root)

        detector = YoloDetector(this)

        binding.btnBack.setOnClickListener { finish() }
        binding.btnGallery.setOnClickListener { openGallery() }
        binding.btnCamera.setOnClickListener { openCamera() }
        binding.btnDetect.setOnClickListener { detectCurrentImage() }
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        galleryLauncher.launch(intent)
    }

    private fun openCamera() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        if (intent.resolveActivity(packageManager) != null) {
            cameraLauncher.launch(intent)
        } else {
            Toast.makeText(this, "No camera app available", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadImageFromUri(uri: Uri) {
        try {
            val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = android.graphics.ImageDecoder.createSource(contentResolver, uri)
                android.graphics.ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                    decoder.isMutableRequired = true
                }
            } else {
                val stream: InputStream? = contentResolver.openInputStream(uri)
                BitmapFactory.decodeStream(stream).also { stream?.close() }
            }

            if (bitmap != null) {
                setCurrentImage(bitmap)
            } else {
                Toast.makeText(this, "Unable to load image", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Image load failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setCurrentImage(bitmap: Bitmap) {
        currentBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        binding.imagePreview.setImageBitmap(currentBitmap)
        binding.resultsContainer.removeAllViews()
        binding.emptyResults.text = "Ready to detect"
        binding.emptyResults.visibility = View.VISIBLE
    }

    private fun detectCurrentImage() {
        val bitmap = currentBitmap
        if (bitmap == null) {
            Toast.makeText(this, "Select or capture an image first", Toast.LENGTH_SHORT).show()
            return
        }

        binding.btnDetect.isEnabled = false
        binding.btnDetect.text = "Detecting..."

        scope.launch {
            val (detections, inferenceMs) = withContext(Dispatchers.IO) {
                detector.detect(bitmap)
            }

            val annotated = drawDetections(bitmap, detections)
            binding.imagePreview.setImageBitmap(annotated)
            showResultChips(detections, inferenceMs)
            binding.btnDetect.text = "Detect"
            binding.btnDetect.isEnabled = true
        }
    }

    private fun drawDetections(source: Bitmap, detections: List<Detection>): Bitmap {
        val annotated = source.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(annotated)
        val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = maxOf(4f, annotated.width * 0.006f)
        }
        val chipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.argb(210, 0, 0, 0)
        }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = maxOf(28f, annotated.width * 0.035f)
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }

        detections.forEachIndexed { index, detection ->
            val color = colorForDetection(detection, index)
            val left = detection.rect.left * annotated.width
            val top = detection.rect.top * annotated.height
            val right = detection.rect.right * annotated.width
            val bottom = detection.rect.bottom * annotated.height
            val label = "${detection.label} ${(detection.confidence * 100).toInt()}%"
            val pad = textPaint.textSize * 0.28f
            val textWidth = textPaint.measureText(label)
            val chipHeight = textPaint.textSize + pad * 2f
            val chipTop = (top - chipHeight).coerceAtLeast(0f)

            boxPaint.color = color
            canvas.drawRect(left, top, right, bottom, boxPaint)
            canvas.drawRect(left, chipTop, left + textWidth + pad * 2f, chipTop + chipHeight, chipPaint)
            canvas.drawRect(left, chipTop, left + 7f, chipTop + chipHeight, Paint().apply { this.color = color })
            canvas.drawText(label, left + pad + 7f, chipTop + chipHeight - pad, textPaint)
        }

        return annotated
    }

    private fun showResultChips(detections: List<Detection>, inferenceMs: Long) {
        binding.resultsContainer.removeAllViews()

        if (detections.isEmpty()) {
            binding.emptyResults.text = "No objects detected | ${inferenceMs}ms"
            binding.emptyResults.visibility = View.VISIBLE
            return
        }

        binding.emptyResults.visibility = View.GONE
        detections.forEachIndexed { index, detection ->
            val chip = TextView(this).apply {
                text = "${detection.label} ${(detection.confidence * 100).toInt()}%"
                textSize = 13f
                setTextColor(Color.parseColor("#2D2D2D"))
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                setPadding(dp(14), dp(8), dp(14), dp(8))
                background = getDrawable(com.yolo.detector.R.drawable.neumorphic_raised)
                compoundDrawablePadding = dp(6)
                setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0)
            }
            chip.setTextColor(colorForDetection(detection, index))
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, dp(4), dp(8), dp(8))
            }
            binding.resultsContainer.addView(chip, params)
        }

        val summary = TextView(this).apply {
            text = "${detections.size} objects | ${inferenceMs}ms"
            textSize = 12f
            setTextColor(Color.parseColor("#888888"))
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            setPadding(dp(14), dp(8), dp(14), dp(8))
        }
        binding.resultsContainer.addView(summary)
    }

    private fun colorForDetection(detection: Detection, fallbackIndex: Int): Int {
        val classId = YoloDetector.LABELS.indexOf(detection.label).takeIf { it >= 0 } ?: fallbackIndex
        return Color.parseColor(classColors[classId % classColors.size])
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        detector.close()
    }
}
