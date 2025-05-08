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
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.net.InetSocketAddress
import java.net.Socket
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
    
    // IP local del dispositivo cuando está en modo host
    private val _localIpAddress = MutableStateFlow("127.0.0.1")
    val localIpAddress: StateFlow<String> = _localIpAddress
    
    // Lista de hosts disponibles encontrados en la red
    private val _availableHosts = MutableStateFlow<List<HostInfo>>(emptyList())
    val availableHosts: StateFlow<List<HostInfo>> = _availableHosts
    
    // Estado de escaneo: true cuando está escaneando la red
    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning
    
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
    
    // Actualiza la dirección IP local del servidor
    fun updateLocalIpAddress(ip: String) {
        _localIpAddress.value = ip
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
    
    data class HostInfo(
        val ip: String,
        val name: String = "",
        val lastSeen: Long = System.currentTimeMillis()
    )
      /**
     * Comprueba si un host tiene un servidor JAM ejecutándose
     * @param hostIp IP del host a comprobar
     * @return true si hay un servidor JAM en ejecución
     */
    private suspend fun testHostConnection(hostIp: String): Boolean = withContext(Dispatchers.IO) {
        var socket: Socket? = null
        try {
            // Timeout corto para no bloquear demasiado el escaneo
            socket = Socket()
            socket.connect(InetSocketAddress(hostIp, 5000), 1000)
            // Si podemos conectarnos, es un host válido
            return@withContext true
        } catch (e: Exception) {
            Timber.d("Host $hostIp no disponible: ${e.message}")
            return@withContext false
        } finally {
            try {
                socket?.close()
            } catch (e: Exception) { }
        }
    }
    
    /**
     * Inicia un escaneo en la red para encontrar hosts JAM disponibles
     * Utiliza un escaneo de puertos TCP en la subred local
     */
    fun scanForHosts() {
        if (_isScanning.value) return
        
        _isScanning.value = true
        _availableHosts.value = emptyList()
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val discoveredHosts = mutableListOf<HostInfo>()
                val baseIp = _localIpAddress.value.substringBeforeLast('.') + "."
                
                // Para pruebas UI, convertir esto a un escaneo real
                val testData = listOf(
                    Triple(baseIp + "15", "Bedroom Speaker", 300L),
                    Triple(baseIp + "23", "Living Room TV", 200L),
                    Triple(baseIp + "45", "Kitchen Speaker", 400L),
                    Triple(_localIpAddress.value, "This Device", 100L)
                )
                
                // Actualizar UI con el progreso
                withContext(Dispatchers.Main) {
                    // Establecer el modo de escaneo para mostrar la interfaz de progreso
                    _isScanning.value = true
                }
                  // Decidir qué implementación usar basado en un flag de desarrollo
                val useTestData = false // Cambiar a false para usar implementación real
                val scanJobs = mutableListOf<Deferred<HostInfo?>>()
                
                if (useTestData) {
                    // Para pruebas: usar datos de prueba simulando un escaneo real
                    testData.forEach { (ip, name, delayTime) ->
                        val job = async(Dispatchers.IO) {
                            try {
                                // Simular la latencia de red
                                delay(delayTime)
                                HostInfo(ip = ip, name = name)
                            } catch (e: Exception) {
                                null
                            }
                        }
                        scanJobs.add(job)
                    }
                } else {
                    // Implementación real: escanear un rango de IPs en la subred local
                    val subnet = _localIpAddress.value.substringBeforeLast('.')
                    
                    // Incluir la IP propia
                    val ownIpJob = async(Dispatchers.IO) {
                        HostInfo(ip = _localIpAddress.value, name = "This Device")
                    }
                    scanJobs.add(ownIpJob)
                    
                    // Escanear IPs en la subred local con límite de conexiones en paralelo
                    val scanBatchSize = 15  // Número máximo de conexiones paralelas
                    
                    for (batch in 1..254 step scanBatchSize) {
                        val batchJobs = mutableListOf<Deferred<HostInfo?>>()
                        
                        for (i in batch until minOf(batch + scanBatchSize, 255)) {
                            val ip = "$subnet.$i"
                            // Evitar escanear nuestra propia IP
                            if (ip == _localIpAddress.value) continue
                            
                            val job = async(Dispatchers.IO) {
                                try {
                                    if (testHostConnection(ip)) {
                                        // Asignar un nombre genérico - en una implementación real
                                        // podríamos intentar obtener el nombre del dispositivo
                                        HostInfo(ip = ip, name = "JAM Device")
                                    } else null
                                } catch (e: Exception) {
                                    null
                                }
                            }
                            batchJobs.add(job)
                        }
                        
                        // Esperar a que termine este lote antes de iniciar el siguiente
                        batchJobs.awaitAll().filterNotNull().forEach { host ->
                            discoveredHosts.add(host)
                            // Actualizar la UI con cada host encontrado
                            withContext(Dispatchers.Main) {
                                _availableHosts.value = discoveredHosts.toList()
                            }
                        }
                    }
                }
                
                // Si usamos datos de prueba, recoger los resultados a medida que van llegando
                if (useTestData) {
                    scanJobs.forEach { job -> 
                        val result = job.await()
                        if (result != null) {
                            discoveredHosts.add(result)
                            // Actualizar la UI con cada host encontrado
                            withContext(Dispatchers.Main) {
                                _availableHosts.value = discoveredHosts.toList()
                            }
                        }
                    }
                }
                
                // Finalizar el escaneo
                withContext(Dispatchers.Main) {
                    _isScanning.value = false
                }
                
            } catch (e: Exception) {
                Timber.e(e, "Error scanning for hosts")
                withContext(Dispatchers.Main) {
                    _isScanning.value = false
                }
            }
        }
    }
      /**
     * Conecta con un host seleccionado
     */
    fun connectToHost(hostInfo: HostInfo) {
        setHostIp(hostInfo.ip)
        
        // Guardar en el historial de conexiones
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            .format(Date())
        
        val newConnection = JamConnection(
            ip = hostInfo.ip,
            connectedAt = timestamp
        )
        
        // Actualizar el historial
        val currentHistory = _connectionHistory.value.toMutableList()
        
        // Verificar si ya existe esta conexión y eliminarla (para ponerla al principio)
        currentHistory.removeIf { it.ip == hostInfo.ip }
        
        // Añadir al principio (más reciente)
        currentHistory.add(0, newConnection)
        
        // Mantener solo las últimas 10 conexiones para no saturar la lista
        val updatedHistory = if (currentHistory.size > 10) {
            currentHistory.take(10)
        } else {
            currentHistory
        }
        
        _connectionHistory.value = updatedHistory
        
        // Guardar en persistencia
        viewModelScope.launch {
            val historyStr = updatedHistory.joinToString(";") { 
                "${it.ip}|${it.connectedAt}" 
            }
            context.dataStore.edit { it[JamConnectionHistoryKey] = historyStr }
        }
    }
}
