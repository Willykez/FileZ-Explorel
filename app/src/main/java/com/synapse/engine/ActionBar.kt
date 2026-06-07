package com.synapse.engine

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface

/**
 * Bottom action bar — appears when files are selected.
 * Shows: Copy | Move | Delete | Deselect.
 */
class ActionBar(
    private val onCopy:     (List<FileModel>) -> Unit,
    private val onMove:     (List<FileModel>) -> Unit,
    private val onDelete:   (List<FileModel>) -> Unit,
    private val onDeselect: () -> Unit
) {
    var left   = 0f
    var top    = 0f
    var width  = 0f
    val height = 88f

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path  = Path()

    // Plain class (not data class) avoids function-reference equality issues
    private inner class ActionButton(
        val label: String,
        val color: Int,
        val bounds: RectF = RectF()
    )

    private val buttons = listOf(
        ActionButton("Copy",   Theme.ACCENT),
        ActionButton("Move",   Theme.WARNING),
        ActionButton("Delete", Theme.DANGER),
        ActionButton("None",   Theme.TEXT_MUTED)
    )

    private var selectedFiles = listOf<FileModel>()
    var visible = false

    fun update(selected: List<FileModel>) {
        selectedFiles = selected
        visible = selected.isNotEmpty()
    }

    fun draw(canvas: Canvas) {
        if (!visible) return

        // Background
        DrawUtils.drawRoundRect(canvas, paint, left, top, left + width, top + height, 0f, Theme.BG_SURFACE)

        // Top accent line
        paint.color = Theme.ACCENT
        paint.style = Paint.Style.FILL
        canvas.drawRect(left, top, left + width, top + 2f, paint)

        val count = selectedFiles.size
        DrawUtils.drawText(canvas, paint,
            "$count item${if (count != 1) "s" else ""} selected",
            left + 22f, top + 54f, Theme.TEXT_SECONDARY, 24f)

        // Buttons
        val btnW = 148f
        val btnH = 58f
        val gap  = 10f
        val totalW = buttons.size * btnW + (buttons.size - 1) * gap
        var bx = left + width - totalW - 18f

        buttons.forEach { btn ->
            val by = top + (height - btnH) / 2f
            btn.bounds.set(bx, by, bx + btnW, by + btnH)

            val bg = if (btn.label == "Delete") DrawUtils.adjustAlpha(Theme.DANGER, 35)
                     else Theme.BG_SURFACE2
            DrawUtils.drawRoundRect(canvas, paint, bx, by, bx + btnW, by + btnH, 10f, bg)

            paint.color = btn.color
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1.5f
            canvas.drawRoundRect(bx, by, bx + btnW, by + btnH, 10f, 10f, paint)

            val iconX = bx + 22f
            val iconY = by + btnH / 2f
            when (btn.label) {
                "Copy"   -> drawCopyIcon(canvas, iconX, iconY)
                "Move"   -> drawMoveIcon(canvas, iconX, iconY)
                "Delete" -> drawDeleteIcon(canvas, iconX, iconY)
                "None"   -> drawCloseIcon(canvas, iconX, iconY)
            }

            DrawUtils.drawText(canvas, paint, btn.label,
                bx + 36f, by + btnH / 2f + 9f,
                btn.color, 22f, typeface = Typeface.DEFAULT_BOLD)

            bx += btnW + gap
        }
    }

    fun onTouchUp(x: Float, y: Float): Boolean {
        if (!visible) return false
        buttons.forEach { btn ->
            if (btn.bounds.contains(x, y)) {
                when (btn.label) {
                    "Copy"   -> onCopy(selectedFiles)
                    "Move"   -> onMove(selectedFiles)
                    "Delete" -> onDelete(selectedFiles)
                    "None"   -> onDeselect()
                }
                return true
            }
        }
        return false
    }

    fun contains(x: Float, y: Float) =
        visible && x in left..(left + width) && y in top..(top + height)

    // ── Icon drawers ──────────────────────────────────────────────────────────

    private fun drawCopyIcon(canvas: Canvas, x: Float, y: Float) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = Theme.ACCENT
        canvas.drawRoundRect(x - 6f, y - 6f, x + 4f, y + 6f, 2f, 2f, paint)
        canvas.drawRoundRect(x - 2f, y - 9f, x + 8f, y + 3f, 2f, 2f, paint)
    }

    private fun drawMoveIcon(canvas: Canvas, x: Float, y: Float) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.5f
        paint.strokeCap = Paint.Cap.ROUND
        paint.color = Theme.WARNING
        canvas.drawLine(x - 7f, y, x + 4f, y, paint)
        path.reset()
        path.moveTo(x + 1f, y - 5f)
        path.lineTo(x + 7f, y)
        path.lineTo(x + 1f, y + 5f)
        canvas.drawPath(path, paint)
    }

    private fun drawDeleteIcon(canvas: Canvas, x: Float, y: Float) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.5f
        paint.strokeCap = Paint.Cap.ROUND
        paint.color = Theme.DANGER
        canvas.drawLine(x - 6f, y - 6f, x + 6f, y + 6f, paint)
        canvas.drawLine(x + 6f, y - 6f, x - 6f, y + 6f, paint)
    }

    private fun drawCloseIcon(canvas: Canvas, x: Float, y: Float) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = Theme.TEXT_MUTED
        canvas.drawRoundRect(x - 7f, y - 7f, x + 7f, y + 7f, 3f, 3f, paint)
        canvas.drawLine(x - 3f, y, x + 3f, y, paint)
    }
}
