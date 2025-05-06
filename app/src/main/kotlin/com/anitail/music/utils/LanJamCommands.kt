package com.anitail.music.utils

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Comandos específicos para controlar la reproducción en dispositivos JAM
 */
object LanJamCommands {
    private val json = Json { 
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        prettyPrint = false
    }
    
    /**
     * Tipos de comandos disponibles
     */
    enum class CommandType {
        PLAY,
        PAUSE,
        NEXT,
        PREVIOUS,
        SEEK,
        TOGGLE_REPEAT,
        TOGGLE_SHUFFLE
    }
    
    /**
     * Clase para serializar comandos
     */
    @Serializable
    data class Command(
        val type: CommandType,
        val position: Long = 0, // Usado para SEEK
        val repeatMode: Int = 0 // Usado para TOGGLE_REPEAT
    )
    
    /**
     * Serializa un comando a JSON
     */
    fun serialize(command: Command): String {
        return "JAM_CMD:" + json.encodeToString(command)
    }
    
    /**
     * Deserializa un comando desde JSON
     * @return null si no es un comando válido
     */
    fun deserialize(jsonString: String): Command? {
        if (!jsonString.startsWith("JAM_CMD:")) {
            return null
        }
        val commandJson = jsonString.substring(8) // Quitar el prefijo JAM_CMD:
        return json.decodeFromString(Command.serializer(), commandJson)
    }
    
    /**
     * Comprueba si un mensaje es un comando JAM
     */
    fun isCommand(message: String): Boolean {
        return message.startsWith("JAM_CMD:")
    }
}
