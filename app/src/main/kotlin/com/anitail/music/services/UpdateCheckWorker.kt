package com.anitail.music.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.anitail.music.BuildConfig
import com.anitail.music.MainActivity
import com.anitail.music.R
import com.anitail.music.constants.AutoUpdateEnabledKey
import com.anitail.music.utils.Updater
import com.anitail.music.utils.dataStore
import com.anitail.music.utils.get
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.TimeUnit

class UpdateCheckWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        private const val WORK_NAME = "update_check_worker"
        private const val UPDATE_CHANNEL_ID = "update_channel"
        private const val UPDATE_NOTIFICATION_ID = 1001

        // Schedule periodic update checks
        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            // Default to daily checks
            val repeatInterval = 24L
            val repeatIntervalTimeUnit = TimeUnit.HOURS

            val updateWorkRequest = PeriodicWorkRequestBuilder<UpdateCheckWorker>(
                repeatInterval, repeatIntervalTimeUnit
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                updateWorkRequest
            )
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            // Check if auto-update is enabled
            val autoUpdateEnabled = context.dataStore[AutoUpdateEnabledKey, true]

            if (!autoUpdateEnabled || !Updater.shouldCheckForUpdates(context)) {
                return@withContext Result.success()
            }

            // Check for updates
            val latestReleaseResult = Updater.getLatestReleaseInfo()

            if (latestReleaseResult.isSuccess) {
                val releaseInfo = latestReleaseResult.getOrThrow()

                if (releaseInfo.versionName != BuildConfig.VERSION_NAME) {
                    // Create update notification
                    createUpdateNotificationChannel()
                    showUpdateNotification(releaseInfo.versionName, releaseInfo.downloadUrl)
                }
            } else {
                Timber.e(latestReleaseResult.exceptionOrNull(), "Failed to check for updates")
            }

            Result.success()
        } catch (e: Exception) {
            Timber.e(e, "Error checking for updates")
            Result.failure()
        }
    }

    private fun createUpdateNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                UPDATE_CHANNEL_ID,
                context.getString(R.string.update_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.update_channel_description)
            }

            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun showUpdateNotification(newVersion: String, downloadUrl: String) {
        // Intent to open the app
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )

        // Intent to download the update
        val downloadIntent = Intent(context, UpdateBroadcastReceiver::class.java).apply {
            action = UpdateBroadcastReceiver.ACTION_DOWNLOAD_UPDATE
            putExtra(UpdateBroadcastReceiver.EXTRA_DOWNLOAD_URL, downloadUrl)
        }
        val downloadPendingIntent = PendingIntent.getBroadcast(
            context,
            1,
            downloadIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )

        // Build notification
        val notification = NotificationCompat.Builder(context, UPDATE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_ani)
            .setContentTitle(context.getString(R.string.update_available))
            .setContentText(context.getString(R.string.new_version_available_description, newVersion))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .addAction(
                R.drawable.download,
                context.getString(R.string.download_update),
                downloadPendingIntent
            )
            .build()

        notificationManager.notify(UPDATE_NOTIFICATION_ID, notification)
    }
}