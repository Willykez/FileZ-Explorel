package com.synapse.engine

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.channels.FileChannel

class FileOperations {
    fun copyFile(source: File, dest: File) {
        var sourceChannel: FileChannel? = null
        var destChannel: FileChannel? = null
        try {
            sourceChannel = FileInputStream(source).channel
            destChannel = FileOutputStream(dest).channel
            destChannel.transferFrom(sourceChannel, 0, sourceChannel.size())
        } finally {
            sourceChannel?.close()
            destChannel?.close()
        }
    }

    fun moveFile(source: File, dest: File) {
        if (!source.renameTo(dest)) {
            copyFile(source, dest)
            source.delete()
        }
    }
}
