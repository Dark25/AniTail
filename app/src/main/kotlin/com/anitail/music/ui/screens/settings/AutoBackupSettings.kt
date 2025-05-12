package com.anitail.music.ui.screens.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Environment.getExternalStoragePublicDirectory
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement.Center
import androidx.compose.foundation.layout.Arrangement.SpaceBetween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CardDefaults.cardColors
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.ripple
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
import androidx.compose.ui.text.font.FontWeight.Companion.SemiBold
import androidx.compose.ui.text.style.TextOverflow.Companion.Ellipsis
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.edit
import androidx.navigation.NavController
import com.anitail.music.BuildConfig
import com.anitail.music.LocalPlayerAwareWindowInsets
import com.anitail.music.R
import com.anitail.music.constants.AutoBackupCustomLocationKey
import com.anitail.music.constants.AutoBackupEnabledKey
import com.anitail.music.constants.AutoBackupFrequencyKey
import com.anitail.music.constants.AutoBackupKeepCountKey
import com.anitail.music.constants.AutoBackupUseCustomLocationKey
import com.anitail.music.constants.BackupFrequency
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

@RequiresApi(Build.VERSION_CODES.M)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoBackupSettings(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Auto backup preferences
    val (autoBackupEnabled, _) = rememberPreference(AutoBackupEnabledKey, defaultValue = false)

    val (backupFrequency, _) =
        rememberPreference(AutoBackupFrequencyKey, defaultValue = BackupFrequency.DAILY.hours)

    val (backupCount, _) = rememberPreference(AutoBackupKeepCountKey, defaultValue = 5)

    val (useCustomLocation, _) =
        rememberPreference(AutoBackupUseCustomLocationKey, defaultValue = false)

    val (customLocation, _) = rememberPreference(AutoBackupCustomLocationKey, defaultValue = "")

    // State for frequency selection dialog
    var showFrequencyDialog by remember { mutableStateOf(false) }

    // State to track if a manual backup is in progress
    var isManualBackupInProgress by remember { mutableStateOf(false) }

    // File picker for custom location
    val directoryPicker =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocumentTree(),
            onResult = { uri ->
                if (uri != null) {
                    try {
                        // Get persist permissions for the directory
                        val flag =
                            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
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
                        )
                            .show()
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to get permissions for the selected directory")
                        Toast.makeText(
                            context,
                            context.getString(R.string.error_unknown),
                            Toast.LENGTH_SHORT
                        )
                            .show()
                    }
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
            modifier =
                Modifier.padding(paddingValues)
                    .windowInsetsPadding(
                        LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Bottom)
                    )
                    .verticalScroll(rememberScrollState())
        ) {
            Spacer(
                Modifier.windowInsetsPadding(
                    LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Top)
                )
            )

            PreferenceGroupTitle(title = stringResource(R.string.auto_backup))

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
                                    context.getString(R.string.auto_backup) + "" + context.getString(
                                        R.string.enabled
                                    )
                                else context.getString(R.string.auto_backup) + "" + context.getString(
                                    R.string.disabled
                                ),
                                Toast.LENGTH_SHORT
                            )
                                .show()
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
                PreferenceGroupTitle(title = stringResource(R.string.backups_to_keep))

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

                //                // Custom location settings
                //                PreferenceGroupTitle(
                //                    title = stringResource(R.string.backup_custom_location)
                //                )
                //
                //                SwitchPreference(
                //                    title = {
                // Text(stringResource(R.string.auto_backup_custom_location)) },
                //                    description =
                // stringResource(R.string.auto_backup_custom_location_desc),
                //                    icon = { Icon(painterResource(R.drawable.storage), null) },
                //                    checked = useCustomLocation,
                //                    onCheckedChange = { enabled ->
                //                        coroutineScope.launch {
                //                            context.dataStore.edit { preferences ->
                //                                preferences[AutoBackupUseCustomLocationKey] =
                // enabled
                //                            }
                //                        }
                //                    }
                //                )
                //
                //                if (useCustomLocation) {
                //                    PreferenceEntry(
                //                        title = { Text(stringResource(R.string.select_folder)) },
                //                        description = if (customLocation.isEmpty())
                //                            stringResource(R.string.no_folder_selected)
                //                        else
                //                            customLocation.toUri().path ?: customLocation,
                //                        icon = { Icon(painterResource(R.drawable.storage), null)
                // },
                //                        onClick = { directoryPicker.launch(null) }
                //                    )
                //                }
            }

            HorizontalDivider(Modifier.padding(vertical = 16.dp))

            PreferenceEntry(
                title = { Text(stringResource(R.string.backups_location_info)) },
                icon = { Icon(painterResource(R.drawable.info), null) }
            )

            var isBackupInProgress by remember { mutableStateOf(false) }

            if (BuildConfig.DEBUG) {
                PreferenceEntry(
                    title = { Text(stringResource(R.string.create_backup_now)) },
                    icon = { Icon(painterResource(R.drawable.backup), null) },
                    onClick = {
                        if (!isBackupInProgress && autoBackupEnabled) {
                            isBackupInProgress = true
                            coroutineScope.launch {
                                try {
                                    // Schedule immediate backup
                                    AutoBackupWorker.scheduleImmediateBackup(context)

                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.backup_scheduled),
                                        Toast.LENGTH_SHORT
                                    )
                                        .show()
                                } catch (e: Exception) {
                                    Timber.e(e, "Failed to start backup")
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.backup_failed),
                                        Toast.LENGTH_SHORT
                                    )
                                        .show()
                                } finally {
                                    isBackupInProgress = false
                                }
                            }
                        }
                    },
                    isEnabled = autoBackupEnabled && !isBackupInProgress
                )
            }

            var showDiagnosticDialog by remember { mutableStateOf(false) }
            val diagnosticState = remember {
                mutableStateOf(DiagnosticState())
            }

            PreferenceEntry(
                title = { Text(stringResource(R.string.backup_diagnostics)) },
                icon = { 
                    Icon(
                        painter = painterResource(R.drawable.info),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    ) 
                },
                onClick = {
                    showDiagnosticDialog = true
                    
                    if (diagnosticState.value.rawText.isEmpty()) {
                        coroutineScope.launch(Dispatchers.IO) {
                            withContext(Dispatchers.Main) {
                                diagnosticState.value = diagnosticState.value.copy(isLoading = true)
                            }
                            
                            val rawText = AutoBackupWorker.diagnoseBackupIssues(context)
                            
                            val sections = processDiagnosticText(rawText, context)
                            
                            withContext(Dispatchers.Main) {
                                diagnosticState.value = DiagnosticState(
                                    isLoading = false,
                                    rawText = rawText,
                                    sections = sections
                                )
                            }
                        }
                    }
                }
            )
            if (showDiagnosticDialog) {
                AlertDialog(
                    onDismissRequest = { showDiagnosticDialog = false },
                    properties = androidx.compose.ui.window.DialogProperties(
                        dismissOnBackPress = true,
                        dismissOnClickOutside = true,
                        decorFitsSystemWindows = false,
                        usePlatformDefaultWidth = false
                    ),
                    modifier = Modifier.padding(24.dp),
                    title = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.backup_diagnostics),
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            
                            IconButton(
                                onClick = { showDiagnosticDialog = false },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.close),
                                    contentDescription = stringResource(R.string.close),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    text = {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(300.dp)
                        ) {
                            if (diagnosticState.value.isLoading) {
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Center
                                ) {
                                    CircularProgressIndicator(
                                        strokeWidth = 3.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    
                                    Spacer(modifier = Modifier.height(16.dp))
                                    
                                    Text(
                                        text = stringResource(R.string.loading),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            } else {
                               LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                ) {
                                    if (diagnosticState.value.sections.isEmpty()) {
                                        item {
                                            Text(
                                                text = diagnosticState.value.rawText.ifEmpty { 
                                                    stringResource(R.string.no_diagnostic_data) 
                                                },
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(vertical = 8.dp)
                                            )
                                        }
                                    } else {
                                        items(diagnosticState.value.sections) { (title, content) ->
                                            DiagnosticCard(title = title, content = content)
                                        }
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        FilledTonalButton(
                            onClick = { showDiagnosticDialog = false }
                        ) {
                            Text(stringResource(R.string.ok))
                        }
                    }
                )
            }
        }
    }

    // Frequency selection dialog
    if (showFrequencyDialog) {
        val options =
            listOf(
                BackupFrequency.ONE_HOUR.getDisplayName(),
                BackupFrequency.THREE_HOURS.getDisplayName(),
                BackupFrequency.SIX_HOURS.getDisplayName(),
                BackupFrequency.DAILY.getDisplayName(),
                BackupFrequency.WEEKLY.getDisplayName(),
            )

        // Get current frequency index
        val currentIndex =
            when (backupFrequency) {
                BackupFrequency.ONE_HOUR.hours -> 0
                BackupFrequency.THREE_HOURS.hours -> 1
                BackupFrequency.SIX_HOURS.hours -> 2
                BackupFrequency.DAILY.hours -> 3
                BackupFrequency.WEEKLY.hours -> 4
                else -> 2 // Default to daily
            }

        ListDialog(onDismiss = { showFrequencyDialog = false }) {
            options.forEachIndexed { index, option ->
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier =
                            Modifier.fillMaxWidth()
                                .clickable {
                                    val newFrequency =
                                        when (index) {
                                            0 -> BackupFrequency.ONE_HOUR
                                            1 -> BackupFrequency.THREE_HOURS
                                            2 -> BackupFrequency.SIX_HOURS
                                            3 -> BackupFrequency.DAILY
                                            4 -> BackupFrequency.WEEKLY
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
                        RadioButton(selected = index == currentIndex, onClick = null)
                        Text(text = option, modifier = Modifier.padding(start = 16.dp))
                    }
                }
            }
        }
    }
}

