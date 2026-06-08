package com.synapse.engine

import android.graphics.Color

object Theme {
    // Backgrounds
    val BG_BASE      = Color.parseColor("#07070F")
    val BG_PANEL     = Color.parseColor("#0D0D1A")
    val BG_SURFACE   = Color.parseColor("#12121E")
    val BG_SURFACE2  = Color.parseColor("#191926")
    val BG_SURFACE3  = Color.parseColor("#202030")
    val BG_SELECTED  = Color.parseColor("#0C1E3C")
    val SCRIM        = Color.parseColor("#BF000000")
    val DIVIDER      = Color.parseColor("#1A1A2C")

    // Text
    val TEXT_PRIMARY   = Color.parseColor("#EEEEFF")
    val TEXT_SECONDARY = Color.parseColor("#7A7A9E")
    val TEXT_MUTED     = Color.parseColor("#3E3E58")
    val TEXT_ON_DARK   = Color.parseColor("#FFFFFF")

    // Accents
    val ACCENT       = Color.parseColor("#5E9FFF")
    val ACCENT_DIM   = Color.parseColor("#0C1E3A")
    val DANGER       = Color.parseColor("#FF4F4F")
    val DANGER_DIM   = Color.parseColor("#200A0A")
    val SUCCESS      = Color.parseColor("#4CE87A")
    val WARNING      = Color.parseColor("#FFB74D")
    val WARNING_DIM  = Color.parseColor("#1E1300")
    val PURPLE       = Color.parseColor("#A855F7")
    val PURPLE_DIM   = Color.parseColor("#180C2E")

    // File type palette
    val COLOR_FOLDER  = Color.parseColor("#5E9FFF")
    val COLOR_IMAGE   = Color.parseColor("#FF6FA8")
    val COLOR_VIDEO   = Color.parseColor("#A855F7")
    val COLOR_AUDIO   = Color.parseColor("#22D4EE")
    val COLOR_DOC     = Color.parseColor("#F9A825")
    val COLOR_ARCHIVE = Color.parseColor("#FF7043")
    val COLOR_CODE    = Color.parseColor("#4CE87A")
    val COLOR_APK     = Color.parseColor("#FF8A65")
    val COLOR_UNKNOWN = Color.parseColor("#58587A")

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
