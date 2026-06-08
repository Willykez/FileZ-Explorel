
package com.synapse.engine

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.os.Environment
import java.io.File

class FolderTreePanel(
private val onFolderSelected: (File) -> Unit
) {
var left   = 0f
var top    = 0f
var width  = 0f
var height = 0f

private val paint = Paint(Paint.ANTI_ALIAS_FLAG)  
private val path  = Path()  

// Quick Access shortcuts  
private data class QuickItem(val label: String, val icon: Int, val file: File)  
private val quickItems = mutableListOf<QuickItem>()  

// Tree nodes  
private data class TreeNode(  
    val file: File, val depth: Int,  
    var expanded: Boolean = false,  
    var children: List<TreeNode>? = null  
)  
private val roots    = mutableListOf<TreeNode>()  
private val flatList = mutableListOf<TreeNode>()  

private val HEADER_H  = 88f  
private val QA_TITLE_H = 44f  
private val QA_ROW_H  = 70f  
private val TREE_TITLE_H = 44f  
private val TREE_ROW_H  = 76f  
private val INDENT    = 30f  
private val CORNER    = 10f  

private var scrollY       = 0f  
private var selectedFile: File? = null  
private var touchDownY    = 0f  
private var touchDownScroll = 0f  
private var isDragging    = false  

fun init() {  
    val root = Environment.getExternalStorageDirectory()  
    // Quick access  
    quickItems.clear()  
    quickItems += QuickItem("Internal Storage", 0, root)  
    listOf("Download" to 1, "DCIM" to 2, "Pictures" to 3,  
           "Music" to 4, "Movies" to 5, "Documents" to 6).forEach { (name, icon) ->  
        val f = File(root, name); if (f.exists()) quickItems += QuickItem(name, icon, f)  
    }  
    // Tree  
    roots.clear()  
    val node = TreeNode(root, 0, expanded = true,  
        children = loadChildren(root, 1))  
    roots.add(node); rebuildFlat()  
    selectedFile = root; onFolderSelected(root)  
}  

private fun loadChildren(dir: File, depth: Int) = try {  
    dir.listFiles()  
        ?.filter { it.isDirectory && !it.name.startsWith(".") }  
        ?.sortedBy { it.name.lowercase() }  
        ?.map { TreeNode(it, depth) } ?: emptyList()  
} catch (_: Exception) { emptyList() }  

private fun rebuildFlat() {  
    flatList.clear()  
    fun add(n: TreeNode) { flatList.add(n); if (n.expanded) n.children?.forEach { add(it) } }  
    roots.forEach { add(it) }  
}  

fun draw(canvas: Canvas) {  
    // Background  
    DrawUtils.drawRoundRect(canvas, paint, left, top, left + width, top + height, 0f, Theme.BG_PANEL)  

    // Right edge border  
    paint.color = Theme.DIVIDER; paint.style = Paint.Style.STROKE; paint.strokeWidth = 1f  
    canvas.drawLine(left + width, top, left + width, top + height, paint)  

    // ── Header ───────────────────────────────────────────────────────────  
    DrawUtils.drawRoundRect(canvas, paint, left, top, left + width, top + HEADER_H, 0f, Theme.BG_SURFACE)  

    // App logo area: coloured circle + F letter  
    paint.color = Theme.ACCENT; paint.style = Paint.Style.FILL  
    canvas.drawCircle(left + 44f, top + HEADER_H / 2f, 26f, paint)  
    DrawUtils.drawText(canvas, paint, "F", left + 44f, top + HEADER_H / 2f + 11f,  
        Theme.BG_BASE, 34f, Paint.Align.CENTER, Typeface.DEFAULT_BOLD)  
    DrawUtils.drawText(canvas, paint, "FileZ Explorer", left + 82f, top + HEADER_H / 2f - 2f,  
        Theme.TEXT_PRIMARY, 26f, typeface = Typeface.DEFAULT_BOLD)  
    DrawUtils.drawText(canvas, paint, "Browse your storage", left + 82f, top + HEADER_H / 2f + 22f,  
        Theme.TEXT_MUTED, 19f)  

    // Divider  
    paint.color = Theme.DIVIDER; canvas.drawLine(left, top + HEADER_H, left + width, top + HEADER_H, paint)  

    // ── Scrollable content ────────────────────────────────────────────────  
    canvas.save(); canvas.clipRect(left, top + HEADER_H, left + width, top + height)  

    var rowY = top + HEADER_H - scrollY  

    // Quick Access section title  
    drawSectionTitle(canvas, "QUICK ACCESS", left + 16f, rowY + 30f)  
    rowY += QA_TITLE_H  

    quickItems.forEachIndexed { i, qa ->  
        val isActive = qa.file == selectedFile  
        if (isActive) DrawUtils.drawRoundRect(canvas, paint,  
            left + 8f, rowY + 4f, left + width - 8f, rowY + QA_ROW_H - 4f, CORNER, Theme.BG_SELECTED)  
        drawQuickIcon(canvas, qa.icon, left + 38f, rowY + QA_ROW_H / 2f,  
            if (isActive) Theme.ACCENT else Theme.fileTypeColor(FileType.FOLDER))  
        DrawUtils.drawText(canvas, paint, qa.label, left + 66f, rowY + QA_ROW_H / 2f + 9f,  
            if (isActive) Theme.ACCENT else Theme.TEXT_PRIMARY, 24f,  
            typeface = if (isActive) Typeface.DEFAULT_BOLD else Typeface.DEFAULT)  
        rowY += QA_ROW_H  
    }  

    // Divider before tree  
    rowY += 6f  
    paint.color = Theme.DIVIDER; canvas.drawLine(left + 12f, rowY, left + width - 12f, rowY, paint); rowY += 6f  

    // Folders section title  
    drawSectionTitle(canvas, "FOLDERS", left + 16f, rowY + 30f); rowY += TREE_TITLE_H  

    flatList.forEach { node ->  
        val isActive = node.file == selectedFile  
        if (isActive) DrawUtils.drawRoundRect(canvas, paint,  
            left + 8f, rowY + 3f, left + width - 8f, rowY + TREE_ROW_H - 3f, CORNER, Theme.BG_SELECTED)  

        val indentX = left + 16f + node.depth * INDENT  
        val cy = rowY + TREE_ROW_H / 2f  

        // Expand arrow  
        if (node.file.isDirectory) {  
            paint.color = if (node.expanded) Theme.ACCENT else Theme.TEXT_MUTED  
            paint.style = Paint.Style.STROKE; paint.strokeWidth = 2.2f; paint.strokeCap = Paint.Cap.ROUND  
            if (node.expanded) {  
                canvas.drawLine(indentX, cy - 5f, indentX + 6f, cy + 5f, paint)  
                canvas.drawLine(indentX + 6f, cy + 5f, indentX + 12f, cy - 5f, paint)  
            } else {  
                canvas.drawLine(indentX, cy - 7f, indentX + 7f, cy, paint)  
                canvas.drawLine(indentX + 7f, cy, indentX, cy + 7f, paint)  
            }  
        }  

        // Folder icon  
        DrawUtils.drawFolderIcon(canvas, paint, path, indentX + 22f, cy, 18f,  
            if (isActive) Theme.ACCENT else Theme.COLOR_FOLDER)  

        // Name  
        val nameX = indentX + 42f  
        val maxC = ((left + width - nameX - 14f) / 12f).toInt().coerceAtLeast(6)  
        DrawUtils.drawText(canvas, paint, DrawUtils.truncate(node.file.name.ifBlank { "Storage" }, maxC),  
            nameX, cy + 9f, if (isActive) Theme.TEXT_PRIMARY else Theme.TEXT_SECONDARY,  
            22f, typeface = if (node.depth == 0) Typeface.DEFAULT_BOLD else Typeface.DEFAULT)  

        rowY += TREE_ROW_H  
    }  

    canvas.restore()  
}  

private fun drawSectionTitle(canvas: Canvas, title: String, x: Float, y: Float) {  
    DrawUtils.drawText(canvas, paint, title, x, y, Theme.TEXT_MUTED, 19f,  
        typeface = Typeface.DEFAULT_BOLD)  
}  

private fun drawQuickIcon(canvas: Canvas, icon: Int, cx: Float, cy: Float, color: Int) {  
    paint.color = DrawUtils.adjustAlpha(color, 25); paint.style = Paint.Style.FILL  
    canvas.drawRoundRect(cx - 18f, cy - 18f, cx + 18f, cy + 18f, 8f, 8f, paint)  
    paint.color = color; paint.style = Paint.Style.STROKE; paint.strokeWidth = 2.5f; paint.strokeCap = Paint.Cap.ROUND  
    when (icon) {  
        0 -> DrawUtils.drawFolderIcon(canvas, paint, path, cx, cy, 18f, color)  // Storage  
        1 -> { // Download arrow  
            canvas.drawLine(cx, cy - 10f, cx, cy + 6f, paint)  
            canvas.drawLine(cx - 6f, cy, cx, cy + 6f, paint)  
            canvas.drawLine(cx + 6f, cy, cx, cy + 6f, paint)  
            canvas.drawLine(cx - 10f, cy + 10f, cx + 10f, cy + 10f, paint)  
        }  
        2 -> { // Camera (DCIM)  
            canvas.drawRoundRect(cx - 12f, cy - 7f, cx + 12f, cy + 9f, 4f, 4f, paint)  
            canvas.drawCircle(cx, cy + 1f, 5f, paint)  
            canvas.drawRoundRect(cx - 5f, cy - 12f, cx + 5f, cy - 7f, 2f, 2f, paint)  
        }  
        3 -> { // Pictures: mountain  
            path.reset(); path.moveTo(cx - 10f, cy + 8f)  
            path.lineTo(cx, cy - 6f); path.lineTo(cx + 10f, cy + 8f); path.close()  
            canvas.drawPath(path, paint)  
        }  
        4 -> { // Music: note  
            canvas.drawLine(cx - 2f, cy - 10f, cx + 8f, cy - 14f, paint)  
            canvas.drawLine(cx + 8f, cy - 14f, cx + 8f, cy, paint)  
            canvas.drawCircle(cx - 2f, cy + 2f, 5f, paint)  
            canvas.drawCircle(cx + 8f, cy + 2f, 4f, paint)  
        }  
        5 -> { // Videos: play in rect  
            canvas.drawRoundRect(cx - 12f, cy - 9f, cx + 12f, cy + 9f, 3f, 3f, paint)  
            path.reset(); path.moveTo(cx - 3f, cy - 5f); path.lineTo(cx + 6f, cy); path.lineTo(cx - 3f, cy + 5f); path.close()  
            paint.style = Paint.Style.FILL; canvas.drawPath(path, paint)  
        }  
        6 -> { // Documents: lines  
            paint.style = Paint.Style.STROKE  
            canvas.drawLine(cx - 9f, cy - 6f, cx + 9f, cy - 6f, paint)  
            canvas.drawLine(cx - 9f, cy, cx + 9f, cy, paint)  
            canvas.drawLine(cx - 9f, cy + 6f, cx + 5f, cy + 6f, paint)  
        }  
    }  
}  

fun onTouchDown(x: Float, y: Float) { touchDownY = y; touchDownScroll = scrollY; isDragging = false }  
fun onTouchMove(x: Float, y: Float) {  
    if (!isDragging && kotlin.math.abs(touchDownY - y) > 10f) isDragging = true  
    if (isDragging) scrollY = (touchDownScroll + (touchDownY - y)).coerceIn(0f, maxScroll())  
}  

fun onTouchUp(x: Float, y: Float): Boolean {  
    if (isDragging) { isDragging = false; return true }  
    // Quick access tap  
    var rowY = top + HEADER_H - scrollY + QA_TITLE_H  
    for (qa in quickItems) {  
        if (y in rowY..(rowY + QA_ROW_H)) {  
            selectedFile = qa.file; onFolderSelected(qa.file); return true  
        }  
        rowY += QA_ROW_H  
    }  
    rowY += 12f + TREE_TITLE_H  
    for (node in flatList) {  
        if (y in rowY..(rowY + TREE_ROW_H)) {  
            selectedFile = node.file  
            if (node.expanded) node.expanded = false  
            else {  
                if (node.children == null) node.children = loadChildren(node.file, node.depth + 1)  
                node.expanded = true  
            }  
            rebuildFlat(); onFolderSelected(node.file); return true  
        }  
        rowY += TREE_ROW_H  
    }  
    return false  
}  

// ── Add inside FolderTreePanel ──

/**
 * Selects a folder programmatically (for ExplorerView navigation)
 */
fun selectFolder(folder: File) {
    selectedFile = folder

    // Expand tree nodes leading to this folder
    fun expandPath(node: TreeNode) {
        if (folder.startsWith(node.file)) {
            node.expanded = true
            node.children?.forEach { expandPath(it) }
        }
    }
    roots.forEach { expandPath(it) }

    rebuildFlat()
}

private fun maxScroll(): Float {  
    val content = HEADER_H + QA_TITLE_H + quickItems.size * QA_ROW_H + 12f +  
                  TREE_TITLE_H + flatList.size * TREE_ROW_H + 32f  
    return (content - height).coerceAtLeast(0f)  
}  

fun contains(x: Float, y: Float) = x in left..(left + width) && y in top..(top + height)

}