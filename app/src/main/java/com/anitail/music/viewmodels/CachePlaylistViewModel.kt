package com.anitail.music.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.datasource.cache.SimpleCache
import com.anitail.music.db.MusicDatabase
import com.anitail.music.db.entities.Song
import com.anitail.music.di.DownloadCache
import com.anitail.music.di.PlayerCache
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import javax.inject.Inject

@HiltViewModel
class CachePlaylistViewModel @Inject constructor(
    private val database: MusicDatabase,
    @PlayerCache private val playerCache: SimpleCache,
    @DownloadCache private val downloadCache: SimpleCache
) : ViewModel() {

    private val _cachedSongs = MutableStateFlow<List<Song>>(emptyList())
    val cachedSongs: StateFlow<List<Song>> = _cachedSongs

    init {
        viewModelScope.launch {
            while (true) {
                val cachedIds = playerCache.keys.mapNotNull { it?.toString() }.toSet()
                val downloadedIds = downloadCache.keys.mapNotNull { it?.toString() }.toSet()
                val pureCacheIds = cachedIds.subtract(downloadedIds)

                val songs = if (pureCacheIds.isNotEmpty()) {
                    database.getSongsByIds(pureCacheIds.toList())
                } else {
                    emptyList()
                }

                if (songs.isNotEmpty()) {
                    database.query {
                        songs.forEach {
                            if (it.song.dateDownload == null) {
                                update(it.song.copy(dateDownload = LocalDateTime.now()))
                            }
                        }
                    }
                }

                _cachedSongs.value = songs
                    .filter { it.song.dateDownload != null }
                    .sortedByDescending { it.song.dateDownload }

                delay(1000)
            }
        }
    }
}