package com.synapse.engine

import android.app.AlertDialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import android.widget.Toast
import java.io.File
import java.util.concurrent.Executors

class ExplorerView(context: Context) : View(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path  = Path()
    private val main  = Handler(Looper.getMainLooper())
    private val io    = Executors.newSingleThreadExecutor()

    // ── Navigation ────────────────────────────────────────────────────────────
    private val navStack = ArrayDeque<File>()

    // ── Clipboard ─────────────────────────────────────────────────────────────
    private var clipFiles = listOf<FileModel>()
    private var clipMode  = ""

    // ── UI State ──────────────────────────────────────────────────────────────
    private var drawerOpen   = false
    private var overflowOpen = false

    private data class OvItem(val label: String, var top: Float = 0f, var bot: Float = 0f)
    private val ovItems = listOf(
        OvItem("New Folder"), OvItem("Select All"),
        OvItem("Refresh"),    OvItem("Clear Clipboard")
    )

    // ── Dimensions ────────────────────────────────────────────────────────────
    private val TOP_H = 110f
    private val BC_H  = 42f
    private val AB_H  = 96f
    private val PS_H  = 52f
    private val FAB_R = 52f
    private val DRAW_W get() = width * 0.80f

    private val contentTop get() = TOP_H + BC_H
    private val pasteVis   get() = clipFiles.isNotEmpty() && !actionBar.visible
    private val gridBottom get() = height.toFloat() -
        (if (actionBar.visible) AB_H else 0f) -
        (if (pasteVis) PS_H else 0f)

    // ── Panels ────────────────────────────────────────────────────────────────
    private val drawerPanel = FolderTreePanel { dir ->
        openDirectory(dir); drawerOpen = false; invalidate()
    }

    private val gridPanel: FileGridPanel = FileGridPanel(
        onFileOpen        = { f -> if (f.isDirectory) openDirectory(f.file) else toast("Open: ${f.name}") },
        onSelectionChanged = { sel -> actionBar.update(sel); main.post { relayout(); invalidate() } }
    )

    private val actionBar: ActionBar = ActionBar(
        onCopy      = { f -> doClip(f, "copy") },
        onMove      = { f -> doClip(f, "move") },
        onDelete    = { f -> doDelete(f) },
        onRename    = { f -> doRename(f) },
        onArchive   = { f -> doArchive(f) },
        onDeselect  = { gridPanel.clearSelection(); invalidate() },
        onSelectAll = { gridPanel.selectAll(); invalidate() }
    )

    private val gesture = GestureDetector(context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onLongPress(e: MotionEvent) {
                if (!drawerOpen && gridPanel.contains(e.x, e.y)) {
                    gridPanel.onLongPress(e.x, e.y); invalidate()
                }
            }
        })

    private var zone  = ""
    private var touchSX = 0f

    // ── Init ──────────────────────────────────────────────────────────────────
    init {
        setBackgroundColor(Theme.BG_BASE)
        gridPanel.onRedrawNeeded = { postInvalidate() }
    }

    fun loadRoot() { main.post { drawerPanel.init(); invalidate() } }

    private fun openDirectory(dir: File) {
        if (navStack.isEmpty() || navStack.last() != dir) navStack.addLast(dir)
        gridPanel.loadDirectory(dir); main.post { relayout(); invalidate() }
    }

    fun navigateUp(): Boolean {
        if (overflowOpen) { overflowOpen = false; invalidate(); return true }
        if (drawerOpen)   { drawerOpen   = false; invalidate(); return true }
        if (navStack.size <= 1) return false
        navStack.removeLast(); gridPanel.loadDirectory(navStack.last())
        main.post { relayout(); invalidate() }
        return true
    }

    // ── Layout ────────────────────────────────────────────────────────────────
    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        drawerPanel.left = 0f; drawerPanel.top = 0f
        drawerPanel.width = DRAW_W; drawerPanel.height = h.toFloat()
        relayout()
    }

    private fun relayout() {
        gridPanel.left = 0f;    gridPanel.top  = contentTop
        gridPanel.width = width.toFloat(); gridPanel.height = gridBottom - contentTop
        actionBar.left  = 0f;   actionBar.top  = height - AB_H
        actionBar.width = width.toFloat(); actionBar.height = AB_H
    }

    // ── Draw ──────────────────────────────────────────────────────────────────
    override fun onDraw(canvas: Canvas) {
        gridPanel.draw(canvas)
        drawTopBar(canvas)
        drawBreadcrumb(canvas)
        if (pasteVis)          drawPasteStrip(canvas)
        if (actionBar.visible) actionBar.draw(canvas)
        if (!actionBar.visible) drawFAB(canvas)
        if (drawerOpen)        { drawScrim(canvas); drawerPanel.draw(canvas) }
        if (overflowOpen)      drawOverflow(canvas)
    }

    private fun drawTopBar(canvas: Canvas) {
        DrawUtils.drawRoundRect(canvas, paint, 0f, 0f, width.toFloat(), TOP_H, 0f, Theme.BG_SURFACE)
        val cy = TOP_H / 2f; val hasParent = navStack.size > 1
        paint.color = if (hasParent) Theme.ACCENT else Theme.TEXT_PRIMARY
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 3f; paint.strokeCap = Paint.Cap.ROUND
        if (hasParent) {
            canvas.drawLine(60f, cy - 12f, 40f, cy, paint); canvas.drawLine(40f, cy, 60f, cy + 12f, paint); canvas.drawLine(40f, cy, 78f, cy, paint)
        } else {
            canvas.drawLine(34f, cy - 10f, 70f, cy - 10f, paint); canvas.drawLine(34f, cy, 70f, cy, paint); canvas.drawLine(34f, cy + 10f, 70f, cy + 10f, paint)
        }
        DrawUtils.drawText(canvas, paint, navStack.lastOrNull()?.name?.takeIf { it.isNotBlank() } ?: "FileZ",
            width / 2f, cy + 11f, Theme.TEXT_PRIMARY, 30f, Paint.Align.CENTER, android.graphics.Typeface.DEFAULT_BOLD)
        val r1 = width - 44f; val r2 = width - 112f; val r3 = width - 178f
        paint.color = if (overflowOpen) Theme.ACCENT else Theme.TEXT_SECONDARY; paint.style = Paint.Style.FILL
        canvas.drawCircle(r1, cy - 9f, 5f, paint); canvas.drawCircle(r1, cy, 5f, paint); canvas.drawCircle(r1, cy + 9f, 5f, paint)
        drawViewIcon(canvas, r2, cy)
        paint.color = if (gridPanel.hasActiveSearch) Theme.ACCENT else Theme.TEXT_SECONDARY
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 2.8f
        canvas.drawCircle(r3 - 3f, cy - 2f, 11f, paint); canvas.drawLine(r3 + 5f, cy + 6f, r3 + 14f, cy + 15f, paint)
        paint.color = Theme.DIVIDER; paint.strokeWidth = 1f; canvas.drawLine(0f, TOP_H, width.toFloat(), TOP_H, paint)
    }

    private fun drawViewIcon(canvas: Canvas, cx: Float, cy: Float) {
        paint.color = Theme.TEXT_SECONDARY
        if (gridPanel.isListMode) {
            paint.style = Paint.Style.STROKE; paint.strokeWidth = 2.2f
            canvas.drawRect(cx-12f,cy-12f,cx-2f,cy-2f,paint); canvas.drawRect(cx+2f,cy-12f,cx+12f,cy-2f,paint)
            canvas.drawRect(cx-12f,cy+2f,cx-2f,cy+12f,paint); canvas.drawRect(cx+2f,cy+2f,cx+12f,cy+12f,paint)
        } else {
            paint.style = Paint.Style.STROKE; paint.strokeWidth = 2.8f; paint.strokeCap = Paint.Cap.ROUND
            canvas.drawLine(cx-12f,cy-8f,cx+12f,cy-8f,paint); canvas.drawLine(cx-12f,cy,cx+12f,cy,paint); canvas.drawLine(cx-12f,cy+8f,cx+12f,cy+8f,paint)
        }
    }

    private fun drawBreadcrumb(canvas: Canvas) {
        val ty = TOP_H
        DrawUtils.drawRoundRect(canvas, paint, 0f, ty, width.toFloat(), ty + BC_H, 0f, Theme.BG_PANEL)
        val stack  = navStack.toList()
        val take   = if (stack.size > 3) stack.takeLast(3) else stack
        val labels = (if (stack.size > 3) listOf("…") else emptyList<String>()) +
                     take.map { if (it.name.isBlank()) "Storage" else it.name }
        paint.textSize = 21f; var tx = 18f; val textY = ty + BC_H / 2f + 8f
        labels.forEachIndexed { i, lbl ->
            val last = i == labels.size - 1
            DrawUtils.drawText(canvas, paint, lbl, tx, textY, if (last) Theme.ACCENT else Theme.TEXT_MUTED, 21f)
            tx += paint.measureText(lbl)
            if (!last) {
                tx += 4f
                paint.color = Theme.TEXT_MUTED; paint.style = Paint.Style.STROKE; paint.strokeWidth = 2f; paint.strokeCap = Paint.Cap.ROUND
                canvas.drawLine(tx + 2f, ty + BC_H/2f - 5f, tx + 8f, ty + BC_H/2f, paint)
                canvas.drawLine(tx + 8f, ty + BC_H/2f,      tx + 2f, ty + BC_H/2f + 5f, paint)
                tx += 18f
            }
        }
        paint.color = Theme.DIVIDER; paint.style = Paint.Style.STROKE; paint.strokeWidth = 1f
        canvas.drawLine(0f, ty + BC_H, width.toFloat(), ty + BC_H, paint)
    }

    private fun drawPasteStrip(canvas: Canvas) {
        val sy = gridBottom
        DrawUtils.drawRoundRect(canvas, paint, 0f, sy, width.toFloat(), sy + PS_H, 0f, Theme.WARNING_DIM)
        paint.color = Theme.WARNING; paint.style = Paint.Style.STROKE; paint.strokeWidth = 1f
        canvas.drawLine(0f, sy, width.toFloat(), sy, paint)
        val verb = if (clipMode == "copy") "Copied" else "Cut"
        DrawUtils.drawText(canvas, paint, "  ${clipFiles.size} item(s) $verb", 16f, sy + PS_H/2f + 9f, Theme.WARNING, 21f)
        val btnW = 136f; val btnX = width - btnW - 12f
        DrawUtils.drawRoundRect(canvas, paint, btnX, sy + 8f, btnX + btnW, sy + PS_H - 8f, 8f, Theme.WARNING)
        DrawUtils.drawText(canvas, paint, "Paste Here", btnX + btnW/2f, sy + PS_H/2f + 9f,
            Theme.BG_BASE, 20f, Paint.Align.CENTER, android.graphics.Typeface.DEFAULT_BOLD)
        val xc = btnX - 28f; val xcy = sy + PS_H/2f
        paint.color = Theme.TEXT_MUTED; paint.strokeWidth = 2.5f; paint.strokeCap = Paint.Cap.ROUND
        canvas.drawLine(xc - 7f, xcy - 7f, xc + 7f, xcy + 7f, paint)
        canvas.drawLine(xc + 7f, xcy - 7f, xc - 7f, xcy + 7f, paint)
    }

    private fun drawFAB(canvas: Canvas) {
        val cx = width - FAB_R - 26f; val cy = height - FAB_R - 26f
        DrawUtils.drawShadow(canvas, paint, cx, cy, FAB_R, Theme.ACCENT)
        paint.color = Theme.ACCENT; paint.style = Paint.Style.FILL; canvas.drawCircle(cx, cy, FAB_R, paint)
        paint.color = Theme.BG_BASE; paint.style = Paint.Style.STROKE; paint.strokeWidth = 4f; paint.strokeCap = Paint.Cap.ROUND
        canvas.drawLine(cx - 18f, cy, cx + 18f, cy, paint); canvas.drawLine(cx, cy - 18f, cx, cy + 18f, paint)
    }

    private fun drawScrim(canvas: Canvas) {
        paint.color = Theme.SCRIM; paint.style = Paint.Style.FILL
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    }

    private fun drawOverflow(canvas: Canvas) {
        val vis  = if (clipFiles.isEmpty()) ovItems.filter { it.label != "Clear Clipboard" } else ovItems
        val mW   = 290f; val mX = width - mW - 14f; val mY = TOP_H + 6f
        val iH   = 72f;  val mH = vis.size * iH + 14f
        paint.color = DrawUtils.adjustAlpha(0, 90); paint.style = Paint.Style.FILL
        canvas.drawRoundRect(mX+4f, mY+4f, mX+mW+4f, mY+mH+4f, 14f, 14f, paint)
        DrawUtils.drawRoundRect(canvas, paint, mX, mY, mX+mW, mY+mH, 14f, Theme.BG_SURFACE2)
        paint.color = Theme.DIVIDER; paint.style = Paint.Style.STROKE; paint.strokeWidth = 1f
        canvas.drawRoundRect(mX, mY, mX+mW, mY+mH, 14f, 14f, paint)
        vis.forEachIndexed { i, item ->
            val iy = mY + 7f + i * iH; item.top = iy; item.bot = iy + iH
            val col = if (item.label == "Clear Clipboard") Theme.DANGER else Theme.TEXT_PRIMARY
            paint.color = col; paint.style = Paint.Style.FILL; canvas.drawCircle(mX + 24f, iy + iH/2f, 5f, paint)
            DrawUtils.drawText(canvas, paint, item.label, mX + 40f, iy + iH/2f + 9f, col, 25f)
            if (i < vis.size - 1) { paint.color = Theme.DIVIDER; paint.style = Paint.Style.STROKE; paint.strokeWidth = 1f; canvas.drawLine(mX+14f, iy+iH, mX+mW-14f, iy+iH, paint) }
        }
    }

    // ── Touch ─────────────────────────────────────────────────────────────────
    override fun onTouchEvent(event: MotionEvent): Boolean {
        gesture.onTouchEvent(event); val x = event.x; val y = event.y
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchSX = x
                if (overflowOpen) { zone = "overflow"; return true }
                if (drawerOpen) { zone = if (x < DRAW_W) "drawer" else "scrim"; if (zone == "drawer") drawerPanel.onTouchDown(x, y); return true }
                zone = when {
                    x < 28f                                               -> "edge"
                    y < TOP_H                                             -> "topbar"
                    y < contentTop                                        -> "breadcrumb"
                    actionBar.visible && actionBar.contains(x, y)        -> { actionBar.onTouchDown(x, y); "actionbar" }
                    pasteVis && y >= gridBottom && y < gridBottom + PS_H -> "pastestrip"
                    isFAB(x, y)                                           -> "fab"
                    else                                                  -> { gridPanel.onTouchDown(x, y); "grid" }
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (zone == "edge" && x - touchSX > 55f) { drawerOpen = true; zone = "scrim"; invalidate() }
                when (zone) {
                    "grid"      -> { gridPanel.onTouchMove(x, y); invalidate() }
                    "drawer"    -> { drawerPanel.onTouchMove(x, y); invalidate() }
                    "actionbar" -> { actionBar.onTouchMove(x, y); invalidate() }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                when (zone) {
                    "fab"        -> showNewFolderDialog()
                    "topbar"     -> handleTopBar(x, y)
                    "actionbar"  -> { actionBar.onTouchUp(x, y); invalidate() }
                    "pastestrip" -> handlePasteStrip(x, y)
                    "grid"       -> { gridPanel.onTouchUp(x, y); invalidate() }
                    "drawer"     -> { drawerPanel.onTouchUp(x, y); invalidate() }
                    "scrim"      -> { drawerOpen = false; invalidate() }
                    "overflow"   -> handleOverflow(x, y)
                }
                zone = ""
            }
        }
        return true
    }

    private fun isFAB(x: Float, y: Float): Boolean {
        if (actionBar.visible) return false
        val cx = width - FAB_R - 26f; val cy = height - FAB_R - 26f
        val dx = x - cx; val dy = y - cy; return dx*dx + dy*dy < FAB_R*FAB_R
    }

    private fun handleTopBar(x: Float, y: Float) {
        when {
            x < 100f          -> if (navStack.size > 1) navigateUp() else { drawerOpen = true; invalidate() }
            x > width - 74f  -> { overflowOpen = !overflowOpen; invalidate() }
            x > width - 140f -> { gridPanel.toggleViewMode(); invalidate() }
            x > width - 206f -> showSearchDialog()
        }
    }

    private fun handlePasteStrip(x: Float, y: Float) {
        val sy = gridBottom; val btnW = 136f; val btnX = width - btnW - 12f; val xc = btnX - 28f
        when {
            x in btnX..(btnX + btnW) -> doPaste()
            x in (xc-14f)..(xc+14f) -> { clipFiles = emptyList(); relayout(); invalidate() }
        }
    }

    private fun handleOverflow(x: Float, y: Float) {
        val vis = if (clipFiles.isEmpty()) ovItems.filter { it.label != "Clear Clipboard" } else ovItems
        vis.forEach { item ->
            if (y in item.top..item.bot) {
                overflowOpen = false
                when (item.label) {
                    "New Folder"      -> showNewFolderDialog()
                    "Select All"      -> { gridPanel.selectAll(); invalidate() }
                    "Refresh"         -> { gridPanel.refresh(); invalidate() }
                    "Clear Clipboard" -> { clipFiles = emptyList(); relayout(); invalidate() }
                }
                return
            }
        }
        overflowOpen = false; invalidate()
    }

    // ── File Operations ───────────────────────────────────────────────────────
    private fun doClip(files: List<FileModel>, mode: String) {
        clipFiles = files.toList(); clipMode = mode; gridPanel.clearSelection()
        toast("${files.size} item(s) ${if (mode == "copy") "copied" else "cut"}")
        relayout(); invalidate()
    }

    private fun doPaste() {
        val dest = navStack.lastOrNull() ?: return
        val files = clipFiles.toList(); val mode = clipMode
        io.execute {
            var ok = true
            files.forEach { fm ->
                val d = File(dest, fm.name)
                if (!when (mode) { "copy" -> FileOperations.copyFile(fm.file, d); "move" -> FileOperations.moveFile(fm.file, d); else -> false }) ok = false
            }
            main.post { if (mode == "move") clipFiles = emptyList(); gridPanel.refresh(); relayout(); invalidate(); toast(if (ok) "Pasted ${files.size} item(s)" else "Paste failed") }
        }
    }

    private fun doDelete(files: List<FileModel>) {
        main.post {
            AlertDialog.Builder(context).setTitle("Delete ${files.size} item(s)?").setMessage("This cannot be undone.")
                .setPositiveButton("Delete") { _, _ -> io.execute { files.forEach { FileOperations.deleteRecursive(it.file) }; main.post { gridPanel.clearSelection(); gridPanel.refresh(); invalidate() } } }
                .setNegativeButton("Cancel", null).show()
        }
    }

    private fun doRename(file: FileModel) {
        main.post {
            val ed = EditText(context).also { it.setText(file.name); it.selectAll(); it.setPadding(40,20,40,20) }
            AlertDialog.Builder(context).setTitle("Rename").setView(ed)
                .setPositiveButton("Rename") { _, _ ->
                    val n = ed.text.toString().trim()
                    if (n.isNotEmpty() && n != file.name) io.execute { val ok = FileOperations.renameFile(file.file, n); main.post { gridPanel.clearSelection(); gridPanel.refresh(); if (!ok) toast("Rename failed"); invalidate() } }
                }.setNegativeButton("Cancel", null).show()
        }
    }

    private fun doArchive(files: List<FileModel>) {
        val dir = navStack.lastOrNull() ?: return
        val name = if (files.size == 1) files[0].name else "archive_${System.currentTimeMillis()}"
        io.execute {
            val ok = ArchiveManager.createZip(files.map { it.file }, File(dir, "$name.zip"))
            main.post { if (ok) { gridPanel.clearSelection(); gridPanel.refresh(); toast("Archive created") } else toast("Archive failed"); invalidate() }
        }
    }

    private fun showNewFolderDialog() {
        main.post {
            val ed = EditText(context).also { it.hint = "Folder name"; it.setPadding(40,20,40,20) }
            AlertDialog.Builder(context).setTitle("New Folder").setView(ed)
                .setPositiveButton("Create") { _, _ ->
                    val n = ed.text.toString().trim(); if (n.isEmpty()) return@setPositiveButton
                    val parent = navStack.lastOrNull() ?: return@setPositiveButton
                    val ok = File(parent, n).mkdir(); gridPanel.refresh()
                    toast(if (ok) "Folder created" else "Failed to create folder"); invalidate()
                }.setNegativeButton("Cancel", null).show()
        }
    }

    private fun showSearchDialog() {
        main.post {
            val ed = EditText(context).also { it.hint = "Search files\u2026"; it.setPadding(40,20,40,20) }
            AlertDialog.Builder(context).setTitle("Search").setView(ed)
                .setPositiveButton("Search") { _, _ ->
                    val q = ed.text.toString().trim()
                    gridPanel.setSearchQuery(q); gridPanel.searchActive = q.isNotEmpty(); invalidate()
                }
                .setNegativeButton("Clear") { _, _ -> gridPanel.setSearchQuery(""); gridPanel.searchActive = false; invalidate() }
                .show()
        }
    }

    private fun toast(msg: String) = main.post { Toast.makeText(context, msg, Toast.LENGTH_SHORT).show() }
}
