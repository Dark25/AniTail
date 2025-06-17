package com.anitail.music.utils

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.anitail.music.BuildConfig
import com.anitail.music.constants.LastFmEnabledKey
import com.anitail.music.constants.LastFmLoveTracksKey
import com.anitail.music.constants.LastFmScrobbleEnabledKey
import com.anitail.music.constants.LastFmSessionKey
import com.anitail.music.constants.LastFmUsernameKey
import com.anitail.music.db.entities.Song
import dagger.hilt.android.qualifiers.ApplicationContext
import de.umass.lastfm.Authenticator
import de.umass.lastfm.Caller
import de.umass.lastfm.scrobble.ScrobbleData
import de.umass.lastfm.Session
import de.umass.lastfm.Track
import de.umass.lastfm.User
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LastFmService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataStore: DataStore<Preferences>
) {    companion object {
        private val API_KEY = BuildConfig.LASTFM_API_KEY
        private val API_SECRET = BuildConfig.LASTFM_API_SECRET
        private const val USER_AGENT = "Anitail Music"
    }private var session: Session? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    
    init {
        // Configure Last.fm API to use HTTPS
        Caller.getInstance().userAgent = USER_AGENT
        // Force HTTPS by setting the cache to use secure URLs
        try {
            System.setProperty("lastfm.api.url", "https://ws.audioscrobbler.com/2.0/")
        } catch (e: Exception) {
            Timber.w("Could not set Last.fm API URL to HTTPS: ${e.message}")
        }
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
                } else {
                    Timber.w("Failed to scrobble: $artist - $title")
                }
            } catch (e: Exception) {
                Timber.e(e, "Error scrobbling track")
            }
        }
    }    fun updateNowPlaying(song: Song) {
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
    }

    suspend fun enableLoveTracks(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[LastFmLoveTracksKey] = enabled
        }
    }
}
