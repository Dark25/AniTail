package com.anitail.music.utils

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket

class LanJamServer(private val port: Int = 5000, private val onMessage: (String) -> Unit) {
    private var serverSocket: ServerSocket? = null
    private val clients = mutableListOf<Socket>()
    private var running = false

    fun start() {
        running = true
        CoroutineScope(Dispatchers.IO).launch {
            serverSocket = ServerSocket(port)
            while (running) {
                try {
                    val client = serverSocket!!.accept()
                    synchronized(clients) { clients.add(client) }
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val reader = BufferedReader(InputStreamReader(client.getInputStream()))
                            var line: String?
                            while (reader.readLine().also { line = it } != null) {
                                onMessage(line!!)
                                broadcast(line, client)
                            }
                        } catch (_: Exception) {
                            // Client disconnected or error
                        } finally {
                            synchronized(clients) { clients.remove(client) }
                            try { client.close() } catch (_: Exception) {}
                        }
                    }
                } catch (_: Exception) {
                    // Accept failed, probably shutting down
                }
            }
        }
    }

    fun send(message: String) {
        broadcast(message, null)
    }

    private fun broadcast(message: String, except: Socket?) {
        val toRemove = mutableListOf<Socket>()
        synchronized(clients) {
            for (client in clients) {
                if (client != except && !client.isClosed) {
                    try {
                        PrintWriter(client.getOutputStream(), true).println(message)
                    } catch (_: Exception) {
                        toRemove.add(client)
                    }
                }
            }
            clients.removeAll(toRemove)
            toRemove.forEach { try { it.close() } catch (_: Exception) {} }
        }
    }

    fun stop() {
        running = false
        try { serverSocket?.close() } catch (_: Exception) {}
        synchronized(clients) {
            clients.forEach { try { it.close() } catch (_: Exception) {} }
            clients.clear()
        }
    }
}
