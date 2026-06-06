package com.synapse.engine

import android.graphics.Color

object Theme {
    // Backgrounds
    val BG_BASE        = Color.parseColor("#0D0D0D")
    val BG_PANEL       = Color.parseColor("#141414")
    val BG_SURFACE     = Color.parseColor("#1C1C1C")
    val BG_SURFACE2    = Color.parseColor("#242424")
    val BG_SELECTED    = Color.parseColor("#1A2940")
    val BG_HOVER       = Color.parseColor("#1E1E1E")
    val DIVIDER        = Color.parseColor("#2A2A2A")

    // Text
    val TEXT_PRIMARY   = Color.parseColor("#F0F0F0")
    val TEXT_SECONDARY = Color.parseColor("#888888")
    val TEXT_MUTED     = Color.parseColor("#555555")
    val TEXT_ACCENT    = Color.parseColor("#4A9EFF")

    // Accent
    val ACCENT         = Color.parseColor("#4A9EFF")
    val ACCENT_DIM     = Color.parseColor("#1A3A5C")
    val DANGER         = Color.parseColor("#FF4A4A")
    val SUCCESS        = Color.parseColor("#4AFF8A")
    val WARNING        = Color.parseColor("#FFB84A")

    // File type icon colors
    val COLOR_FOLDER   = Color.parseColor("#4A9EFF")
    val COLOR_IMAGE    = Color.parseColor("#FF6B9D")
    val COLOR_VIDEO    = Color.parseColor("#A855F7")
    val COLOR_AUDIO    = Color.parseColor("#22D3EE")
    val COLOR_DOC      = Color.parseColor("#F59E0B")
    val COLOR_ARCHIVE  = Color.parseColor("#F97316")
    val COLOR_CODE     = Color.parseColor("#4ADE80")
    val COLOR_APK      = Color.parseColor("#FB923C")
    val COLOR_UNKNOWN  = Color.parseColor("#6B7280")

    fun fileTypeColor(type: FileType): Int = when (type) {
        FileType.FOLDER   -> COLOR_FOLDER
        FileType.IMAGE    -> COLOR_IMAGE
        FileType.VIDEO    -> COLOR_VIDEO
        FileType.AUDIO    -> COLOR_AUDIO
        FileType.DOCUMENT -> COLOR_DOC
        FileType.ARCHIVE  -> COLOR_ARCHIVE
        FileType.CODE     -> COLOR_CODE
        FileType.APK      -> COLOR_APK
        FileType.UNKNOWN  -> COLOR_UNKNOWN
    }
}
