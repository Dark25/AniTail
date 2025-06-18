package com.anitail.music.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anitail.music.utils.LastFmService
import dagger.hilt.android.lifecycle.HiltViewModel
import de.umass.lastfm.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class LastFmViewModel @Inject constructor(private val lastFmService: LastFmService) : ViewModel() {

  private val _uiState = MutableStateFlow(LastFmUiState())
  val uiState = _uiState.asStateFlow()

  init {
    loadInitialState()
  }

  private fun loadInitialState() {
    viewModelScope.launch {
      val isLoggedIn = lastFmService.isEnabled()
      val username = lastFmService.getUsername()
      val isScrobbleEnabled = lastFmService.isScrobbleEnabled()
      val isLoveTracksEnabled = lastFmService.isLoveTracksEnabled()
      val showLastFmAvatar = lastFmService.isShowAvatarEnabled()

      _uiState.value =
          _uiState.value.copy(
              isLoggedIn = isLoggedIn,
              username = username,
              isScrobbleEnabled = isScrobbleEnabled,
              isLoveTracksEnabled = isLoveTracksEnabled,
              showLastFmAvatar = showLastFmAvatar)

      if (isLoggedIn) {
        loadPendingScrobblesCount()
        loadUserInfo()
      }
    }
  }

  fun loadUserInfo() {
    if (!_uiState.value.isLoggedIn) return

    viewModelScope.launch {
      lastFmService.getUserInfo().onSuccess { user ->
        _uiState.value = _uiState.value.copy(userInfo = user)
      }
    }
  }

  suspend fun login(username: String, password: String): Boolean {
    _uiState.value = _uiState.value.copy(isLoading = true, loginError = null)

    return try {
      val result = lastFmService.authenticate(username, password)
      result
          .onSuccess {
            _uiState.value =
                _uiState.value.copy(
                    isLoggedIn = true, username = username, isLoading = false, loginError = null)
            loadUserInfo()
          }
          .onFailure { error ->
            _uiState.value =
                _uiState.value.copy(isLoading = false, loginError = error.message ?: "Login failed")
          }
      result.isSuccess
    } catch (e: Exception) {
      _uiState.value =
          _uiState.value.copy(isLoading = false, loginError = e.message ?: "Login failed")
      false
    }
  }

  suspend fun logout() {
    lastFmService.logout()
    _uiState.value = LastFmUiState()
  }

  fun setScrobbleEnabled(enabled: Boolean) {
    viewModelScope.launch {
      lastFmService.enableScrobbling(enabled)
      _uiState.value = _uiState.value.copy(isScrobbleEnabled = enabled)
    }
  }

  fun setLoveTracksEnabled(enabled: Boolean) {
    viewModelScope.launch {
      lastFmService.enableLoveTracks(enabled)
      _uiState.value = _uiState.value.copy(isLoveTracksEnabled = enabled)
    }
  }

  fun loveTrack(song: com.anitail.music.db.entities.Song) {
    lastFmService.loveTrack(song)
  }

  fun unloveTrack(song: com.anitail.music.db.entities.Song) {
    lastFmService.unloveTrack(song)
  }

  fun loadPendingScrobblesCount() {
    viewModelScope.launch {
      val count = lastFmService.getPendingScrobblesCount()
      _uiState.value = _uiState.value.copy(pendingScrobblesCount = count)
    }
  }

  fun loadRecentTracks() {
    if (!_uiState.value.isLoggedIn) return

    viewModelScope.launch {
      lastFmService
          .getRecentTracks(10)
          .onSuccess { tracks ->
            Timber.d("Loaded ${tracks.size} recent tracks")
            _uiState.value = _uiState.value.copy(recentTracks = tracks)
          }
          .onFailure { error -> Timber.e(error, "Failed to load recent tracks") }
    }
  }

  fun loadTopTracks() {
    if (!_uiState.value.isLoggedIn) return

    viewModelScope.launch {
      lastFmService
          .getTopTracks(limit = 10)
          .onSuccess { tracks ->
            if (tracks.isNotEmpty()) {
              Timber.d("Loaded ${tracks.size} top tracks")
              _uiState.value = _uiState.value.copy(topTracks = tracks)
            } else {
              Timber.i("No top tracks data available")
              _uiState.value = _uiState.value.copy(topTracks = emptyList())
            }
          }
          .onFailure { error ->
            Timber.e(error, "Failed to load top tracks")
            _uiState.value = _uiState.value.copy(topTracks = emptyList())
          }
    }
  }

  fun loadTopArtists() {
    if (!_uiState.value.isLoggedIn) return

    viewModelScope.launch {
      lastFmService
          .getTopArtists(limit = 10)
          .onSuccess { artists ->
            if (artists.isNotEmpty()) {
              Timber.d("Loaded ${artists.size} top artists")
              _uiState.value = _uiState.value.copy(topArtists = artists)
            } else {
              Timber.i("No top artists data available")
              _uiState.value = _uiState.value.copy(topArtists = emptyList())
            }
          }
          .onFailure { error ->
            Timber.e(error, "Failed to load top artists")
            _uiState.value = _uiState.value.copy(topArtists = emptyList())
          }
    }
  }

  fun retryPendingScrobbles() {
    viewModelScope.launch {
      _uiState.value = _uiState.value.copy(isSyncing = true)
      lastFmService.retryPendingScrobbles()
      // Esperar un poco y actualizar el conteo
      kotlinx.coroutines.delay(2000)
      loadPendingScrobblesCount()
      _uiState.value = _uiState.value.copy(isSyncing = false)
    }
  }

  fun clearPendingScrobbles() {
    viewModelScope.launch {
      lastFmService.clearAllPendingScrobbles()
      _uiState.value = _uiState.value.copy(pendingScrobblesCount = 0)
    }
  }

  fun setShowLastFmAvatar(show: Boolean) {
    viewModelScope.launch {
      lastFmService.enableShowAvatar(show)
      _uiState.value = _uiState.value.copy(showLastFmAvatar = show)
    }
  }
}

data class LastFmUiState(
    val isLoggedIn: Boolean = false,
    val username: String? = null,
    val userInfo: User? = null,
    val isScrobbleEnabled: Boolean = true,
    val isLoveTracksEnabled: Boolean = false,
    val loginError: String? = null,
    val isLoading: Boolean = false,
    val pendingScrobblesCount: Int = 0,
    val showLastFmAvatar: Boolean = false,
    val isSyncing: Boolean = false,
    val recentTracks: List<de.umass.lastfm.Track> = emptyList(),
    val topTracks: List<Any> = emptyList(), // TrackType is a sealed interface
    val topArtists: List<Any> = emptyList() // ArtistType is a sealed interface
)
