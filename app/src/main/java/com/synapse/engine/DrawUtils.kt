package com.synapse.engine

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface

object DrawUtils {

    private val tmpRect = RectF()

    fun drawRoundRect(
        canvas: Canvas, paint: Paint,
        l: Float, t: Float, r: Float, b: Float,
        radius: Float, color: Int
    ) {
        paint.color = color
        paint.style = Paint.Style.FILL
        canvas.drawRoundRect(l, t, r, b, radius, radius, paint)
    }

    fun drawText(
        canvas: Canvas, paint: Paint,
        text: String, x: Float, y: Float,
        color: Int, size: Float,
        align: Paint.Align = Paint.Align.LEFT,
        typeface: Typeface = Typeface.DEFAULT
    ) {
        paint.color     = color
        paint.textSize  = size
        paint.textAlign = align
        paint.typeface  = typeface
        paint.style     = Paint.Style.FILL
        canvas.drawText(text, x, y, paint)
    }

    fun drawShadow(canvas: Canvas, paint: Paint, cx: Float, cy: Float, r: Float, color: Int) {
        paint.color = adjustAlpha(color, 50)
        paint.style = Paint.Style.FILL
        canvas.drawCircle(cx, cy + 6f, r, paint)
    }

    fun drawFolderIcon(
        canvas: Canvas, paint: Paint, path: Path,
        cx: Float, cy: Float, size: Float, color: Int
    ) {
        val s = size * 0.52f
        // Back shadow layer
        paint.color = adjustAlpha(color, 30); paint.style = Paint.Style.FILL
        path.reset()
        path.moveTo(cx - s, cy - s * 0.5f)
        path.lineTo(cx - s * 0.15f, cy - s * 0.5f)
        path.lineTo(cx + s * 0.05f, cy - s * 0.12f)
        path.lineTo(cx + s, cy - s * 0.12f)
        path.lineTo(cx + s, cy + s * 0.62f)
        path.lineTo(cx - s, cy + s * 0.62f)
        path.close()
        canvas.drawPath(path, paint)
        // Main body
        paint.color = color
        path.reset()
        path.moveTo(cx - s, cy - s * 0.3f)
        path.lineTo(cx + s, cy - s * 0.3f)
        path.lineTo(cx + s, cy + s * 0.62f)
        path.lineTo(cx - s, cy + s * 0.62f)
        path.close()
        canvas.drawPath(path, paint)
        // Shine stripe
        paint.color = adjustAlpha(color, 70)
        canvas.drawRoundRect(cx - s + 5f, cy - s * 0.28f + 4f,
            cx + s - 5f, cy - s * 0.28f + 10f, 3f, 3f, paint)
    }

    fun drawFileIcon(
        canvas: Canvas, paint: Paint, path: Path,
        cx: Float, cy: Float, size: Float, color: Int
    ) {
        val s    = size * 0.44f
        val fold = s * 0.36f
        paint.color = adjustAlpha(color, 28); paint.style = Paint.Style.FILL
        path.reset()
        path.moveTo(cx - s, cy - s)
        path.lineTo(cx + s - fold, cy - s)
        path.lineTo(cx + s, cy - s + fold)
        path.lineTo(cx + s, cy + s)
        path.lineTo(cx - s, cy + s)
        path.close()
        canvas.drawPath(path, paint)
        // Fold triangle
        paint.color = adjustAlpha(color, 65)
        path.reset()
        path.moveTo(cx + s - fold, cy - s)
        path.lineTo(cx + s - fold, cy - s + fold)
        path.lineTo(cx + s, cy - s + fold)
        path.close()
        canvas.drawPath(path, paint)
        // Left accent bar
        paint.color = color
        canvas.drawRoundRect(cx - s, cy - s, cx - s + 6f, cy + s, 2f, 2f, paint)
        // Content lines
        paint.color = adjustAlpha(color, 80); paint.strokeWidth = 2.2f
        paint.style = Paint.Style.STROKE
        val ls = (s * 1.4f) / 4f
        for (i in 0..3) {
            val ly = cy - s * 0.28f + i * ls
            val lw = if (i == 3) s * 0.8f else s * 1.2f
            canvas.drawLine(cx - s + 12f, ly, cx - s + 12f + lw, ly, paint)
        }
    }

    fun drawMediaIcon(
        canvas: Canvas, paint: Paint, path: Path,
        cx: Float, cy: Float, size: Float, color: Int
    ) {
        drawFileIcon(canvas, paint, path, cx, cy, size, color)
        val ps = size * 0.24f
        paint.color = color; paint.style = Paint.Style.FILL
        path.reset()
        path.moveTo(cx - ps * 0.6f, cy - ps)
        path.lineTo(cx + ps, cy)
        path.lineTo(cx - ps * 0.6f, cy + ps)
        path.close()
        canvas.drawPath(path, paint)
    }

    fun drawImageIcon(
        canvas: Canvas, paint: Paint, path: Path,
        cx: Float, cy: Float, size: Float, color: Int
    ) {
        drawFileIcon(canvas, paint, path, cx, cy, size, color)
        val s = size * 0.28f
        paint.color = color; paint.style = Paint.Style.FILL
        canvas.drawCircle(cx - s * 0.3f, cy - s * 0.3f, s * 0.32f, paint)
        path.reset()
        path.moveTo(cx - s, cy + s * 0.45f)
        path.lineTo(cx, cy - s * 0.2f)
        path.lineTo(cx + s, cy + s * 0.45f)
        path.close()
        canvas.drawPath(path, paint)
    }

    fun drawCodeIcon(
        canvas: Canvas, paint: Paint, path: Path,
        cx: Float, cy: Float, size: Float, color: Int
    ) {
        drawFileIcon(canvas, paint, path, cx, cy, size, color)
        paint.color = color; paint.style = Paint.Style.STROKE
        paint.strokeWidth = size * 0.06f; paint.strokeCap = Paint.Cap.ROUND
        val s = size * 0.22f
        canvas.drawLine(cx - s * 0.8f, cy - s * 0.5f, cx - s * 1.4f, cy, paint)
        canvas.drawLine(cx - s * 1.4f, cy, cx - s * 0.8f, cy + s * 0.5f, paint)
        canvas.drawLine(cx + s * 0.8f, cy - s * 0.5f, cx + s * 1.4f, cy, paint)
        canvas.drawLine(cx + s * 1.4f, cy, cx + s * 0.8f, cy + s * 0.5f, paint)
    }

    fun drawApkIcon(
        canvas: Canvas, paint: Paint, path: Path,
        cx: Float, cy: Float, size: Float, color: Int
    ) {
        drawFileIcon(canvas, paint, path, cx, cy, size, color)
        val s = size * 0.24f
        paint.color = color; paint.style = Paint.Style.FILL
        tmpRect.set(cx - s, cy - s * 0.6f, cx + s, cy + s * 0.2f)
        canvas.drawArc(tmpRect, 180f, 180f, true, paint)
        paint.color = Theme.BG_SURFACE
        canvas.drawCircle(cx - s * 0.4f, cy - s * 0.22f, s * 0.13f, paint)
        canvas.drawCircle(cx + s * 0.4f, cy - s * 0.22f, s * 0.13f, paint)
    }

    fun adjustAlpha(color: Int, alpha: Int): Int =
        (color and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)

    fun truncate(text: String, maxChars: Int): String =
        if (text.length <= maxChars) text else text.take(maxChars - 1) + "\u2026"
}
