package com.anitail.music.utils

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.anitail.music.constants.LastFmEnabledKey
import com.anitail.music.constants.LastFmLoveTracksKey
import com.anitail.music.constants.LastFmScrobbleEnabledKey
import com.anitail.music.constants.LastFmSessionKey
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
import timber.log.Timber
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class PendingScrobble(
    val artist: String,
    val title: String,
    val album: String? = null,
    val timestamp: Long,
    val duration: Int? = null,
    val addedAt: Long = System.currentTimeMillis() / 1000 / 60 // Solo guardar una canción por minuto
)

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
            
            // Evitar duplicados
            val isDuplicate = currentScrobbles.any { 
                it.artist == pendingScrobble.artist && 
                it.title == pendingScrobble.title && 
                it.addedAt == pendingScrobble.addedAt 
            }
            
            if (!isDuplicate) {
                currentScrobbles.add(pendingScrobble)
                val jsonString = json.encodeToString(currentScrobbles)
                sharedPrefs.edit().putString(PENDING_SCROBBLES_KEY, jsonString).apply()
                Timber.d("Scrobble guardado para envío posterior: ${pendingScrobble.artist} - ${pendingScrobble.title}")
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
        return exception is IOException || 
               exception is ConnectException || 
               exception is SocketTimeoutException || 
               exception is UnknownHostException ||
               exception.message?.contains("network", ignoreCase = true) == true
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
    }    fun scrobble(song: Song, timestamp: Long = System.currentTimeMillis() / 1000) {
        scope.launch {
            try {
                if (!isEnabled() || !isScrobbleEnabled()) return@launch
                
                val currentSession = getSession() ?: return@launch
                val artist = song.song.artistName ?: song.artists.firstOrNull()?.name ?: return@launch
                val title = song.song.title
                val album = song.album?.title
                val duration = if (song.song.duration > 0) song.song.duration else null

                val scrobbleData = ScrobbleData(artist, title, timestamp.toInt())
                if (album != null) scrobbleData.album = album
                if (duration != null) scrobbleData.duration = duration

                val result = Track.scrobble(scrobbleData, currentSession)
                if (result.isSuccessful) {
                    Timber.d("Successfully scrobbled: $artist - $title")
                    
                    // Después de un scrobble exitoso, intentar enviar pendientes
                    sendPendingScrobbles()
                } else {
                    Timber.w("Failed to scrobble: $artist - $title")
                    
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
                Timber.e(e, "Error scrobbling track")
                
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
    }fun updateNowPlaying(song: Song) {
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
    }    fun loveTrack(song: Song) {
        scope.launch {
            try {
                if (!isEnabled() || !isLoveTracksEnabled()) return@launch
                
                val currentSession = getSession() ?: return@launch
                val artist = song.song.artistName ?: song.artists.firstOrNull()?.name ?: return@launch
                val title = song.song.title

                val result = Track.love(artist, title, currentSession)
                if (result.isSuccessful) {
                    Timber.d("Successfully loved track: $artist - $title")
                } else {
                    Timber.w("Failed to love track: $artist - $title")
                }
            } catch (e: Exception) {
                Timber.e(e, "Error loving track")
            }
        }
    }

    fun unloveTrack(song: Song) {
        scope.launch {
            try {
                if (!isEnabled() || !isLoveTracksEnabled()) return@launch
                
                val currentSession = getSession() ?: return@launch
                val artist = song.song.artistName ?: song.artists.firstOrNull()?.name ?: return@launch
                val title = song.song.title

                val result = Track.unlove(artist, title, currentSession)
                if (result.isSuccessful) {
                    Timber.d("Successfully unloved track: $artist - $title")
                } else {
                    Timber.w("Failed to unlove track: $artist - $title")
                }
            } catch (e: Exception) {
                Timber.e(e, "Error unloving track")
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

    suspend fun getTopTracks(period: de.umass.lastfm.Period = de.umass.lastfm.Period.OVERALL, limit: Int = 10): Result<List<Track>> = withContext(Dispatchers.IO) {
        try {
            val username = getUsername() ?: return@withContext Result.failure(Exception("No username"))
            val tracks = User.getTopTracks(username, period, API_KEY)
            Result.success(tracks.take(limit))
        } catch (e: Exception) {
            Timber.e(e, "Error getting top tracks")
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
