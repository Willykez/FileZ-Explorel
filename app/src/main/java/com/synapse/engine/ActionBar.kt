package com.synapse.engine

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.abs

class ActionBar(
    private val onCopy:      (List<FileModel>) -> Unit,
    private val onMove:      (List<FileModel>) -> Unit,
    private val onDelete:    (List<FileModel>) -> Unit,
    private val onRename:    (FileModel) -> Unit,
    private val onArchive:   (List<FileModel>) -> Unit,
    private val onDeselect:  () -> Unit,
    private val onSelectAll: () -> Unit
) {
    var left   = 0f
    var top    = 0f
    var width  = 0f
    var height = 96f

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path  = Path()
    private val rect  = RectF()

    private var selected = listOf<FileModel>()
    var visible = false
        private set

    private var btnScrollX    = 0f
    private var btnDownX      = 0f
    private var btnScrollStart = 0f
    private var btnDragging   = false
    private var totalBtnW     = 0f

    private val BTN_H   = 58f
    private val BTN_PAD = 10f
    private val LABEL_W = 110f
    private val CLOSE_W = 64f

    private data class Btn(val label: String, val icon: Int, val color: Int, val w: Float)

    private fun buildButtons(): List<Btn> {
        val single = selected.size == 1
        return buildList {
            add(Btn("Copy",    0, Theme.ACCENT,        118f))
            add(Btn("Cut",     1, Theme.WARNING,        108f))
            add(Btn("Delete",  2, Theme.DANGER,         122f))
            if (single) add(Btn("Rename", 3, Theme.PURPLE, 128f))
            add(Btn("Archive", 4, Theme.COLOR_ARCHIVE,  138f))
            add(Btn("All",     5, Theme.TEXT_SECONDARY, 102f))
        }
    }

    fun update(sel: List<FileModel>) {
        selected = sel; visible = sel.isNotEmpty(); btnScrollX = 0f
        totalBtnW = buildButtons().sumOf { it.w.toDouble() }.toFloat() +
                    (buildButtons().size - 1) * BTN_PAD
    }

    fun draw(canvas: Canvas) {
        if (!visible) return
        val r = left + width; val b = top + height
        // Rounded-top background
        path.reset(); rect.set(left, top, r, b)
        path.addRoundRect(rect, floatArrayOf(22f,22f,22f,22f,0f,0f,0f,0f), Path.Direction.CW)
        paint.color = Theme.BG_SURFACE2; paint.style = Paint.Style.FILL; canvas.drawPath(path, paint)
        // Accent pill at top
        paint.color = Theme.ACCENT
        canvas.drawRoundRect(left + width/2f - 36f, top + 6f, left + width/2f + 36f, top + 10f, 4f, 4f, paint)
        // Count label
        val count = selected.size
        DrawUtils.drawText(canvas, paint, "$count item${if (count > 1) "s" else ""}",
            left + 14f, top + height / 2f + 10f, Theme.TEXT_SECONDARY, 22f)
        // Close button
        val cy = top + height / 2f; val closeX = r - CLOSE_W / 2f
        paint.color = Theme.BG_SURFACE3; paint.style = Paint.Style.FILL; canvas.drawCircle(closeX, cy, 24f, paint)
        paint.color = Theme.TEXT_SECONDARY; paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.8f; paint.strokeCap = Paint.Cap.ROUND
        canvas.drawLine(closeX - 8f, cy - 8f, closeX + 8f, cy + 8f, paint)
        canvas.drawLine(closeX + 8f, cy - 8f, closeX - 8f, cy + 8f, paint)
        // Scrollable buttons
        val aL = left + LABEL_W; val aR = r - CLOSE_W
        canvas.save(); canvas.clipRect(aL, top, aR, b)
        val buttons = buildButtons(); var bx = aL - btnScrollX; val by = top + (height - BTN_H) / 2f
        buttons.forEach { btn ->
            val br = bx + btn.w
            if (br > aL && bx < aR) {
                paint.color = DrawUtils.adjustAlpha(0, 35); paint.style = Paint.Style.FILL
                canvas.drawRoundRect(bx + 2f, by + 3f, br + 2f, by + BTN_H + 3f, 12f, 12f, paint)
                val bg = when (btn.label) { "Delete" -> Theme.DANGER_DIM; "Rename" -> Theme.PURPLE_DIM; else -> Theme.BG_SURFACE3 }
                DrawUtils.drawRoundRect(canvas, paint, bx, by, br, by + BTN_H, 12f, bg)
                paint.color = btn.color; paint.style = Paint.Style.STROKE; paint.strokeWidth = 1.5f
                canvas.drawRoundRect(bx, by, br, by + BTN_H, 12f, 12f, paint)
                drawBtnIcon(canvas, btn.icon, bx + 22f, by + BTN_H / 2f, btn.color)
                DrawUtils.drawText(canvas, paint, btn.label, bx + 38f, by + BTN_H / 2f + 9f,
                    btn.color, 21f, typeface = android.graphics.Typeface.DEFAULT_BOLD)
            }
            bx += btn.w + BTN_PAD
        }
        canvas.restore()
        // Scroll indicator dots
        val availW = aR - aL
        if (totalBtnW > availW) {
            val dotCount = buildButtons().size; val dotY = b - 8f
            var dotX = left + width / 2f - dotCount * 6f
            val maxScroll = (totalBtnW - availW).coerceAtLeast(1f)
            val activeDot = ((btnScrollX / maxScroll) * (dotCount - 1)).toInt().coerceIn(0, dotCount - 1)
            for (i in 0 until dotCount) {
                paint.color = if (i == activeDot) Theme.ACCENT else Theme.TEXT_MUTED
                paint.style = Paint.Style.FILL
                canvas.drawCircle(dotX, dotY, if (i == activeDot) 3.5f else 2.5f, paint)
                dotX += 12f
            }
        }
    }

    private fun drawBtnIcon(canvas: Canvas, icon: Int, cx: Float, cy: Float, color: Int) {
        paint.color = color; paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.5f; paint.strokeCap = Paint.Cap.ROUND
        when (icon) {
            0 -> { canvas.drawRoundRect(cx-7f,cy-8f,cx+5f,cy+6f,2f,2f,paint); canvas.drawRoundRect(cx-3f,cy-12f,cx+9f,cy+2f,2f,2f,paint) }
            1 -> { canvas.drawLine(cx-6f,cy-8f,cx+7f,cy+6f,paint); canvas.drawLine(cx+7f,cy-8f,cx-6f,cy+6f,paint) }
            2 -> { canvas.drawLine(cx-8f,cy-6f,cx+8f,cy-6f,paint); canvas.drawRect(cx-6f,cy-4f,cx+6f,cy+8f,paint); canvas.drawLine(cx-2f,cy-1f,cx-2f,cy+5f,paint); canvas.drawLine(cx+2f,cy-1f,cx+2f,cy+5f,paint) }
            3 -> { path.reset(); path.moveTo(cx-7f,cy+7f); path.lineTo(cx-3f,cy-6f); path.lineTo(cx+7f,cy+4f); path.close(); canvas.drawPath(path, paint) }
            4 -> { canvas.drawRoundRect(cx-8f,cy-2f,cx+8f,cy+8f,2f,2f,paint); canvas.drawLine(cx-8f,cy-2f,cx-8f,cy-7f,paint); canvas.drawLine(cx+8f,cy-2f,cx+8f,cy-7f,paint); canvas.drawLine(cx-10f,cy-7f,cx+10f,cy-7f,paint); canvas.drawLine(cx-3f,cy+2f,cx+3f,cy+2f,paint) }
            5 -> { canvas.drawRect(cx-9f,cy-9f,cx-1f,cy-1f,paint); canvas.drawRect(cx+1f,cy-9f,cx+9f,cy-1f,paint); canvas.drawRect(cx-9f,cy+1f,cx-1f,cy+9f,paint); canvas.drawRect(cx+1f,cy+1f,cx+9f,cy+9f,paint) }
        }
    }

    fun onTouchDown(x: Float, y: Float) { btnDownX = x; btnScrollStart = btnScrollX; btnDragging = false }
    fun onTouchMove(x: Float, y: Float) {
        val dx = btnDownX - x
        if (!btnDragging && abs(dx) > 12f) btnDragging = true
        if (btnDragging) {
            val availW = width - LABEL_W - CLOSE_W
            btnScrollX = (btnScrollStart + dx).coerceIn(0f, (totalBtnW - availW).coerceAtLeast(0f))
        }
    }

    fun onTouchUp(x: Float, y: Float): Boolean {
        if (btnDragging) { btnDragging = false; return true }
        val r = left + width; val cy = top + height / 2f; val closeX = r - CLOSE_W / 2f
        if (abs(x - closeX) < 30f && abs(y - cy) < 30f) { onDeselect(); return true }
        val aL = left + LABEL_W; val buttons = buildButtons()
        var bx = aL - btnScrollX; val by = top + (height - BTN_H) / 2f
        for (btn in buttons) {
            val br = bx + btn.w
            if (x >= bx && x <= br && y >= by && y <= by + BTN_H) {
                when (btn.label) {
                    "Copy"    -> onCopy(selected)
                    "Cut"     -> onMove(selected)
                    "Delete"  -> onDelete(selected)
                    "Rename"  -> if (selected.size == 1) onRename(selected[0])
                    "Archive" -> onArchive(selected)
                    "All"     -> onSelectAll()
                }
                return true
            }
            bx += btn.w + BTN_PAD
        }
        return false
    }

    fun contains(x: Float, y: Float) = visible && x in left..(left + width) && y in top..(top + height)
}
