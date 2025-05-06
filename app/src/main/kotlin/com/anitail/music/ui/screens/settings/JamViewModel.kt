package com.anitail.music.ui.screens.settings

import android.app.Application
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.anitail.music.constants.JamEnabledKey
import com.anitail.music.constants.JamHostIpKey
import com.anitail.music.constants.JamHostKey
import com.anitail.music.utils.dataStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class JamViewModel(application: Application) : AndroidViewModel(application) {
    private val context = getApplication<Application>().applicationContext

    private val _isJamEnabled = MutableStateFlow(false)
    val isJamEnabled: StateFlow<Boolean> = _isJamEnabled

    private val _isJamHost = MutableStateFlow(false)
    val isJamHost: StateFlow<Boolean> = _isJamHost

    private val _hostIp = MutableStateFlow("")
    val hostIp: StateFlow<String> = _hostIp

    init {
        viewModelScope.launch {
            val prefs = context.dataStore.data.first()
            _isJamEnabled.value = prefs[JamEnabledKey] ?: false
            _isJamHost.value = prefs[JamHostKey] ?: false
            _hostIp.value = prefs[JamHostIpKey] ?: ""
        }
    }

    fun setJamEnabled(enabled: Boolean) {
        _isJamEnabled.value = enabled
        viewModelScope.launch {
            context.dataStore.edit { it[JamEnabledKey] = enabled }
        }
    }

    fun setJamHost(isHost: Boolean) {
        _isJamHost.value = isHost
        viewModelScope.launch {
            context.dataStore.edit { it[JamHostKey] = isHost }
        }
    }

    fun setHostIp(ip: String) {
        _hostIp.value = ip
        viewModelScope.launch {
            context.dataStore.edit { it[JamHostIpKey] = ip }
        }
    }
}