package com.anitail.music.utils

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketException
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.min

/**
 * Cliente para conectar con un servidor LanJam
 * Gestiona conexiones de red para sincronización de reproducción entre dispositivos
 */
class LanJamClient(val host: String, private val port: Int = 5000, private val onMessage: (String) -> Unit) {
    private var socket: Socket? = null
    private var writer: PrintWriter? = null
    private var running = false
    private var reconnectJob: kotlinx.coroutines.Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + kotlinx.coroutines.SupervisorJob())
    private var connectionAttempts = 0
    private val receivingMessages = AtomicBoolean(false)
    private val maxReconnectRetries = 20
    private val maxReconnectDelayMs = 60000L  // 1 minuto máximo entre reintentos

    /**
     * Inicia la conexión al servidor
     * @param initialRetryDelayMs Tiempo inicial entre reintentos (3 segundos por defecto)
     */
    fun connect(initialRetryDelayMs: Long = 3000) {
        if (running) {
            Timber.tag("LanJamClient").d("Cliente ya está en ejecución, ignorando solicitud de conexión")
            return
        }

        running = true
        connectionAttempts = 0
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            while (running && connectionAttempts < maxReconnectRetries) {
                try {
                    // Crear un nuevo socket para cada intento de conexión
                    val newSocket = Socket()
                    
                    synchronized(this@LanJamClient) {
                        socket = newSocket
                    }
                    
                    // Configurar opciones de socket
                    try {
                        newSocket.tcpNoDelay = true
                        newSocket.keepAlive = true
                        newSocket.reuseAddress = true
                    } catch (e: Exception) {
                        Timber.tag("LanJamClient").w(e, "No se pudieron establecer opciones avanzadas del socket")
                    }
                    
                    // Usar connect con timeout en lugar de constructor para mejor manejo de errores
                    val socketAddress = InetSocketAddress(host, port)
                    Timber.tag("LanJamClient").d("Intentando conectar a $host:$port (intento #${connectionAttempts + 1})...")
                    
                    try {
                        // Tiempo de conexión de 15 segundos
                        newSocket.connect(socketAddress, 15000)
                    } catch (e: Exception) {
                        throw ConnectException("Error conectando a $host:$port - ${e.message}")
                    }
                    
                    // Configurar timeout para operaciones de lectura
                    try {
                        newSocket.soTimeout = 30000 // 30 segundos de timeout para lectura
                    } catch (e: Exception) {
                        Timber.tag("LanJamClient").w(e, "No se pudo establecer timeout de lectura")
                    }
                    
                    // Reiniciar contador de intentos cuando se establece la conexión
                    connectionAttempts = 0
                    Timber.tag("LanJamClient").d("Conexión establecida con $host:$port")
                    
                    val outputStream = newSocket.getOutputStream()
                    writer = PrintWriter(outputStream, true)
                    val reader = BufferedReader(InputStreamReader(newSocket.getInputStream()))
                    
                    // Enviar mensaje inicial para registrarse con el servidor
                    try {
                        writer?.println("REGISTER_CLIENT")
                        writer?.flush()
                    } catch (e: Exception) {
                        Timber.tag("LanJamClient").e(e, "Error enviando mensaje de registro")
                        throw e
                    }
                    
                    receivingMessages.set(true)
                    var line: String? = null
                    try {
                        // Bucle de lectura
                        while (running && socket?.isConnected == true && !socket?.isClosed!! && 
                               reader.readLine().also { line = it } != null) {
                            if (line != null) {
                                val displayLine = if (line!!.length > 50) "${line!!.take(50)}..." else line
                                Timber.tag("LanJamClient").d("Mensaje recibido: $displayLine")
                                withContext(Dispatchers.Main) {
                                    onMessage(line!!)
                                }
                            }
                        }
                    } catch (e: SocketTimeoutException) {
                        // Los timeouts de lectura son esperados con soTimeout, no son errores fatales
                        Timber.tag("LanJamClient").d("Timeout de lectura, es normal con soTimeout configurado")
                        continue
                    } catch (readEx: Exception) {
                        if (running) {
                            Timber.tag("LanJamClient").e(readEx, "Error leyendo del socket: ${readEx.message}")
                            // No lanzamos la excepción para que el bucle principal continúe y reintente
                        }
                    } finally {
                        receivingMessages.set(false)
                    }
                    
                    Timber.tag("LanJamClient").d("Conexión con $host:$port cerrada")
                } catch (e: Exception) {
                    // Diferenciar entre tipos de excepciones para mejor diagnóstico
                    when {
                        e is SocketTimeoutException || 
                        e.message?.contains("ETIMEDOUT") == true || 
                        e.message?.contains("timed out") == true -> {
                            Timber.tag("LanJamClient").e("Timeout al conectar a $host:$port")
                        }
                        e is SocketException || 
                        e.message?.contains("ECONNREFUSED") == true -> {
                            Timber.tag("LanJamClient").e("Conexión rechazada en $host:$port - ¿El servidor está activo?")
                        }
                        e is ConnectException -> {
                            Timber.tag("LanJamClient").e("Error de conexión con $host:$port: ${e.message}")
                        }
                        e.message?.contains("Network is unreachable") == true -> {
                            Timber.tag("LanJamClient").e("Red inalcanzable para $host:$port - Comprueba la conectividad WiFi")
                        }
                        else -> {
                            Timber.tag("LanJamClient").e(e, "Error al comunicarse con $host:$port: ${e.message}")
                        }
                    }
                } finally {
                    // Limpieza adecuada de recursos
                    synchronized(this@LanJamClient) {
                        try { 
                            writer?.close()
                            socket?.close() 
                        } catch (_: Exception) {}
                        writer = null
                        socket = null
                    }
                }
                
                // Implementar backoff exponencial para evitar reconexiones agresivas
                if (running) {
                    connectionAttempts++
                    if (connectionAttempts >= maxReconnectRetries) {
                        Timber.tag("LanJamClient").w("Se alcanzó el máximo de intentos de reconexión ($maxReconnectRetries)")
                        running = false
                        break
                    }
                    
                    val calculatedDelay = initialRetryDelayMs * (1L shl min(connectionAttempts, 6)) 
                    val nextRetryDelay = min(calculatedDelay, maxReconnectDelayMs)
                    
                    Timber.tag("LanJamClient").d("Reintentando conexión en ${nextRetryDelay}ms (intento #$connectionAttempts)")
                    try {
                        delay(nextRetryDelay)
                    } catch (_: Exception) {}
                }
            }
            
            if (!running) {
                Timber.tag("LanJamClient").d("Cliente detenido manualmente")
            } else if (connectionAttempts >= maxReconnectRetries) {
                Timber.tag("LanJamClient").w("Abandonando reconexión después de $maxReconnectRetries intentos")
                running = false
            }
        }
    }

    /**
     * Envía un mensaje al servidor
     * @param message El mensaje a enviar
     */
    fun send(message: String) {
        if (writer == null || socket?.isConnected != true || socket?.isClosed == true) {
            Timber.tag("LanJamClient").w("No se puede enviar mensaje - no hay conexión activa")
            return
        }
        
        scope.launch(Dispatchers.IO) {
            try {
                synchronized(this@LanJamClient) {
                    val localWriter = writer
                    if (localWriter != null && !socket!!.isClosed) {
                        localWriter.println(message)
                        localWriter.flush()
                        
                        if (localWriter.checkError()) {
                            Timber.tag("LanJamClient").e("Error detectado al enviar mensaje")
                            throw IOException("Error de escritura en PrintWriter")
                        }
                        
                        Timber.tag("LanJamClient").d("Mensaje enviado al servidor: ${message.take(50)}...")
                    } else {
                        Timber.tag("LanJamClient").w("No se pudo enviar mensaje - conexión cerrada inesperadamente")
                    }
                }
            } catch (e: Exception) {
                Timber.tag("LanJamClient").e(e, "Error al enviar mensaje al servidor")
                
                // Intentar reconectar si hay error al enviar
                synchronized(this@LanJamClient) {
                    socket?.let {
                        if (!it.isClosed) {
                            try { it.close() } catch (_: Exception) {}
                        }
                    }
                }
                
                // Si no estamos ya en proceso de reconexión, reintentarla
                if (running && !receivingMessages.get()) {
                    Timber.tag("LanJamClient").d("Reintentando conexión tras error de envío...")
                    connect()
                }
            }
        }
    }

    /**
     * Desconecta el cliente del servidor
     */
    fun disconnect() {
        Timber.tag("LanJamClient").d("Desconectando cliente LanJam...")
        running = false
        reconnectJob?.cancel()
        
        // Usar scope para garantizar la ejecución asíncrona segura de la desconexión
        scope.launch(Dispatchers.IO) {
            synchronized(this@LanJamClient) {
                try {
                    writer?.close()
                    socket?.close()
                } catch (e: Exception) {
                    Timber.tag("LanJamClient").e(e, "Error al cerrar el socket del cliente")
                } finally {
                    writer = null
                    socket = null
                    Timber.tag("LanJamClient").d("Cliente LanJam desconectado")
                }
            }
        }
    }
    
    /**
     * Comprueba si el cliente está conectado al servidor
     * @return true si la conexión está activa
     */
    fun isConnected(): Boolean {
        synchronized(this@LanJamClient) {
            val s = socket
            return s != null && s.isConnected && !s.isClosed
        }
    }
    
    // Clase interna de excepción para errores de IO
    private class IOException(message: String) : Exception(message)
}
