package com.synapse.engine

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class GhostLocker {
    private val key = 0x5A.toByte()

    fun lock(source: File, targetPath: String) {
        val totalSize = source.length()
        val partSize = totalSize / 3
        val filePrefix = source.name.hashCode().toString()

        FileInputStream(source).use { fis ->
            for (i in 0 until 3) {
                val fragmentFile = File(targetPath, "sys_log_${filePrefix}_${i}.tmp")
                FileOutputStream(fragmentFile).use { fos ->
                    val currentPartSize = if (i == 2) totalSize - (partSize * 2) else partSize
                    val buffer = ByteArray(4096)
                    var readTotal = 0L
                    while (readTotal < currentPartSize) {
                        val toRead = Math.min(buffer.size.toLong(), currentPartSize - readTotal).toInt()
                        val read = fis.read(buffer, 0, toRead)
                        if (read == -1) break

                        for (j in 0 until read) {
                            buffer[j] = (buffer[j].toInt() xor key.toInt()).toByte()
                        }
                        fos.write(buffer, 0, read)
                        readTotal += read
                    }
                }
            }
        }
        source.delete()
    }

    fun unlock(fragmentPath: String, originalFile: File) {
        val filePrefix = originalFile.name.hashCode().toString()
        FileOutputStream(originalFile).use { fos ->
            for (i in 0 until 3) {
                val fragmentFile = File(fragmentPath, "sys_log_${filePrefix}_${i}.tmp")
                if (fragmentFile.exists()) {
                    FileInputStream(fragmentFile).use { fis ->
                        val buffer = ByteArray(4096)
                        var read: Int
                        while (fis.read(buffer).also { read = it } != -1) {
                            for (j in 0 until read) {
                                buffer[j] = (buffer[j].toInt() xor key.toInt()).toByte()
                            }
                            fos.write(buffer, 0, read)
                        }
                    }
                    fragmentFile.delete()
                }
            }
        }
    }
}
