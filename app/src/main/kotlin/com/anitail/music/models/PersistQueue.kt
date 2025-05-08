package com.anitail.music.models

import kotlinx.serialization.Serializable

@Serializable
data class PersistQueue(
    val title: String?,
    val items: List<MediaMetadata>,
    val mediaItemIndex: Int,
    val position: Long,
)