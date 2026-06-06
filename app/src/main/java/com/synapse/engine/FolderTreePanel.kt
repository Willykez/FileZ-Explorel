package com.synapse.engine

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.os.Environment
import java.io.File

/**
 * Left panel: collapsible folder tree.
 * Draws itself onto a Canvas region. Reports touches via callback.
 */
class FolderTreePanel(
    private val onFolderSelected: (File) -> Unit
) {
    // Layout
    var left = 0f
    var top = 0f
    var width = 0f
    var height = 0f

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()

    private data class TreeNode(
        val file: File,
        val depth: Int,
        var expanded: Boolean = false,
        var children: List<TreeNode>? = null
    )

    private val roots = mutableListOf<TreeNode>()
    private val flatList = mutableListOf<TreeNode>()

    private val ROW_HEIGHT = 96f
    private val INDENT = 36f
    private val ICON_SIZE = 22f
    private val TEXT_SIZE = 28f
    private val CORNER = 10f

    private var scrollY = 0f
    private var selectedFile: File? = null

    // Touch tracking
    private var touchDownY = 0f
    private var touchDownScrollY = 0f
    private var isDragging = false

    fun init() {
        roots.clear()
        val root = Environment.getExternalStorageDirectory()
        val storageNode = TreeNode(root, 0, expanded = true)
        storageNode.children = loadChildren(root, 1)
        roots.add(storageNode)

        // Add common quick-access paths
        val extras = listOf("Download", "DCIM", "Pictures", "Music", "Movies", "Documents")
        extras.forEach { name ->
            val f = File(root, name)
            if (f.exists() && f.isDirectory) {
                val n = TreeNode(f, 1, expanded = false)
                storageNode.children = storageNode.children?.toMutableList()?.also { it.add(n) }
            }
        }

        rebuildFlat()
        // Auto-select root
        selectedFile = root
        onFolderSelected(root)
    }

    private fun loadChildren(dir: File, depth: Int): List<TreeNode> {
        return try {
            dir.listFiles()
                ?.filter { it.isDirectory && !it.name.startsWith(".") }
                ?.sortedBy { it.name.lowercase() }
                ?.map { TreeNode(it, depth) }
                ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun rebuildFlat() {
        flatList.clear()
        fun addNode(node: TreeNode) {
            flatList.add(node)
            if (node.expanded) {
                node.children?.forEach { addNode(it) }
            }
        }
        roots.forEach { addNode(it) }
    }

    fun draw(canvas: Canvas) {
        // Panel background
        DrawUtils.drawRoundRect(canvas, paint, left, top, left + width, top + height, 0f, Theme.BG_PANEL)

        // Right border divider
        paint.color = Theme.DIVIDER
        paint.strokeWidth = 1f
        paint.style = Paint.Style.STROKE
        canvas.drawLine(left + width, top, left + width, top + height, paint)

        // Header
        DrawUtils.drawRoundRect(canvas, paint, left, top, left + width, top + 80f, 0f, Theme.BG_SURFACE)
        DrawUtils.drawText(canvas, paint, "FOLDERS", left + 20f, top + 50f,
            Theme.TEXT_MUTED, 22f, typeface = Typeface.DEFAULT_BOLD)

        val contentTop = top + 88f
        canvas.save()
        canvas.clipRect(left, contentTop, left + width, top + height)

        val visibleTop = contentTop - scrollY
        flatList.forEachIndexed { index, node ->
            val rowTop = visibleTop + index * ROW_HEIGHT
            val rowBottom = rowTop + ROW_HEIGHT
            if (rowBottom < contentTop || rowTop > top + height) return@forEachIndexed

            val isSelected = node.file == selectedFile
            if (isSelected) {
                DrawUtils.drawRoundRect(canvas, paint,
                    left + 6f, rowTop + 4f, left + width - 6f, rowBottom - 4f,
                    CORNER, Theme.BG_SELECTED)
            }

            val indentX = left + 16f + node.depth * INDENT
            val centerY = rowTop + ROW_HEIGHT / 2f

            // Expand/collapse arrow
            val hasChildren = node.file.isDirectory
            if (hasChildren) {
                paint.color = if (node.expanded) Theme.TEXT_ACCENT else Theme.TEXT_MUTED
                paint.style = Paint.Style.FILL
                path.reset()
                val ax = indentX
                val ay = centerY
                if (node.expanded) {
                    path.moveTo(ax, ay - 6f); path.lineTo(ax + 10f, ay + 6f); path.lineTo(ax - 10f, ay + 6f)
                } else {
                    path.moveTo(ax - 6f, ay - 10f); path.lineTo(ax + 6f, ay); path.lineTo(ax - 6f, ay + 10f)
                }
                path.close()
                canvas.drawPath(path, paint)
            }

            // Folder icon
            val iconX = indentX + 20f
            DrawUtils.drawFolderIcon(canvas, paint, path, iconX, centerY, ICON_SIZE,
                if (isSelected) Theme.ACCENT else Theme.COLOR_FOLDER)

            // Name
            val nameX = iconX + ICON_SIZE + 14f
            val nameColor = when {
                isSelected -> Theme.TEXT_PRIMARY
                node.depth == 0 -> Theme.TEXT_PRIMARY
                else -> Theme.TEXT_SECONDARY
            }
            val maxW = (left + width - nameX - 12f)
            val textTypef = if (node.depth == 0) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            paint.textSize = TEXT_SIZE
            paint.typeface = textTypef
            val name = DrawUtils.truncate(node.file.name, 18)
            DrawUtils.drawText(canvas, paint, name, nameX, centerY + 10f, nameColor, TEXT_SIZE,
                typeface = textTypef)
        }

        canvas.restore()
    }

    fun onTouchDown(x: Float, y: Float) {
        touchDownY = y
        touchDownScrollY = scrollY
        isDragging = false
    }

    fun onTouchMove(x: Float, y: Float) {
        val dy = touchDownY - y
        if (!isDragging && Math.abs(dy) > 8) isDragging = true
        if (isDragging) {
            scrollY = (touchDownScrollY + dy).coerceIn(0f, maxScroll())
        }
    }

    fun onTouchUp(x: Float, y: Float): Boolean {
        if (isDragging) { isDragging = false; return true }
        val contentTop = top + 88f
        val rowIndex = ((y + scrollY - contentTop) / ROW_HEIGHT).toInt()
        if (rowIndex < 0 || rowIndex >= flatList.size) return false

        val node = flatList[rowIndex]
        selectedFile = node.file

        // Toggle expand
        if (node.expanded) {
            node.expanded = false
        } else {
            if (node.children == null) {
                node.children = loadChildren(node.file, node.depth + 1)
            }
            node.expanded = true
        }
        rebuildFlat()
        onFolderSelected(node.file)
        return true
    }

    fun selectFolder(file: File) {
        selectedFile = file
    }

    private fun maxScroll(): Float {
        val contentHeight = flatList.size * ROW_HEIGHT
        val visibleHeight = height - 88f
        return (contentHeight - visibleHeight).coerceAtLeast(0f)
    }

    fun contains(x: Float, y: Float) = x in left..(left + width) && y in top..(top + height)
}
