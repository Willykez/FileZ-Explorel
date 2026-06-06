package com.synapse.engine

import java.io.File

enum class FileType { FOLDER, IMAGE, VIDEO, AUDIO, DOCUMENT, ARCHIVE, CODE, APK, UNKNOWN }

data class FileModel(
    val file: File,
    val name: String = file.name,
    val size: Long = if (file.isFile) file.length() else 0L,
    val lastModified: Long = file.lastModified(),
    val isDirectory: Boolean = file.isDirectory,
    val type: FileType = resolveType(file)
) {
    var isSelected: Boolean = false

    companion object {
        fun resolveType(file: File): FileType {
            if (file.isDirectory) return FileType.FOLDER
            return when (file.extension.lowercase()) {
                "jpg", "jpeg", "png", "gif", "bmp", "webp", "heic", "svg" -> FileType.IMAGE
                "mp4", "mkv", "avi", "mov", "webm", "3gp", "ts"           -> FileType.VIDEO
                "mp3", "wav", "flac", "aac", "ogg", "m4a", "opus"         -> FileType.AUDIO
                "pdf", "doc", "docx", "txt", "xls", "xlsx", "ppt", "pptx",
                "odt", "csv", "rtf", "md"                                  -> FileType.DOCUMENT
                "zip", "rar", "7z", "tar", "gz", "bz2", "xz"             -> FileType.ARCHIVE
                "kt", "java", "py", "js", "ts", "html", "css", "json",
                "xml", "sh", "c", "cpp", "h", "cs", "go", "rs", "rb"     -> FileType.CODE
                "apk"                                                       -> FileType.APK
                else                                                        -> FileType.UNKNOWN
            }
        }

        fun formatSize(bytes: Long): String {
            if (bytes <= 0) return "—"
            val kb = bytes / 1024.0
            val mb = kb / 1024.0
            val gb = mb / 1024.0
            return when {
                gb >= 1.0  -> String.format("%.1f GB", gb)
                mb >= 1.0  -> String.format("%.1f MB", mb)
                kb >= 1.0  -> String.format("%.0f KB", kb)
                else       -> "$bytes B"
            }
        }
    }
}

enum class SortMode { NAME, SIZE, DATE, TYPE }
