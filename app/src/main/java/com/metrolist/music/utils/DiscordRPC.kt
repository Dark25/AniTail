package com.metrolist.music.utils

import android.content.Context
import com.metrolist.music.R
import com.metrolist.music.db.entities.Song
import com.my.kizzy.rpc.KizzyRPC
import com.my.kizzy.rpc.RpcImage
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.*

class DiscordRPC(
    val context: Context,
    token: String,
) : KizzyRPC(token) {

    suspend fun updateSong(
        song: Song,
        elapsedTime: Long = 0L,  // Posición actual en segundos
        remainingTime: Long? = null       // Duración total en segundos (opcional)
    ) = runCatching {
        val nowUnixMs = System.currentTimeMillis()  // Unix timestamp en milisegundos
        val startTimestamp = (nowUnixMs - (elapsedTime * 1000)).coerceAtLeast(0)  // Aseguramos que no sea negativo
        val endTimestamp = remainingTime?.let { (startTimestamp + (it * 1000)).coerceAtLeast(0) }  // Aseguramos que no sea negativo

        Timber.d("""
            Discord RPC Update:
            Song: ${song.song.title}
            Now: ${nowUnixMs.toFormattedTime()} ($nowUnixMs)
            Start: ${startTimestamp.toFormattedTime()} ($startTimestamp)
            ${endTimestamp?.let { "End: ${it.toFormattedTime()} ($it)" } ?: "No end time"}
            Position: ${elapsedTime}s
            ${remainingTime?.let { "Duration: ${it}s" } ?: "No duration"}
        """.trimIndent())

        setActivity(
            name = context.getString(R.string.app_name).removeSuffix(" Debug"),
            details = song.song.title,
            state = song.artists.joinToString { it.name },
            largeImage = song.song.thumbnailUrl?.let { RpcImage.ExternalImage(it) },
            smallImage = song.artists.firstOrNull()?.thumbnailUrl?.let { RpcImage.ExternalImage(it) },
            largeText = song.album?.title,
            smallText = song.artists.firstOrNull()?.name,
            buttons = listOf(
                "Listen on YouTube Music" to "https://music.youtube.com/watch?v=${song.song.id}",
                "Visit AniTail" to "https://github.com/Animetailapp/Anitail"
            ),
            type = Type.LISTENING,
            startTime = startTimestamp,
            endTime = endTimestamp,
            since = System.currentTimeMillis(),
            applicationId = APPLICATION_ID
        )
    }

    companion object {
        private const val APPLICATION_ID = "1271273225120125040"

        // Formateador de Unix timestamp (segundos) a hora legible
        private fun Long.toFormattedTime(): String {
            return SimpleDateFormat("mm:ss", Locale.getDefault())
                .format(Date(this))  // Los valores ya están en milisegundos
        }
    }
}