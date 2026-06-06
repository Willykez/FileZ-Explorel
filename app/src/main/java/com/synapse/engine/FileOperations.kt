package com.synapse.engine

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.channels.FileChannel

object FileOperations {

    fun copyFile(source: File, dest: File): Boolean {
        return try {
            if (source.isDirectory) {
                copyDirectory(source, dest)
            } else {
                dest.parentFile?.mkdirs()
                FileInputStream(source).channel.use { src ->
                    FileOutputStream(dest).channel.use { dst ->
                        dst.transferFrom(src, 0, src.size())
                    }
                }
                true
            }
        } catch (e: Exception) { false }
    }

    private fun copyDirectory(source: File, dest: File): Boolean {
        dest.mkdirs()
        source.listFiles()?.forEach { child ->
            val target = File(dest, child.name)
            if (child.isDirectory) copyDirectory(child, target)
            else copyFile(child, target)
        }
        return true
    }

    fun moveFile(source: File, dest: File): Boolean {
        return if (source.renameTo(dest)) true
        else copyFile(source, dest) && source.deleteRecursively()
    }

    fun deleteRecursive(file: File): Boolean = file.deleteRecursively()

    fun formatSize(bytes: Long): String = FileModel.formatSize(bytes)
}
