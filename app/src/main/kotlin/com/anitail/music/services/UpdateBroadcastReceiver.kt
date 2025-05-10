package com.anitail.music.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.anitail.music.R
import com.anitail.music.utils.Updater
import timber.log.Timber

class UpdateBroadcastReceiver : BroadcastReceiver() {
    
    companion object {
        const val ACTION_DOWNLOAD_UPDATE = "com.anitail.music.action.DOWNLOAD_UPDATE"
        const val EXTRA_DOWNLOAD_URL = "downloadUrl"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_DOWNLOAD_UPDATE -> {
                val downloadUrl = intent.getStringExtra(EXTRA_DOWNLOAD_URL)
                if (!downloadUrl.isNullOrEmpty()) {
                    downloadUpdate(context, downloadUrl)
                } else {
                    Timber.e("Download URL is null or empty")
                    Toast.makeText(context, R.string.update_error, Toast.LENGTH_SHORT).show()
                }
            }
            Intent.ACTION_BOOT_COMPLETED -> {
                // Ensure the update check worker is scheduled after device boot
                UpdateCheckWorker.schedule(context)
            }
        }
    }
    
    private fun downloadUpdate(context: Context, downloadUrl: String) {
        try {
            val downloadId = Updater.downloadUpdate(context, downloadUrl)
            if (downloadId != -1L) {
                Toast.makeText(context, R.string.downloading_update, Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to download update")
            Toast.makeText(context, R.string.update_download_failed, Toast.LENGTH_SHORT).show()
        }
    }
}
