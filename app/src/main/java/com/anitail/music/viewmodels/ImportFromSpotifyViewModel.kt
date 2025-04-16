package com.anitail.music.viewmodels

import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.datastore.preferences.core.edit // Added import
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anitail.innertube.YouTube
import com.anitail.innertube.models.SongItem
import com.anitail.music.constants.SpotifyAccessTokenKey
import com.anitail.music.constants.SpotifyRefreshTokenKey
import com.anitail.music.db.MusicDatabase
import com.anitail.music.db.entities.ArtistEntity
import com.anitail.music.db.entities.PlaylistEntity
import com.anitail.music.db.entities.PlaylistSongMap
import com.anitail.music.db.entities.SongArtistMap
import com.anitail.music.db.entities.SongEntity
import com.anitail.music.models.SpotifyAuthResponse
import com.anitail.music.models.SpotifyUserProfile
import com.anitail.music.models.spotify.playlists.SpotifyPlaylistPaginatedResponse
import com.anitail.music.models.spotify.tracks.SpotifyResultPaginatedResponse
import com.anitail.music.models.spotify.tracks.TrackItem
import com.anitail.music.ui.screens.settings.import_from_spotify.model.ImportFromSpotifyScreenState
import com.anitail.music.ui.screens.settings.import_from_spotify.model.ImportProgressEvent
import com.anitail.music.ui.screens.settings.import_from_spotify.model.Playlist
import com.anitail.music.utils.dataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.basicAuth
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Parameters
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.time.LocalDateTime
import javax.inject.Inject

