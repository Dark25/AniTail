package com.anitail.music.ui.screens.settings

import android.net.Uri
import android.os.Environment
import android.os.Environment.getExternalStoragePublicDirectory
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.datastore.preferences.core.edit
import androidx.navigation.NavController
import com.anitail.music.LocalPlayerAwareWindowInsets
import com.anitail.music.R
import com.anitail.music.constants.AutoBackupCustomLocationKey
import com.anitail.music.constants.AutoBackupEnabledKey
import com.anitail.music.constants.AutoBackupFrequencyKey
import com.anitail.music.constants.AutoBackupKeepCountKey
import com.anitail.music.constants.AutoBackupUseCustomLocationKey
import com.anitail.music.constants.BackupFrequency
import com.anitail.music.db.InternalDatabase
import com.anitail.music.services.AutoBackupWorker
import com.anitail.music.ui.component.BackupCountSlider
import com.anitail.music.ui.component.IconButton
import com.anitail.music.ui.component.ListDialog
import com.anitail.music.ui.component.PreferenceEntry
import com.anitail.music.ui.component.PreferenceGroupTitle
import com.anitail.music.ui.component.SwitchPreference
import com.anitail.music.ui.utils.backToMain
import com.anitail.music.utils.dataStore
import com.anitail.music.utils.get
import com.anitail.music.utils.rememberPreference
import com.anitail.music.viewmodels.BackupRestoreViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoBackupSettings(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    // Auto backup preferences
    val (autoBackupEnabled, _) = rememberPreference(
        AutoBackupEnabledKey, 
        defaultValue = false
    )
    
    val (backupFrequency, _) = rememberPreference(
        AutoBackupFrequencyKey,
        defaultValue = BackupFrequency.DAILY.hours
    )
    
    val (backupCount, _) = rememberPreference(
        AutoBackupKeepCountKey,
        defaultValue = 5
    )
    
    val (useCustomLocation, _) = rememberPreference(
        AutoBackupUseCustomLocationKey,
        defaultValue = false
    )
    
    val (customLocation, _) = rememberPreference(
        AutoBackupCustomLocationKey,
        defaultValue = ""
    )
    
    // State for frequency selection dialog
    var showFrequencyDialog by remember { mutableStateOf(false) }
    
    // State to track if a manual backup is in progress
    var isManualBackupInProgress by remember { mutableStateOf(false) }
    
    // File picker for custom location
    val directoryPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
        onResult = { uri ->
            if (uri != null) {
                // Get persist permissions for the directory
                val flag = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or 
                          android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, flag)
                
                // Save the location
                coroutineScope.launch {
                    context.dataStore.edit { preferences ->
                        preferences[AutoBackupCustomLocationKey] = uri.toString()
                    }
                }
                
                // Show toast confirmation
                Toast.makeText(
                    context,
                    context.getString(R.string.backup_custom_location),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.auto_backup_settings)) },
                navigationIcon = {
                    IconButton(
                        onClick = navController::navigateUp,
                        onLongClick = navController::backToMain,
                    ) {
                        Icon(
                            painterResource(R.drawable.arrow_back),
                            contentDescription = null,
                        )
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .windowInsetsPadding(
                    LocalPlayerAwareWindowInsets.current.only(
                        WindowInsetsSides.Bottom
                    )
                )
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(
                Modifier.windowInsetsPadding(
                    LocalPlayerAwareWindowInsets.current.only(
                        WindowInsetsSides.Top
                    )
                )
            )
        
        PreferenceGroupTitle(
            title = stringResource(R.string.auto_backup)
        )
          // Enable auto backups
        SwitchPreference(
            title = { Text(stringResource(R.string.auto_backup)) },
            description = stringResource(R.string.auto_backup_settings_desc),
            icon = { Icon(painterResource(R.drawable.backup), null) },
            checked = autoBackupEnabled,
            onCheckedChange = { enabled ->
                coroutineScope.launch {
                    context.dataStore.edit { preferences ->
                        preferences[AutoBackupEnabledKey] = enabled
                    }
                    
                    // Schedule or cancel backups based on the setting
                    AutoBackupWorker.schedule(context)
                    
                    // Show toast confirmation
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            context,
                            if (enabled) 
                                context.getString(R.string.auto_backup) + " enabled"
                            else 
                                context.getString(R.string.auto_backup) + " disabled",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        )
        
        // Frequency selection (only enabled if auto backup is enabled)
        val currentFrequency = BackupFrequency.fromHours(backupFrequency)
        PreferenceEntry(
            title = { Text(stringResource(R.string.backup_frequency)) },
            description = currentFrequency.getDisplayName(),
            icon = { Icon(painterResource(R.drawable.sync), null) },
            onClick = { showFrequencyDialog = true },
            isEnabled = autoBackupEnabled
        )
        
        // Number of backups to keep
        if (autoBackupEnabled) {
            PreferenceGroupTitle(
                title = stringResource(R.string.backups_to_keep)
            )
            
            BackupCountSlider(
                value = backupCount.toFloat(),
                onValueChange = { newValue ->
                    coroutineScope.launch {
                        context.dataStore.edit { preferences ->
                            preferences[AutoBackupKeepCountKey] = newValue.toInt()
                        }
                    }
                },
                valueRange = 1f..20f,
                steps = 19
            )
            
            // Custom location settings
            PreferenceGroupTitle(
                title = stringResource(R.string.backup_custom_location)
            )
            
            SwitchPreference(
                title = { Text(stringResource(R.string.auto_backup_custom_location)) },
                description = stringResource(R.string.auto_backup_custom_location_desc),
                icon = { Icon(painterResource(R.drawable.storage), null) },
                checked = useCustomLocation,
                onCheckedChange = { enabled ->
                    coroutineScope.launch {
                        context.dataStore.edit { preferences ->
                            preferences[AutoBackupUseCustomLocationKey] = enabled
                        }
                    }
                }
            )
            
            if (useCustomLocation) {
                PreferenceEntry(
                    title = { Text(stringResource(R.string.select_folder)) },
                    description = if (customLocation.isEmpty()) 
                        stringResource(R.string.no_folder_selected) 
                    else
                        customLocation.toUri().path ?: customLocation,
                    icon = { Icon(painterResource(R.drawable.storage), null) },
                    onClick = { directoryPicker.launch(null) }
                )
            }

            HorizontalDivider(Modifier.padding(vertical = 16.dp))

            // Manual backup button
            PreferenceEntry(
                title = { Text(stringResource(R.string.run_backup_now)) },
                description = stringResource(R.string.run_backup_now_desc),
                icon = { Icon(painterResource(R.drawable.backup), null) },
                onClick = {
                    if (!isManualBackupInProgress) {
                        isManualBackupInProgress = true
                        coroutineScope.launch {
                            // Run the backup manually
                            val result = runManualBackup(context)

                            // Show toast based on result
                            withContext(Dispatchers.Main) {
                                Toast.makeText(
                                    context,
                                    if (result)
                                        context.getString(R.string.backup_create_success)
                                    else
                                        context.getString(R.string.backup_create_failed),
                                    Toast.LENGTH_SHORT
                                ).show()

                                isManualBackupInProgress = false
                            }
                        }
                    }
                },
                isEnabled = !isManualBackupInProgress
            )

            HorizontalDivider(Modifier.padding(vertical = 16.dp))

            PreferenceEntry(
                title = { Text(stringResource(R.string.backups_location_info)) },
                icon = { Icon(painterResource(R.drawable.info), null) }
            )
        }
    }
    
    // Frequency selection dialog
    if (showFrequencyDialog) {
        val options = listOf(
            BackupFrequency.THREE_HOURS.getDisplayName(),
            BackupFrequency.SIX_HOURS.getDisplayName(),
            BackupFrequency.DAILY.getDisplayName(),
            BackupFrequency.WEEKLY.getDisplayName(),
        )
        
        // Get current frequency index
        val currentIndex = when (backupFrequency) {
            BackupFrequency.THREE_HOURS.hours -> 0
            BackupFrequency.SIX_HOURS.hours -> 1
            BackupFrequency.DAILY.hours -> 2
            BackupFrequency.WEEKLY.hours -> 3
            else -> 2 // Default to daily
        }
          ListDialog(onDismiss = { showFrequencyDialog = false }) {
            options.forEachIndexed { index, option ->
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val newFrequency = when (index) {
                                    0 -> BackupFrequency.THREE_HOURS
                                    1 -> BackupFrequency.SIX_HOURS
                                    2 -> BackupFrequency.DAILY
                                    3 -> BackupFrequency.WEEKLY
                                    else -> BackupFrequency.DAILY
                                }
                                
                                coroutineScope.launch {
                                    context.dataStore.edit { preferences ->
                                        preferences[AutoBackupFrequencyKey] = newFrequency.hours
                                    }
                                    
                                    // Update the scheduled backup task with the new frequency
                                    AutoBackupWorker.schedule(context)
                                }
                                showFrequencyDialog = false
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        RadioButton(
                            selected = index == currentIndex,
                            onClick = null
                        )
                        Text(
                            text = option,
                            modifier = Modifier.padding(start = 16.dp)
                        )
                    }

                }
            }
          }
    }
    }
}

