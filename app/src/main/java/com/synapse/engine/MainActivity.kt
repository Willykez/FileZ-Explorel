package com.synapse.engine

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.Window
import android.view.WindowManager

class MainActivity : Activity() {

    private lateinit var explorer: ExplorerView

    override fun onCreate(saved: Bundle?) {
        super.onCreate(saved)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                        WindowManager.LayoutParams.FLAG_FULLSCREEN)
        window.statusBarColor     = Theme.BG_SURFACE
        window.navigationBarColor = Theme.BG_BASE

        explorer = ExplorerView(this)
        setContentView(explorer)
        checkPermissions()
    }

    override fun onResume() {
        super.onResume()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager())
            explorer.loadRoot()
    }

    private fun checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:$packageName")))
                } catch (_: Exception) {
                    startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                }
            } else { explorer.loadRoot() }
        } else {
            if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED)
                requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE), 101)
            else explorer.loadRoot()
        }
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, res: IntArray) {
        super.onRequestPermissionsResult(code, perms, res)
        if (code == 101 && res.isNotEmpty() && res[0] == PackageManager.PERMISSION_GRANTED)
            explorer.loadRoot()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (!explorer.navigateUp()) super.onBackPressed()
    }
}