@HiltViewModel
class ImportFromSpotifyViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val httpClient: HttpClient, private val localDatabase: MusicDatabase
) : ViewModel() {
    val importFromSpotifyScreenState = mutableStateOf(
        ImportFromSpotifyScreenState(
            isRequesting = false,
            accessToken = "",
            error = false,
            exception = null,
            userName = "",
            isObtainingAccessTokenSuccessful = false,
            playlists = emptyList(),
            totalPlaylistsCount = 0,
            reachedEndForPlaylistPagination = false
        )
    )
    val selectedPlaylists = mutableStateListOf<Playlist>()
    val isLikedSongsSelectedForImport = mutableStateOf(false)
    val isImportingCompleted = mutableStateOf(false)
    val isImportingInProgress = mutableStateOf(false)

    init {
        checkAndUseExistingTokens()
    }

    private fun checkAndUseExistingTokens() {
        viewModelScope.launch {
            importFromSpotifyScreenState.value = importFromSpotifyScreenState.value.copy(isRequesting = true)
            val accessToken = context.dataStore.data.firstOrNull()?.get(SpotifyAccessTokenKey)
            val refreshToken = context.dataStore.data.firstOrNull()?.get(SpotifyRefreshTokenKey)

            if (accessToken != null) {
                try {
                    // Try using the existing access token
                    val userProfile = getUserProfileFromSpotify(accessToken, context)
                    if (userProfile.displayName.isNotEmpty()) { // Check if token is valid by fetching profile
                        importFromSpotifyScreenState.value = importFromSpotifyScreenState.value.copy(
                            accessToken = accessToken,
                            userName = userProfile.displayName,
                            isObtainingAccessTokenSuccessful = true,
                            isRequesting = false
                        )
                        fetchInitialPlaylists(accessToken)
                    } else {
                        if (refreshToken != null) {
                            refreshAccessToken(refreshToken)
                        } else {
                            clearTokensAndResetState()
                        }
                    }
                } catch (e: Exception) {
                    Timber.tag("SpotifyAuth").e(e, "Error validating access token")
                    //
                    if (refreshToken != null) {
                        importFromSpotifyScreenState.value = importFromSpotifyScreenState.value.copy(
                            isRequesting = false,
                            error = true,
                            exception = Exception("Client credentials required for token refresh"),
                            needsCredentialsForRefresh = true,
                            storedRefreshToken = refreshToken
                        )
                    } else {
                        // No refresh token, need to login
                        clearTokensAndResetState()
                    }
                }
            } else {
                // No access token found, need to login
                importFromSpotifyScreenState.value = importFromSpotifyScreenState.value.copy(isRequesting = false, isObtainingAccessTokenSuccessful = false)
            }
        }
    }

    private suspend fun refreshAccessToken(refreshToken: String, spotifyClientId: String = "", spotifyClientSecret: String = "") {
        // Check if credentials are provided
        if (spotifyClientId.isBlank() || spotifyClientSecret.isBlank()) {
            importFromSpotifyScreenState.value = importFromSpotifyScreenState.value.copy(
                isRequesting = false,
                error = true,
                exception = Exception("Client credentials required to refresh token"),
                isObtainingAccessTokenSuccessful = false
            )
            return
        }
        try {
            val response = httpClient.post(urlString = "https://accounts.spotify.com/api/token") {
                basicAuth(spotifyClientId, spotifyClientSecret) // Use injected credentials
                setBody(FormDataContent(Parameters.build {
                    append("grant_type", "refresh_token")
                    append("refresh_token", refreshToken)
                }))
            }

            if (response.status.isSuccess()) {
                val authResponse = response.body<SpotifyAuthResponse>()
                val newAccessToken = authResponse.accessToken
                // Spotify might issue a new refresh token, store it if provided
                val newRefreshToken = authResponse.refreshToken ?: refreshToken

                // Save new tokens
                context.dataStore.edit {
                    it[SpotifyAccessTokenKey] = newAccessToken
                    it[SpotifyRefreshTokenKey] = newRefreshToken
                }

                Timber.tag("SpotifyAuth").d("Access token refreshed successfully")

                // Use the new access token
                val userProfile = getUserProfileFromSpotify(newAccessToken, context)
                importFromSpotifyScreenState.value = importFromSpotifyScreenState.value.copy(
                    accessToken = newAccessToken,
                    userName = userProfile.displayName,
                    isObtainingAccessTokenSuccessful = true,
                    isRequesting = false
                )
                fetchInitialPlaylists(newAccessToken)

            } else {
                Timber.tag("SpotifyAuth").e("Failed to refresh token: ${response.status} - ${response.bodyAsText()}")
                // Refresh failed, clear tokens and require login
                clearTokensAndResetState()
            }
        } catch (e: Exception) {
            Timber.tag("SpotifyAuth").e(e, "Exception during token refresh")
            // Exception during refresh, clear tokens and require login
            clearTokensAndResetState()
        }
    }

    private suspend fun clearTokensAndResetState() {
        context.dataStore.edit {
            it.remove(SpotifyAccessTokenKey)
            it.remove(SpotifyRefreshTokenKey)
        }
        importFromSpotifyScreenState.value = importFromSpotifyScreenState.value.copy(
            isRequesting = false,
            accessToken = "",
            error = false, // Reset error state
            exception = null,
            userName = "",
            isObtainingAccessTokenSuccessful = false,
            playlists = emptyList(),
            totalPlaylistsCount = 0,
            reachedEndForPlaylistPagination = false
        )
        // Reset pagination state
        playListPaginationOffset = -paginatedResultsLimit
    }
    fun spotifyLoginAndFetchPlaylists(
        clientId: String, clientSecret: String, authorizationCode: String, context: Context
    ) {
        viewModelScope.launch {
            try {
                importFromSpotifyScreenState.value = importFromSpotifyScreenState.value.copy(
                    isRequesting = true,
                    error = false,
                    exception = null,
                    accessToken = "",
                    userName = "",
                    isObtainingAccessTokenSuccessful = false,
                    playlists = emptyList()
                )
                getSpotifyAccessTokenDataResponse(
                    clientId = clientId,
                    clientSecret = clientSecret,
                    authorizationCode = authorizationCode,
                    context = context
                ).onSuccess {
                    it.let { response ->
                        if (response.status.isSuccess()) {
                            importFromSpotifyScreenState.value =
                                importFromSpotifyScreenState.value.copy(
                                    accessToken = response.body<SpotifyAuthResponse>().accessToken, // Keep this for immediate use
                                    isRequesting = false,
                                    isObtainingAccessTokenSuccessful = true
                                )

                           // Save tokens to DataStore
                           val authResponse = response.body<SpotifyAuthResponse>()
                           context.dataStore.edit {
                               it[SpotifyAccessTokenKey] = authResponse.accessToken
                               authResponse.refreshToken?.let { refreshToken ->
                                   it[SpotifyRefreshTokenKey] = refreshToken
                               }
                           }

                            logTheString("Access token obtained and saved.") // Updated log

                            // Fetch profile and initial playlists after successful login
                            val userProfile = getUserProfileFromSpotify(importFromSpotifyScreenState.value.accessToken, context)
                            importFromSpotifyScreenState.value = importFromSpotifyScreenState.value.copy(
                                userName = userProfile.displayName
                            )
                            fetchInitialPlaylists(importFromSpotifyScreenState.value.accessToken)

                        } else {
                            throw Exception("Request failed with status code : ${response.status.value}\n${response.bodyAsText()}")
                        }
                    }
                }
            } catch (e: Exception) {
                importFromSpotifyScreenState.value = importFromSpotifyScreenState.value.copy(
                    isRequesting = false,
                    error = true,
                    exception = e,
                    accessToken = "",
                    isObtainingAccessTokenSuccessful = false
                )
            }
        }
    }

    // Extracted playlist fetching logic
    private fun fetchInitialPlaylists(token: String) {
        viewModelScope.launch {
            // Reset pagination before fetching initial playlists
            playListPaginationOffset = -paginatedResultsLimit
            importFromSpotifyScreenState.value = importFromSpotifyScreenState.value.copy(
                playlists = emptyList(), // Clear existing playlists before fetching new ones
                totalPlaylistsCount = 0,
                reachedEndForPlaylistPagination = false
            )
            getPlaylists(token, context).let {
                importFromSpotifyScreenState.value = importFromSpotifyScreenState.value.copy(
                    playlists = it.items, // Replace, don't append
                    totalPlaylistsCount = it.totalResults ?: 0,
                    reachedEndForPlaylistPagination = it.nextUrl == null
                )
            }
        }
    }

    private var paginatedResultsLimit = 50
    private var playListPaginationOffset = -paginatedResultsLimit

    fun retrieveNextPageOfPlaylists(context: Context) {
        // Ensure we have a valid token before fetching next page
        val currentToken = importFromSpotifyScreenState.value.accessToken
        if (currentToken.isBlank()) {
            logTheString("Cannot retrieve next page, no valid access token.")
            // Optionally trigger re-login or refresh here if needed
            return
        }
        viewModelScope.launch {
            importFromSpotifyScreenState.value =
                importFromSpotifyScreenState.value.copy(isRequesting = true)
            getPlaylists(currentToken, context).let {
                importFromSpotifyScreenState.value = importFromSpotifyScreenState.value.copy(
                    playlists = importFromSpotifyScreenState.value.playlists + it.items,
                    totalPlaylistsCount = it.totalResults ?: 0,
                    reachedEndForPlaylistPagination = it.nextUrl == null,
                    isRequesting = false
                )
            }
        }
    }

    val selectedAllPlaylists = mutableStateOf(false)
    fun selectAllPlaylists(context: Context, onCompletion: () -> Unit) {
        isLikedSongsSelectedForImport.value = true
        selectedAllPlaylists.value = false
        selectedPlaylists.clear()
        viewModelScope.launch {
            selectedPlaylists.addAll(fetchAllPlaylists(importFromSpotifyScreenState.value.accessToken))
        }.invokeOnCompletion {
            selectedAllPlaylists.value = true
            onCompletion()
            viewModelScope.launch {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context, "Selected all playlists successfully", Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private suspend fun fetchAllPlaylists(
        authToken: String
    ): List<Playlist> {
        var url: String? = "https://api.spotify.com/v1/me/playlists"
        val playlists = mutableListOf<Playlist>()

        while (url != null) {
            httpClient.get(url) {
                bearerAuth(authToken)
            }.body<SpotifyPlaylistPaginatedResponse>().let { response ->
                if (response.items.isNotEmpty()) {
                    playlists.addAll(response.items.map {
                        Playlist(
                            name = it.playlistName, id = it.playlistId
                        )
                    })
                }
                logTheString(url.toString())
                url = response.nextUrl
            }
        }
        return playlists
    }

    private suspend fun getPlaylists(
        authToken: String, context: Context
    ): SpotifyPlaylistPaginatedResponse {
        if (playListPaginationOffset >= -paginatedResultsLimit) {
             playListPaginationOffset += paginatedResultsLimit
        }
        return try {
            httpClient.get("https://api.spotify.com/v1/me/playlists?offset=${playListPaginationOffset.coerceAtLeast(0)}&limit=$paginatedResultsLimit") {
                bearerAuth(authToken)
            }.body<SpotifyPlaylistPaginatedResponse>()
        } catch (e: Exception) {
            Toast.makeText(context, e.message.toString(), Toast.LENGTH_SHORT).show()
            SpotifyPlaylistPaginatedResponse()
        }
    }

    private suspend fun getLikedSongsFromSpotify(
        authToken: String, url: String, context: Context
    ): SpotifyResultPaginatedResponse {
        return try {
            httpClient.get(url) {
                bearerAuth(authToken)
            }.body<SpotifyResultPaginatedResponse>()
        } catch (e: Exception) {
            Toast.makeText(context, e.message.toString(), Toast.LENGTH_SHORT).show()
            SpotifyResultPaginatedResponse(
                totalCountOfLikedSongs = 0, nextPaginatedUrl = null, tracks = listOf()
            )
        }
    }

    private suspend fun getUserProfileFromSpotify(
        authToken: String, context: Context
    ): SpotifyUserProfile {
        return try {
            httpClient.get("https://api.spotify.com/v1/me") {
                bearerAuth(authToken)
            }.body<SpotifyUserProfile>()
        } catch (e: Exception) {
            Toast.makeText(context, e.message.toString(), Toast.LENGTH_SHORT).show()
            SpotifyUserProfile(displayName = "")
        }
    }

    private suspend fun getSpotifyAccessTokenDataResponse(
        authorizationCode: String, clientId: String, clientSecret: String, context: Context
    ): Result<HttpResponse> {
        return try {
            Result.success(httpClient.post(urlString = "https://accounts.spotify.com/api/token") {
                basicAuth(clientId, clientSecret)
                setBody(FormDataContent(Parameters.build {
                   append("grant_type", "authorization_code")
                   append("code", authorizationCode)
                    // Use the correct redirect URI configured in Spotify Developer Dashboard
                    append("redirect_uri", "http://127.0.0.1:8888")
               }))
            })
        } catch (e: Exception) {
            Toast.makeText(context, e.message.toString(), Toast.LENGTH_SHORT).show()
            Result.failure(e)
        }
    }

    fun logTheString(string: String) {
        Timber.tag("Muzza Log").d(string)
    }

    private val generatedPlaylistId = PlaylistEntity.generatePlaylistId()
    private val currentTime = LocalDateTime.now()


    private val _likedSongsImportProgress = MutableStateFlow(
        ImportProgressEvent.LikedSongsProgress(
            completed = false, currentCount = 0, totalTracksCount = 0
        )
    )
    val importLogs = mutableStateListOf<String>()

    private val _playlistsImportProgress = MutableStateFlow(
        ImportProgressEvent.PlaylistsProgress(
            completed = false,
            playlistName = "",
            progressedTrackCount = 0,
            totalTracksCount = 0,
            currentPlaylistIndex = 0,

            )
    )

    private var progressedTracksInAPlaylistCount = 0

    init {
        viewModelScope.launch {
            _playlistsImportProgress.collectLatest {
                "Importing playlist \"${it.playlistName}\" – ${it.progressedTrackCount}/${it.totalTracksCount} tracks completed".let {
                    importLogs.add(it)
                    logTheString(it)
                }
            }
        }
        viewModelScope.launch {
            _likedSongsImportProgress.collectLatest {
                "Importing Liked Songs – ${it.currentCount} of ${it.totalTracksCount} completed".let {
                    importLogs.add(it)
                    logTheString(it)
                }
            }
        }
    }

    fun importSelectedItems(saveInDefaultLikedSongs: Boolean?, context: Context) {
        importLogs.clear()
        isImportingCompleted.value = false
        isImportingInProgress.value = true
        viewModelScope.launch(Dispatchers.IO) {
            supervisorScope {
                logTheString("Starting the import process")
                val likedSongsJob = launch {
                    saveInDefaultLikedSongs?.let {
                        importSpotifyLikedSongs(it, context)
                    }
                }

                val playlistsJob = launch {
                    importPlaylists(
                        selectedPlaylists, importFromSpotifyScreenState.value.accessToken
                    )
                }
                likedSongsJob.join()
                playlistsJob.join()
                logTheString("Import Succeeded!")
                isImportingCompleted.value = true
                isImportingInProgress.value = false
            }
        }
    }

    private suspend fun importPlaylists(
        selectedPlaylists: List<Playlist>, authToken: String
    ) = supervisorScope {
        selectedPlaylists.forEachIndexed { playlistIndex, playlist ->
            progressedTracksInAPlaylistCount = 0
            val generatedPlaylistId = PlaylistEntity.generatePlaylistId()
            localDatabase.insert(
                PlaylistEntity(
                    id = generatedPlaylistId, name = playlist.name, bookmarkedAt = currentTime
                )
            )
            getTracksFromAPlaylist(
                spotifyPlaylistId = playlist.id, authToken = authToken
            ).let { trackItems ->
                val trackJobs = mutableListOf<Job>()
                trackItems.forEach { trackItem ->
                    trackJobs.add(launch {
                        val youtubeSearchResult = YouTube.search(
                            query = trackItem.trackName + " " + trackItem.artists.first().name,
                            filter = YouTube.SearchFilter.FILTER_SONG
                        )
                        youtubeSearchResult.onSuccess { result ->
                            if (result.items.isEmpty()) {
                                return@onSuccess
                            }
                            val firstSong = result.items.first() as SongItem
                            firstSong.artists.forEachIndexed { index, artist ->
                                artist.id?.let { artistId ->
                                    try {
                                        if (localDatabase.artistIdExists(artistId).not()) {
                                            YouTube.artist(artistId).onSuccess { artistPage ->
                                                localDatabase.insert(
                                                    ArtistEntity(
                                                        id = artistId,
                                                        name = artistPage.artist.title
                                                    )
                                                )
                                            }
                                        }
                                        localDatabase.insert(
                                            SongArtistMap(
                                                songId = firstSong.id,
                                                artistId = artistId,
                                                position = index
                                            )
                                        )
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }
                            }
                            localDatabase.insert(
                                SongEntity(
                                    id = firstSong.id,
                                    thumbnailUrl = firstSong.thumbnail.getOriginalSizeThumbnail(),
                                    title = firstSong.title
                                )
                            )
                            localDatabase.insert(
                                PlaylistSongMap(
                                    playlistId = generatedPlaylistId, songId = firstSong.id
                                )
                            )
                            _playlistsImportProgress.emit(
                                ImportProgressEvent.PlaylistsProgress(
                                    completed = false,
                                    progressedTrackCount = ++progressedTracksInAPlaylistCount,
                                    playlistName = playlist.name,
                                    totalTracksCount = trackItems.size,
                                    currentPlaylistIndex = playlistIndex
                                )
                            )
                        }
                    })
                }
                trackJobs.joinAll()
            }
        }
        _playlistsImportProgress.emit(
            ImportProgressEvent.PlaylistsProgress(
                completed = true,
                playlistName = "",
                progressedTrackCount = 0,
                totalTracksCount = 0,
                currentPlaylistIndex = 0,
            )
        )
    }

    private suspend fun getTracksFromAPlaylist(
        spotifyPlaylistId: String, authToken: String
    ): List<TrackItem> {
        var url: String? = "https://api.spotify.com/v1/playlists/$spotifyPlaylistId/tracks"
        val tracks: MutableList<TrackItem> = mutableListOf()
        while (url != null) {
            try {
                httpClient.get(url) {
                    bearerAuth(authToken)
                }.body<SpotifyResultPaginatedResponse>().let {
                    tracks.addAll(it.tracks.map { it.trackItem })
                    url = it.nextPaginatedUrl
                }
            } catch (e: Exception) {
                url = null
                e.printStackTrace()
            }
        }
        return tracks
    }

    private var progressedTracksInTheLikedSongsCount = 0

    private suspend fun importSpotifyLikedSongs(
        saveInDefaultLikedSongs: Boolean, context: Context
    ): Unit = supervisorScope {
        var url: String? = "https://api.spotify.com/v1/me/tracks?offset=0&limit=50"
        var totalSongsCount = -1
        while (url != null) {
            getLikedSongsFromSpotify(
                authToken = importFromSpotifyScreenState.value.accessToken, url = url, context
            ).let { spotifyLikedSongsPaginatedResponse ->
                totalSongsCount = spotifyLikedSongsPaginatedResponse.totalCountOfLikedSongs
                spotifyLikedSongsPaginatedResponse.tracks.forEachIndexed { index, likedSong ->
                    launch {
                        val youtubeSearchResult = YouTube.search(
                            query = likedSong.trackItem.trackName + " " + likedSong.trackItem.artists.first().name,
                            filter = YouTube.SearchFilter.FILTER_SONG
                        )
                        youtubeSearchResult.onSuccess { result ->
                            if (result.items.isEmpty()) {
                                return@onSuccess
                            }
                            result.items.first().let { songItem ->
                                songItem as SongItem
                                withContext(Dispatchers.IO) {
                                    localDatabase.insert(
                                        SongEntity(
                                            id = songItem.id,
                                            title = songItem.title,
                                            liked = saveInDefaultLikedSongs,
                                            thumbnailUrl = songItem.thumbnail.getOriginalSizeThumbnail()
                                        )
                                    )
                                    songItem.artists.forEachIndexed { index, artist ->
                                        artist.id?.let { artistId ->
                                            try {
                                                if (localDatabase.artistIdExists(artistId).not()) {
                                                    YouTube.artist(artistId)
                                                        .onSuccess { artistPage ->
                                                            localDatabase.insert(
                                                                ArtistEntity(
                                                                    id = artistId,
                                                                    name = artistPage.artist.title
                                                                )
                                                            )
                                                        }
                                                }
                                                localDatabase.insert(
                                                    SongArtistMap(
                                                        songId = songItem.id,
                                                        artistId = artistId,
                                                        position = index
                                                    )
                                                )
                                            } catch (e: Exception) {
                                                e.printStackTrace()
                                            }
                                        }
                                    }
                                    if (saveInDefaultLikedSongs.not()) {
                                        localDatabase.insert(
                                            PlaylistEntity(
                                                id = generatedPlaylistId,
                                                name = "Liked Songs",
                                                bookmarkedAt = currentTime
                                            )
                                        )
                                        localDatabase.insert(
                                            PlaylistSongMap(
                                                playlistId = generatedPlaylistId,
                                                songId = songItem.id
                                            )
                                        )
                                    } else {/*
                                     Updates `liked` to `true` for a song already in the database.
                                     Does not affect a newly added song if it is already marked as `liked`.
                                     */
                                        localDatabase.toggleLikedToTrue(songId = songItem.id)
                                    }
                                }
                            }
                            _likedSongsImportProgress.emit(
                                ImportProgressEvent.LikedSongsProgress(
                                    completed = false,
                                    currentCount = ++progressedTracksInTheLikedSongsCount,
                                    totalTracksCount = spotifyLikedSongsPaginatedResponse.totalCountOfLikedSongs
                                )
                            )
                        }
                    }
                }
                url = spotifyLikedSongsPaginatedResponse.nextPaginatedUrl
            }
        }
        _likedSongsImportProgress.emit(
            ImportProgressEvent.LikedSongsProgress(
                completed = true,
                currentCount = progressedTracksInTheLikedSongsCount,
                totalTracksCount = totalSongsCount
            )
        )
    }
}

private fun String.getOriginalSizeThumbnail(): String {
    return this.substringBefore("=w")
}
