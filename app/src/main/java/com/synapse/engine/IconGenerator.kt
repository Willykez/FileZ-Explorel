package com.synapse.engine

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path

class IconGenerator {
    private val path = Path()
    private val hsv = FloatArray(3) { 0f }

    fun drawIcon(canvas: Canvas, paint: Paint, typeHash: Int, size: Float) {
        val positiveHash = if (typeHash == Int.MIN_VALUE) 0 else Math.abs(typeHash)

        hsv[0] = (positiveHash % 360).toFloat()
        hsv[1] = 0.7f
        hsv[2] = 0.9f
        val color = Color.HSVToColor(hsv)
        paint.color = color
        paint.style = Paint.Style.FILL

        val shapeType = positiveHash % 4
        when (shapeType) {
            0 -> canvas.drawCircle(0f, 0f, size, paint)
            1 -> canvas.drawRect(-size, -size, size, size, paint)
            2 -> {
                path.reset()
                path.moveTo(0f, -size)
                path.lineTo(-size, size)
                path.lineTo(size, size)
                path.close()
                canvas.drawPath(path, paint)
            }
            3 -> {
                path.reset()
                path.moveTo(0f, -size)
                path.lineTo(size, 0f)
                path.lineTo(0f, size)
                path.lineTo(-size, 0f)
                path.close()
                canvas.drawPath(path, paint)
            }
        }

        paint.color = Color.WHITE
        paint.strokeWidth = size / 5
        paint.style = Paint.Style.STROKE
        canvas.drawLine(-size/2, 0f, size/2, 0f, paint)
    }
}
