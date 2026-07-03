package com.example.smartvisionassist

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class OverlayView(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var detections = listOf<Detection>()

    private val boxPaint = Paint().apply {
        color = Color.GREEN
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }

    private val textPaint = Paint().apply {
        color = Color.GREEN
        textSize = 40f
    }

    fun setDetections(list: List<Detection>) {
        detections = list
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        for (detection in detections) {

            canvas.drawRect(
                detection.x1,
                detection.y1,
                detection.x2,
                detection.y2,
                boxPaint
            )

                canvas.drawText(
                    "${detection.className} %.2f".format(detection.confidence) ,
                detection.x1,
                detection.y1 - 10,
                textPaint
            )
        }
    }
}