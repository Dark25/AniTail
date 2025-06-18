package com.anitail.music.utils

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.anitail.music.constants.LastFmEnabledKey
import com.anitail.music.constants.LastFmLoveTracksKey
import com.anitail.music.constants.LastFmScrobbleEnabledKey
import com.anitail.music.constants.LastFmSessionKey
import com.anitail.music.constants.LastFmShowAvatarKey
import com.anitail.music.constants.LastFmUsernameKey
import com.anitail.music.db.entities.Song
import dagger.hilt.android.qualifiers.ApplicationContext
import de.umass.lastfm.Authenticator
import de.umass.lastfm.Caller
import de.umass.lastfm.Session
import de.umass.lastfm.Track
import de.umass.lastfm.User
import de.umass.lastfm.scrobble.ScrobbleData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LastFmService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataStore: DataStore<Preferences>
) {
    companion object {
        private val API_KEY = LastFmKeyManager.getApiKey()
        private val API_SECRET = LastFmKeyManager.getApiSecret()
        private const val USER_AGENT = "Anitail Music"
        private const val PENDING_SCROBBLES_KEY = "pending_scrobbles"
    }

    private var session: Session? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }
    private val sharedPrefs = context.getSharedPreferences("lastfm_offline", Context.MODE_PRIVATE)
      init {
        // Configure Last.fm API to use HTTPS
        Caller.getInstance().userAgent = USER_AGENT
        // Force HTTPS by setting the cache to use secure URLs
        try {
            System.setProperty("lastfm.api.url", "https://ws.audioscrobbler.com/2.0/")
        } catch (e: Exception) {
            Timber.w("Could not set Last.fm API URL to HTTPS: ${e.message}")
        }
        
        // Intentar enviar scrobbles pendientes al inicializar
        scope.launch {
            if (isEnabled()) {
                sendPendingScrobbles()
            }
        }
    }

    // === Métodos para manejo de scrobbles offline ===
      private fun addPendingScrobble(pendingScrobble: PendingScrobble) {
        try {
            val currentScrobbles = getPendingScrobbles().toMutableList()
            
            // Evitar duplicados: misma canción en los últimos 30 segundos
            val now = System.currentTimeMillis()
            val isDuplicate = currentScrobbles.any { 
                it.artist == pendingScrobble.artist && 
                it.title == pendingScrobble.title && 
                (now - it.addedAt) < 30_000 // 30 segundos en milisegundos
            }
            
            if (!isDuplicate) {
                currentScrobbles.add(pendingScrobble)
                val jsonString = json.encodeToString(currentScrobbles)
                sharedPrefs.edit().putString(PENDING_SCROBBLES_KEY, jsonString).apply()
                Timber.d("Scrobble guardado para envío posterior: ${pendingScrobble.artist} - ${pendingScrobble.title}")
            } else {
                Timber.d("Scrobble duplicado ignorado: ${pendingScrobble.artist} - ${pendingScrobble.title}")
            }
        } catch (e: Exception) {
            Timber.e(e, "Error al guardar scrobble pendiente")
        }
    }
    
    private fun getPendingScrobbles(): List<PendingScrobble> {
        return try {
            val jsonString = sharedPrefs.getString(PENDING_SCROBBLES_KEY, null) ?: return emptyList()
            json.decodeFromString<List<PendingScrobble>>(jsonString)
        } catch (e: Exception) {
            Timber.e(e, "Error al cargar scrobbles pendientes")
            emptyList()
        }
    }
    
    private fun removePendingScrobble(pendingScrobble: PendingScrobble) {
        try {
            val currentScrobbles = getPendingScrobbles().toMutableList()
            currentScrobbles.remove(pendingScrobble)
            val jsonString = json.encodeToString(currentScrobbles)
            sharedPrefs.edit().putString(PENDING_SCROBBLES_KEY, jsonString).apply()
        } catch (e: Exception) {
            Timber.e(e, "Error al remover scrobble pendiente")
        }
    }
    
    private fun clearPendingScrobbles() {
        sharedPrefs.edit().remove(PENDING_SCROBBLES_KEY).apply()
        Timber.d("Scrobbles pendientes limpiados")
    }
    
    /**
     * Envía todos los scrobbles pendientes
     */
    suspend fun sendPendingScrobbles() {
        val pendingScrobbles = getPendingScrobbles()
        if (pendingScrobbles.isEmpty()) return
        
        var successCount = 0
        val failedScrobbles = mutableListOf<PendingScrobble>()
        
        for (pendingScrobble in pendingScrobbles) {
            try {
                val currentSession = getSession() ?: break
                
                val scrobbleData = ScrobbleData(
                    pendingScrobble.artist, 
                    pendingScrobble.title, 
                    pendingScrobble.timestamp.toInt()
                )
                if (pendingScrobble.album != null) scrobbleData.album = pendingScrobble.album
                if (pendingScrobble.duration != null) scrobbleData.duration = pendingScrobble.duration
                
                val result = Track.scrobble(scrobbleData, currentSession)
                if (result.isSuccessful) {
                    removePendingScrobble(pendingScrobble)
                    successCount++
                    Timber.d("Scrobble pendiente enviado exitosamente: ${pendingScrobble.artist} - ${pendingScrobble.title}")
                } else {
                    failedScrobbles.add(pendingScrobble)
                    Timber.w("Falló envío de scrobble pendiente: ${pendingScrobble.artist} - ${pendingScrobble.title}")
                }
            } catch (e: Exception) {
                failedScrobbles.add(pendingScrobble)
                if (isNetworkError(e)) {
                    Timber.d("Error de red al enviar scrobble pendiente, se mantendrá en cola")
                    break // Salir del bucle si hay error de red
                } else {
                    // Error no relacionado con red, remover este scrobble
                    removePendingScrobble(pendingScrobble)
                    Timber.e(e, "Error no recuperable al enviar scrobble pendiente")
                }
            }
        }
        
        if (successCount > 0) {
            Timber.i("Enviados $successCount scrobbles pendientes exitosamente")
        }
    }
    
    private fun isNetworkError(exception: Exception): Boolean {
        return exception is IOException || exception.message?.contains("network", ignoreCase = true) == true
    }

    suspend fun isEnabled(): Boolean = dataStore.data.map { 
        it[LastFmEnabledKey] ?: false 
    }.first()

    suspend fun isScrobbleEnabled(): Boolean = dataStore.data.map { 
        it[LastFmScrobbleEnabledKey] ?: true 
    }.first()

    suspend fun isLoveTracksEnabled(): Boolean = dataStore.data.map { 
        it[LastFmLoveTracksKey] ?: false 
    }.first()

    suspend fun getUsername(): String? = dataStore.data.map { 
        it[LastFmUsernameKey] 
    }.first()

    suspend fun getSession(): Session? {
        if (session != null) return session
        
        val sessionKey = dataStore.data.map { it[LastFmSessionKey] }.first()
        // username ya no es necesario para createSession
        return if (sessionKey != null) {
            val createdSession = Session.createSession(API_KEY, API_SECRET, sessionKey)
            session = createdSession
            createdSession
        } else null
    }

    suspend fun authenticate(username: String, password: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val authSession = Authenticator.getMobileSession(username, password, API_KEY, API_SECRET)
            if (authSession != null) {
                session = authSession
                dataStore.edit { preferences ->
                    preferences[LastFmUsernameKey] = username
                    preferences[LastFmSessionKey] = authSession.key
                    preferences[LastFmEnabledKey] = true
                }
                Result.success(true)
            } else {
                Result.failure(Exception("Authentication failed"))
            }
        } catch (e: Exception) {
            Timber.e(e, "Last.fm authentication failed")
            Result.failure(e)
        }
    }

    suspend fun logout() {
        dataStore.edit { preferences ->
            preferences.remove(LastFmUsernameKey)
            preferences.remove(LastFmSessionKey)
            preferences[LastFmEnabledKey] = false
        }
        session = null
    }
    fun scrobble(song: Song, timestamp: Long = System.currentTimeMillis() / 1000) {
        scope.launch {
            try {
                val artist = song.song.artistName ?: song.artists.firstOrNull()?.name
                val title = song.song.title
                
                Timber.d("🎵 Attempting to scrobble: $artist - $title with timestamp: $timestamp")
                
                if (!isEnabled()) {
                    Timber.d("❌ Last.fm not enabled, skipping scrobble")
                    return@launch
                }
                
                if (!isScrobbleEnabled()) {
                    Timber.d("❌ Scrobbling disabled, skipping scrobble")
                    return@launch
                }
                
                val currentSession = getSession()
                if (currentSession == null) {
                    Timber.w("❌ No Last.fm session available, cannot scrobble")
                    return@launch
                }
                
                if (artist.isNullOrBlank()) {
                    Timber.w("❌ No artist found for song, cannot scrobble: $title")
                    return@launch
                }
                
                if (title.isBlank()) {
                    Timber.w("❌ No title found for song, cannot scrobble")
                    return@launch
                }
                
                val album = song.album?.title
                val duration = if (song.song.duration > 0) song.song.duration else null

                val scrobbleData = ScrobbleData(artist, title, timestamp.toInt())
                if (album != null) scrobbleData.album = album
                val result = Track.scrobble(scrobbleData, currentSession)
                
                // Log detailed response information
                Timber.d("📊 Scrobble result - Success: ${result.isSuccessful}")
                Timber.d("📊 Scrobble result - Status: ${result.status}")
                
                // Try to get more details from the response
                try {
                    val responseDoc = result.resultDocument
                    if (responseDoc != null) {
                        val scrobbles = responseDoc.getElementsByTagName("scrobbles")
                        if (scrobbles.length > 0) {
                            val scrobblesElement = scrobbles.item(0) as org.w3c.dom.Element
                            val accepted = scrobblesElement.getAttribute("accepted")
                            val ignored = scrobblesElement.getAttribute("ignored")
                            Timber.d("📊 Scrobbles - Accepted: $accepted, Ignored: $ignored")
                            
                            // Check for ignored messages
                            val scrobbleNodes = responseDoc.getElementsByTagName("scrobble")
                            if (scrobbleNodes.length > 0) {
                                val scrobbleElement = scrobbleNodes.item(0) as org.w3c.dom.Element
                                val ignoredMessages = scrobbleElement.getElementsByTagName("ignoredmessage")
                                if (ignoredMessages.length > 0) {
                                    val ignoredElement = ignoredMessages.item(0) as org.w3c.dom.Element
                                    val code = ignoredElement.getAttribute("code")
                                    val message = ignoredElement.textContent
                                    Timber.w("🚫 Scrobble ignored - Code: $code, Message: $message")
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Timber.w(e, "Could not parse scrobble response details")
                }
                
                if (result.isSuccessful) {
                    Timber.d("✅ Successfully scrobbled: $artist - $title")
                    
                    // Después de un scrobble exitoso, intentar enviar pendientes
                    sendPendingScrobbles()
                } else {
                    // Guardar para envío posterior
                    val pendingScrobble = PendingScrobble(
                        artist = artist,
                        title = title,
                        album = album,
                        timestamp = timestamp,
                        duration = duration
                    )
                    addPendingScrobble(pendingScrobble)
                }
            } catch (e: Exception) {
                Timber.e(e, "❌ Exception while scrobbling track")
                
                // Si es error de red, guardar para envío posterior
                if (isNetworkError(e)) {
                    val artist = song.song.artistName ?: song.artists.firstOrNull()?.name
                    val title = song.song.title
                    val album = song.album?.title
                    val duration = if (song.song.duration > 0) song.song.duration else null
                    
                    if (artist != null) {
                        val pendingScrobble = PendingScrobble(
                            artist = artist,
                            title = title,
                            album = album,
                            timestamp = timestamp,
                            duration = duration
                        )
                        addPendingScrobble(pendingScrobble)
                    }
                }
            }
        }
    }
    fun updateNowPlaying(song: Song) {
        scope.launch {
            try {
                if (!isEnabled()) return@launch
                
                val currentSession = getSession() ?: return@launch
                val artist = song.song.artistName ?: song.artists.firstOrNull()?.name ?: return@launch
                val title = song.song.title
                val album = song.album?.title
                val duration = if (song.song.duration > 0) song.song.duration else null

                val scrobbleData = ScrobbleData(artist, title, (System.currentTimeMillis() / 1000).toInt())
                if (album != null) scrobbleData.album = album
                if (duration != null) scrobbleData.duration = duration

                val result = Track.updateNowPlaying(scrobbleData, currentSession)
                if (result.isSuccessful) {
                    Timber.d("Successfully updated now playing: $artist - $title")
                } else {
                    Timber.w("Failed to update now playing: $artist - $title")
                }
            } catch (e: Exception) {
                Timber.e(e, "Error updating now playing")
            }
        }
    }
    fun loveTrack(song: Song) {
        scope.launch {
            try {
                if (!isEnabled()) {
                    Timber.d("Last.fm not enabled, skipping love track")
                    return@launch
                }
                
                if (!isLoveTracksEnabled()) {
                    Timber.d("Love tracks not enabled, skipping love track")
                    return@launch
                }
                
                val currentSession = getSession()
                if (currentSession == null) {
                    Timber.w("No Last.fm session available, cannot love track")
                    return@launch
                }
                
                val artist = song.song.artistName ?: song.artists.firstOrNull()?.name
                if (artist == null) {
                    Timber.w("No artist found for song, cannot love track")
                    return@launch
                }
                
                val title = song.song.title

                Timber.d("Attempting to love track: $artist - $title")
                val result = Track.love(artist, title, currentSession)
                if (result.isSuccessful) {
                    Timber.i("✅ Successfully loved track: $artist - $title")
                } else {
                    Timber.w("❌ Failed to love track: $artist - $title")
                    Timber.w("Last.fm API response: ${result.status}")
                }
            } catch (e: Exception) {
                Timber.e(e, "Error loving track: ${song.song.title}")
                
                // Si es error de red, podríamos implementar cola de "loves" pendientes
                if (isNetworkError(e)) {
                    Timber.d("Network error detected, consider implementing pending loves queue")
                }
            }
        }
    }    fun unloveTrack(song: Song) {
        scope.launch {
            try {
                if (!isEnabled()) {
                    Timber.d("Last.fm not enabled, skipping unlove track")
                    return@launch
                }
                
                if (!isLoveTracksEnabled()) {
                    Timber.d("Love tracks not enabled, skipping unlove track")
                    return@launch
                }
                
                val currentSession = getSession()
                if (currentSession == null) {
                    Timber.w("No Last.fm session available, cannot unlove track")
                    return@launch
                }
                
                val artist = song.song.artistName ?: song.artists.firstOrNull()?.name
                if (artist == null) {
                    Timber.w("No artist found for song, cannot unlove track")
                    return@launch
                }
                
                val title = song.song.title

                Timber.d("Attempting to unlove track: $artist - $title")
                val result = Track.unlove(artist, title, currentSession)
                if (result.isSuccessful) {
                    Timber.i("✅ Successfully unloved track: $artist - $title")
                } else {
                    Timber.w("❌ Failed to unlove track: $artist - $title")
                    Timber.w("Last.fm API response: ${result.status}")
                }
            } catch (e: Exception) {
                Timber.e(e, "Error unloving track: ${song.song.title}")
                
                // Si es error de red, podríamos implementar cola de "unloves" pendientes
                if (isNetworkError(e)) {
                    Timber.d("Network error detected, consider implementing pending unloves queue")
                }
            }
        }
    }

    suspend fun getUserInfo(): Result<User?> = withContext(Dispatchers.IO) {
        try {
            val username = getUsername() ?: return@withContext Result.failure(Exception("No username"))
            val user = User.getInfo(username, API_KEY)
            Result.success(user)
        } catch (e: Exception) {
            Timber.e(e, "Error getting user info")
            Result.failure(e)
        }
    }

    suspend fun getRecentTracks(limit: Int = 10): Result<List<Track>> = withContext(Dispatchers.IO) {
        try {
            val username = getUsername() ?: return@withContext Result.failure(Exception("No username"))
            val tracks = User.getRecentTracks(username, 1, limit, API_KEY)
            Result.success(tracks.toList())
        } catch (e: Exception) {
            Timber.e(e, "Error getting recent tracks")
            Result.failure(e)
        }
    }
    suspend fun getTopTracks(period: de.umass.lastfm.Period = de.umass.lastfm.Period.OVERALL, limit: Int = 10): Result<List<Any>> = withContext(Dispatchers.IO) {
        try {
            val username = getUsername() ?: return@withContext Result.failure(Exception("No username"))
            
            // Intentar con diferentes períodos si falla
            val periods = listOf(
                de.umass.lastfm.Period.ONE_MONTH,
                de.umass.lastfm.Period.THREE_MONTHS,
                de.umass.lastfm.Period.SIX_MONTHS,
                de.umass.lastfm.Period.OVERALL
            )
            
            for (periodToTry in periods) {
                try {
                    Timber.d("Trying to get top tracks for period: $periodToTry")
                    val tracks = User.getTopTracks(username, periodToTry, API_KEY)
                    Timber.d("API returned tracks collection: ${tracks != null}")
                    
                    if (tracks != null) {
                        val tracksList = tracks.toList()
                        Timber.d("Converted to list: ${tracksList.size} tracks")
                        
                        if (tracksList.isNotEmpty()) {
                            Timber.d("Successfully loaded ${tracksList.size} top tracks with period: $periodToTry")
                            tracksList.forEachIndexed { index, track ->
                                Timber.d("Track $index: ${track.name} by ${track.artist} (${track.playcount} plays)")
                            }
                            return@withContext Result.success(tracksList.take(limit))
                        } else {
                            Timber.w("Track list is empty for period $periodToTry")
                        }
                    } else {
                        Timber.w("API returned null for period $periodToTry")
                    }
                } catch (e: Exception) {
                    Timber.w("Failed to get top tracks for period $periodToTry: ${e.message}", e)
                    continue
                }
            }
            
            // Si la librería falla, intentar con HTTP directo
            Timber.i("Library failed, trying direct HTTP fallback for top tracks")
            val periodString = when (period) {
                de.umass.lastfm.Period.ONE_MONTH -> "1month"
                de.umass.lastfm.Period.THREE_MONTHS -> "3month"
                de.umass.lastfm.Period.SIX_MONTHS -> "6month"
                de.umass.lastfm.Period.TWELVE_MONTHS -> "12month"
                else -> "overall"
            }
            
            val fallbackResult = getTopTracksDirectly(periodString, limit)
            if (fallbackResult.isSuccess) {
                val tracks = fallbackResult.getOrNull()
                if (!tracks.isNullOrEmpty()) {
                    Timber.i("Fallback successful: loaded ${tracks.size} top tracks")
                    return@withContext Result.success(tracks)
                }
            }
            
            // Si todos los períodos fallan, retornar lista vacía en lugar de error
            Timber.i("No top tracks data available for any period")
            Result.success(emptyList<Any>())
        } catch (e: Exception) {
            Timber.e(e, "Error getting top tracks")
            Result.success(emptyList<Any>()) // Retornar lista vacía en lugar de fallo
        }
    }
    suspend fun getTopArtists(period: de.umass.lastfm.Period = de.umass.lastfm.Period.OVERALL, limit: Int = 10): Result<List<Any>> = withContext(Dispatchers.IO) {
        try {
            val username = getUsername() ?: return@withContext Result.failure(Exception("No username"))
            
            // Intentar con diferentes períodos si falla
            val periods = listOf(
                de.umass.lastfm.Period.ONE_MONTH,
                de.umass.lastfm.Period.THREE_MONTHS,
                de.umass.lastfm.Period.SIX_MONTHS,
                de.umass.lastfm.Period.OVERALL
            )
            
            for (periodToTry in periods) {
                try {
                    Timber.d("Trying to get top artists for period: $periodToTry")
                    val artists = User.getTopArtists(username, periodToTry, API_KEY)
                    Timber.d("API returned artists collection: ${artists != null}")
                    
                    if (artists != null) {
                        val artistsList = artists.toList()
                        Timber.d("Converted to list: ${artistsList.size} artists")
                        
                        if (artistsList.isNotEmpty()) {
                            Timber.d("Successfully loaded ${artistsList.size} top artists with period: $periodToTry")
                            artistsList.forEachIndexed { index, artist ->
                                Timber.d("Artist $index: ${artist.name} (${artist.playcount} plays)")
                            }
                            return@withContext Result.success(artistsList.take(limit))
                        } else {
                            Timber.w("Artist list is empty for period $periodToTry")
                        }
                    } else {
                        Timber.w("API returned null for period $periodToTry")
                    }
                } catch (e: Exception) {
                    Timber.w(e, "Failed to get top artists for period $periodToTry: ${e.message}")
                    continue
                }
            }
            
            // Si la librería falla, intentar con HTTP directo
            Timber.i("Library failed, trying direct HTTP fallback for top artists")
            val periodString = when (period) {
                de.umass.lastfm.Period.ONE_MONTH -> "1month"
                de.umass.lastfm.Period.THREE_MONTHS -> "3month"
                de.umass.lastfm.Period.SIX_MONTHS -> "6month"
                de.umass.lastfm.Period.TWELVE_MONTHS -> "12month"
                else -> "overall"
            }
            
            val fallbackResult = getTopArtistsDirectly(periodString, limit)
            if (fallbackResult.isSuccess) {
                val artists = fallbackResult.getOrNull()
                if (!artists.isNullOrEmpty()) {
                    Timber.i("Fallback successful: loaded ${artists.size} top artists")
                    return@withContext Result.success(artists)
                }
            }
            
            // Si todos los períodos fallan, retornar lista vacía en lugar de error
            Timber.i("No top artists data available for any period")
            Result.success(emptyList<Any>())
        } catch (e: Exception) {
            Timber.e(e, "Error getting top artists")
            Result.success(emptyList<Any>()) // Retornar lista vacía en lugar de fallo
        }
    }

   private suspend fun getTopTracksDirectly(period: String = "overall", limit: Int = 10): Result<List<LocalTrack>> = withContext(Dispatchers.IO) {
        try {
            val username = getUsername() ?: return@withContext Result.failure(Exception("No username"))
            
            val client = OkHttpClient()
            val url = "https://ws.audioscrobbler.com/2.0/?method=user.gettoptracks&user=$username&period=$period&limit=$limit&api_key=$API_KEY&format=json"
            
            val request = Request.Builder()
                .url(url)
                .build()
                
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(IOException("HTTP error: ${response.code}"))
                }
                
                val jsonString = response.body?.string() ?: return@withContext Result.failure(Exception("Empty response"))
                val json = Json.parseToJsonElement(jsonString).jsonObject
                
                val toptracks = json["toptracks"]?.jsonObject
                val tracksArray = toptracks?.get("track")?.jsonArray
                
                if (tracksArray == null || tracksArray.isEmpty()) {
                    Timber.i("No top tracks found in direct API response")
                    return@withContext Result.success(emptyList())
                }
                
                val tracks = tracksArray.map { trackElement ->
                    val trackObj = trackElement.jsonObject
                    val name = trackObj["name"]?.jsonPrimitive?.content ?: "Unknown"
                    val artist = trackObj["artist"]?.jsonObject?.get("name")?.jsonPrimitive?.content ?: "Unknown Artist"
                    val playcount = trackObj["playcount"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                    val url = trackObj["url"]?.jsonPrimitive?.content
                    
                    LocalTrack(
                        name = name,
                        artist = artist,
                        playcount = playcount,
                        url = url
                    )
                }
                
                Timber.d("Successfully parsed ${tracks.size} top tracks from direct API")
                Result.success(tracks)
            }
        } catch (e: Exception) {
            Timber.e(e, "Error in direct top tracks API call")
            Result.failure(e)
        }
    }
      private suspend fun getTopArtistsDirectly(period: String = "overall", limit: Int = 10): Result<List<LocalArtist>> = withContext(Dispatchers.IO) {
        try {
            val username = getUsername() ?: return@withContext Result.failure(Exception("No username"))
            
            val client = OkHttpClient()
            val url = "https://ws.audioscrobbler.com/2.0/?method=user.gettopartists&user=$username&period=$period&limit=$limit&api_key=$API_KEY&format=json"
            
            val request = Request.Builder()
                .url(url)
                .build()
                
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(IOException("HTTP error: ${response.code}"))
                }
                
                val jsonString = response.body?.string() ?: return@withContext Result.failure(Exception("Empty response"))
                val json = Json.parseToJsonElement(jsonString).jsonObject
                
                val topartists = json["topartists"]?.jsonObject
                val artistsArray = topartists?.get("artist")?.jsonArray
                
                if (artistsArray == null || artistsArray.isEmpty()) {
                    Timber.i("No top artists found in direct API response")
                    return@withContext Result.success(emptyList())
                }
                
                val artists = artistsArray.map { artistElement ->
                    val artistObj = artistElement.jsonObject
                    val name = artistObj["name"]?.jsonPrimitive?.content ?: "Unknown"
                    val playcount = artistObj["playcount"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                    val url = artistObj["url"]?.jsonPrimitive?.content
                    
                    LocalArtist(
                        name = name,
                        playcount = playcount,
                        url = url
                    )
                }
                
                Timber.d("Successfully parsed ${artists.size} top artists from direct API")
                Result.success(artists)
            }
        } catch (e: Exception) {
            Timber.e(e, "Error in direct top artists API call")
            Result.failure(e)
        }
    }

    suspend fun enableScrobbling(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[LastFmScrobbleEnabledKey] = enabled
        }
    }    suspend fun enableLoveTracks(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[LastFmLoveTracksKey] = enabled
        }
    }

    suspend fun enableShowAvatar(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[LastFmShowAvatarKey] = enabled
        }
    }

    suspend fun isShowAvatarEnabled(): Boolean = dataStore.data.map { 
        it[LastFmShowAvatarKey] ?: false 
    }.first()

    /**
     * Fuerza el envío de scrobbles pendientes (útil cuando se restaura la conexión)
     */
    fun retryPendingScrobbles() {
        scope.launch {
            if (isEnabled()) {
                sendPendingScrobbles()
            }
        }
    }

    /**
     * Obtiene el número de scrobbles pendientes
     */
    fun getPendingScrobblesCount(): Int {
        return getPendingScrobbles().size
    }
    /**
     * Limpia todos los scrobbles pendientes (usar con cuidado)
     */
    
    fun clearAllPendingScrobbles() {
        clearPendingScrobbles()
    }

}

@Serializable
data class PendingScrobble(
    val artist: String,
    val title: String,
    val album: String? = null,
    val timestamp: Long,
    val duration: Int? = null,
    val addedAt: Long = System.currentTimeMillis() // Usar milisegundos completos para identificación única
)

// Modelos locales para usar cuando la librería Last.fm falla
@Serializable
data class LocalTrack(
    val name: String,
    val artist: String,
    val playcount: Int = 0,
    val url: String? = null
)

@Serializable
data class LocalArtist(
    val name: String,
    val playcount: Int = 0,
    val url: String? = null
)
