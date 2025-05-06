package com.anitail.music.utils

import com.anitail.music.models.PersistQueue
import kotlinx.serialization.json.Json

object LanJamQueueSync {
    private val json = Json { ignoreUnknownKeys = true }

    fun serializeQueue(queue: PersistQueue): String = json.encodeToString(queue)

    fun deserializeQueue(data: String): PersistQueue = json.decodeFromString(data)
}
