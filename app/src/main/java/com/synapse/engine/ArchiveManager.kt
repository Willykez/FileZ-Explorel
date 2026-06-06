package com.synapse.engine

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object ArchiveManager {
    fun createZip(files: List<File>, zipFile: File): Boolean {
        return try {
            ZipOutputStream(FileOutputStream(zipFile)).use { out ->
                files.forEach { file ->
                    if (file.isFile) {
                        FileInputStream(file).use { fi ->
                            out.putNextEntry(ZipEntry(file.name))
                            val buf = ByteArray(8192)
                            var len: Int
                            while (fi.read(buf).also { len = it } > 0) out.write(buf, 0, len)
                            out.closeEntry()
                        }
                    }
                }
            }
            true
        } catch (e: Exception) { false }
    }
}
