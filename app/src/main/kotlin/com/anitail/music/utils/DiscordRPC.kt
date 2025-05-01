package com.anitail.music.utils

import android.content.Context
import com.anitail.music.R
import com.anitail.music.db.entities.Song
import com.my.kizzy.rpc.KizzyRPC
import com.my.kizzy.rpc.RpcImage

class DiscordRPC(
    val context: Context,
    token: String,
) : KizzyRPC(token) {

    suspend fun updateSong(
        song: Song,
        timeStart: Long,
        timeEnd: Long
    ) = runCatching {
        setActivity(
            name = context.getString(R.string.app_name).removeSuffix(" Debug"),
            details = song.song.title,
            state = song.song.artistName ?: song.artists.joinToString { it.name },
            largeImage = song.song.thumbnailUrl?.let { RpcImage.ExternalImage(it) },
            smallImage = RpcImage.DiscordImage("emojis/1359946403522285699.webp?quality=lossless"
            ),
            largeText = song.album?.title,
            smallText = song.artists.firstOrNull()?.name,
            buttons = listOf(
                "Listen on YouTube Music" to "https://music.youtube.com/watch?v=${song.song.id}",
                "Vistit our Discord" to "https://discord.gg/H8x3yNbc67",
            ),
            type = Type.LISTENING,
            startTime = timeStart,
            endTime = timeEnd,
            applicationId = APPLICATION_ID
        )
        }

    companion object {
        private const val APPLICATION_ID = "1271273225120125040"
    }
}