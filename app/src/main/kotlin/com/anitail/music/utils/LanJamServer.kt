package com.anitail.music.utils

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.SocketTimeoutException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

class LanJamServer(
    private val port: Int = 5000,
    private val onMessage: (String) -> Unit,
    private val onClientConnected: (String, String) -> Unit = { _, _ -> }  // IP, timestamp
) {
    private var serverSocket: ServerSocket? = null
    private val isRunning = AtomicBoolean(false)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // Mantiene un seguimiento de los clientes conectados
    private val activeConnections = ConcurrentHashMap<String, Long>()
    
    val clientList: List<ClientInfo>
        get() = activeConnections.map { 
            ClientInfo(
                ip = it.key,
                connectedAt = it.value
            ) 
        }

    fun start() {
        if (isRunning.get()) {
            Timber.tag("LanJamServer").d("El servidor ya está en ejecución")
            return
        }

        scope.launch {
            try {
                // Crear un nuevo socket solo si el anterior está cerrado o es null
                if (serverSocket?.isClosed != false) {
                    serverSocket = ServerSocket()
                    // Permitir reusar la dirección IP/puerto
                    serverSocket?.reuseAddress = true
                    // Enlazar a todas las interfaces de red
                    serverSocket?.bind(InetSocketAddress(port))
                }

                isRunning.set(true)
                val localIp = getLocalIpAddress()
                Timber.tag("LanJamServer").d("Servidor iniciado en $localIp:$port (esperando conexiones)")

                while (isRunning.get() && !serverSocket!!.isClosed) {
                    try {
                        Timber.tag("LanJamServer").d("Esperando conexión de cliente en $localIp:$port ...")
                        val clientSocket = serverSocket?.accept()
                        if (clientSocket == null) {
                            Timber.tag("LanJamServer").d("accept() devolvió null, posiblemente el socket está cerrado")
                            break
                        }

                        // Configurar opciones de socket para cliente aceptado
                        try {
                            clientSocket.tcpNoDelay = true
                            clientSocket.keepAlive = true
                            clientSocket.soTimeout = 30000 // 30 segundos timeout
                        } catch (e: Exception) {
                            Timber.tag("LanJamServer").w("No se pudieron establecer opciones avanzadas del socket: ${e.message}")
                        }

                        val clientIp = clientSocket.inetAddress.hostAddress ?: "desconocido"
                        val timestamp = System.currentTimeMillis()

                        // Registrar la conexión
                        activeConnections[clientIp] = timestamp
                        
                        // Almacenar el socket del cliente para comunicaciones futuras
                        clientSockets[clientIp] = clientSocket

                        // Notificar sobre la conexión
                        val formattedTime = SimpleDateFormat(
                            "yyyy-MM-dd HH:mm:ss",
                            Locale.getDefault()
                        ).format(Date(timestamp))

                        Timber.tag("LanJamServer").d("Cliente conectado: $clientIp a las $formattedTime")
                        onClientConnected(clientIp, formattedTime)

                        handleClient(clientSocket)
                    } catch (e: SocketTimeoutException) {
                        Timber.tag("LanJamServer").d("Timeout esperando conexión de cliente")
                    } catch (e: SocketException) {
                        if (!isRunning.get()) {
                            Timber.tag("LanJamServer").d("Servidor detenido mientras esperaba conexiones")
                        } else {
                            Timber.tag("LanJamServer").e("Error de socket aceptando conexión: ${e.message}")
                            e.printStackTrace()

                            // Reintentar creando un nuevo socket si hay un error que no sea por detener el servidor
                            try {
                                serverSocket?.close()
                                serverSocket = ServerSocket()
                                serverSocket?.reuseAddress = true
                                serverSocket?.bind(InetSocketAddress(port))
                                Timber.tag("LanJamServer").d("Socket recreado después de error")
                            } catch (re: Exception) {
                                Timber.tag("LanJamServer").e("No se pudo recrear el socket: ${re.message}")
                                isRunning.set(false)
                                break
                            }
                        }
                    } catch (e: Exception) {
                        if (!isRunning.get()) {
                            Timber.tag("LanJamServer").d("Servidor detenido mientras esperaba conexiones")
                        } else {
                            Timber.tag("LanJamServer").e("Error aceptando conexión: ${e.message}")
                            e.printStackTrace()

                            // Reintentar creando un nuevo socket si hay un error que no sea por detener el servidor
                            try {
                                serverSocket?.close()
                                serverSocket = ServerSocket()
                                serverSocket?.reuseAddress = true
                                serverSocket?.bind(InetSocketAddress(port))
                                Timber.tag("LanJamServer").d("Socket recreado después de error")
                            } catch (re: Exception) {
                                Timber.tag("LanJamServer").e("No se pudo recrear el socket: ${re.message}")
                                isRunning.set(false)
                                break
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.tag("LanJamServer").e("Error iniciando servidor: ${e.message}")
                e.printStackTrace()
            } finally {
                // Asegurar que el socket se cierre en caso de error
                try {
                    serverSocket?.close()
                } catch (e: Exception) {
                    Timber.tag("LanJamServer").e("Error cerrando socket del servidor: ${e.message}")
                }
                isRunning.set(false)
            }
        }
    }

    private fun handleClient(socket: Socket) {
        scope.launch {
            try {
                val reader = socket.getInputStream().bufferedReader()
                val writer = socket.getOutputStream().bufferedWriter()
                val clientIp = socket.inetAddress.hostAddress ?: "desconocido"
                
                // Guardar el socket para enviar mensajes directamente
                clientSockets[clientIp] = socket
                
                while (isRunning.get() && !socket.isClosed) {
                    val message = try {
                        reader.readLine()
                    } catch (e: SocketTimeoutException) {
                        // Timeout de lectura, es normal en sockets con soTimeout
                        continue
                    } catch (e: Exception) {
                        Timber.tag("LanJamServer").e("Error leyendo mensaje de $clientIp: ${e.message}")
                        null
                    }
                    
                    if (message == null) {
                        Timber.tag("LanJamServer").d("Cliente $clientIp desconectado (fin de stream)")
                        break
                    }
                    
                    // Si el cliente envía un mensaje de registro, es una conexión inicial
                    if (message == "REGISTER_CLIENT") {
                        Timber.tag("LanJamServer").d("Cliente $clientIp registrado correctamente")
                        continue
                    }
                    
                    // Si es un ping, respondemos inmediatamente con un pong
                    if (message == "PING") {
                        try {
                            withContext(Dispatchers.IO) {
                                writer.write("PONG")
                                writer.newLine()
                                writer.flush()
                            }
                            Timber.tag("LanJamServer").d("PONG enviado a $clientIp")
                        } catch (e: Exception) {
                            Timber.tag("LanJamServer").e("Error enviando PONG a $clientIp: ${e.message}")
                        }
                        continue
                    }
                    
                    Timber.tag("LanJamServer").d("Mensaje recibido de $clientIp: ${message.take(50)}...")
                    onMessage(message)
                }
            } catch (e: Exception) {
                val clientIp = socket.inetAddress.hostAddress ?: "desconocido"
                if (isRunning.get()) {
                    Timber.tag("LanJamServer").e("Error con cliente $clientIp: ${e.message}")
                } else {
                    Timber.tag("LanJamServer").d("Cliente $clientIp desconectado durante cierre del servidor")
                }
            } finally {
                try {
                    if (!socket.isClosed) {
                        socket.close()
                    }
                    // Eliminar de conexiones activas cuando se desconecta
                    socket.inetAddress.hostAddress?.let { ip ->
                        activeConnections.remove(ip)
                        clientSockets.remove(ip)
                        Timber.tag("LanJamServer").d("Cliente $ip eliminado de conexiones activas")
                    }
                } catch (e: Exception) {
                    Timber.tag("LanJamServer").e("Error cerrando socket cliente: ${e.message}")
                }
            }
        }
    }

    // Mapa para almacenar sockets activos por IP
    private val clientSockets = ConcurrentHashMap<String, Socket>()
    
    fun send(message: String) {
        if (!isRunning.get()) {
            Timber.tag("LanJamServer").d("No se puede enviar mensaje, el servidor no está en ejecución")
            return
        }
        
        scope.launch {
            // Copiar la lista de clientes para evitar modificaciones concurrentes
            val clientsSnapshot = ArrayList(activeConnections.keys)
            
            if (clientsSnapshot.isEmpty()) {
                Timber.tag("LanJamServer").d("No hay clientes conectados para enviar mensaje")
                return@launch
            }
            
            Timber.tag("LanJamServer").d("Enviando mensaje a ${clientsSnapshot.size} clientes")
            
            for (clientIp in clientsSnapshot) {
                try {
                    // Primero intentar usar un socket existente si está disponible
                    val existingSocket = clientSockets[clientIp]
                    if (existingSocket != null && !existingSocket.isClosed && existingSocket.isConnected) {
                        try {
                            synchronized(existingSocket) {
                                val writer = existingSocket.getOutputStream().bufferedWriter()
                                writer.write(message)
                                writer.newLine()
                                writer.flush()
                            }
                            Timber.tag("LanJamServer").d("Mensaje enviado a $clientIp (socket existente)")
                            continue // Siguiente cliente
                        } catch (e: Exception) {
                            Timber.tag("LanJamServer").d("Error usando socket existente: ${e.message}")
                            // El socket existente falló, lo cerramos y removemos del mapa
                            try {
                                existingSocket.close()
                            } catch (ce: Exception) {
                                // Ignorar errores al cerrar
                            }
                            clientSockets.remove(clientIp)
                            activeConnections.remove(clientIp)
                        }
                    }
                    
                    // El cliente ya no está disponible, quitarlo de las conexiones activas
                    if (existingSocket == null || existingSocket.isClosed || !existingSocket.isConnected) {
                        activeConnections.remove(clientIp)
                        clientSockets.remove(clientIp)
                        Timber.tag("LanJamServer").d("Cliente $clientIp eliminado por socket inválido")
                        continue
                    }
                } catch (e: Exception) {
                    Timber.tag("LanJamServer").e("Error enviando mensaje a $clientIp: ${e.message}")
                    // Remover cliente que ya no está disponible
                    activeConnections.remove(clientIp)
                    clientSockets.remove(clientIp)
                }
            }
        }
    }

    fun stop() {
        if (!isRunning.get()) {
            Timber.tag("LanJamServer").d("El servidor ya está detenido")
            return
        }
        
        isRunning.set(false)
        
        try {
            // Cerrar conexiones de clientes
            val clientSocketsSnapshot = ArrayList(clientSockets.values)
            for (clientSocket in clientSocketsSnapshot) {
                try {
                    clientSocket.close()
                } catch (e: Exception) {
                    Timber.tag("LanJamServer").e("Error al cerrar socket de cliente: ${e.message}")
                }
            }
            clientSockets.clear()
            
            // Cerrar socket del servidor
            val socket = serverSocket
            if (socket != null && !socket.isClosed) {
                socket.close()
            }
            serverSocket = null
            Timber.tag("LanJamServer").d("Servidor detenido")
        } catch (e: Exception) {
            Timber.tag("LanJamServer").e("Error al detener el servidor: ${e.message}")
        }
        
        activeConnections.clear()
    }
    
    fun getLocalIpAddress(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                
                // Ignorar interfaces virtuales y desactivadas
                if (networkInterface.isLoopback || !networkInterface.isUp) {
                    continue
                }
                
                // Priorizar interfaces WiFi y ethernet
                val isWifiOrEthernet = networkInterface.displayName?.let { name ->
                    name.contains("wlan", ignoreCase = true) || 
                    name.contains("eth", ignoreCase = true) ||
                    name.contains("en", ignoreCase = true) // Común en algunos dispositivos
                } ?: false
                
                if (isWifiOrEthernet) {
                    val addresses = networkInterface.inetAddresses
                    while (addresses.hasMoreElements()) {
                        val address = addresses.nextElement()
                        if (!address.isLoopbackAddress && address is InetAddress) {
                            val hostAddress = address.hostAddress ?: continue
                            
                            // Preferir IPv4 cuando esté disponible
                            if (!hostAddress.contains(":")) {
                                Timber.tag("LanJamServer").d("Usando IPv4: $hostAddress de interfaz ${networkInterface.displayName}")
                                return hostAddress
                            }
                        }
                    }
                }
            }
            
            // Si no encontramos una interfaz WiFi/ethernet, probar cualquier interfaz con IPv4
            val interfaces2 = NetworkInterface.getNetworkInterfaces()
            while (interfaces2.hasMoreElements()) {
                val networkInterface = interfaces2.nextElement()
                if (networkInterface.isLoopback || !networkInterface.isUp) continue
                
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (!address.isLoopbackAddress && address is InetAddress) {
                        val hostAddress = address.hostAddress ?: continue
                        
                        // Preferir IPv4
                        if (!hostAddress.contains(":")) {
                            Timber.tag("LanJamServer").d("Usando IPv4: $hostAddress de interfaz ${networkInterface.displayName}")
                            return hostAddress
                        }
                    }
                }
            }
            
            // Si solo hay IPv6 disponible
            val interfaces3 = NetworkInterface.getNetworkInterfaces()
            while (interfaces3.hasMoreElements()) {
                val networkInterface = interfaces3.nextElement()
                if (!networkInterface.isLoopback && networkInterface.isUp) {
                    val addresses = networkInterface.inetAddresses
                    while (addresses.hasMoreElements()) {
                        val address = addresses.nextElement()
                        if (!address.isLoopbackAddress && address is InetAddress) {
                            val hostAddress = address.hostAddress ?: continue
                            Timber.tag("LanJamServer").d("Usando IPv6: $hostAddress de interfaz ${networkInterface.displayName}")
                            return hostAddress
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Timber.tag("LanJamServer").e("Error obteniendo IP local: ${e.message}")
            e.printStackTrace()
        }
        
        Timber.tag("LanJamServer").d("No se encontró dirección IP válida, usando 127.0.0.1")
        return "127.0.0.1"
    }
    
    data class ClientInfo(
        val ip: String,
        val connectedAt: Long
    )
}
