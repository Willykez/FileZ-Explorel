package com.synapse.engine

import java.io.File
import java.util.concurrent.Executors

/** Simple background directory scanner (used for search). */
class FileScanner(private val onFileFound: (File) -> Unit) {
    private val executor = Executors.newSingleThreadExecutor()

    fun scanDirectory(root: File) {
        executor.execute { walkDir(root) }
    }

    private fun walkDir(dir: File) {
        try {
            dir.listFiles()?.forEach { file ->
                onFileFound(file)
                if (file.isDirectory && !file.name.startsWith(".")) walkDir(file)
            }
        } catch (e: SecurityException) { /* skip */ }
    }

    fun stop() = executor.shutdownNow()
}
