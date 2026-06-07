package com.synapse.engine

import android.app.AlertDialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import java.io.File
import java.util.concurrent.Executors

/**
 * Single custom View owning the entire UI.
 * Left: FolderTreePanel  |  Right: FileGridPanel  |  Bottom: ActionBar
 */
class ExplorerView(context: Context) : View(context) {

    private val paint       = Paint(Paint.ANTI_ALIAS_FLAG)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val ioExecutor  = Executors.newSingleThreadExecutor()

    private val navStack = ArrayDeque<File>()

    private val treePanel = FolderTreePanel { dir -> openDirectory(dir) }

    private val gridPanel = FileGridPanel(
        onFileOpen = { file ->
            if (file.isDirectory) openDirectory(file.file)
        },
        onSelectionChanged = { selected ->
            actionBar.update(selected)
            mainHandler.post { invalidate() }
        }
    )

    private val actionBar = ActionBar(
        onCopy     = { files -> handleCopy(files) },
        onMove     = { files -> handleMove(files) },
        onDelete   = { files -> handleDelete(files) },
        onDeselect = { gridPanel.clearSelection(); invalidate() }
    )

    private val gestureDetector = GestureDetector(context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onLongPress(e: MotionEvent) {
                if (gridPanel.contains(e.x, e.y)) {
                    gridPanel.onLongPress(e.x, e.y)
                    invalidate()
                }
            }
        })

    private var activePanel: String? = null
    private var clipboardFiles = listOf<FileModel>()
    private var clipboardMode  = ""

    init {
        setBackgroundColor(Theme.BG_BASE)
    }

    fun loadRoot() {
        mainHandler.post {
            treePanel.init()
            invalidate()
        }
    }

    private fun openDirectory(dir: File) {
        if (navStack.isEmpty() || navStack.last() != dir) navStack.addLast(dir)
        treePanel.selectFolder(dir)
        gridPanel.loadDirectory(dir)
        mainHandler.post { invalidate() }
    }

    fun navigateUp(): Boolean {
        if (navStack.size <= 1) return false
        navStack.removeLast()
        val parent = navStack.last()
        treePanel.selectFolder(parent)
        gridPanel.loadDirectory(parent)
        invalidate()
        return true
    }

    // ── Layout ────────────────────────────────────────────────────────────────

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val treeW   = w * 0.28f
        val actionH = actionBar.height

        treePanel.left   = 0f
        treePanel.top    = 0f
        treePanel.width  = treeW
        treePanel.height = h.toFloat()

        gridPanel.left   = treeW + 1f
        gridPanel.top    = 0f
        gridPanel.width  = w - treeW - 1f
        gridPanel.height = h.toFloat()

        actionBar.left  = treeW + 1f
        actionBar.top   = h - actionH
        actionBar.width = w - treeW - 1f
    }

    // ── Draw ──────────────────────────────────────────────────────────────────

    override fun onDraw(canvas: Canvas) {
        treePanel.draw(canvas)
        gridPanel.draw(canvas)
        if (actionBar.visible) actionBar.draw(canvas)
        // Re-draw while loading
        postInvalidateDelayed(150)
    }

    // ── Touch ─────────────────────────────────────────────────────────────────

    override fun onTouchEvent(event: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(event)
        val x = event.x
        val y = event.y

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activePanel = when {
                    actionBar.contains(x, y) -> "action"
                    treePanel.contains(x, y) -> "tree"
                    else                     -> "grid"
                }
                when (activePanel) {
                    "tree" -> treePanel.onTouchDown(x, y)
                    "grid" -> gridPanel.onTouchDown(x, y)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                when (activePanel) {
                    "tree" -> { treePanel.onTouchMove(x, y); invalidate() }
                    "grid" -> { gridPanel.onTouchMove(x, y); invalidate() }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                when (activePanel) {
                    "action" -> actionBar.onTouchUp(x, y)
                    "tree"   -> treePanel.onTouchUp(x, y)
                    "grid"   -> gridPanel.onTouchUp(x, y)
                }
                activePanel = null
                invalidate()
            }
        }
        return true
    }

    // ── File Operations ───────────────────────────────────────────────────────

    private fun handleCopy(files: List<FileModel>) {
        clipboardFiles = files
        clipboardMode  = "copy"
        gridPanel.clearSelection()
        toast("${files.size} item(s) copied. Navigate to destination.")
    }

    private fun handleMove(files: List<FileModel>) {
        clipboardFiles = files
        clipboardMode  = "move"
        gridPanel.clearSelection()
        toast("${files.size} item(s) cut. Navigate to destination.")
    }

    private fun handleDelete(files: List<FileModel>) {
        mainHandler.post {
            AlertDialog.Builder(context)
                .setTitle("Delete ${files.size} item(s)?")
                .setMessage("This cannot be undone.")
                .setPositiveButton("Delete") { _, _ ->
                    ioExecutor.execute {
                        files.forEach { FileOperations.deleteRecursive(it.file) }
                        mainHandler.post {
                            gridPanel.clearSelection()
                            gridPanel.refresh()
                            invalidate()
                        }
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun toast(msg: String) {
        mainHandler.post {
            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
        }
    }
}
