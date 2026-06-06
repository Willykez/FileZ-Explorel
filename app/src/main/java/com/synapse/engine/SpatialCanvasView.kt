package com.synapse.engine

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import java.io.File
import java.io.FileInputStream

class SpatialCanvasView(context: Context) : View(context) {
    private val mainMatrix = Matrix()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val iconGenerator = IconGenerator()
    private val dataManager = DataManager()
    private val physicsEngine = PhysicsEngine(dataManager)
    private val mediaEngine = MediaEngine()
    private val hexViewer = HexViewer()
    private val scanner = FileScanner { file ->
        dataManager.addFile(
            file.name ?: "Unknown",
            file.absolutePath,
            file.length(),
            file.lastModified(),
            file.extension.hashCode()
        )
    }

    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var isPanning = false
    private var running = false
    private var selectedNodeIndex = -1
    private var hexViewFileIndex = -1
    private var currentScale = 1.0f

    private val queuedNodeIds = IntArray(100)
    private var queuedCount = 0

    private val typeCentersX = mutableMapOf<Int, Float>()
    private val typeCentersY = mutableMapOf<Int, Float>()
    private val typeCounts = mutableMapOf<Int, Int>()

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val scaleFactor = detector.scaleFactor
            currentScale *= scaleFactor

            if (scaleFactor < 0.8f) {
                queueNearbyNodes(detector.focusX, detector.focusY)
            }

            mainMatrix.postScale(scaleFactor, scaleFactor, detector.focusX, detector.focusY)

            if (currentScale > 5.0f && hexViewFileIndex == -1) {
                val pts = floatArrayOf(detector.focusX, detector.focusY)
                val inverse = Matrix()
                mainMatrix.invert(inverse)
                inverse.mapPoints(pts)
                hexViewFileIndex = findNodeAt(pts[0], pts[1])
            } else if (currentScale < 3.0f) {
                hexViewFileIndex = -1
            }

