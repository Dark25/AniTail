package com.anitail.music.utils

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.anitail.music.BuildConfig
import com.anitail.music.R
import com.anitail.music.constants.AutoUpdateCheckFrequencyKey
import com.anitail.music.constants.AutoUpdateEnabledKey
import com.anitail.music.constants.UpdateCheckFrequency
import com.anitail.music.extensions.toEnum
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import org.json.JSONObject
import timber.log.Timber
import java.io.File


object Updater {
    private val client = HttpClient()
    var lastCheckTime = -1L
        private set
    
    private var downloadID: Long = -1
    
    data class ReleaseInfo(
        val versionName: String,
        val downloadUrl: String,
        val releaseNotes: String
    )

    suspend fun getLatestVersionName(): Result<String> =
        runCatching {
            val response =
                client.get("https://api.github.com/repos/Animetailapp/Anitail/releases/latest")
                    .bodyAsText()
            val json = JSONObject(response)
            val versionName = json.getString("name")
            lastCheckTime = System.currentTimeMillis()
            versionName
        }
        
    suspend fun getLatestReleaseInfo(): Result<ReleaseInfo> =
        runCatching {
            val response = 
                client.get("https://api.github.com/repos/Animetailapp/Anitail/releases/latest")
                    .bodyAsText()
            val json = JSONObject(response)
            val versionName = json.getString("name")
            val releaseNotes = json.getString("body")
            
            // Get universal APK download URL
            val assets = json.getJSONArray("assets")
            var downloadUrl = ""
            
            // Get proper APK download URL based on device architecture
            val arch = BuildConfig.ARCHITECTURE
            
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val name = asset.getString("name")
                
                // Match APK for current architecture or use universal as fallback
                if (name.contains("universal") || name == "AniTail.apk") {
                    downloadUrl = asset.getString("browser_download_url")
                }
                
                // If we find a match for the current architecture, prefer it
                if (arch != "universal" && name.contains(arch)) {
                    downloadUrl = asset.getString("browser_download_url")
                    break
                }
            }
            
            if (downloadUrl.isEmpty()) {
                // Fallback to direct universal APK download URL
                downloadUrl = "https://github.com/Animetailapp/Anitail/releases/latest/download/AniTail.apk"
            }
            
            lastCheckTime = System.currentTimeMillis()
            ReleaseInfo(versionName, downloadUrl, releaseNotes)
        }
        
    fun shouldCheckForUpdates(context: Context): Boolean {
        val dataStore = context.dataStore
        val lastCheck = lastCheckTime
        val frequency = dataStore[AutoUpdateCheckFrequencyKey].toEnum(UpdateCheckFrequency.DAILY)
        
        // If auto-updates are disabled, don't check
        if (!dataStore[AutoUpdateEnabledKey, true]) {
            return false
        }
        
        // Don't check if frequency is NEVER
        if (frequency == UpdateCheckFrequency.NEVER) {
            return false
        }
        
        // If we've never checked, or the time since last check exceeds the frequency, check
        return lastCheck == -1L || System.currentTimeMillis() - lastCheck > frequency.toMillis()
    }
    
    fun downloadUpdate(context: Context, downloadUrl: String): Long {
        val fileName = "AniTail_update.apk"
        val request = DownloadManager.Request(downloadUrl.toUri())
            .setTitle(context.getString(R.string.update_notification_title))
            .setDescription(context.getString(R.string.update_notification_description))
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)
        
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        downloadID = downloadManager.enqueue(request)
        
        // Register a receiver to install the update once download completes
        val onComplete = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id == downloadID) {
                    installUpdate(context, fileName)
                    context.unregisterReceiver(this)
                }
            }
        }

        ContextCompat.registerReceiver(
            context,
            onComplete,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        return downloadID
    }
    
    private fun installUpdate(context: Context, fileName: String) {
        val file = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName)
        
        if (!file.exists()) {
            Timber.e("APK file not found for installation")
            return
        }
        
        val intent = Intent(Intent.ACTION_VIEW)
        val fileUri: Uri
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            intent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            fileUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                file
            )
        } else {
            fileUri = Uri.fromFile(file)
        }
        
        intent.setDataAndType(fileUri, "application/vnd.android.package-archive")
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    }
}