// Function to manually run the backup
private fun runManualBackup(context: android.content.Context): Boolean {
    return try {
        // Use direct dependency injection to create BackupRestoreViewModel
        val db = InternalDatabase.newInstance(context)
        val viewModel = BackupRestoreViewModel(db)
        
        // Check backup settings
        val useCustomLocation = context.dataStore[AutoBackupUseCustomLocationKey, false]
        val customLocationUri = context.dataStore[AutoBackupCustomLocationKey, ""]
        
        // Create backup file
        if (useCustomLocation && customLocationUri.isNotEmpty()) {
            // Use custom location
            viewModel.backup(context, customLocationUri.toUri())
        } else {
            // Use default location: Downloads/AniTail/AutoBackup
            val backupDir = java.io.File(
                getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS
                ),
                "AniTail/AutoBackup"
            )
            
            if (!backupDir.exists()) {
                backupDir.mkdirs()
            }
            
            // Generate backup file name with timestamp
            val formatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
            val timestamp = LocalDateTime.now().format(formatter)
            val backupFile = File(backupDir, "AniTail_AutoBackup_$timestamp.backup")
            
            // Create the backup
            viewModel.backup(context, Uri.fromFile(backupFile))
            
            // Clean up old backups according to settings
            val keepCount = context.dataStore[AutoBackupKeepCountKey, 5]
            val backupFiles = backupDir.listFiles { file ->
                file.isFile && file.name.endsWith(".backup")
            }?.sortedByDescending { it.lastModified() } ?: return true
            
            // Delete old backups if we have more than the keep count
            if (backupFiles.size > keepCount) {
                for (i in keepCount until backupFiles.size) {
                    backupFiles[i].delete()
                }
            }
        }
        
        true
    } catch (e: Exception) {
        Timber.e(e, "Error running manual backup")
        false
    }
}