package com.anitail.music.ui.screens.settings

import android.annotation.SuppressLint
import android.app.Application
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.anitail.music.constants.JamConnectionHistoryKey
import com.anitail.music.constants.JamEnabledKey
import com.anitail.music.constants.JamHostIpKey
import com.anitail.music.constants.JamHostKey
import com.anitail.music.utils.dataStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class JamViewModel(application: Application) : AndroidViewModel(application) {
    @SuppressLint("StaticFieldLeak")
    private val context = getApplication<Application>().applicationContext

    private val _isJamEnabled = MutableStateFlow(false)
    val isJamEnabled: StateFlow<Boolean> = _isJamEnabled

    private val _isJamHost = MutableStateFlow(false)
    val isJamHost: StateFlow<Boolean> = _isJamHost

    private val _hostIp = MutableStateFlow("")
    val hostIp: StateFlow<String> = _hostIp
    
    // Nueva propiedad para las conexiones
    private val _connectionHistory = MutableStateFlow<List<JamConnection>>(emptyList())
    val connectionHistory: StateFlow<List<JamConnection>> = _connectionHistory
    
    // Si estamos en modo host, podemos obtener conexiones activas
    private val _activeConnections = MutableStateFlow<List<JamConnection>>(emptyList())
    val activeConnections: StateFlow<List<JamConnection>> = _activeConnections

    init {
        viewModelScope.launch {
            val prefs = context.dataStore.data.first()
            _isJamEnabled.value = prefs[JamEnabledKey] ?: false
            _isJamHost.value = prefs[JamHostKey] ?: false
            _hostIp.value = prefs[JamHostIpKey] ?: ""
            
            loadConnectionHistory()
        }
        
        // Observar cambios en el historial de conexiones
        viewModelScope.launch {
            context.dataStore.data
                .map { prefs -> prefs[JamConnectionHistoryKey] ?: "" }
                .collect { historyStr ->
                    if (historyStr.isNotEmpty()) {
                        val connections = historyStr.split(";")
                            .filter { it.isNotEmpty() }.mapNotNull { entry ->
                                val parts = entry.split("|")
                                if (parts.size >= 2) {
                                    JamConnection(
                                        ip = parts[0],
                                        connectedAt = parts[1]
                                    )
                                } else {
                                    null
                                }
                            }
                        _connectionHistory.value = connections
                    } else {
                        _connectionHistory.value = emptyList()
                    }
                }
        }
    }

    fun setJamEnabled(enabled: Boolean) {
        _isJamEnabled.value = enabled
        viewModelScope.launch {
            context.dataStore.edit { it[JamEnabledKey] = enabled }
            if (!enabled) {
                // Limpiar conexiones activas si se desactiva
                _activeConnections.value = emptyList()
            }
        }
    }

    fun setJamHost(isHost: Boolean) {
        _isJamHost.value = isHost
        viewModelScope.launch {
            context.dataStore.edit { it[JamHostKey] = isHost }
            if (!isHost) {
                // Limpiar conexiones activas si ya no somos host
                _activeConnections.value = emptyList()
            }
        }
    }

    fun setHostIp(ip: String) {
        _hostIp.value = ip
        viewModelScope.launch {
            context.dataStore.edit { it[JamHostIpKey] = ip }
        }
    }
    
    // Nueva función para cargar el historial de conexiones
    private suspend fun loadConnectionHistory() {
        val historyStr = context.dataStore.data.first()[JamConnectionHistoryKey] ?: ""
        if (historyStr.isNotEmpty()) {
            val connections = historyStr.split(";")
                .filter { it.isNotEmpty() }.mapNotNull { entry ->
                    val parts = entry.split("|")
                    if (parts.size >= 2) {
                        JamConnection(
                            ip = parts[0],
                            connectedAt = parts[1]
                        )
                    } else {
                        null
                    }
                }
            _connectionHistory.value = connections
        }
    }
    
    // Para actualizar conexiones activas desde el servicio
    fun updateActiveConnections(activeClients: List<Pair<String, Long>>) {
        _activeConnections.value = activeClients.map { (ip, timestamp) ->
            JamConnection(
                ip = ip,
                connectedAt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    .format(Date(timestamp))
            )
        }
    }
    
    // Función para limpiar el historial de conexiones
    fun clearConnectionHistory() {
        viewModelScope.launch {
            context.dataStore.edit { prefs ->
                prefs[JamConnectionHistoryKey] = ""
            }
            _connectionHistory.value = emptyList()
        }
    }
    
    data class JamConnection(
        val ip: String,
        val connectedAt: String
    )
}
