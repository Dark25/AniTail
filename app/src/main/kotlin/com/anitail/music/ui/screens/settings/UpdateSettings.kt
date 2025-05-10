package com.anitail.music.ui.screens.settings

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.edit
import androidx.navigation.NavController
import com.anitail.music.BuildConfig
import com.anitail.music.LocalPlayerAwareWindowInsets
import com.anitail.music.R
import com.anitail.music.constants.AutoUpdateCheckFrequencyKey
import com.anitail.music.constants.AutoUpdateEnabledKey
import com.anitail.music.constants.UpdateCheckFrequency
import com.anitail.music.ui.component.IconButton
import com.anitail.music.ui.component.ListDialog
import com.anitail.music.ui.component.PreferenceEntry
import com.anitail.music.ui.component.ReleaseNotesCard
import com.anitail.music.ui.component.SwitchPreference
import com.anitail.music.ui.utils.backToMain
import com.anitail.music.utils.Updater
import com.anitail.music.utils.dataStore
import com.anitail.music.utils.rememberPreference
import kotlinx.coroutines.launch


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateSettings(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    latestVersionName: String,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val autoUpdateEnabled by rememberPreference(AutoUpdateEnabledKey, true)
    val updateFrequency by rememberPreference(
        AutoUpdateCheckFrequencyKey,
        UpdateCheckFrequency.DAILY.name
    )

    // Dialog state
    val showFrequencyDialog = remember { mutableStateOf(false) }

    Column(
        Modifier
            .windowInsetsPadding(
                LocalPlayerAwareWindowInsets.current.only(
                    WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
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

        SwitchPreference(
            title = { Text(stringResource(R.string.auto_update_enabled)) },
            description = stringResource(R.string.auto_update_enabled_description),
            icon = { Icon(painterResource(R.drawable.update), null) },
            checked = autoUpdateEnabled,
            onCheckedChange = { newValue ->
                coroutineScope.launch {
                    context.dataStore.edit { preferences ->
                        preferences[AutoUpdateEnabledKey] = newValue
                    }
                }
            }
        )
        val frequency = UpdateCheckFrequency.valueOf(updateFrequency)
        PreferenceEntry(
            title = { Text(stringResource(R.string.update_check_frequency)) },
            description = when (frequency) {
                UpdateCheckFrequency.DAILY -> stringResource(R.string.daily)
                UpdateCheckFrequency.WEEKLY -> stringResource(R.string.weekly)
                UpdateCheckFrequency.MONTHLY -> stringResource(R.string.monthly)
                UpdateCheckFrequency.NEVER -> stringResource(R.string.never)
            },
            icon = { Icon(painterResource(R.drawable.clock), null) },
            onClick = { showFrequencyDialog.value = true },
            isEnabled = autoUpdateEnabled
        )

        PreferenceEntry(
            title = { Text(stringResource(R.string.check_for_updates_now)) },
            description = stringResource(R.string.current_version, BuildConfig.VERSION_NAME),
            icon = { Icon(painterResource(R.drawable.refresh), null) },
            onClick = {
                coroutineScope.launch {
                    try {
                        val result = Updater.getLatestReleaseInfo()

                        if (result.isSuccess) {
                            val releaseInfo = result.getOrThrow()
                            if (releaseInfo.versionName != BuildConfig.VERSION_NAME) {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.new_version_available_toast, releaseInfo.versionName),
                                    Toast.LENGTH_LONG
                                ).show()

                                if (autoUpdateEnabled) {
                                    val downloadId = Updater.downloadUpdate(context, releaseInfo.downloadUrl)
                                    if (downloadId != -1L) {
                                        Toast.makeText(
                                            context,
                                            R.string.downloading_update,
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            } else {
                                Toast.makeText(
                                    context,
                                    R.string.app_up_to_date,
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        } else {
                            Toast.makeText(
                                context,
                                R.string.update_check_failed,
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(
                            context,
                            R.string.update_check_failed,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        )
        if (latestVersionName != BuildConfig.VERSION_NAME) {
            PreferenceEntry(
                title = {
                    Text(
                        text = stringResource(R.string.new_version_available),
                    )
                },
                description = latestVersionName,
                icon = {
                    BadgedBox(
                        badge = { Badge() }
                    ) {
                        Icon(painterResource(R.drawable.update), null)
                    }
                },
            )
            ReleaseNotesCard()
        }
    }



// Frequency selection dialog
    if (showFrequencyDialog.value) {
        val options = UpdateCheckFrequency.entries.map {
            when (it) {
                UpdateCheckFrequency.DAILY -> context.getString(R.string.daily)
                UpdateCheckFrequency.WEEKLY -> context.getString(R.string.weekly)
                UpdateCheckFrequency.MONTHLY -> context.getString(R.string.monthly)
                UpdateCheckFrequency.NEVER -> context.getString(R.string.never)
            }
        }

        val currentIndex = UpdateCheckFrequency.entries.toTypedArray().indexOfFirst {
            it.name == updateFrequency
        }
        ListDialog(
            onDismiss = { showFrequencyDialog.value = false }
        ) {
            items(options) { option ->
                val index = options.indexOf(option)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            coroutineScope.launch {
                                context.dataStore.edit { preferences ->
                                    preferences[AutoUpdateCheckFrequencyKey] = UpdateCheckFrequency.entries[index].name
                                }
                            }
                            showFrequencyDialog.value = false
                        }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    RadioButton(
                        selected = index == currentIndex,
                        onClick = null
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(text = option)
                }
            }
        }
    }

    TopAppBar(
        title = { Text(stringResource(R.string.update_settings)) },
        navigationIcon = {
            IconButton(
                onClick = navController::navigateUp,
                onLongClick = navController::backToMain
            ) {
                Icon(
                    painterResource(R.drawable.arrow_back),
                    contentDescription = null
                )
            }
        },
        scrollBehavior = scrollBehavior
    )
}