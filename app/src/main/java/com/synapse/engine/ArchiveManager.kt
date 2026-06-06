package com.synapse.engine

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ArchiveManager {
    fun createZip(files: List<File>, zipFile: File) {
        ZipOutputStream(FileOutputStream(zipFile)).use { out ->
            for (file in files) {
                if (file.isDirectory) continue
                FileInputStream(file).use { fi ->
                    val entry = ZipEntry(file.name)
                    out.putNextEntry(entry)
                    val buffer = ByteArray(4096)
                    var len: Int
                    while (fi.read(buffer).also { len = it } > 0) {
                        out.write(buffer, 0, len)
                    }
                    out.closeEntry()
                }
            }
        }
    }
}
