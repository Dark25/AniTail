package com.anitail.music.installer

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.core.content.FileProvider
import timber.log.Timber
import java.io.File

class UpdateInstallerActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val fileName = intent.getStringExtra(EXTRA_FILE_NAME)
        if (fileName == null) {
            Timber.e("No file name provided to installer activity")
            finish()
            return
        }
        val file = File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), fileName)
        if (!file.exists()) {
            Timber.e("APK file not found for installation: $fileName")
            finish()
            return
        }
        val installIntent = Intent(Intent.ACTION_VIEW)
        val fileUri: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            installIntent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            FileProvider.getUriForFile(
                this,
                "${packageName}.FileProvider",
                file
            )
        } else {
            Uri.fromFile(file)
        }
        installIntent.setDataAndType(fileUri, "application/vnd.android.package-archive")
        installIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            startActivity(installIntent)
        } catch (e: Exception) {
            Timber.e(e, "Failed to start installer activity")
        }
        finish()
    }
    companion object {
        const val EXTRA_FILE_NAME = "extra_file_name"
    }
}
