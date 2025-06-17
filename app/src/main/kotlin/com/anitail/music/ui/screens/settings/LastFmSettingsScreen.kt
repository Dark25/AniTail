package com.anitail.music.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.anitail.music.R
import com.anitail.music.ui.component.IconButton
import com.anitail.music.ui.component.PreferenceEntry
import com.anitail.music.ui.component.SwitchPreference
import com.anitail.music.ui.utils.backToMain
import com.anitail.music.viewmodels.LastFmViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LastFmSettingsScreen(
    navController: NavController,
    viewModel: LastFmViewModel = hiltViewModel()
) {
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    
    var showLoginDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.loadUserInfo()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars)
    ) {
        TopAppBar(
            title = { Text(stringResource(R.string.lastfm_settings)) },
            navigationIcon = {
                IconButton(
                    onClick = navController::navigateUp,
                    onLongClick = navController::backToMain
                ) {
                    Icon(
                        painter = painterResource(R.drawable.arrow_back),
                        contentDescription = null
                    )
                }
            }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp)
        ) {
            // Account Section
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = stringResource(R.string.account),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    if (uiState.isLoggedIn && uiState.username != null) {
                        Text(
                            text = stringResource(R.string.logged_in_as_lastfm, uiState.username!!),
                            style = MaterialTheme.typography.bodyMedium
                        )
                          uiState.userInfo?.let { user ->
                            Text(
                                text = stringResource(R.string.playcount, user.playcount),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (user.realname.isNotBlank()) {
                                Text(
                                    text = user.realname,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = { showLogoutDialog = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.logout))
                        }
                    } else {
                        Text(
                            text = stringResource(R.string.not_logged_in),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = { showLoginDialog = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.login))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Scrobbling Settings
            if (uiState.isLoggedIn) {
                SwitchPreference(
                    title = { Text(stringResource(R.string.enable_scrobbling)) },
                    description = stringResource(R.string.enable_scrobbling_description),
                    icon = { Icon(painterResource(R.drawable.music_note), contentDescription = null) },
                    checked = uiState.isScrobbleEnabled,
                    onCheckedChange = { viewModel.setScrobbleEnabled(it) }
                )

                SwitchPreference(
                    title = { Text(stringResource(R.string.love_tracks)) },
                    description = stringResource(R.string.love_tracks_description),
                    icon = { Icon(painterResource(R.drawable.favorite), contentDescription = null) },
                    checked = uiState.isLoveTracksEnabled,
                    onCheckedChange = { viewModel.setLoveTracksEnabled(it) }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // About Last.fm
            PreferenceEntry(
                title = { Text(stringResource(R.string.about_lastfm)) },
                description = stringResource(R.string.about_lastfm_description),
                icon = { Icon(painterResource(R.drawable.info), contentDescription = null) },
                onClick = { /* Open Last.fm website */ }
            )
        }
    }

    // Login Dialog
    if (showLoginDialog) {
        AlertDialog(
            onDismissRequest = { 
                showLoginDialog = false 
                username = ""
                password = ""
            },
            title = { Text(stringResource(R.string.login_to_lastfm)) },
            text = {
                Column {
                    Text(
                        text = stringResource(R.string.lastfm_login_description),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text(stringResource(R.string.username)) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isLoading,
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text(stringResource(R.string.password)) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isLoading,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true
                    )

                    if (uiState.loginError != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = uiState.loginError!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (username.isNotBlank() && password.isNotBlank()) {
                            isLoading = true
                            scope.launch {
                                val success = viewModel.login(username, password)
                                isLoading = false
                                if (success) {
                                    showLoginDialog = false
                                    username = ""
                                    password = ""
                                }
                            }
                        }
                    },
                    enabled = !isLoading && username.isNotBlank() && password.isNotBlank()
                ) {
                    Text(stringResource(R.string.login))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { 
                        showLoginDialog = false 
                        username = ""
                        password = ""
                    }
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // Logout Dialog
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text(stringResource(R.string.logout)) },
            text = { Text(stringResource(R.string.logout_confirmation)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            viewModel.logout()
                            showLogoutDialog = false
                        }
                    }
                ) {
                    Text(stringResource(R.string.logout))
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}
