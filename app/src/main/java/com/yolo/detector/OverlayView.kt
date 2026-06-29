package com.yolo.detector

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

class OverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var detections: List<Detection> = emptyList()

    // Unique color per class - cycles through this palette
    private val classColors = listOf(
        "#00FF41", // neon green
        "#FF3131", // neon red
        "#00CFFF", // cyan
        "#FFD700", // gold
        "#FF6EC7", // pink
        "#BF5FFF", // purple
        "#FF8C00", // orange
        "#00FF99", // mint
        "#FF4500", // red-orange
        "#1E90FF", // blue
        "#ADFF2F", // yellow-green
        "#FF1493", // deep pink
        "#00FFFF", // aqua
        "#FF6347", // tomato
        "#7FFF00", // chartreuse
        "#FF00FF", // magenta
    )

    private fun getColorForClass(classId: Int): Int {
        val hex = classColors[classId % classColors.size]
        return Color.parseColor(hex)
    }

    private val bgPaint = Paint().apply {
        style = Paint.Style.FILL
    }

    private val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        strokeJoin = Paint.Join.MITER
        strokeCap = Paint.Cap.SQUARE
    }

    private val cornerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 8f
        strokeJoin = Paint.Join.MITER
        strokeCap = Paint.Cap.SQUARE
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 38f
        typeface = Typeface.MONOSPACE
        style = Paint.Style.FILL
        color = Color.WHITE
        isFakeBoldText = true
    }

    private val confBarPaint = Paint().apply {
        style = Paint.Style.FILL
    }

    fun setResults(detections: List<Detection>) {
        this.detections = detections
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (detections.isEmpty()) return

        val vw = width.toFloat()
        val vh = height.toFloat()

        // Use full view area - padding is handled by layout XML
        val drawLeft   = 0f
        val drawTop    = 0f
        val drawRight  = vw
        val drawBottom = vh
        val drawW = drawRight - drawLeft
        val drawH = drawBottom - drawTop

        for ((index, det) in detections.withIndex()) {
            val classId = YoloDetector.LABELS.indexOf(det.label).takeIf { it >= 0 } ?: index
            val color = getColorForClass(classId)

            val r = det.rect

            // Map normalized coords to view coords with margin
            val left   = drawLeft + r.left   * drawW
            val top    = drawTop  + r.top    * drawH
            val right  = drawLeft + r.right  * drawW
            val bottom = drawTop  + r.bottom * drawH

            // Box
            boxPaint.color = color
            canvas.drawRect(left, top, right, bottom, boxPaint)

            // Corner accents
            cornerPaint.color = color
            val cornerLen = minOf((right - left), (bottom - top)) * 0.18f
            drawCorners(canvas, left, top, right, bottom, cornerLen)

            // Label chip
            val label = "${det.label} ${(det.confidence * 100).toInt()}%"
            val textW = textPaint.measureText(label)
            val textH = textPaint.textSize
            val pad = 10f

            val chipTop    = (top - textH - pad * 2).coerceAtLeast(drawTop)
            val chipBottom = chipTop + textH + pad * 2
            val chipRight  = (left + textW + pad * 2).coerceAtMost(drawRight)

            // Background
            bgPaint.color = Color.argb(200, 0, 0, 0)
            canvas.drawRect(left, chipTop, chipRight, chipBottom, bgPaint)

            // Colored left accent bar on chip
            bgPaint.color = color
            canvas.drawRect(left, chipTop, left + 5f, chipBottom, bgPaint)

            // Confidence bar
            confBarPaint.color = Color.argb(60,
                Color.red(color), Color.green(color), Color.blue(color))
            val barW = (textW + pad * 2) * det.confidence
            canvas.drawRect(left + 5f, chipTop, left + 5f + barW, chipBottom, confBarPaint)

            // Text
            textPaint.color = Color.WHITE
            canvas.drawText(label, left + pad + 5f, chipBottom - pad, textPaint)
        }
    }

    private fun drawCorners(
        canvas: Canvas,
        left: Float, top: Float,
        right: Float, bottom: Float,
        len: Float
    ) {
        // Top-left
        canvas.drawLine(left, top, left + len, top, cornerPaint)
        canvas.drawLine(left, top, left, top + len, cornerPaint)
        // Top-right
        canvas.drawLine(right, top, right - len, top, cornerPaint)
        canvas.drawLine(right, top, right, top + len, cornerPaint)
        // Bottom-left
        canvas.drawLine(left, bottom, left + len, bottom, cornerPaint)
        canvas.drawLine(left, bottom, left, bottom - len, cornerPaint)
        // Bottom-right
        canvas.drawLine(right, bottom, right - len, bottom, cornerPaint)
        canvas.drawLine(right, bottom, right, bottom - len, cornerPaint)
    }
}
