package com.synapse.engine

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Right panel: file grid with smooth scroll, multi-select, and sort.
 */
class FileGridPanel(
    private val onFileOpen: (FileModel) -> Unit,
    private val onSelectionChanged: (List<FileModel>) -> Unit
) {
    var left = 0f
    var top = 0f
    var width = 0f
    var height = 0f

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val executor = Executors.newSingleThreadExecutor()
    private val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

    private var files = listOf<FileModel>()
    private var filteredFiles = listOf<FileModel>()
    private var currentDir: File? = null
    private var searchQuery = ""
    private var sortMode = SortMode.NAME
    private var isLoading = false

    // Layout constants
    private val HEADER_H = 100f
    private val SEARCH_H = 90f
    private val SORT_H   = 72f
    private val CONTENT_TOP get() = top + HEADER_H + SEARCH_H + SORT_H

    private val COLS = 3
    private var cellW = 0f
    private var cellH = 220f
    private val CELL_PAD = 12f
    private val ICON_SIZE = 42f
    private val CORNER = 14f

    private var scrollY = 0f
    private var touchDownY = 0f
    private var touchDownScrollY = 0f
    private var isDragging = false

    // Search bar state
    private var searchFocused = false
    private val searchChars = StringBuilder()

    // Sort tabs
    private val sortLabels = listOf("Name", "Date", "Size", "Type")
    private val sortModes  = listOf(SortMode.NAME, SortMode.DATE, SortMode.SIZE, SortMode.TYPE)

    // Long-press selection
    private var touchDownX = 0f
    private var touchStartTime = 0L
    private var longPressHandled = false

    fun loadDirectory(dir: File) {
        currentDir = dir
        scrollY = 0f
        searchQuery = ""
        searchChars.clear()
        isLoading = true
        onSelectionChanged(emptyList())
        executor.execute {
            val list = try {
                dir.listFiles()
                    ?.map { FileModel(it) }
                    ?.let { sortList(it) }
                    ?: emptyList()
            } catch (e: Exception) { emptyList() }
            files = list
            applyFilter()
            isLoading = false
        }
    }

    private fun sortList(list: List<FileModel>): List<FileModel> {
        val folders = list.filter { it.isDirectory }
        val nonFolders = list.filter { !it.isDirectory }
        fun sort(l: List<FileModel>) = when (sortMode) {
            SortMode.NAME -> l.sortedBy { it.name.lowercase() }
            SortMode.SIZE -> l.sortedByDescending { it.size }
            SortMode.DATE -> l.sortedByDescending { it.lastModified }
            SortMode.TYPE -> l.sortedWith(compareBy({ it.type.ordinal }, { it.name.lowercase() }))
        }
        return sort(folders) + sort(nonFolders)
    }

    private fun applyFilter() {
        filteredFiles = if (searchQuery.isEmpty()) files
        else files.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    fun draw(canvas: Canvas) {
        cellW = (width - CELL_PAD * (COLS + 1)) / COLS

        // Panel background
        DrawUtils.drawRoundRect(canvas, paint, left, top, left + width, top + height, 0f, Theme.BG_BASE)

        drawHeader(canvas)
        drawSearchBar(canvas)
        drawSortTabs(canvas)

        // File grid
        canvas.save()
        canvas.clipRect(left, CONTENT_TOP, left + width, top + height)

        if (isLoading) {
            drawLoading(canvas)
        } else if (filteredFiles.isEmpty()) {
            drawEmpty(canvas)
        } else {
            drawGrid(canvas)
        }
        canvas.restore()
    }

    private fun drawHeader(canvas: Canvas) {
        DrawUtils.drawRoundRect(canvas, paint, left, top, left + width, top + HEADER_H, 0f, Theme.BG_SURFACE)
        val dir = currentDir
        val dirName = dir?.name ?: "Storage"
        val path2 = dir?.absolutePath?.replace("/storage/emulated/0", "~") ?: ""

        DrawUtils.drawText(canvas, paint, dirName, left + 24f, top + 42f,
            Theme.TEXT_PRIMARY, 34f, typeface = Typeface.DEFAULT_BOLD)
        DrawUtils.drawText(canvas, paint, DrawUtils.truncate(path2, 45), left + 24f, top + 72f,
            Theme.TEXT_MUTED, 22f)

        // File count badge
        val count = filteredFiles.size
        val countText = "$count item${if (count != 1) "s" else ""}"
        paint.textSize = 22f
        paint.typeface = Typeface.DEFAULT
        val tw = paint.measureText(countText) + 24f
        DrawUtils.drawRoundRect(canvas, paint, left + width - tw - 20f, top + 28f,
            left + width - 20f, top + 62f, 8f, Theme.BG_SURFACE2)
        DrawUtils.drawText(canvas, paint, countText, left + width - tw / 2f - 20f, top + 51f,
            Theme.TEXT_SECONDARY, 22f, Paint.Align.CENTER)

        // Bottom divider
        paint.color = Theme.DIVIDER
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        canvas.drawLine(left, top + HEADER_H, left + width, top + HEADER_H, paint)
    }

    private fun drawSearchBar(canvas: Canvas) {
        val barTop = top + HEADER_H + 14f
        val barBot = barTop + 60f
        val barColor = if (searchFocused) Theme.ACCENT_DIM else Theme.BG_SURFACE
        DrawUtils.drawRoundRect(canvas, paint, left + 16f, barTop, left + width - 16f, barBot, 12f, barColor)

        // Border
        paint.color = if (searchFocused) Theme.ACCENT else Theme.DIVIDER
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = if (searchFocused) 2f else 1f
        canvas.drawRoundRect(left + 16f, barTop, left + width - 16f, barBot, 12f, 12f, paint)

        // Search icon (magnifying glass)
        paint.color = Theme.TEXT_MUTED
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        canvas.drawCircle(left + 44f, barTop + 30f, 12f, paint)
        canvas.drawLine(left + 52f, barTop + 38f, left + 60f, barTop + 46f, paint)

        val displayText = if (searchQuery.isEmpty() && !searchFocused) "Search files…"
        else searchQuery + if (searchFocused) "|" else ""
        val textColor = if (searchQuery.isEmpty() && !searchFocused) Theme.TEXT_MUTED else Theme.TEXT_PRIMARY
        DrawUtils.drawText(canvas, paint, displayText, left + 68f, barTop + 38f, textColor, 26f)

        // Clear button
        if (searchQuery.isNotEmpty()) {
            paint.color = Theme.TEXT_MUTED
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 3f
            val cx = left + width - 40f
            val cy = barTop + 30f
            canvas.drawLine(cx - 8f, cy - 8f, cx + 8f, cy + 8f, paint)
            canvas.drawLine(cx + 8f, cy - 8f, cx - 8f, cy + 8f, paint)
        }
    }

    private fun drawSortTabs(canvas: Canvas) {
        val tabTop = top + HEADER_H + SEARCH_H
        DrawUtils.drawRoundRect(canvas, paint, left, tabTop, left + width, tabTop + SORT_H, 0f, Theme.BG_SURFACE)
        paint.color = Theme.DIVIDER
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        canvas.drawLine(left, tabTop + SORT_H, left + width, tabTop + SORT_H, paint)

        val tabW = width / sortLabels.size
        sortLabels.forEachIndexed { i, label ->
            val tx = left + i * tabW
            val isActive = sortModes[i] == sortMode
            if (isActive) {
                DrawUtils.drawRoundRect(canvas, paint, tx + 6f, tabTop + 6f,
                    tx + tabW - 6f, tabTop + SORT_H - 6f, 8f, Theme.ACCENT_DIM)
                // Active underline
                paint.color = Theme.ACCENT
                paint.style = Paint.Style.FILL
                canvas.drawRoundRect(tx + 16f, tabTop + SORT_H - 6f,
                    tx + tabW - 16f, tabTop + SORT_H - 2f, 2f, 2f, paint)
            }
            DrawUtils.drawText(canvas, paint, label, tx + tabW / 2f, tabTop + 44f,
                if (isActive) Theme.ACCENT else Theme.TEXT_SECONDARY,
                24f, Paint.Align.CENTER,
                if (isActive) Typeface.DEFAULT_BOLD else Typeface.DEFAULT)
        }
    }

    private fun drawGrid(canvas: Canvas) {
        val startY = CONTENT_TOP - scrollY
        filteredFiles.forEachIndexed { index, file ->
            val col = index % COLS
            val row = index / COLS
            val cx = left + CELL_PAD + col * (cellW + CELL_PAD)
            val cy = startY + row * (cellH + CELL_PAD) + CELL_PAD
            if (cy + cellH < CONTENT_TOP || cy > top + height) return@forEachIndexed
            drawCell(canvas, file, cx, cy)
        }
    }

    private fun drawCell(canvas: Canvas, file: FileModel, x: Float, y: Float) {
        val isSelected = file.isSelected
        val bgColor = when {
            isSelected -> Theme.BG_SELECTED
            else       -> Theme.BG_SURFACE
        }
        DrawUtils.drawRoundRect(canvas, paint, x, y, x + cellW, y + cellH, CORNER, bgColor)

        // Selection border
        if (isSelected) {
            paint.color = Theme.ACCENT
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f
            canvas.drawRoundRect(x, y, x + cellW, y + cellH, CORNER, CORNER, paint)

            // Checkmark badge
            paint.color = Theme.ACCENT
            paint.style = Paint.Style.FILL
            canvas.drawCircle(x + cellW - 18f, y + 18f, 14f, paint)
            paint.color = Theme.BG_BASE
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2.5f
            paint.strokeCap = Paint.Cap.ROUND
            canvas.drawLine(x + cellW - 24f, y + 18f, x + cellW - 19f, y + 24f, paint)
            canvas.drawLine(x + cellW - 19f, y + 24f, x + cellW - 11f, y + 12f, paint)
        }

        val iconCX = x + cellW / 2f
        val iconCY = y + 84f
        val color = Theme.fileTypeColor(file.type)

        when (file.type) {
            FileType.FOLDER   -> DrawUtils.drawFolderIcon(canvas, paint, path, iconCX, iconCY, ICON_SIZE, color)
            FileType.IMAGE    -> DrawUtils.drawImageIcon(canvas, paint, path, iconCX, iconCY, ICON_SIZE, color)
            FileType.VIDEO    -> DrawUtils.drawMediaIcon(canvas, paint, path, iconCX, iconCY, ICON_SIZE, color)
            FileType.AUDIO    -> DrawUtils.drawMediaIcon(canvas, paint, path, iconCX, iconCY, ICON_SIZE, color)
            FileType.CODE     -> DrawUtils.drawCodeIcon(canvas, paint, path, iconCX, iconCY, ICON_SIZE, color)
            FileType.APK      -> DrawUtils.drawApkIcon(canvas, paint, path, iconCX, iconCY, ICON_SIZE, color)
            else              -> DrawUtils.drawFileIcon(canvas, paint, path, iconCX, iconCY, ICON_SIZE, color)
        }

        // File name
        val maxChars = ((cellW - 16f) / 14f).toInt().coerceAtLeast(6)
        DrawUtils.drawText(canvas, paint, DrawUtils.truncate(file.name, maxChars),
            x + cellW / 2f, y + cellH - 70f,
            if (file.isDirectory) Theme.TEXT_PRIMARY else Theme.TEXT_SECONDARY,
            22f, Paint.Align.CENTER,
            if (file.isDirectory) Typeface.DEFAULT_BOLD else Typeface.DEFAULT)

        // Meta line
        val meta = when {
            file.isDirectory -> {
                try { "${file.file.listFiles()?.size ?: 0} items" }
                catch (e: Exception) { "—" }
            }
            else -> FileModel.formatSize(file.size)
        }
        DrawUtils.drawText(canvas, paint, meta, x + cellW / 2f, y + cellH - 44f,
            Theme.TEXT_MUTED, 20f, Paint.Align.CENTER)

        // Date
        DrawUtils.drawText(canvas, paint, dateFormat.format(Date(file.lastModified)),
            x + cellW / 2f, y + cellH - 20f,
            Theme.TEXT_MUTED, 18f, Paint.Align.CENTER)
    }

    private fun drawLoading(canvas: Canvas) {
        DrawUtils.drawText(canvas, paint, "Loading…",
            left + width / 2f, CONTENT_TOP + 120f,
            Theme.TEXT_MUTED, 30f, Paint.Align.CENTER)
    }

    private fun drawEmpty(canvas: Canvas) {
        DrawUtils.drawText(canvas, paint,
            if (searchQuery.isNotEmpty()) "No files match \"$searchQuery\""
            else "Empty folder",
            left + width / 2f, CONTENT_TOP + 120f,
            Theme.TEXT_MUTED, 28f, Paint.Align.CENTER)
    }

    // ── Touch handling ──────────────────────────────────────────────────────

    fun onTouchDown(x: Float, y: Float) {
        touchDownX = x
        touchDownY = y
        touchDownScrollY = scrollY
        touchStartTime = System.currentTimeMillis()
        isDragging = false
        longPressHandled = false
    }

    fun onTouchMove(x: Float, y: Float) {
        val dy = touchDownY - y
        if (!isDragging && Math.abs(dy) > 10) isDragging = true
        if (isDragging) scrollY = (touchDownScrollY + dy).coerceIn(0f, maxScroll())
    }

    fun onTouchUp(x: Float, y: Float): Boolean {
        if (isDragging) { isDragging = false; return true }
        if (longPressHandled) { longPressHandled = false; return true }

        // Search bar tap
        val searchBarTop = top + HEADER_H + 14f
        val searchBarBot = searchBarTop + 60f
        if (y in searchBarTop..searchBarBot && x in (left + 16f)..(left + width - 16f)) {
            searchFocused = !searchFocused
            return true
        }

        // Sort tab tap
        val tabTop = top + HEADER_H + SEARCH_H
        if (y in tabTop..(tabTop + SORT_H)) {
            val tabW = width / sortLabels.size
            val tabIndex = ((x - left) / tabW).toInt().coerceIn(0, sortLabels.size - 1)
            val newSort = sortModes[tabIndex]
            if (newSort != sortMode) {
                sortMode = newSort
                files = sortList(files.toMutableList())
                applyFilter()
            }
            return true
        }

        // Grid tap
        val idx = hitTestGrid(x, y)
        if (idx >= 0) {
            val file = filteredFiles[idx]
            val selectedCount = filteredFiles.count { it.isSelected }
            if (selectedCount > 0) {
                // In selection mode, tap toggles
                file.isSelected = !file.isSelected
                onSelectionChanged(filteredFiles.filter { it.isSelected })
            } else {
                if (file.isDirectory) onFileOpen(file)
                else onFileOpen(file)
            }
            return true
        }
        return false
    }

    fun onLongPress(x: Float, y: Float) {
        val idx = hitTestGrid(x, y)
        if (idx >= 0) {
            longPressHandled = true
            val file = filteredFiles[idx]
            file.isSelected = !file.isSelected
            onSelectionChanged(filteredFiles.filter { it.isSelected })
        }
    }

    fun typeChar(c: Char) {
        if (searchFocused) {
            if (c == '\b') { if (searchChars.isNotEmpty()) searchChars.deleteCharAt(searchChars.length - 1) }
            else searchChars.append(c)
            searchQuery = searchChars.toString()
            applyFilter()
        }
    }

    fun clearSearch() {
        searchQuery = ""; searchChars.clear(); applyFilter()
    }

    fun clearSelection() {
        files.forEach { it.isSelected = false }
        filteredFiles.forEach { it.isSelected = false }
        onSelectionChanged(emptyList())
    }

    fun getSelected() = filteredFiles.filter { it.isSelected }

    private fun hitTestGrid(x: Float, y: Float): Int {
        val gy = y - CONTENT_TOP + scrollY - CELL_PAD
        val gx = x - left - CELL_PAD
        val col = (gx / (cellW + CELL_PAD)).toInt()
        val row = (gy / (cellH + CELL_PAD)).toInt()
        val idx = row * COLS + col
        if (col < 0 || col >= COLS || row < 0 || idx >= filteredFiles.size) return -1
        // Verify within cell bounds
        val cx = left + CELL_PAD + col * (cellW + CELL_PAD)
        val cy = CONTENT_TOP - scrollY + row * (cellH + CELL_PAD) + CELL_PAD
        if (x < cx || x > cx + cellW || y < cy || y > cy + cellH) return -1
        return idx
    }

    private fun maxScroll(): Float {
        val rows = Math.ceil(filteredFiles.size.toDouble() / COLS).toInt()
        val contentH = rows * (cellH + CELL_PAD) + CELL_PAD
        val visibleH = (top + height) - CONTENT_TOP
        return (contentH - visibleH).coerceAtLeast(0f)
    }

    fun contains(x: Float, y: Float) = x in left..(left + width) && y in top..(top + height)
    fun refresh() { currentDir?.let { loadDirectory(it) } }
}
