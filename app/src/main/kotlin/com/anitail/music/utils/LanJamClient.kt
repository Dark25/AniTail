package com.anitail.music.utils

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Socket

class LanJamClient(val host: String, private val port: Int = 5000, private val onMessage: (String) -> Unit) {
    private var socket: Socket? = null
    private var writer: PrintWriter? = null
    private var running = false

    fun connect() {
        running = true
        CoroutineScope(Dispatchers.IO).launch {
            try {
                socket = Socket(host, port)
                writer = PrintWriter(socket!!.getOutputStream(), true)
                val reader = BufferedReader(InputStreamReader(socket!!.getInputStream()))
                var line: String? = null
                while (running && reader.readLine().also { line = it } != null) {
                    try {
                        if (line != null) onMessage(line!!)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                try { socket?.close() } catch (_: Exception) {}
                writer = null
            }
        }
    }

    fun send(message: String) {
        writer?.println(message)
    }

    fun disconnect() {
        running = false
        socket?.close()
        writer = null
    }
}