data class DiagnosticState(
    val isLoading: Boolean = false,
    val rawText: String = "",
    val sections: List<Pair<String, String>> = emptyList()
)

@Composable
private fun DiagnosticCard(title: String, content: String) {
    var expanded by remember { mutableStateOf(false) }

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        ),
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = 2.dp
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(
                            bounded = true,
                            radius = 24.dp,
                            color = MaterialTheme.colorScheme.primary
                        ),
                    ) { 
                        expanded = !expanded 
                    }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = SemiBold,
                    maxLines = 1,
                    overflow = Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                
                Icon(
                    painter = painterResource(
                        if (expanded) R.drawable.expand_less else R.drawable.expand_more
                    ),
                    contentDescription = if (expanded) 
                        stringResource(R.string.collapse) 
                    else 
                        stringResource(R.string.expand),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn() +
                        expandVertically(),
                exit = fadeOut() +
                        shrinkVertically()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                        thickness = 0.5.dp
                    )
                    
                    Text(
                        text = content,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
    }
}

private fun processDiagnosticText(
    rawText: String, 
    context: android.content.Context
): List<Pair<String, String>> {
    if (rawText.isEmpty()) return emptyList()
    
    val sections = mutableListOf<Pair<String, String>>()
    val lines = rawText.split("\n").filter { it.isNotEmpty() }
    
    var currentTitle = ""
    var currentContent = StringBuilder()
    
    for (line in lines) {
        if (line.endsWith(":")) {
            if (currentTitle.isNotEmpty()) {
                sections.add(Pair(currentTitle, currentContent.toString().trim()))
                currentContent = StringBuilder()
            }
            currentTitle = line.removeSuffix(":")
        } else {
            currentContent.append(line).append("\n")
        }
    }
    
    if (currentTitle.isNotEmpty()) {
        sections.add(Pair(currentTitle, currentContent.toString().trim()))
    }
    
    if (sections.isEmpty()) {
        sections.add(Pair(context.getString(R.string.diagnostic_info), rawText))
    }
    
    return sections
}

private fun createBackupToDefaultLocation(
    context: android.content.Context,
    viewModel: BackupRestoreViewModel
): Boolean {
    try {
        val backupDir =
            File(
                getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "AniTail/AutoBackup"
            )

        if (!backupDir.exists()) {
            backupDir.mkdirs()
        }

        val formatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
        val timestamp = LocalDateTime.now().format(formatter)
        val backupFile = File(backupDir, "AniTail_AutoBackup_$timestamp.backup")

        viewModel.backup(context, Uri.fromFile(backupFile))

        val keepCount = context.dataStore[AutoBackupKeepCountKey, 5]
        val backupFiles =
            backupDir
                .listFiles { file -> file.isFile && file.name.endsWith(".backup") }
                ?.sortedByDescending { it.lastModified() } ?: return true

        if (backupFiles.size > keepCount) {
            for (i in keepCount until backupFiles.size) {
                backupFiles[i].delete()
            }
        }

        return true
    } catch (e: Exception) {
        Timber.e(e, "Failed to create backup to default location")
        return false
    }
}
