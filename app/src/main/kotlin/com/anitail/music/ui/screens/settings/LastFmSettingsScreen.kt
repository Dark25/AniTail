package com.anitail.music.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.anitail.music.R
import com.anitail.music.ui.component.IconButton
import com.anitail.music.ui.component.PreferenceEntry
import com.anitail.music.ui.component.SwitchPreference
import com.anitail.music.ui.utils.backToMain
import com.anitail.music.utils.LocalArtist
import com.anitail.music.utils.LocalTrack
import com.anitail.music.viewmodels.LastFmViewModel
import de.umass.lastfm.Artist
import de.umass.lastfm.Track
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LastFmSettingsScreen(
    navController: NavController,
    viewModel: LastFmViewModel = hiltViewModel()
) {
  val scope = rememberCoroutineScope()
  val scrollState = rememberScrollState()
  val uriHandler = LocalUriHandler.current

  val uiState by viewModel.uiState.collectAsStateWithLifecycle()

  var showLoginDialog by remember { mutableStateOf(false) }
  var showLogoutDialog by remember { mutableStateOf(false) }
  var username by remember { mutableStateOf("") }
  var password by remember { mutableStateOf("") }
  var isLoading by remember { mutableStateOf(false) }

  LaunchedEffect(Unit) {
    viewModel.loadUserInfo()
    viewModel.loadPendingScrobblesCount()
  }
  LaunchedEffect(uiState.isLoggedIn) {
    if (uiState.isLoggedIn) {
      viewModel.loadRecentTracks()
      viewModel.loadTopTracks()
      viewModel.loadTopArtists()
    }
  }

  Column(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) {
    TopAppBar(
        title = { Text(stringResource(R.string.lastfm_settings)) },
        navigationIcon = {
          IconButton(onClick = navController::navigateUp, onLongClick = navController::backToMain) {
            Icon(painter = painterResource(R.drawable.arrow_back), contentDescription = null)
          }
        })

    Column(modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(16.dp)) {
      // Account Section
      Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
          Text(
              text = stringResource(R.string.account),
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold)
          Spacer(modifier = Modifier.height(8.dp))
          if (uiState.isLoggedIn && uiState.username != null) {
            uiState.username?.let { username ->
              Row(verticalAlignment = Alignment.CenterVertically) {
                // Avatar de Last.fm
                uiState.userInfo?.imageURL?.let { imageUrl ->
                  AsyncImage(
                      model = imageUrl,
                      contentDescription = stringResource(R.string.lastfm_profile_picture),
                      modifier = Modifier.size(48.dp).clip(CircleShape),
                      placeholder = painterResource(R.drawable.person),
                      error = painterResource(R.drawable.person))
                  Spacer(modifier = Modifier.padding(8.dp))
                }

                Column {
                  Text(
                      text = stringResource(R.string.logged_in_as_lastfm, username),
                      style = MaterialTheme.typography.bodyMedium,
                      fontWeight = FontWeight.SemiBold)

                  uiState.userInfo?.let { user ->
                    Text(
                        text = stringResource(R.string.playcount, user.playcount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)

                    if (user.realname.isNotBlank()) {
                      Text(
                          text = user.realname,
                          style = MaterialTheme.typography.bodySmall,
                          color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }


                    if (user.country.isNotBlank()) {
                      Text(
                          text = stringResource(R.string.user_country, user.country),
                          style = MaterialTheme.typography.bodySmall,
                          color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    if (user.age > 0) {
                      Text(
                          text = stringResource(R.string.user_age, user.age.toString()),
                          style = MaterialTheme.typography.bodySmall,
                          color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                  }
                }
              }
            }
            Spacer(modifier = Modifier.height(16.dp))

            Button(onClick = { showLogoutDialog = true }, modifier = Modifier.fillMaxWidth()) {
              Text(stringResource(R.string.logout))
            }
          } else {
            Text(
                text = stringResource(R.string.not_logged_in),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)

            Spacer(modifier = Modifier.height(16.dp))

            Button(onClick = { showLoginDialog = true }, modifier = Modifier.fillMaxWidth()) {
              Text(stringResource(R.string.login))
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(16.dp))

      // Scrobble Progress Section
      if (uiState.isLoggedIn) {
        Card(modifier = Modifier.fillMaxWidth()) {
          Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.scrobble_progress),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold)

            Spacer(modifier = Modifier.height(8.dp))

            if (uiState.isSyncing) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.padding(8.dp))
                Text(
                    text = stringResource(R.string.syncing_scrobbles),
                    style = MaterialTheme.typography.bodyMedium)
              }

              Spacer(modifier = Modifier.height(8.dp))
              LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            } else {
              if (uiState.pendingScrobblesCount > 0) {
                Text(
                    text =
                        stringResource(R.string.pending_scrobbles, uiState.pendingScrobblesCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error)

                Spacer(modifier = Modifier.height(8.dp))

                Row {
                  Button(
                      onClick = { viewModel.retryPendingScrobbles() },
                      modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.retry_pending_scrobbles))
                      }

                  Spacer(modifier = Modifier.padding(4.dp))

                  TextButton(
                      onClick = { viewModel.clearPendingScrobbles() },
                      modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.clear_pending_scrobbles))
                      }
                }
              } else {
                Text(
                    text = stringResource(R.string.scrobbles_synced),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary)
              }
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
            onCheckedChange = { viewModel.setScrobbleEnabled(it) })

        SwitchPreference(
            title = { Text(stringResource(R.string.love_tracks)) },
            description = stringResource(R.string.love_tracks_description),
            icon = { Icon(painterResource(R.drawable.favorite), contentDescription = null) },
            checked = uiState.isLoveTracksEnabled,
            onCheckedChange = { viewModel.setLoveTracksEnabled(it) })

        // Recent Tracks Section
        Card(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp)) {
          Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.recent_tracks),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            if (uiState.recentTracks.isNotEmpty()) {
              uiState.recentTracks.forEach { track ->
                Text(text = "• ${track.artist} - ${track.name}")
              }
            } else {
              Text(
                  text = stringResource(R.string.no_recent_tracks_data),
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
          }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Top Tracks Section
        Card(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp)) {
          Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.top_tracks),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            if (uiState.topTracks.isNotEmpty()) {
              uiState.topTracks.forEachIndexed { index, track ->
                val trackName = getTrackName(track)
                val trackArtist = getTrackArtist(track)
                val trackPlaycount = getTrackPlaycount(track)
                Text(text = "${index + 1}. $trackArtist - $trackName ($trackPlaycount plays)")
              }
            } else {
              Text(
                  text = stringResource(R.string.no_top_tracks_data),
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
          }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Top Artists Section
        Card(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp)) {
          Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.top_artists),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            if (uiState.topArtists.isNotEmpty()) {
              uiState.topArtists.forEachIndexed { index, artist ->
                val artistName = getArtistName(artist)
                val artistPlaycount = getArtistPlaycount(artist)
                Text(text = "${index + 1}. $artistName ($artistPlaycount plays)")
              }
            } else {
              Text(
                  text = stringResource(R.string.no_top_artists_data),
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(16.dp))

      // About Last.fm
      PreferenceEntry(
          title = { Text(stringResource(R.string.about_lastfm)) },
          description = stringResource(R.string.about_lastfm_description),
          icon = { Icon(painterResource(R.drawable.info), contentDescription = null) },
          onClick = { uriHandler.openUri("https://www.last.fm/about") })
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
                modifier = Modifier.padding(bottom = 16.dp))

            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text(stringResource(R.string.username)) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading,
                singleLine = true)

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(stringResource(R.string.password)) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true)

            if (uiState.loginError != null) {
              Spacer(modifier = Modifier.height(8.dp))
              Text(
                  text = uiState.loginError!!,
                  color = MaterialTheme.colorScheme.error,
                  style = MaterialTheme.typography.bodySmall)
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
              enabled = !isLoading && username.isNotBlank() && password.isNotBlank()) {
                Text(stringResource(R.string.login))
              }
        },
        dismissButton = {
          TextButton(
              onClick = {
                showLoginDialog = false
                username = ""
                password = ""
              }) {
                Text(stringResource(R.string.cancel))
              }
        })
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
              }) {
                Text(stringResource(R.string.logout))
              }
        },
        dismissButton = {
          TextButton(onClick = { showLogoutDialog = false }) {
            Text(stringResource(R.string.cancel))
          }
        })
  }
}

// Helper functions to extract track/artist info from polymorphic types
private fun getTrackName(track: Any): String =
    when (track) {
      is Track -> track.name
      is LocalTrack -> track.name
      else -> "Unknown"
    }

private fun getTrackArtist(track: Any): String =
    when (track) {
      is Track -> track.artist
      is LocalTrack -> track.artist
      else -> "Unknown Artist"
    }

private fun getTrackPlaycount(track: Any): Int =
    when (track) {
      is Track -> track.playcount
      is LocalTrack -> track.playcount
      else -> 0
    }

private fun getArtistName(artist: Any): String =
    when (artist) {
      is Artist -> artist.name
      is LocalArtist -> artist.name
      else -> "Unknown"
    }

private fun getArtistPlaycount(artist: Any): Int =
    when (artist) {
      is Artist -> artist.playcount
      is LocalArtist -> artist.playcount
      else -> 0
    }
