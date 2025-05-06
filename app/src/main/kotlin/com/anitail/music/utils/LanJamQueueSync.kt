package com.anitail.music.utils

import com.anitail.music.models.MediaMetadata
import com.anitail.music.models.PersistQueue
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Utilidad para serializar y deserializar colas de reproducción entre dispositivos JAM
 */
object LanJamQueueSync {
    private val json = Json { 
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        prettyPrint = false
    }
    
    /**
     * Clase auxiliar serializable para transferir la información de cola
     */
    @Serializable
    private data class SerializableQueue(
        val title: String?,
        val items: List<SerializableMediaItem>,
        val mediaItemIndex: Int,
        val position: Long,
    )
    
    @Serializable
    private data class SerializableMediaItem(
        val id: String,
        val title: String,
        val artistName: String?,
        val thumbnailUrl: String?,
        val duration: Int
    )
    
    /**
     * Serializa una PersistQueue a JSON
     */
    fun serializeQueue(queue: PersistQueue): String {
        val serializableItems = queue.items.map { item ->
            SerializableMediaItem(
                id = item.id,
                title = item.title,
                artistName = item.artistName,
                thumbnailUrl = item.thumbnailUrl,
                duration = item.duration
            )
        }
        
        val serializableQueue = SerializableQueue(
            title = queue.title,
            items = serializableItems,
            mediaItemIndex = queue.mediaItemIndex,
            position = queue.position
        )
        
        return json.encodeToString(serializableQueue)
    }
    
    /**
     * Deserializa JSON a PersistQueue
     */
    fun deserializeQueue(jsonString: String): PersistQueue {
        val serializableQueue = json.decodeFromString<SerializableQueue>(jsonString)

        val mediaItems = serializableQueue.items.map { item ->
            MediaMetadata(
                id = item.id,
                title = item.title,
                artists = listOf(), // o puedes intentar inferir el artista desde artistName si lo deseas
                artistName = item.artistName ?: "",
                duration = item.duration,
                thumbnailUrl = item.thumbnailUrl
            )
        }

        return PersistQueue(
            title = serializableQueue.title,
            items = mediaItems,
            mediaItemIndex = serializableQueue.mediaItemIndex,
            position = serializableQueue.position
        )
    }
}
