package com.synapse.engine

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import java.io.File
import java.io.FileInputStream

class HexViewer {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.GREEN
        textSize = 30f
        typeface = android.graphics.Typeface.MONOSPACE
    }

    private var cachedData: ByteArray? = null
    private var cachedFileName: String? = null

    fun draw(canvas: Canvas, file: File, width: Float, height: Float) {
        canvas.drawColor(Color.argb(220, 0, 0, 0))
        val title = "Hex Viewer: ${file.name}"
        canvas.drawText(title, 50f, 50f, paint)

        if (!file.exists() || file.isDirectory) return

        // Lazy load and cache data to avoid I/O on every frame
        if (cachedFileName != file.absolutePath) {
            loadCache(file)
        }

        val data = cachedData ?: return
        val read = data.size

        for (i in 0 until read step 16) {
            val row = StringBuilder()
            row.append(String.format("%04X: ", i))
            for (j in 0 until 16) {
                if (i + j < read) {
                    row.append(String.format("%02X ", data[i + j]))
                } else {
                    row.append("   ")
                }
            }
            row.append(" ")
            for (j in 0 until 16) {
                if (i + j < read) {
                    val c = data[i + j].toInt().toChar()
                    row.append(if (c in ' '..'~') c else '.')
                }
            }
            canvas.drawText(row.toString(), 50f, 100f + (i / 16) * 40f, paint)
            if (100f + (i / 16) * 40f > height) break
        }
    }

    private fun loadCache(file: File) {
        try {
            FileInputStream(file).use { fis ->
                val buffer = ByteArray(512) // Show first 512 bytes
                val read = fis.read(buffer)
                if (read != -1) {
                    cachedData = buffer.copyOfRange(0, read)
                    cachedFileName = file.absolutePath
                }
            }
        } catch (e: Exception) {
            cachedData = null
            cachedFileName = null
        }
    }

    fun clearCache() {
        cachedData = null
        cachedFileName = null
    }
}
