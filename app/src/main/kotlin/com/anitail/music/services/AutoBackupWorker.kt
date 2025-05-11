package com.anitail.music.services

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.hilt.work.HiltWorker
import androidx.hilt.work.WorkerAssistedFactory
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
import dagger.assisted.AssistedFactory
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

    // Factory for creating AutoBackupWorker instances with Hilt
    @SuppressLint("RestrictedApi")
    @AssistedFactory
    interface Factory : WorkerAssistedFactory<AutoBackupWorker> {
        override fun create(
            appContext: Context,
            params: WorkerParameters
        ): AutoBackupWorker
    }
    
    companion object {
        const val AUTO_BACKUP_WORK_NAME = "auto_backup_work"
        const val BACKUP_FOLDER_NAME = "AniTail/AutoBackup"
        private const val BACKUP_CHANNEL_ID = "backup_channel"
        private const val BACKUP_NOTIFICATION_ID = 2001
        private const val FILE_PROVIDER_AUTHORITY = "com.anitail.music.fileprovider"

        // Check if storage permissions are granted 
        private fun hasStoragePermission(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Environment.isExternalStorageManager()
            } else {
                val readPermission = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.READ_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
                val writePermission = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
                readPermission && writePermission
            }
        }        /**
         * Schedule automatic backups. Will intelligently decide whether an immediate backup
         * should be performed based on when the last backup was made.
         */
        @RequiresApi(Build.VERSION_CODES.M)
        fun schedule(context: Context) {
            // The schedulePeriodicOnly method now intelligently determines
            // if an immediate backup is needed based on the last backup time
            schedulePeriodicOnly(context)
        }
          /**
         * Schedule only periodic backups without running an immediate backup
         * Will check if a backup should be performed now based on when the last backup was made
         */
        @RequiresApi(Build.VERSION_CODES.M)
        fun schedulePeriodicOnly(context: Context): Boolean {
            val enabled = context.dataStore[AutoBackupEnabledKey, false]
            if (!enabled) {
                // Cancel any existing work if auto backup is disabled
                WorkManager.getInstance(context).cancelUniqueWork(AUTO_BACKUP_WORK_NAME)
                Timber.d("Auto backups are disabled, cancelling scheduled work")
                return false
            }
            
            // Check for storage permissions
            if (!hasStoragePermission(context)) {
                Timber.w("Storage permissions not granted, cannot schedule auto backup")
                return false
            }
            
            // Get frequency from preferences (default to daily)
            val frequencyHours = context.dataStore.get(AutoBackupFrequencyKey, BackupFrequency.DAILY.hours)
            val backupFrequency = BackupFrequency.fromHours(frequencyHours)

            // Ensure backup directory exists for default location
            val useCustomLocation = context.dataStore.get(AutoBackupUseCustomLocationKey, false)
            var backupDir: File? = null
            
            if (!useCustomLocation) {
                try {
                    backupDir = File(
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                        BACKUP_FOLDER_NAME
                    )
                    if (!backupDir.exists()) {
                        backupDir.mkdirs()
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Failed to create backup directory")
                    return false
                }
            }            // Check if we should run an immediate backup based on when the last backup was made
            var shouldRunImmediateBackup = false
            var lastBackupTime: Long = 0
            var initialDelayMinutes: Long = 0
            
            // Check for existing backups and get the timestamp of the most recent one
            if (backupDir != null && backupDir.exists()) {
                val backupFiles = backupDir.listFiles { file ->
                    file.isFile && file.name.endsWith(".backup")
                }?.sortedByDescending { it.lastModified() }
                
                if (backupFiles.isNullOrEmpty()) {
                    // No backups found, we should run an immediate backup
                    Timber.d("No existing backup files found, should run immediate backup")
                    shouldRunImmediateBackup = true
                } else {
                    // Found backups, check when the last one was made
                    val lastBackupFile = backupFiles.first()
                    lastBackupTime = lastBackupFile.lastModified()
                    
                    val currentTime = System.currentTimeMillis()
                    val elapsedMillis = currentTime - lastBackupTime
                    val elapsedHours = elapsedMillis / (1000 * 60 * 60)
                    
                    Timber.d("Last backup was $elapsedHours hours ago (${java.util.Date(lastBackupTime)})")
                    
                    // Calculate when the next backup should run based on the frequency setting
                    val backupIntervalMillis = backupFrequency.hours * 60 * 60 * 1000L
                    val nextScheduledBackupTime = lastBackupTime + backupIntervalMillis
                    
                    if (nextScheduledBackupTime <= currentTime) {
                        // Next scheduled backup is in the past, run immediate backup
                        Timber.d("Next scheduled backup time has passed, should run immediate backup")
                        shouldRunImmediateBackup = true
                    } else {
                        // Calculate initial delay for next backup in minutes
                        val delayMillis = nextScheduledBackupTime - currentTime
                        initialDelayMinutes = delayMillis / (60 * 1000)
                        
                        Timber.d("Next backup scheduled for ${java.util.Date(nextScheduledBackupTime)}, in $initialDelayMinutes minutes")
                        Timber.d("Last backup is recent enough, no need for immediate backup")
                    }
                }
            } else if (useCustomLocation) {
                // For custom location, we can't easily check the last backup time
                // We'll rely only on the periodic schedule
                Timber.d("Using custom backup location, will rely on periodic schedule only")
            } else {
                // No backup directory found, likely first run
                Timber.d("No backup directory found, should run immediate backup")
                shouldRunImmediateBackup = true
            }
              // Set up periodic backups
            val constraints = Constraints.Builder()
                .setRequiresDeviceIdle(false)
                .setRequiresCharging(false)
                .build()
                
            // Create work request with calculated initial delay
            val backupWorkRequestBuilder = PeriodicWorkRequestBuilder<AutoBackupWorker>(
                backupFrequency.hours.toLong(), TimeUnit.HOURS
            ).setConstraints(constraints)
            
            // Apply calculated initial delay if available
            if (initialDelayMinutes > 0 && !shouldRunImmediateBackup) {
                backupWorkRequestBuilder.setInitialDelay(initialDelayMinutes, TimeUnit.MINUTES)
                Timber.d("Setting initial delay of $initialDelayMinutes minutes for periodic backup")
            }
            
            val backupWorkRequest = backupWorkRequestBuilder.build()

            try {
                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    AUTO_BACKUP_WORK_NAME,
                    ExistingPeriodicWorkPolicy.REPLACE, // Replace existing work to ensure new settings are applied
                    backupWorkRequest
                )
                
                // Log detailed scheduling information
                if (initialDelayMinutes > 0 && !shouldRunImmediateBackup) {
                    Timber.d("Scheduled auto backups every ${backupFrequency.getDisplayName()} (${backupFrequency.hours} hours), first backup in $initialDelayMinutes minutes")
                } else {
                    Timber.d("Scheduled auto backups every ${backupFrequency.getDisplayName()} (${backupFrequency.hours} hours)")
                  }
                
                // If needed, schedule an immediate backup
                if (shouldRunImmediateBackup) {
                    Timber.d("Also scheduling an immediate backup")
                    scheduleImmediateBackup(context)
                }
                
                return true
            } catch (e: Exception) {
                Timber.e(e, "Failed to schedule periodic backups: ${e.message}")
                return false
            }
        }
          /**
         * Schedule a one-time immediate backup
         */
          @RequiresApi(Build.VERSION_CODES.M)
          fun scheduleImmediateBackup(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiresDeviceIdle(false)
                .setRequiresCharging(false)
                .build()
                
            val oneTimeWorkRequest = androidx.work.OneTimeWorkRequestBuilder<AutoBackupWorker>()
                .setConstraints(constraints)
                // Add a small delay to avoid system ignoring the request
                .setInitialDelay(30, TimeUnit.SECONDS)
                .addTag("immediate_backup")
                .build()
                
            WorkManager.getInstance(context).enqueue(oneTimeWorkRequest)
            Timber.d("Scheduled immediate one-time backup (will run in 30 seconds)")
        }
        
        /**
         * Diagnose potential issues with auto backup
         * This can be called from settings to help troubleshoot backup issues
         */
        fun diagnoseBackupIssues(context: Context): String {
            val sb = StringBuilder()
            
            // Check if backups are enabled
            val enabled = context.dataStore[AutoBackupEnabledKey, false]
            sb.append("Auto backup enabled: $enabled\n")
            
            if (!enabled) {
                return sb.append("Auto backup is disabled. Enable it in settings.").toString()
            }
            
            // Check storage permissions
            val hasPermission = hasStoragePermission(context)
            sb.append("Storage permissions granted: $hasPermission\n")
            
            if (!hasPermission) {
                sb.append("Storage permissions required for backup to work.\n")
            }
            
            // Check backup settings
            val frequency = context.dataStore[AutoBackupFrequencyKey, BackupFrequency.DAILY.hours]
            val backupFrequency = BackupFrequency.fromHours(frequency)
            sb.append("Backup frequency: ${backupFrequency.getDisplayName()}\n")
            
            val keepCount = context.dataStore[AutoBackupKeepCountKey, 5]
            sb.append("Backups to keep: $keepCount\n")
            
            val useCustomLocation = context.dataStore[AutoBackupUseCustomLocationKey, false]
            sb.append("Use custom location: $useCustomLocation\n")
            
            if (useCustomLocation) {
                val customLocation = context.dataStore[AutoBackupCustomLocationKey, ""]
                sb.append("Custom location: $customLocation\n")
                
                if (customLocation.isEmpty()) {
                    sb.append("ERROR: Custom location enabled but no location selected\n")
                } else {
                    try {
                        val uri = customLocation.toUri()
                        val hasAccess = try {
                            context.contentResolver.takePersistableUriPermission(
                                uri,
                                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                        android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                            )
                            true
                        } catch (_: Exception) {
                            false
                        }
                        sb.append("Has access to custom location: $hasAccess\n")
                    } catch (_: Exception) {
                        sb.append("ERROR: Invalid custom location URI\n")
                    }
                }
            } else {
                // Check default location
                val backupDir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    BACKUP_FOLDER_NAME
                )
                sb.append("Default backup directory: ${backupDir.absolutePath}\n")
                sb.append("Directory exists: ${backupDir.exists()}\n")
                sb.append("Directory is writable: ${backupDir.canWrite()}\n")
                
                if (backupDir.exists()) {
                    val files = backupDir.listFiles()?.filter { it.name.endsWith(".backup") } ?: emptyList()
                    sb.append("Existing backup files: ${files.size}\n")
                      if (files.isNotEmpty()) {
                        val lastBackup = files.maxByOrNull { it.lastModified() }
                        if (lastBackup != null) {
                            val lastBackupTime = lastBackup.lastModified()
                            val date = java.util.Date(lastBackupTime)
                            sb.append("Last backup: ${date}\n")
                            
                            // Calculate time until next backup
                            val currentTime = System.currentTimeMillis()
                            val backupIntervalMillis = backupFrequency.hours * 60 * 60 * 1000L
                            val nextScheduledBackupTime = lastBackupTime + backupIntervalMillis
                            
                            if (nextScheduledBackupTime > currentTime) {
                                val timeUntilNextBackup = nextScheduledBackupTime - currentTime
                                val hoursUntil = timeUntilNextBackup / (1000 * 60 * 60)
                                val minutesUntil = (timeUntilNextBackup % (1000 * 60 * 60)) / (1000 * 60)
                                
                                sb.append("Next backup scheduled for: ${java.util.Date(nextScheduledBackupTime)}\n")
                                sb.append("Time until next backup: ${hoursUntil}h ${minutesUntil}m\n")
                            } else {
                                sb.append("Next backup is due now (overdue since ${java.util.Date(nextScheduledBackupTime)})\n")
                            }
                        }
                    }
                }
            }
              // Check work manager status
            val workManager = WorkManager.getInstance(context)
            sb.append("WorkManager initialized: ${true}\n")
            
            // Get status of scheduled work
            try {
                val workInfoLiveData = workManager.getWorkInfosForUniqueWorkLiveData(AUTO_BACKUP_WORK_NAME)
                val workInfos = workInfoLiveData.value
                if (workInfos != null && workInfos.isNotEmpty()) {
                    val workInfo = workInfos.firstOrNull()
                    sb.append("Scheduled work status: ${workInfo?.state}\n")
                    
                    // If we have a running work, get its progress
                    when (workInfo?.state) {
                        androidx.work.WorkInfo.State.RUNNING -> {
                            sb.append("Work is currently RUNNING\n")
                        }
                        androidx.work.WorkInfo.State.ENQUEUED -> {
                            sb.append("Work is ENQUEUED and waiting to run\n")
                        }
                        androidx.work.WorkInfo.State.SUCCEEDED -> {
                            sb.append("Last work SUCCEEDED\n")
                        }
                        androidx.work.WorkInfo.State.FAILED -> {
                            sb.append("Last work FAILED\n")
                        }
                        androidx.work.WorkInfo.State.CANCELLED -> {
                            sb.append("Last work was CANCELLED\n")
                        }
                        else -> {
                            sb.append("Work is in state: ${workInfo?.state}\n")
                        }
                    }
                    
                    // Get scheduled time if available
                    val triggerTime = workInfo?.nextScheduleTimeMillis
                    if (triggerTime != null && triggerTime > 0) {
                        val date = java.util.Date(triggerTime)
                        sb.append("Next scheduled run: $date\n")
                    }
                } else {
                    sb.append("No scheduled work found. Auto backup may not be properly scheduled.\n")
                }
            } catch (e: Exception) {
                sb.append("Error checking work status: ${e.message}\n")
            }
            
            return sb.toString()
        }
    }
    
    override suspend fun doWork(): Result {
        try {
            // Create notification channel
            createNotificationChannel()

            Timber.d("Starting auto backup... Time: ${java.util.Date()}")
            
            // Check if auto backup is enabled in settings
            val enabled = context.dataStore[AutoBackupEnabledKey, false]
            if (!enabled) {
                Timber.w("Auto backup is disabled in settings, cancelling work")
                return Result.success() // Return success to avoid retries
            }
            
            // Check storage permissions first
            if (!hasStoragePermission(context)) {
                Timber.e("Storage permissions not granted, cannot perform backup")
                showNotification(
                    title = context.getString(R.string.backup_permission_error),
                    message = context.getString(R.string.storage_permissions_required)
                )
                return Result.failure()
            }
            
            // Check if auto backup should use custom location
            val useCustomLocation = context.dataStore[AutoBackupUseCustomLocationKey, false]
            val customLocationUri = context.dataStore[AutoBackupCustomLocationKey, ""]
            
            // Execute backup based on location settings
            return if (useCustomLocation && customLocationUri.isNotEmpty()) {
                try {
                    createBackupToCustomLocation(customLocationUri.toUri())
                } catch (e: Exception) {
                    Timber.e(e, "Failed to create backup to custom location")
                    // Fallback to internal storage
                    showNotification(
                        title = context.getString(R.string.backup_permission_error),
                        message = context.getString(R.string.backup_fallback_to_internal)
                    )
                    createBackupToInternalStorage()
                }
            } else {
                createBackupToInternalStorage()
            }
        } catch (e: Exception) {
            // Catch all exceptions to ensure the worker doesn't crash
            Timber.e(e, "Failed to perform auto backup: ${e.message}")
            showNotification(
                title = context.getString(R.string.backup_create_failed),
                message = "Unexpected error: ${e.message ?: "Unknown error"}"
            )
            return Result.failure()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = context.getString(R.string.auto_backup)
            val descriptionText = context.getString(R.string.auto_backup_settings_desc)
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(BACKUP_CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private suspend fun createBackupToInternalStorage(): Result = withContext(Dispatchers.IO) {
        try {
            // Create backup directory: Downloads/AniTail/AutoBackup
            val backupDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                BACKUP_FOLDER_NAME
            )

            // Ensure the directory exists
            if (!backupDir.exists()) {
                val dirCreated = backupDir.mkdirs()
                if (!dirCreated) {
                    Timber.e("Failed to create backup directory")
                    return@withContext Result.failure()
                }
            }

            // Verify we have write access
            if (!backupDir.canWrite()) {
                Timber.e("No write permission for backup directory")
                showNotification(
                    title = context.getString(R.string.backup_permission_error),
                    message = context.getString(R.string.storage_permissions_required)
                )
                return@withContext Result.failure()
            }            // Generate backup file name with timestamp
            val formatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
            val timestamp = LocalDateTime.now().format(formatter)
            val backupFile = File(backupDir, "AniTail_AutoBackup_$timestamp.backup")
            
            // Create the backup file
            createBackup(Uri.fromFile(backupFile))

            // Verify backup file was created successfully
            if (!backupFile.exists() || backupFile.length() == 0L) {
                Timber.e("Backup file wasn't created or is empty")
                showNotification(
                    title = context.getString(R.string.backup_create_failed),
                    message = "File creation failed"
                )
                return@withContext Result.retry()
            }

            // Clean up old backups
            cleanupOldBackups(backupDir)

            // Show success notification
            showNotification(
                title = context.getString(R.string.backup_create_success),
                message = backupFile.name
            )

            Timber.d("Auto backup completed successfully")
            return@withContext Result.success()
        } catch (e: Exception) {
            Timber.e(e, "Failed to create backup to internal storage: ${e.message}")
            showNotification(
                title = context.getString(R.string.backup_create_failed),
                message = e.message ?: "Unknown error"
            )
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

            // Generate backup filename with timestamp
            val formatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
            val timestamp = LocalDateTime.now().format(formatter)
            val backupFileName = "AniTail_AutoBackup_$timestamp.backup"

            // Create a document URI for the new file
            val documentUri = DocumentsContract.createDocument(
                context.contentResolver,
                customLocationUri,
                "application/octet-stream",
                backupFileName
            )

            if (documentUri == null) {
                Timber.e("Failed to create backup file in custom location")
                // Fallback to default location
                return@withContext createBackupToInternalStorage()
            }

            // Create the backup file at the custom location
            createBackup(documentUri)

            // Show success notification
            showNotification(
                title = context.getString(R.string.backup_create_success),
                message = context.getString(R.string.backup_custom_location)
            )

            return@withContext Result.success()
        } catch (e: Exception) {
            Timber.e(e, "Failed to create backup to custom location: ${e.message}")
            return@withContext Result.failure()
        }
    }
    private fun createBackup(uri: Uri) {
        // Use the BackupRestoreViewModel's backup method, but don't show Toast
        // since we're in a background thread without a Looper
        val viewModel = BackupRestoreViewModel(database)
        viewModel.backup(context, uri, showToast = false)
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
