package com.anitail.music.ui.screens.settings.import_from_spotify.model

import com.anitail.music.models.spotify.playlists.SpotifyPlaylistItem

data class ImportFromSpotifyScreenState(
    val isRequesting: Boolean = false,
    val accessToken: String = "",
    val error: Boolean = false,
    val exception: Exception? = null,
    val userName: String = "",
    val isObtainingAccessTokenSuccessful: Boolean = false,
    val playlists: List<SpotifyPlaylistItem> = emptyList(),
    val totalPlaylistsCount: Int = 0,
    val reachedEndForPlaylistPagination: Boolean = false,
    val needsCredentialsForRefresh: Boolean = false,
    val storedRefreshToken: String? = null
)