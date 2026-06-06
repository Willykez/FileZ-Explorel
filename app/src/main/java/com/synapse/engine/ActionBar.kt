package com.synapse.engine

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface

/**
 * Bottom action bar — appears when files are selected.
 * Shows: Copy | Move | Delete | Rename | Deselect.
 */
class ActionBar(
    private val onCopy:      (List<FileModel>) -> Unit,
    private val onMove:      (List<FileModel>) -> Unit,
    private val onDelete:    (List<FileModel>) -> Unit,
    private val onDeselect:  () -> Unit
) {
    var left = 0f
    var top = 0f
    var width = 0f
    var height = 80f

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()

    private var selectedFiles = listOf<FileModel>()
    var visible = false

    private data class ActionButton(
        val label: String,
        val iconDraw: (Canvas, Paint, Float, Float) -> Unit,
        val color: Int,
        var bounds: android.graphics.RectF = android.graphics.RectF()
    )

    private val buttons = listOf(
        ActionButton("Copy", ::drawCopyIcon, Theme.ACCENT),
        ActionButton("Move", ::drawMoveIcon, Theme.WARNING),
        ActionButton("Delete", ::drawDeleteIcon, Theme.DANGER),
        ActionButton("None", ::drawCloseIcon, Theme.TEXT_MUTED)
    )

    fun update(selected: List<FileModel>) {
        selectedFiles = selected
        visible = selected.isNotEmpty()
    }

    fun draw(canvas: Canvas) {
        if (!visible) return

        // Background bar
        DrawUtils.drawRoundRect(canvas, paint, left, top, left + width, top + height, 0f, Theme.BG_SURFACE)

        // Top border accent
        paint.color = Theme.ACCENT
        paint.style = Paint.Style.FILL
        canvas.drawRect(left, top, left + width, top + 2f, paint)

        val count = selectedFiles.size
        val label = "$count file${if (count != 1) "s" else ""} selected"
        DrawUtils.drawText(canvas, paint, label, left + 20f, top + 50f,
            Theme.TEXT_SECONDARY, 24f)

        // Action buttons
        val btnW = 140f
        val btnH = 56f
        val btnGap = 12f
        val totalW = buttons.size * btnW + (buttons.size - 1) * btnGap
        var bx = left + width - totalW - 20f

        buttons.forEachIndexed { i, btn ->
            val by = top + (height - btnH) / 2f
            btn.bounds.set(bx, by, bx + btnW, by + btnH)

            val isDelete = btn.label == "Delete"
            val bgColor = if (isDelete) DrawUtils.adjustAlpha(Theme.DANGER, 30) else Theme.BG_SURFACE2
            DrawUtils.drawRoundRect(canvas, paint, bx, by, bx + btnW, by + btnH, 10f, bgColor)

            paint.color = btn.color
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1.5f
            canvas.drawRoundRect(bx, by, bx + btnW, by + btnH, 10f, 10f, paint)

            // Icon
            btn.iconDraw(canvas, paint, bx + 24f, by + btnH / 2f)
            // Label
            DrawUtils.drawText(canvas, paint, btn.label, bx + 38f, by + btnH / 2f + 9f,
                btn.color, 22f, typeface = Typeface.DEFAULT_BOLD)

            bx += btnW + btnGap
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

    // ── Icon drawers ─────────────────────────────────────────────────────────

    private fun drawCopyIcon(canvas: Canvas, paint: Paint, x: Float, y: Float) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = Theme.ACCENT
        canvas.drawRoundRect(x - 6f, y - 7f, x + 5f, y + 7f, 2f, 2f, paint)
        canvas.drawRoundRect(x - 3f, y - 9f, x + 8f, y + 5f, 2f, 2f, paint)
    }

    private fun drawMoveIcon(canvas: Canvas, paint: Paint, x: Float, y: Float) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.5f
        paint.strokeCap = Paint.Cap.ROUND
        paint.color = Theme.WARNING
        canvas.drawLine(x - 7f, y, x + 5f, y, paint)
        path.reset()
        path.moveTo(x + 1f, y - 5f); path.lineTo(x + 7f, y); path.lineTo(x + 1f, y + 5f)
        canvas.drawPath(path, paint)
    }

    private fun drawDeleteIcon(canvas: Canvas, paint: Paint, x: Float, y: Float) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.5f
        paint.strokeCap = Paint.Cap.ROUND
        paint.color = Theme.DANGER
        canvas.drawLine(x - 6f, y - 6f, x + 6f, y + 6f, paint)
        canvas.drawLine(x + 6f, y - 6f, x - 6f, y + 6f, paint)
    }

    private fun drawCloseIcon(canvas: Canvas, paint: Paint, x: Float, y: Float) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = Theme.TEXT_MUTED
        canvas.drawRoundRect(x - 7f, y - 7f, x + 7f, y + 7f, 3f, 3f, paint)
        canvas.drawLine(x - 3f, y, x + 3f, y, paint)
    }
}
