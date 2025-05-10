package com.anitail.music.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.anitail.music.R
import com.anitail.music.constants.AutoBackupCustomLocationKey
import com.anitail.music.constants.AutoBackupEnabledKey
import com.anitail.music.constants.AutoBackupFrequencyKey
import com.anitail.music.constants.AutoBackupKeepCountKey
import com.anitail.music.constants.AutoBackupUseCustomLocationKey
import com.anitail.music.constants.BackupFrequency
import com.anitail.music.db.MusicDatabase
import com.anitail.music.utils.dataStore
import com.anitail.music.utils.get
import com.anitail.music.viewmodels.BackupRestoreViewModel
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

@HiltWorker
class AutoBackupWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val database: MusicDatabase
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val AUTO_BACKUP_WORK_NAME = "auto_backup_work"
        const val BACKUP_FOLDER_NAME = "AniTail/AutoBackup"
        private const val BACKUP_CHANNEL_ID = "backup_channel"
        private const val BACKUP_NOTIFICATION_ID = 2001
        private const val FILE_PROVIDER_AUTHORITY = "com.anitail.music.fileprovider"

        // Schedule automatic backups
        fun schedule(context: Context) {
            val enabled = context.dataStore.get(AutoBackupEnabledKey, false)
            if (!enabled) {
                // Cancel any existing work if auto backup is disabled
                WorkManager.getInstance(context).cancelUniqueWork(AUTO_BACKUP_WORK_NAME)
                Timber.d("Auto backups are disabled, cancelling scheduled work")
                return
            }

            // Get frequency from preferences (default to daily)
            val frequencyHours = context.dataStore.get(AutoBackupFrequencyKey, BackupFrequency.DAILY.hours)
            val backupFrequency = BackupFrequency.fromHours(frequencyHours)
            
            // Ensure backup directory exists for default location
            val useCustomLocation = context.dataStore.get(AutoBackupUseCustomLocationKey, false)
            if (!useCustomLocation) {
                try {
                    val backupDir = File(
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                        BACKUP_FOLDER_NAME
                    )
                    if (!backupDir.exists()) {
                        backupDir.mkdirs()
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Failed to create backup directory")
                }
            }
            
            val constraints = Constraints.Builder()
                .setRequiresDeviceIdle(false)
                .setRequiresCharging(false)
                .build()

            val backupWorkRequest = PeriodicWorkRequestBuilder<AutoBackupWorker>(
                backupFrequency.hours.toLong(), TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                AUTO_BACKUP_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE, // Update existing work if it exists
                backupWorkRequest
            )
            
            Timber.d("Scheduled auto backups every ${backupFrequency.getDisplayName()}")
        }
    }

    private suspend fun createNotificationChannel() {
        withContext(Dispatchers.Main) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                BACKUP_CHANNEL_ID,
                context.getString(R.string.auto_backup),
                NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(channel)
        }
    }

    override suspend fun doWork(): Result {
        try {
            // Check if auto backup is enabled
            val isEnabled = context.dataStore.get(AutoBackupEnabledKey, false)
            if (!isEnabled) {
                return Result.success()
            }

            // Check for permissions
            if (!hasStoragePermissions()) {
                Timber.e("Storage permissions are not granted")
                showNotification(
                    title = context.getString(R.string.backup_create_failed),
                    message = context.getString(R.string.storage_permissions_required)
                )
                return Result.failure()
            }

            createNotificationChannel()
            
            // Check if we should use custom location
            val useCustomLocation = context.dataStore[AutoBackupUseCustomLocationKey, false]
            val customLocationUri = context.dataStore[AutoBackupCustomLocationKey, ""]
            
            return if (useCustomLocation && customLocationUri.isNotEmpty()) {
                createBackupToCustomLocation(customLocationUri.toUri())
            } else {
                createBackupToInternalStorage()
            }
        } catch (e: Exception) {
            Timber.e(e, "Auto backup failed")
            showNotification(
                title = context.getString(R.string.backup_create_failed),
                message = e.localizedMessage ?:"Error creating backup"
            )
            return Result.failure()
        }
    }

    private fun hasStoragePermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // For Android 11 and above, we need MANAGE_EXTERNAL_STORAGE permission
            context.checkSelfPermission(android.Manifest.permission.MANAGE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        } else {
            // For Android 10 and below, we need WRITE_EXTERNAL_STORAGE permission
            context.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }

    private suspend fun createBackupToInternalStorage(): Result = withContext(Dispatchers.IO) {
        // Create backup folder in Downloads/AniTail/AutoBackup
        val backupDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "AniTail/AutoBackup"
        )
        if (!backupDir.exists()) {
            backupDir.mkdirs()
        }

        // Generate backup file name with timestamp
        val formatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
        val timestamp = LocalDateTime.now().format(formatter)
        val backupFile = File(backupDir, "AniTail_AutoBackup_$timestamp.backup")

        try {
            // Create the backup file
            createBackup(Uri.fromFile(backupFile))
            
            // Clean up old backups
            cleanupOldBackups(backupDir)
            
            // Show success notification
            showNotification(
                title = context.getString(R.string.backup_create_success),
                message = backupFile.name
            )
            
            return@withContext Result.success()
        } catch (e: Exception) {
            Timber.e(e, "Failed to create backup to internal storage")
            return@withContext Result.failure()
        }
    }
    private suspend fun createBackupToCustomLocation(customLocationUri: Uri): Result = withContext(Dispatchers.IO) {
        try {
            // Check if we still have permission for the custom location
            val hasPermission = try {
                context.contentResolver.takePersistableUriPermission(
                    customLocationUri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or 
                    android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                true
            } catch (e: SecurityException) {
                Timber.e(e, "Permission denied for custom location")
                false
            }
            
            // If we don't have permission, fall back to internal storage
            if (!hasPermission) {
                Timber.w("Permission denied for custom backup location, falling back to internal storage")
                showNotification(
                    title = context.getString(R.string.backup_permission_error),
                    message = context.getString(R.string.backup_fallback_to_internal)
                )
                return@withContext createBackupToInternalStorage()
            }
            
            // Create the backup file at the custom location
            createBackup(customLocationUri)
            
            // Show success notification
            showNotification(
                title = context.getString(R.string.backup_create_success),
                message = context.getString(R.string.backup_custom_location)
            )
            
            return@withContext Result.success()
        } catch (e: Exception) {
            Timber.e(e, "Failed to create backup to custom location")
            return@withContext Result.failure()
        }
    }
    
    private fun createBackup(uri: Uri) {
        // Use the BackupRestoreViewModel's backup method
        val viewModel = BackupRestoreViewModel(database)
        viewModel.backup(context, uri)
    }

    private suspend fun cleanupOldBackups(backupDir: File) {
        try {
            // Get the maximum number of backups to keep
            val keepCount = context.dataStore.get(AutoBackupKeepCountKey, 5)
            
            // List all backup files, sorted by last modified time (newest first)
            val backupFiles = backupDir.listFiles { file ->
                file.isFile && file.name.endsWith(".backup")
            }?.sortedByDescending { it.lastModified() } ?: return
            
            // Delete old backups if we have more than the keep count
            if (backupFiles.size > keepCount) {
                for (i in keepCount until backupFiles.size) {
                    backupFiles[i].delete()
                    Timber.d("Deleted old backup: ${backupFiles[i].name}")
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to clean up old backups")
        }
    }

    private suspend fun showNotification(title: String, message: String) = withContext(Dispatchers.Main) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        val notification = NotificationCompat.Builder(context, BACKUP_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(R.drawable.backup)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .build()
        
        notificationManager.notify(BACKUP_NOTIFICATION_ID, notification)
    }
}