            invalidate()
            return true
        }
    })

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onLongPress(e: MotionEvent) {
            val pts = floatArrayOf(e.x, e.y)
            val inverse = Matrix()
            mainMatrix.invert(inverse)
            inverse.mapPoints(pts)
            val idx = findNodeAt(pts[0], pts[1])
            if (idx != -1) {
                val path = synchronized(dataManager) { dataManager.paths[idx] } ?: return
                val file = File(path)
                if (file.exists() && (file.extension == "mp3" || file.extension == "wav" || file.extension == "mp4")) {
                    val fis = FileInputStream(file)
                    mediaEngine.play(fis.fd)
                }
            }
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            queuedCount = 0
            mediaEngine.stop()
            hexViewFileIndex = -1
            currentScale = 1.0f
            mainMatrix.reset()
            invalidate()
            return true
        }
    })

    init {
        setBackgroundColor(Color.BLACK)
    }

    private fun queueNearbyNodes(focusX: Float, focusY: Float) {
        val pts = floatArrayOf(focusX, focusY)
        val inverse = Matrix()
        mainMatrix.invert(inverse)
        inverse.mapPoints(pts)
        val cx = pts[0]
        val cy = pts[1]

        synchronized(dataManager) {
            for (i in 0 until dataManager.count) {
                val dx = dataManager.x[i] - cx
                val dy = dataManager.y[i] - cy
                if (dx * dx + dy * dy < 40000f) {
                    if (queuedCount < queuedNodeIds.size) {
                        var alreadyQueued = false
                        for (j in 0 until queuedCount) {
                            if (queuedNodeIds[j] == i) {
                                alreadyQueued = true
                                break
                            }
                        }
                        if (!alreadyQueued) {
                            queuedNodeIds[queuedCount++] = i
                        }
                    }
                }
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)

        if (scaleDetector.isInProgress) return true

        val pts = floatArrayOf(event.x, event.y)
        val inverse = Matrix()
        mainMatrix.invert(inverse)
        inverse.mapPoints(pts)
        val worldX = pts[0]
        val worldY = pts[1]

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                selectedNodeIndex = findNodeAt(worldX, worldY)
                if (selectedNodeIndex == -1) {
                    lastTouchX = event.x
                    lastTouchY = event.y
                    isPanning = true
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (selectedNodeIndex != -1) {
                    synchronized(dataManager) {
                        dataManager.x[selectedNodeIndex] = worldX
                        dataManager.y[selectedNodeIndex] = worldY
                        dataManager.vx[selectedNodeIndex] = 0f
                        dataManager.vy[selectedNodeIndex] = 0f
                    }
                    invalidate()
                } else if (isPanning) {
                    val dx = event.x - lastTouchX
                    val dy = event.y - lastTouchY
                    mainMatrix.postTranslate(dx, dy)
                    lastTouchX = event.x
                    lastTouchY = event.y
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isPanning = false
                selectedNodeIndex = -1
            }
        }
        return true
    }

    private fun findNodeAt(x: Float, y: Float): Int {
        synchronized(dataManager) {
            for (i in 0 until dataManager.count) {
                val dx = dataManager.x[i] - x
                val dy = dataManager.y[i] - y
                if (dx * dx + dy * dy < 10000f) {
                    return i
                }
            }
        }
        return -1
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (running) {
            physicsEngine.update()
            invalidate()
        }

        canvas.save()
        canvas.concat(mainMatrix)

        synchronized(dataManager) {
            val count = dataManager.count

            typeCentersX.clear()
            typeCentersY.clear()
            typeCounts.clear()

            for (i in 0 until count) {
                val t = dataManager.types[i]
                typeCentersX[t] = typeCentersX.getOrDefault(t, 0f) + dataManager.x[i]
                typeCentersY[t] = typeCentersY.getOrDefault(t, 0f) + dataManager.y[i]
                typeCounts[t] = typeCounts.getOrDefault(t, 0) + 1
            }

            paint.color = Color.argb(50, 255, 255, 255)
            paint.strokeWidth = 2f
            for (i in 0 until count) {
                val t = dataManager.types[i]
                val tx = typeCentersX[t]!! / typeCounts[t]!!
                val ty = typeCentersY[t]!! / typeCounts[t]!!
                canvas.drawLine(dataManager.x[i], dataManager.y[i], tx, ty, paint)
            }

            for (i in 0 until count) {
                canvas.save()
                canvas.translate(dataManager.x[i], dataManager.y[i])

                val isSelected = i == selectedNodeIndex
                val size = if (isSelected) 70f else 50f

                iconGenerator.drawIcon(canvas, paint, dataManager.types[i], size)

                paint.color = Color.WHITE
                paint.style = Paint.Style.FILL
                paint.textSize = 30f
                paint.textAlign = Paint.Align.CENTER
                dataManager.names[i]?.let {
                    canvas.drawText(it, 0f, size + 40f, paint)
                }

                canvas.restore()
            }

            paint.color = Color.YELLOW
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 3f
            for (idx in 0 until queuedCount) {
                val i = queuedNodeIds[idx]
                if (i < count) {
                    canvas.drawCircle(dataManager.x[i], dataManager.y[i], 80f, paint)
                }
            }
        }

        drawVisualizer(canvas)

        canvas.restore()

        if (hexViewFileIndex != -1) {
            val path = synchronized(dataManager) { dataManager.paths[hexViewFileIndex] }
            path?.let { hexViewer.draw(canvas, File(it), width.toFloat(), height.toFloat()) }
        }

        if (queuedCount > 0) {
            paint.color = Color.YELLOW
            paint.style = Paint.Style.FILL
            paint.textSize = 40f
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText("Queued: $queuedCount (Double Tap to Clear)", 50f, height - 50f, paint)
        }
    }

    private fun drawVisualizer(canvas: Canvas) {
        val fft = mediaEngine.fftData
        paint.color = Color.MAGENTA
        paint.strokeWidth = 10f
        for (i in 0 until fft.size / 2) {
            val amplitude = Math.abs(fft[i].toInt()).toFloat()
            canvas.drawLine(i * 20f - 500f, 500f, i * 20f - 500f, 500f - amplitude, paint)
        }
    }

    fun start() {
        running = true
        scanner.startScan()
        invalidate()
    }

    fun stop() {
        running = false
        scanner.stop()
        mediaEngine.stop()
        hexViewer.clearCache()
    }
}
