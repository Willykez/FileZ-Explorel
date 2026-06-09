package com.synapse.engine

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.ceil

class FileGridPanel(
    private val onFileOpen: (FileModel) -> Unit,
    private val onSelectionChanged: (List<FileModel>) -> Unit
) {
    // ── Layout (set externally) ───────────────────────────────────────────────
    var left   = 0f
    var top    = 0f
    var width  = 0f
    var height = 0f

    // ── Public state ──────────────────────────────────────────────────────────
    var isListMode   = false; private set
    var searchActive = false
    var isLoading    = false; private set
    var onRedrawNeeded: (() -> Unit)? = null

    // ── Data ──────────────────────────────────────────────────────────────────
    private val executor      = Executors.newSingleThreadExecutor()
    private var files         = listOf<FileModel>()
    private var filteredFiles = listOf<FileModel>()
    private var currentDir: File? = null
    private var searchQuery   = ""
    private var sortMode      = SortMode.NAME
    private var ascending     = true

    // ── Drawing ───────────────────────────────────────────────────────────────
    private val paint   = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path    = Path()
    private val dateFmt = SimpleDateFormat("MMM d", Locale.getDefault())

    // ── Dimensions ────────────────────────────────────────────────────────────
    private val SEARCH_H = 66f
    private val SORT_H   = 52f
    private val CELL_PAD = 12f
    private val COLS     = 2
    private val CELL_H_G = 194f
    private val CELL_H_L = 76f
    private val CORNER_G = 16f
    private val CORNER_L = 10f
    private var cellW    = 0f

    private val contentTop get() = top + (if (searchActive) SEARCH_H else 0f) + SORT_H

    // ── Scroll / Touch ────────────────────────────────────────────────────────
    private var scrollY          = 0f
    private var touchDownX       = 0f
    private var touchDownY       = 0f
    private var touchDownScroll  = 0f
    private var isDragging       = false
    private var velY             = 0f
    private var lastTY           = 0f
    private var lastTTime        = 0L
    private var longPressHandled = false
    private var flinging         = false

    private val sortLabels = listOf("Name", "Date", "Size", "Type")
    private val sortModes  = listOf(SortMode.NAME, SortMode.DATE, SortMode.SIZE, SortMode.TYPE)

    // ── Public API ────────────────────────────────────────────────────────────
    fun loadDirectory(dir: File) {
        currentDir = dir; scrollY = 0f; velY = 0f; flinging = false
        searchQuery = ""; searchActive = false; isLoading = true
        onSelectionChanged(emptyList())
        executor.execute {
            val raw = try { dir.listFiles()?.map { FileModel(it) } ?: emptyList() }
                      catch (_: Exception) { emptyList() }
            files = sortList(raw); applyFilter(); isLoading = false
            onRedrawNeeded?.invoke()
        }
    }

    fun toggleViewMode()            { isListMode = !isListMode; scrollY = 0f; onRedrawNeeded?.invoke() }
    fun setSearchQuery(q: String)   { searchQuery = q; applyFilter(); onRedrawNeeded?.invoke() }
    fun selectAll()                 { filteredFiles.forEach { it.isSelected = true }; onSelectionChanged(filteredFiles.filter { it.isSelected }) }
    fun clearSelection()            { files.forEach { it.isSelected = false }; onSelectionChanged(emptyList()) }
    fun refresh()                   { currentDir?.let { loadDirectory(it) } }
    val hasActiveSearch get()       = searchQuery.isNotEmpty()

    // ── Draw ──────────────────────────────────────────────────────────────────
    fun draw(canvas: Canvas) {
        cellW = (width - CELL_PAD * (COLS + 1)) / COLS
        DrawUtils.drawRoundRect(canvas, paint, left, top, left + width, top + height, 0f, Theme.BG_BASE)
        if (searchActive) drawSearchBar(canvas)
        drawSortTabs(canvas)
        canvas.save()
        canvas.clipRect(left, contentTop, left + width, top + height)
        when {
            isLoading               -> drawLoading(canvas)
            filteredFiles.isEmpty() -> drawEmpty(canvas)
            isListMode              -> drawListItems(canvas)
            else                    -> drawGridItems(canvas)
        }
        canvas.restore()
    }

    private fun drawSearchBar(canvas: Canvas) {
        val t = top; val b = t + SEARCH_H; val cy = t + SEARCH_H / 2f
        DrawUtils.drawRoundRect(canvas, paint, left, t, left + width, b, 0f, Theme.BG_SURFACE)
        DrawUtils.drawRoundRect(canvas, paint, left + 14f, t + 10f, left + width - 14f, b - 10f, 12f, Theme.BG_SURFACE2)
        paint.color = Theme.ACCENT; paint.style = Paint.Style.STROKE; paint.strokeWidth = 1.5f
        canvas.drawRoundRect(left + 14f, t + 10f, left + width - 14f, b - 10f, 12f, 12f, paint)
        paint.strokeWidth = 2.5f
        canvas.drawCircle(left + 38f, cy, 10f, paint)
        canvas.drawLine(left + 45f, cy + 7f, left + 52f, cy + 14f, paint)
        val display = if (searchQuery.isEmpty()) "Tap to search…" else searchQuery
        DrawUtils.drawText(canvas, paint, display, left + 62f, cy + 9f,
            if (searchQuery.isEmpty()) Theme.TEXT_MUTED else Theme.TEXT_PRIMARY, 23f)
        if (searchQuery.isNotEmpty()) {
            val xc = left + width - 34f
            paint.color = Theme.TEXT_MUTED; paint.strokeWidth = 2.8f
            canvas.drawLine(xc - 7f, cy - 7f, xc + 7f, cy + 7f, paint)
            canvas.drawLine(xc + 7f, cy - 7f, xc - 7f, cy + 7f, paint)
        }
    }

    private fun drawSortTabs(canvas: Canvas) {
        val t = top + (if (searchActive) SEARCH_H else 0f)
        DrawUtils.drawRoundRect(canvas, paint, left, t, left + width, t + SORT_H, 0f, Theme.BG_SURFACE)
        val tabW = width / sortLabels.size
        sortLabels.forEachIndexed { i, lbl ->
            val tx = left + i * tabW; val active = sortModes[i] == sortMode
            if (active) {
                DrawUtils.drawRoundRect(canvas, paint, tx + 6f, t + 6f, tx + tabW - 6f, t + SORT_H - 6f, 8f, Theme.ACCENT_DIM)
                paint.color = Theme.ACCENT; paint.style = Paint.Style.FILL
                canvas.drawRoundRect(tx + 18f, t + SORT_H - 5f, tx + tabW - 18f, t + SORT_H - 1f, 2f, 2f, paint)
            }
            val arrow = if (active) (if (ascending) " \u2191" else " \u2193") else ""
            DrawUtils.drawText(canvas, paint, lbl + arrow, tx + tabW / 2f, t + SORT_H / 2f + 9f,
                if (active) Theme.ACCENT else Theme.TEXT_SECONDARY, 21f, Paint.Align.CENTER,
                if (active) android.graphics.Typeface.DEFAULT_BOLD else android.graphics.Typeface.DEFAULT)
        }
        paint.color = Theme.DIVIDER; paint.style = Paint.Style.STROKE; paint.strokeWidth = 1f
        canvas.drawLine(left, t + SORT_H, left + width, t + SORT_H, paint)
    }

    private fun drawGridItems(canvas: Canvas) {
        val startY = contentTop - scrollY
        filteredFiles.forEachIndexed { idx, f ->
            val col = idx % COLS; val row = idx / COLS
            val cx  = left + CELL_PAD + col * (cellW + CELL_PAD)
            val cy  = startY + CELL_PAD + row * (CELL_H_G + CELL_PAD)
            if (cy + CELL_H_G < contentTop || cy > top + height) return@forEachIndexed
            drawGridCell(canvas, f, cx, cy)
        }
    }

    private fun drawGridCell(canvas: Canvas, f: FileModel, x: Float, y: Float) {
        val color = Theme.fileTypeColor(f.type); val sel = f.isSelected
        val r = x + cellW; val b = y + CELL_H_G
        // Shadow
        paint.color = DrawUtils.adjustAlpha(0, 45); paint.style = Paint.Style.FILL
        canvas.drawRoundRect(x + 3f, y + 5f, r + 3f, b + 5f, CORNER_G, CORNER_G, paint)
        // Card
        DrawUtils.drawRoundRect(canvas, paint, x, y, r, b, CORNER_G, if (sel) Theme.BG_SELECTED else Theme.BG_SURFACE)
        // Top colour zone
        val topH = CELL_H_G * 0.55f
        paint.color = DrawUtils.adjustAlpha(color, 20); paint.style = Paint.Style.FILL
        canvas.save(); canvas.clipRect(x, y, r, y + topH)
        canvas.drawRoundRect(x, y, r, y + topH + CORNER_G, CORNER_G, CORNER_G, paint)
        canvas.restore()
        // Dot texture
        paint.color = DrawUtils.adjustAlpha(color, 10)
        var dx = x + 20f
        while (dx < r - 10f) { var dy2 = y + 14f; while (dy2 < y + topH - 10f) { canvas.drawCircle(dx, dy2, 2f, paint); dy2 += 18f }; dx += 18f }
        // Icon
        val icX = x + cellW / 2f; val icY = y + topH / 2f; val icS = if (f.isDirectory) 36f else 32f
        when (f.type) {
            FileType.FOLDER -> DrawUtils.drawFolderIcon(canvas, paint, path, icX, icY, icS, color)
            FileType.IMAGE  -> DrawUtils.drawImageIcon (canvas, paint, path, icX, icY, icS, color)
            FileType.VIDEO,
            FileType.AUDIO  -> DrawUtils.drawMediaIcon (canvas, paint, path, icX, icY, icS, color)
            FileType.CODE   -> DrawUtils.drawCodeIcon  (canvas, paint, path, icX, icY, icS, color)
            FileType.APK    -> DrawUtils.drawApkIcon   (canvas, paint, path, icX, icY, icS, color)
            else            -> DrawUtils.drawFileIcon  (canvas, paint, path, icX, icY, icS, color)
        }
        // Extension badge
        val ext = f.file.extension.uppercase().take(4)
        if (f.type != FileType.FOLDER && ext.isNotEmpty()) {
            paint.textSize = 15f; val tw = paint.measureText(ext) + 12f
            DrawUtils.drawRoundRect(canvas, paint, icX - tw/2f, y + topH - 22f, icX + tw/2f, y + topH - 4f, 5f, DrawUtils.adjustAlpha(color, 55))
            DrawUtils.drawText(canvas, paint, ext, icX, y + topH - 7f, color, 15f, Paint.Align.CENTER, android.graphics.Typeface.DEFAULT_BOLD)
        }
        // Divider
        paint.color = DrawUtils.adjustAlpha(color, 30); paint.style = Paint.Style.STROKE; paint.strokeWidth = 1f
        canvas.drawLine(x + 14f, y + topH, r - 14f, y + topH, paint)
        // Name
        val maxC = ((cellW - 16f) / 12f).toInt().coerceAtLeast(8)
        DrawUtils.drawText(canvas, paint, DrawUtils.truncate(f.name, maxC), x + cellW / 2f, y + topH + 28f,
            Theme.TEXT_PRIMARY, 21f, Paint.Align.CENTER,
            if (f.isDirectory) android.graphics.Typeface.DEFAULT_BOLD else android.graphics.Typeface.DEFAULT)
        // Meta
        val meta = if (f.isDirectory) { try { "${f.file.listFiles()?.size ?: 0} items" } catch (_: Exception) { "—" } }
                   else FileModel.formatSize(f.size)
        DrawUtils.drawText(canvas, paint, meta,     x + cellW / 2f, y + topH + 50f, Theme.TEXT_MUTED, 18f, Paint.Align.CENTER)
        DrawUtils.drawText(canvas, paint, dateFmt.format(java.util.Date(f.lastModified)),
            x + cellW / 2f, y + topH + 70f, Theme.TEXT_MUTED, 17f, Paint.Align.CENTER)
        // Selection badge
        if (sel) {
            paint.color = Theme.ACCENT; paint.style = Paint.Style.FILL; canvas.drawCircle(r - 22f, y + 22f, 17f, paint)
            paint.color = Theme.BG_BASE; paint.style = Paint.Style.STROKE; paint.strokeWidth = 3f; paint.strokeCap = Paint.Cap.ROUND
            canvas.drawLine(r - 30f, y + 22f, r - 24f, y + 29f, paint)
            canvas.drawLine(r - 24f, y + 29f, r - 14f, y + 15f, paint)
            paint.color = Theme.ACCENT; paint.strokeWidth = 2f; paint.style = Paint.Style.STROKE
            canvas.drawRoundRect(x, y, r, b, CORNER_G, CORNER_G, paint)
        }
    }

    private fun drawListItems(canvas: Canvas) {
        val startY = contentTop - scrollY
        filteredFiles.forEachIndexed { idx, f ->
            val cy = startY + idx * (CELL_H_L + 2f)
            if (cy + CELL_H_L < contentTop || cy > top + height) return@forEachIndexed
            drawListRow(canvas, f, cy)
        }
    }

    private fun drawListRow(canvas: Canvas, f: FileModel, y: Float) {
        val color = Theme.fileTypeColor(f.type); val sel = f.isSelected
        val rb = left + width - 4f; val bot = y + CELL_H_L - 1f
        DrawUtils.drawRoundRect(canvas, paint, left + 4f, y + 1f, rb, bot, CORNER_L, if (sel) Theme.BG_SELECTED else Theme.BG_SURFACE)
        // Left colour bar
        paint.color = color; paint.style = Paint.Style.FILL
        canvas.drawRoundRect(left + 4f, y + 1f, left + 10f, bot, CORNER_L, CORNER_L, paint)
        canvas.drawRect(left + 6f, y + 1f, left + 10f, bot, paint)
        // Icon
        val icX = left + 50f; val icY = y + CELL_H_L / 2f; val icS = 20f
        when (f.type) {
            FileType.FOLDER -> DrawUtils.drawFolderIcon(canvas, paint, path, icX, icY, icS, color)
            FileType.IMAGE  -> DrawUtils.drawImageIcon (canvas, paint, path, icX, icY, icS, color)
            FileType.VIDEO,
            FileType.AUDIO  -> DrawUtils.drawMediaIcon (canvas, paint, path, icX, icY, icS, color)
            FileType.CODE   -> DrawUtils.drawCodeIcon  (canvas, paint, path, icX, icY, icS, color)
            FileType.APK    -> DrawUtils.drawApkIcon   (canvas, paint, path, icX, icY, icS, color)
            else            -> DrawUtils.drawFileIcon  (canvas, paint, path, icX, icY, icS, color)
        }
        // Name + meta
        val textX = left + 78f; val maxC = ((width - 192f) / 13f).toInt().coerceAtLeast(10)
        DrawUtils.drawText(canvas, paint, DrawUtils.truncate(f.name, maxC), textX, y + CELL_H_L / 2f - 3f,
            if (sel) Theme.ACCENT else Theme.TEXT_PRIMARY, 24f,
            typeface = if (f.isDirectory) android.graphics.Typeface.DEFAULT_BOLD else android.graphics.Typeface.DEFAULT)
        val sub = if (f.isDirectory) { try { "${f.file.listFiles()?.size ?: 0} items" } catch (_: Exception) { "—" } }
                  else "${FileModel.formatSize(f.size)}  \u00b7  ${dateFmt.format(java.util.Date(f.lastModified))}"
        DrawUtils.drawText(canvas, paint, sub, textX, y + CELL_H_L / 2f + 20f, Theme.TEXT_MUTED, 19f)
        // Chevron / checkmark
        if (sel) {
            paint.color = Theme.ACCENT; paint.style = Paint.Style.FILL; canvas.drawCircle(rb - 24f, y + CELL_H_L / 2f, 15f, paint)
            paint.color = Theme.BG_BASE; paint.style = Paint.Style.STROKE; paint.strokeWidth = 2.5f; paint.strokeCap = Paint.Cap.ROUND
            canvas.drawLine(rb - 31f, y + CELL_H_L/2f, rb - 25f, y + CELL_H_L/2f + 7f, paint)
            canvas.drawLine(rb - 25f, y + CELL_H_L/2f + 7f, rb - 16f, y + CELL_H_L/2f - 7f, paint)
        } else {
            paint.color = Theme.TEXT_MUTED; paint.style = Paint.Style.STROKE; paint.strokeWidth = 2f; paint.strokeCap = Paint.Cap.ROUND
            canvas.drawLine(rb - 28f, y + CELL_H_L/2f - 6f, rb - 20f, y + CELL_H_L/2f, paint)
            canvas.drawLine(rb - 20f, y + CELL_H_L/2f, rb - 28f, y + CELL_H_L/2f + 6f, paint)
        }
        paint.color = Theme.DIVIDER; paint.style = Paint.Style.STROKE; paint.strokeWidth = 1f
        canvas.drawLine(left + 16f, bot, rb - 16f, bot, paint)
    }

    private fun drawLoading(canvas: Canvas) {
        DrawUtils.drawText(canvas, paint, "Loading\u2026", left + width / 2f, contentTop + 80f,
            Theme.TEXT_MUTED, 26f, Paint.Align.CENTER)
    }

    private fun drawEmpty(canvas: Canvas) {
        val cx = left + width / 2f; val cy = contentTop + 110f
        paint.color = Theme.TEXT_MUTED; paint.style = Paint.Style.STROKE; paint.strokeWidth = 2.5f
        canvas.drawRoundRect(cx - 34f, cy - 24f, cx + 34f, cy + 30f, 6f, 6f, paint)
        canvas.drawLine(cx - 34f, cy - 2f, cx + 34f, cy - 2f, paint)
        val msg = if (searchQuery.isNotEmpty()) "No results for \"$searchQuery\"" else "This folder is empty"
        DrawUtils.drawText(canvas, paint, msg,                   cx, cy + 66f, Theme.TEXT_MUTED, 24f, Paint.Align.CENTER)
        DrawUtils.drawText(canvas, paint, "Tap \u002b to create a folder", cx, cy + 96f, Theme.TEXT_MUTED, 20f, Paint.Align.CENTER)
    }

    // ── Touch ─────────────────────────────────────────────────────────────────
    fun onTouchDown(x: Float, y: Float) {
        flinging = false; touchDownX = x; touchDownY = y; touchDownScroll = scrollY
        isDragging = false; longPressHandled = false
        lastTY = y; lastTTime = System.currentTimeMillis(); velY = 0f
    }

    fun onTouchMove(x: Float, y: Float) {
        val now = System.currentTimeMillis(); val dt = (now - lastTTime).coerceAtLeast(1)
        velY = (lastTY - y) * 1000f / dt; lastTY = y; lastTTime = now
        if (!isDragging && abs(touchDownY - y) > 14f) isDragging = true
        if (isDragging) scrollY = (touchDownScroll + (touchDownY - y)).coerceIn(0f, maxScroll())
    }

    fun onTouchUp(x: Float, y: Float): Boolean {
        if (isDragging) { isDragging = false; if (abs(velY) > 300f) startFling(); return true }
        if (longPressHandled) { longPressHandled = false; return true }
        // Search bar clear
        if (searchActive && y in top..(top + SEARCH_H) && x > left + width - 50f && searchQuery.isNotEmpty()) {
            searchQuery = ""; applyFilter(); onRedrawNeeded?.invoke(); return true
        }
        // Sort tab
        val sortTop = top + (if (searchActive) SEARCH_H else 0f)
        if (y in sortTop..(sortTop + SORT_H)) {
            val tabW = width / sortLabels.size
            val ti = ((x - left) / tabW).toInt().coerceIn(0, sortLabels.size - 1)
            val ns = sortModes[ti]
            if (ns == sortMode) ascending = !ascending else { sortMode = ns; ascending = true }
            files = sortList(files.toMutableList()); applyFilter(); return true
        }
        // File tap
        val idx = if (isListMode) hitList(x, y) else hitGrid(x, y)
        if (idx >= 0) {
            val f = filteredFiles[idx]; val selCount = filteredFiles.count { it.isSelected }
            if (selCount > 0) { f.isSelected = !f.isSelected; onSelectionChanged(filteredFiles.filter { it.isSelected }) }
            else onFileOpen(f)
            return true
        }
        return false
    }

    fun onLongPress(x: Float, y: Float) {
        val idx = if (isListMode) hitList(x, y) else hitGrid(x, y)
        if (idx >= 0) { longPressHandled = true; filteredFiles[idx].isSelected = !filteredFiles[idx].isSelected; onSelectionChanged(filteredFiles.filter { it.isSelected }) }
    }

    private fun hitGrid(x: Float, y: Float): Int {
        val gy = y - contentTop + scrollY; if (gy < 0f) return -1
        val col = ((x - left - CELL_PAD) / (cellW + CELL_PAD)).toInt()
        val row = ((gy - CELL_PAD) / (CELL_H_G + CELL_PAD)).toInt()
        if (col < 0 || col >= COLS || row < 0) return -1
        val idx = row * COLS + col; if (idx >= filteredFiles.size) return -1
        val cx2 = left + CELL_PAD + col * (cellW + CELL_PAD)
        val cy2 = contentTop - scrollY + CELL_PAD + row * (CELL_H_G + CELL_PAD)
        return if (x < cx2 || x > cx2 + cellW || y < cy2 || y > cy2 + CELL_H_G) -1 else idx
    }

    private fun hitList(x: Float, y: Float): Int {
        val gy = y - contentTop + scrollY; if (gy < 0f) return -1
        val idx = (gy / (CELL_H_L + 2f)).toInt(); if (idx < 0 || idx >= filteredFiles.size) return -1
        val cy2 = contentTop - scrollY + idx * (CELL_H_L + 2f)
        return if (y < cy2 || y > cy2 + CELL_H_L) -1 else idx
    }

    private fun startFling() {
        flinging = true
        val handler = Handler(Looper.getMainLooper()); var vel = velY
        val r = object : Runnable {
            override fun run() {
                if (!flinging || abs(vel) < 30f) { flinging = false; return }
                scrollY = (scrollY + vel * 0.016f).coerceIn(0f, maxScroll())
                vel *= 0.91f; onRedrawNeeded?.invoke(); handler.postDelayed(this, 16)
            }
        }
        handler.post(r)
    }

    private fun maxScroll(): Float {
        val h = if (isListMode) filteredFiles.size * (CELL_H_L + 2f)
                else { val rows = ceil(filteredFiles.size.toDouble() / COLS).toInt(); CELL_PAD + rows * (CELL_H_G + CELL_PAD) }
        return (h - ((top + height) - contentTop)).coerceAtLeast(0f)
    }

    private fun sortList(list: List<FileModel>): List<FileModel> {
        val dirs = list.filter { it.isDirectory }; val fils = list.filter { !it.isDirectory }
        fun s(l: List<FileModel>) = when (sortMode) {
            SortMode.NAME -> l.sortedBy { it.name.lowercase() }
            SortMode.SIZE -> l.sortedByDescending { it.size }
            SortMode.DATE -> l.sortedByDescending { it.lastModified }
            SortMode.TYPE -> l.sortedWith(compareBy({ it.type.ordinal }, { it.name.lowercase() }))
        }.let { if (ascending) it else it.reversed() }
        return s(dirs) + s(fils)
    }

    private fun applyFilter() {
        filteredFiles = if (searchQuery.isEmpty()) files
                        else files.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    fun contains(x: Float, y: Float) = x in left..(left + width) && y in top..(top + height)
}
