package com.synapse.engine

import android.os.Environment
import java.io.File
import java.util.concurrent.Executors

class FileScanner(private val onFileFound: (File) -> Unit) {
    private val executor = Executors.newSingleThreadExecutor()

    fun startScan() {
        executor.execute {
            val root = Environment.getExternalStorageDirectory()
            scanDirectory(root)
        }
    }

    private fun scanDirectory(dir: File) {
        val files = dir.listFiles() ?: return
        for (file in files) {
            onFileFound(file)
            if (file.isDirectory && !file.name.startsWith(".")) {
                scanDirectory(file)
            }
        }
    }

    fun stop() {
        executor.shutdownNow()
    }
}
