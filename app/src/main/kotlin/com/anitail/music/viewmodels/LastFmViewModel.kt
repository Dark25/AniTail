package com.anitail.music.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anitail.music.utils.LastFmService
import dagger.hilt.android.lifecycle.HiltViewModel
import de.umass.lastfm.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LastFmUiState(
    val isLoggedIn: Boolean = false,
    val username: String? = null,
    val userInfo: User? = null,
    val isScrobbleEnabled: Boolean = true,
    val isLoveTracksEnabled: Boolean = false,
    val loginError: String? = null,
    val isLoading: Boolean = false
)

@HiltViewModel
class LastFmViewModel @Inject constructor(
    private val lastFmService: LastFmService
) : ViewModel() {

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

            _uiState.value = _uiState.value.copy(
                isLoggedIn = isLoggedIn,
                username = username,
                isScrobbleEnabled = isScrobbleEnabled,
                isLoveTracksEnabled = isLoveTracksEnabled
            )
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
        _uiState.value = _uiState.value.copy(
            isLoading = true,
            loginError = null
        )

        return try {
            val result = lastFmService.authenticate(username, password)
            result.onSuccess {
                _uiState.value = _uiState.value.copy(
                    isLoggedIn = true,
                    username = username,
                    isLoading = false,
                    loginError = null
                )
                loadUserInfo()
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    loginError = error.message ?: "Login failed"
                )
            }
            result.isSuccess
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                loginError = e.message ?: "Login failed"
            )
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
    }    fun setLoveTracksEnabled(enabled: Boolean) {
        viewModelScope.launch {
            lastFmService.enableLoveTracks(enabled)
            _uiState.value = _uiState.value.copy(isLoveTracksEnabled = enabled)
        }
    }    fun loveTrack(song: com.anitail.music.db.entities.Song) {
        lastFmService.loveTrack(song)
    }

    fun unloveTrack(song: com.anitail.music.db.entities.Song) {
        lastFmService.unloveTrack(song)
    }
}
