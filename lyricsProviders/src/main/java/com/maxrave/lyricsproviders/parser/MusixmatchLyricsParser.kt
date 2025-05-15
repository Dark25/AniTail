package com.maxrave.lyricsproviders.parser

import com.maxrave.lyricsproviders.models.lyrics.Line
import com.maxrave.lyricsproviders.models.lyrics.Lyrics
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object MusixmatchLyricsParser {
    /**
     * Extrae el token de usuario de la respuesta HTTP
     */
    suspend fun getToken(response: HttpResponse): String {
        val responseText = response.bodyAsText()
        val json = Json { ignoreUnknownKeys = true; isLenient = true }
        val jsonObject = json.parseToJsonElement(responseText).jsonObject

        return try {
            val message = jsonObject["message"]?.jsonObject
            
            // Primero intentar obtenerlo del body (estructura actual)
            val body = message?.get("body")?.jsonObject
            val userTokenFromBody = body?.get("user_token")?.jsonPrimitive?.content
            
            if (!userTokenFromBody.isNullOrEmpty()) {
                return userTokenFromBody
            }
            
            // Si no está en el body, intentar en el header (estructura anterior)
            val header = message?.get("header")?.jsonObject
            val userTokenFromHeader = header?.get("user_token")?.jsonPrimitive?.content
            
            // Retornar el token o cadena vacía si no se encuentra
            userTokenFromHeader ?: ""
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    /**
     * Extrae el ID de la primera pista encontrada en la respuesta de búsqueda
     */
    suspend fun getFirstTrackId(response: HttpResponse): String? {
        val responseText = response.bodyAsText()
        val json = Json { ignoreUnknownKeys = true }

        return try {
            val jsonObject = json.parseToJsonElement(responseText).jsonObject
            val message = jsonObject["message"]?.jsonObject
            val body = message?.get("body")?.jsonObject
            val macroResultList = body?.get("macro_result_list")?.jsonObject
            val macroResult = macroResultList?.get("0")?.jsonObject
            val messageResult = macroResult?.get("message")?.jsonObject
            val bodyResult = messageResult?.get("body")?.jsonObject
            val trackList = bodyResult?.get("track_list")?.jsonObject

            val trackObj = trackList?.get("0")?.jsonObject
            val track = trackObj?.get("track")?.jsonObject
            track?.get("track_id")?.jsonPrimitive?.content
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Extrae las letras sincronizadas o no sincronizadas de la respuesta
     */
    suspend fun parseLyrics(response: HttpResponse): String {
        return try {
            val responseText = response.bodyAsText()
            val json = Json { ignoreUnknownKeys = true }
            val jsonObject = json.parseToJsonElement(responseText).jsonObject

            val message = jsonObject["message"]?.jsonObject
            val body = message?.get("body")?.jsonObject

            // Intentar obtener letras sincronizadas
            val subtitle = body?.get("subtitle")?.jsonObject
            var lyricsText = subtitle?.get("subtitle_body")?.jsonPrimitive?.content

            if (lyricsText.isNullOrBlank()) {
                // Si no hay letras sincronizadas, intentar con letras no sincronizadas
                val lyrics = body?.get("lyrics")?.jsonObject
                lyricsText = lyrics?.get("lyrics_body")?.jsonPrimitive?.content
            }

            lyricsText ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    fun parseMusixmatchLyrics(data: String): Lyrics {
        val regex = Regex("\\[(\\d{2}):(\\d{2})\\.(\\d{2})\\](.+)")
        val lines = data.lines()
        val linesLyrics = ArrayList<Line>()
        lines.map { line ->
            val matchResult = regex.matchEntire(line)
            if (matchResult != null) {
                val minutes = matchResult.groupValues[1].toLong()
                val seconds = matchResult.groupValues[2].toLong()
                val milliseconds = matchResult.groupValues[3].toLong()
                val timeInMillis = minutes * 60_000L + seconds * 1000L + milliseconds
                val content = (if (matchResult.groupValues[4] == " ") " ♫" else matchResult.groupValues[4]).removeRange(0, 1)
                linesLyrics.add(
                    Line(
                        endTimeMs = "0",
                        startTimeMs = timeInMillis.toString(),
                        syllables = listOf(),
                        words = content,
                    ),
                )
            }
        }
        return Lyrics(
            lyrics =
                Lyrics.LyricsX(
                    lines = linesLyrics,
                    syncType = "LINE_SYNCED",
                ),
        )
    }

    fun parseUnsyncedLyrics(data: String): Lyrics {
        val lines = data.lines()
        val linesLyrics = ArrayList<Line>()
        lines.map { line ->
            linesLyrics.add(
                Line(
                    endTimeMs = "0",
                    startTimeMs = "0",
                    syllables = listOf(),
                    words = line,
                ),
            )
        }
        return Lyrics(
            lyrics =
                Lyrics.LyricsX(
                    lines = linesLyrics,
                    syncType = "UNSYNCED",
                ),
        )
    }
}