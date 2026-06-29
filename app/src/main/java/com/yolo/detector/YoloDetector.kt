package com.yolo.detector

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

data class Detection(
    val label: String,
    val confidence: Float,
    val rect: RectF
)

class YoloDetector(private val context: Context) {

    private var interpreter: Interpreter? = null
    private var gpuDelegate: GpuDelegate? = null

    companion object {
        private const val TAG = "YoloDetector"
        private const val MODEL_FILE = "yolo26n_float32.tflite"
        private const val INPUT_SIZE = 640
        private const val MAX_DETECTIONS = 300
        private const val CONFIDENCE_THRESHOLD = 0.35f

        val LABELS = listOf(
            "person", "bicycle", "car", "motorcycle", "airplane", "bus", "train",
            "truck", "boat", "traffic light", "fire hydrant", "stop sign", "parking meter",
            "bench", "bird", "cat", "dog", "horse", "sheep", "cow", "elephant", "bear",
            "zebra", "giraffe", "backpack", "umbrella", "handbag", "tie", "suitcase",
            "frisbee", "skis", "snowboard", "sports ball", "kite", "baseball bat",
            "baseball glove", "skateboard", "surfboard", "tennis racket", "bottle",
            "wine glass", "cup", "fork", "knife", "spoon", "bowl", "banana", "apple",
            "sandwich", "orange", "broccoli", "carrot", "hot dog", "pizza", "donut",
            "cake", "chair", "couch", "potted plant", "bed", "dining table", "toilet",
            "tv", "laptop", "mouse", "remote", "keyboard", "cell phone", "microwave",
            "oven", "toaster", "sink", "refrigerator", "book", "clock", "vase",
            "scissors", "teddy bear", "hair drier", "toothbrush"
        )
    }

    init {
        setupInterpreter()
    }

    private fun setupInterpreter() {
        try {
            val options = Interpreter.Options()
            val compatList = CompatibilityList()

            if (compatList.isDelegateSupportedOnThisDevice) {
                val delegateOptions = compatList.bestOptionsForThisDevice
                gpuDelegate = GpuDelegate(delegateOptions)
                options.addDelegate(gpuDelegate!!)
                Log.d(TAG, "GPU delegate enabled")
            } else {
                options.numThreads = 4
                Log.d(TAG, "Using CPU with 4 threads")
            }

            val model = loadModelFile(MODEL_FILE)
            interpreter = Interpreter(model, options)

            val inputShape = interpreter!!.getInputTensor(0).shape()
            val outputShape = interpreter!!.getOutputTensor(0).shape()
            Log.d(TAG, "Model loaded - Input: ${inputShape.contentToString()} Output: ${outputShape.contentToString()}")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to setup interpreter: ${e.message}")
            // Fallback to CPU
            try {
                val options = Interpreter.Options().apply { numThreads = 4 }
                interpreter = Interpreter(loadModelFile(MODEL_FILE), options)
                Log.d(TAG, "Fallback CPU interpreter loaded")
            } catch (ex: Exception) {
                Log.e(TAG, "Critical: Could not load model - ${ex.message}")
            }
        }
    }

    private fun loadModelFile(path: String): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(path)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        return fileChannel.map(
            FileChannel.MapMode.READ_ONLY,
            fileDescriptor.startOffset,
            fileDescriptor.declaredLength
        )
    }

    fun detect(bitmap: Bitmap): Pair<List<Detection>, Long> {
        val interp = interpreter ?: return Pair(emptyList(), 0L)
        val startTime = System.currentTimeMillis()

        // Resize to 640x640
        val resized = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true)

        // Fill input buffer: BHWC float32, normalized [0,1]
        val inputBuffer = ByteBuffer.allocateDirect(1 * INPUT_SIZE * INPUT_SIZE * 3 * 4)
            .apply { order(ByteOrder.nativeOrder()) }

        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        resized.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)

        for (pixel in pixels) {
            inputBuffer.putFloat(((pixel shr 16) and 0xFF) / 255.0f) // R
            inputBuffer.putFloat(((pixel shr 8)  and 0xFF) / 255.0f) // G
            inputBuffer.putFloat((pixel           and 0xFF) / 255.0f) // B
        }

        // Ultralytics end-to-end detect export:
        // [1, 300, 6] -> [x1, y1, x2, y2, confidence, class_id].
        // Some exports return normalized 0-1 coords, others return 640px coords.
        val outputArray = Array(1) { Array(MAX_DETECTIONS) { FloatArray(6) } }

        interp.run(inputBuffer, outputArray)

        val detections = mutableListOf<Detection>()

        for (i in 0 until MAX_DETECTIONS) {
            val conf = outputArray[0][i][4]
            if (conf < CONFIDENCE_THRESHOLD) continue

            val classId = outputArray[0][i][5].toInt()
            if (classId !in LABELS.indices) continue

            val x1 = normalizeCoord(outputArray[0][i][0])
            val y1 = normalizeCoord(outputArray[0][i][1])
            val x2 = normalizeCoord(outputArray[0][i][2])
            val y2 = normalizeCoord(outputArray[0][i][3])

            val left = minOf(x1, x2).coerceIn(0f, 1f)
            val top = minOf(y1, y2).coerceIn(0f, 1f)
            val right = maxOf(x1, x2).coerceIn(0f, 1f)
            val bottom = maxOf(y1, y2).coerceIn(0f, 1f)

            // Skip degenerate boxes
            if (right <= left || bottom <= top) continue
            if ((right - left) < 0.01f || (bottom - top) < 0.01f) continue

            detections.add(
                Detection(
                    label = LABELS[classId],
                    confidence = conf,
                    rect = RectF(left, top, right, bottom)
                )
            )
        }

        // Sort by confidence descending
        detections.sortByDescending { it.confidence }

        val inferenceTime = System.currentTimeMillis() - startTime
        Log.d(TAG, "Inference: ${inferenceTime}ms | Detections: ${detections.size}")

        return Pair(detections, inferenceTime)
    }

    private fun normalizeCoord(value: Float): Float {
        return if (value > 1f) value / INPUT_SIZE else value
    }

    fun close() {
        interpreter?.close()
        gpuDelegate?.close()
        interpreter = null
        gpuDelegate = null
    }
}
