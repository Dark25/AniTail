@file:Suppress("DEPRECATION")

package com.anitail.music.playback

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.database.SQLException
import android.media.audiofx.AudioEffect
import android.net.ConnectivityManager
import android.os.Binder
import android.os.Build
import android.os.Looper
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import androidx.datastore.preferences.core.edit
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Player.EVENT_POSITION_DISCONTINUITY
import androidx.media3.common.Player.EVENT_TIMELINE_CHANGED
import androidx.media3.common.Player.REPEAT_MODE_ALL
import androidx.media3.common.Player.REPEAT_MODE_OFF
import androidx.media3.common.Player.REPEAT_MODE_ONE
import androidx.media3.common.Player.STATE_IDLE
import androidx.media3.common.Player.STATE_READY
import androidx.media3.common.Timeline
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.analytics.PlaybackStats
import androidx.media3.exoplayer.analytics.PlaybackStatsListener
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.SilenceSkippingAudioProcessor
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.mkv.MatroskaExtractor
import androidx.media3.extractor.mp4.FragmentedMp4Extractor
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaController
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionToken
import com.anitail.innertube.YouTube
import com.anitail.innertube.models.SongItem
import com.anitail.innertube.models.WatchEndpoint
import com.anitail.jossredconnect.JossRedClient
import com.anitail.music.MainActivity
import com.anitail.music.R
import com.anitail.music.constants.AudioNormalizationKey
import com.anitail.music.constants.AudioQualityKey
import com.anitail.music.constants.AutoDownloadLyricsKey
import com.anitail.music.constants.AutoDownloadOnLikeKey
import com.anitail.music.constants.AutoLoadMoreKey
import com.anitail.music.constants.AutoSkipNextOnErrorKey
import com.anitail.music.constants.DiscordTokenKey
import com.anitail.music.constants.EnableDiscordRPCKey
import com.anitail.music.constants.HideExplicitKey
import com.anitail.music.constants.HistoryDuration
import com.anitail.music.constants.JossRedMultimedia
import com.anitail.music.constants.MediaSessionConstants.CommandClosePlayer
import com.anitail.music.constants.MediaSessionConstants.CommandToggleLike
import com.anitail.music.constants.MediaSessionConstants.CommandToggleRepeatMode
import com.anitail.music.constants.MediaSessionConstants.CommandToggleShuffle
import com.anitail.music.constants.NotificationButtonType
import com.anitail.music.constants.NotificationButtonTypeKey
import com.anitail.music.constants.PauseListenHistoryKey
import com.anitail.music.constants.PauseRemoteListenHistoryKey
import com.anitail.music.constants.PersistentQueueKey
import com.anitail.music.constants.PlayerVolumeKey
import com.anitail.music.constants.RepeatModeKey
import com.anitail.music.constants.ShowLyricsKey
import com.anitail.music.constants.SimilarContent
import com.anitail.music.constants.SkipSilenceKey
import com.anitail.music.db.MusicDatabase
import com.anitail.music.db.entities.Event
import com.anitail.music.db.entities.FormatEntity
import com.anitail.music.db.entities.LyricsEntity
import com.anitail.music.db.entities.RelatedSongMap
import com.anitail.music.di.DownloadCache
import com.anitail.music.di.PlayerCache
import com.anitail.music.extensions.SilentHandler
import com.anitail.music.extensions.collect
import com.anitail.music.extensions.collectLatest
import com.anitail.music.extensions.currentMetadata
import com.anitail.music.extensions.findNextMediaItemById
import com.anitail.music.extensions.mediaItems
import com.anitail.music.extensions.metadata
import com.anitail.music.extensions.toMediaItem
import com.anitail.music.lyrics.LyricsHelper
import com.anitail.music.models.PersistQueue
import com.anitail.music.models.toMediaMetadata
import com.anitail.music.playback.queues.EmptyQueue
import com.anitail.music.playback.queues.ListQueue
import com.anitail.music.playback.queues.Queue
import com.anitail.music.playback.queues.YouTubeQueue
import com.anitail.music.playback.queues.filterExplicit
import com.anitail.music.ui.component.MusicWidgetProvider.Companion.ACTION_NEXT
import com.anitail.music.ui.component.MusicWidgetProvider.Companion.ACTION_PLAY_PAUSE
import com.anitail.music.ui.component.MusicWidgetProvider.Companion.ACTION_PREV
import com.anitail.music.utils.CoilBitmapLoader
import com.anitail.music.utils.DiscordRPC
import com.anitail.music.utils.SyncUtils
import com.anitail.music.utils.YTPlayerUtils
import com.anitail.music.utils.dataStore
import com.anitail.music.utils.enumPreference
import com.anitail.music.utils.get
import com.anitail.music.utils.isInternetAvailable
import com.anitail.music.utils.reportException
import com.google.common.util.concurrent.MoreExecutors
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import timber.log.Timber
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.time.LocalDateTime
import javax.inject.Inject
import kotlin.math.min
import kotlin.math.pow
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@AndroidEntryPoint
class MusicService :
    MediaLibraryService(),
    Player.Listener,
    PlaybackStatsListener.Callback {
    @Inject
    lateinit var database: MusicDatabase

    @Inject
    lateinit var lyricsHelper: LyricsHelper

    @Inject
    lateinit var syncUtils: SyncUtils

    private var widgetUpdateJob: Job? = null

    @Inject
    lateinit var mediaLibrarySessionCallback: MediaLibrarySessionCallback

    private var scope = CoroutineScope(Dispatchers.Main) + Job()
    private val binder = MusicBinder()

    private lateinit var connectivityManager: ConnectivityManager
    private val songUrlCache = HashMap<String, Pair<String, Long>>()

    private val audioQuality by enumPreference(
        this,
        AudioQualityKey,
        com.anitail.music.constants.AudioQuality.AUTO
    )
    private val buttonType by enumPreference(
        this,
        NotificationButtonTypeKey,
        NotificationButtonType.CLOSE
    )

    private var currentQueue: Queue = EmptyQueue
    var queueTitle: String? = null

    val currentMediaMetadata = MutableStateFlow<com.anitail.music.models.MediaMetadata?>(null)
    private val currentSong =
        currentMediaMetadata
            .flatMapLatest { mediaMetadata ->
                database.song(mediaMetadata?.id)
            }.stateIn(scope, SharingStarted.Lazily, null)
    private val currentFormat =
        currentMediaMetadata.flatMapLatest { mediaMetadata ->
            database.format(mediaMetadata?.id)
        }

    private val normalizeFactor = MutableStateFlow(1f)
    val playerVolume = MutableStateFlow(dataStore.get(PlayerVolumeKey, 1f).coerceIn(0f, 1f))

    lateinit var sleepTimer: SleepTimer

    @Inject
    @PlayerCache
    lateinit var playerCache: SimpleCache

    @Inject
    @DownloadCache
    lateinit var downloadCache: SimpleCache

    lateinit var player: ExoPlayer
    private lateinit var mediaSession: MediaLibrarySession

    private var isAudioEffectSessionOpened = false

    private var discordRpc: DiscordRPC? = null

    val automixItems = MutableStateFlow<List<MediaItem>>(emptyList())
    override fun onCreate() {
        super.onCreate()
        instance = this
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider(
                this,
                { NOTIFICATION_ID },
                CHANNEL_ID,
                R.string.music_player
            )
                .apply {
                    setSmallIcon(R.drawable.ic_ani)
                },
        )
        player =
            ExoPlayer
                .Builder(this)
                .setMediaSourceFactory(createMediaSourceFactory())
                .setRenderersFactory(createRenderersFactory())
                .setHandleAudioBecomingNoisy(true)
                .setWakeMode(C.WAKE_MODE_NETWORK)
                .setAudioAttributes(
                    AudioAttributes
                        .Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .build(),
                    true,
                ).setSeekBackIncrementMs(5000)
                .setSeekForwardIncrementMs(5000)
                .build()
                .apply {
                    addListener(this@MusicService)
                    sleepTimer = SleepTimer(scope, this)
                    addListener(sleepTimer)
                    addAnalyticsListener(PlaybackStatsListener(false, this@MusicService))
                }
        mediaLibrarySessionCallback.apply {
            toggleLike = ::toggleLike
            toggleLibrary = ::toggleLibrary
        }
        mediaSession =
            MediaLibrarySession
                .Builder(this, player, mediaLibrarySessionCallback)
                .setSessionActivity(
                    PendingIntent.getActivity(
                        this,
                        0,
                        Intent(this, MainActivity::class.java),
                        PendingIntent.FLAG_IMMUTABLE,
                    ),
                ).setBitmapLoader(CoilBitmapLoader(this, scope))
                .build()
        player.repeatMode = dataStore.get(RepeatModeKey, REPEAT_MODE_OFF)

        // Keep a connected controller so that notification works
        val sessionToken = SessionToken(this, ComponentName(this, MusicService::class.java))
        val controllerFuture = MediaController.Builder(this, sessionToken).buildAsync()
        controllerFuture.addListener({ controllerFuture.get() }, MoreExecutors.directExecutor())

        connectivityManager = getSystemService()!!

        combine(playerVolume, normalizeFactor) { playerVolume, normalizeFactor ->
            playerVolume * normalizeFactor
        }.collectLatest(scope) {
            player.volume = it
        }

        playerVolume.debounce(1000).collect(scope) { volume ->
            dataStore.edit { settings ->
                settings[PlayerVolumeKey] = volume
            }
        }
        currentSong.debounce(1000).collect(scope) { song ->
            updateNotification()
            if (song != null) {
                try {
                    discordRpc?.updateSong(
                        song = song,
                        timeStart = if (player.isPlaying)
                            System.currentTimeMillis() - player.currentPosition else 0L,
                        timeEnd = if (player.isPlaying)
                            (System.currentTimeMillis() - player.currentPosition) + player.duration else 0L
                    )
                } catch (e: Exception) {
                    Timber.e(e, "Error updating Discord RPC")
                    discordRpc?.closeRPC()
                }
            } else {
                discordRpc?.closeRPC()
            }
        }

        combine(
            currentMediaMetadata.distinctUntilChangedBy { it?.id },
            dataStore.data.map { it[ShowLyricsKey] ?: false }.distinctUntilChanged(),
        ) { mediaMetadata, showLyrics ->
            mediaMetadata to showLyrics
        }.collectLatest(scope) { (mediaMetadata, showLyrics) ->
            if (showLyrics && mediaMetadata != null && database.lyrics(mediaMetadata.id)
                    .first() == null
            ) {
                val lyrics = lyricsHelper.getLyrics(mediaMetadata)
                database.query {
                    upsert(
                        LyricsEntity(
                            id = mediaMetadata.id,
                            lyrics = lyrics,
                        ),
                    )
                }
            }
        }

        dataStore.data
            .map { it[SkipSilenceKey] ?: false }
            .distinctUntilChanged()
            .collectLatest(scope) {
                player.skipSilenceEnabled = it
            }

        combine(
            currentFormat,
            dataStore.data
                .map { it[AudioNormalizationKey] ?: true }
                .distinctUntilChanged(),
        ) { format, normalizeAudio ->
            format to normalizeAudio
        }.collectLatest(scope) { (format, normalizeAudio) ->
            normalizeFactor.value =
                if (normalizeAudio && format?.loudnessDb != null) {
                    min(10f.pow(-format.loudnessDb.toFloat() / 20), 1f)
                } else {
                    1f
                }
        }

        dataStore.data
            .map { it[DiscordTokenKey] to (it[EnableDiscordRPCKey] ?: true) }
            .debounce(300)
            .distinctUntilChanged()
            .collect(scope) { (key, enabled) ->
                if (discordRpc?.isRpcRunning() == true) {
                    discordRpc?.closeRPC()
                }
                discordRpc = null
                if (key != null && enabled) {
                    discordRpc = DiscordRPC(this, key)
                    currentSong.value?.let {
                        discordRpc?.updateSong(
                            song = it,
                            timeStart = if (player.isPlaying)
                                System.currentTimeMillis() - player.currentPosition else 0L,
                            timeEnd = if (player.isPlaying)
                                (System.currentTimeMillis() - player.currentPosition) + player.duration else 0L
                        )
                    }
                }
            }

        if (dataStore.get(PersistentQueueKey, true)) {
            runCatching {
                filesDir.resolve(PERSISTENT_QUEUE_FILE).inputStream().use { fis ->
                    ObjectInputStream(fis).use { oos ->
                        oos.readObject() as PersistQueue
                    }
                }
            }.onSuccess { queue ->
                playQueue(
                    queue =
                    ListQueue(
                        title = queue.title,
                        items = queue.items.map { it.toMediaItem() },
                        startIndex = queue.mediaItemIndex,
                        position = queue.position,
                    ),
                    playWhenReady = false,
                )
            }
            runCatching {
                filesDir.resolve(PERSISTENT_AUTOMIX_FILE).inputStream().use { fis ->
                    ObjectInputStream(fis).use { oos ->
                        oos.readObject() as PersistQueue
                    }
                }
            }.onSuccess { queue ->
                automixItems.value = queue.items.map { it.toMediaItem() }
            }
        }

        // Save queue periodically to prevent queue loss from crash or force kill
        scope.launch {
            while (isActive) {
                delay(30.seconds)
                if (dataStore.get(PersistentQueueKey, true)) {
                    saveQueueToDisk()
                }
            }
        }
    }
    private fun updateNotification() {
        val buttons = mutableListOf<CommandButton>()
        when (buttonType) {
            NotificationButtonType.LIKE -> {
                buttons.add(
                    CommandButton
                        .Builder()
                        .setDisplayName(
                            getString(
                                if (currentSong.value?.song?.liked == true) {
                                    R.string.action_remove_like
                                } else {
                                    R.string.action_like
                                },
                            ),
                        )
                        .setIconResId(if (currentSong.value?.song?.liked == true) R.drawable.favorite else R.drawable.favorite_border)
                        .setSessionCommand(CommandToggleLike)
                        .setEnabled(currentSong.value != null)
                        .build()
                )
            }
            NotificationButtonType.CLOSE -> {
                buttons.add(
                    CommandButton
                        .Builder()
                        .setDisplayName(getString(R.string.close))
                        .setIconResId(R.drawable.close)
                        .setSessionCommand(CommandClosePlayer)
                        .setEnabled(currentSong.value != null)
                        .build()
                )
            }
        }
        buttons.add(
            CommandButton
                .Builder()
                .setDisplayName(
                    getString(
                        when (player.repeatMode) {
                            REPEAT_MODE_OFF -> R.string.repeat_mode_off
                            REPEAT_MODE_ONE -> R.string.repeat_mode_one
                            REPEAT_MODE_ALL -> R.string.repeat_mode_all
                            else -> throw IllegalStateException()
                        },
                    ),
                ).setIconResId(
                    when (player.repeatMode) {
                        REPEAT_MODE_OFF -> R.drawable.repeat
                        REPEAT_MODE_ONE -> R.drawable.repeat_one_on
                        REPEAT_MODE_ALL -> R.drawable.repeat_on
                        else -> throw IllegalStateException()
                    },
                ).setSessionCommand(CommandToggleRepeatMode)
                .build()
        )

        buttons.add(
            CommandButton
                .Builder()
                .setDisplayName(getString(if (player.shuffleModeEnabled) R.string.action_shuffle_off else R.string.action_shuffle_on))
                .setIconResId(if (player.shuffleModeEnabled) R.drawable.shuffle_on else R.drawable.shuffle)
                .setSessionCommand(CommandToggleShuffle)
                .build()
        )

        mediaSession.setCustomLayout(buttons)
    }

    private suspend fun recoverSong(
        mediaId: String,
        playbackData: YTPlayerUtils.PlaybackData? = null
    ) {
        val song = database.song(mediaId).first()
        val mediaMetadata = withContext(Dispatchers.Main) {
            player.findNextMediaItemById(mediaId)?.metadata
        } ?: return
        val duration = song?.song?.duration?.takeIf { it != -1 }
            ?: mediaMetadata.duration.takeIf { it != -1 }
            ?: (playbackData?.videoDetails ?: YTPlayerUtils.playerResponseForMetadata(mediaId)
                .getOrNull()?.videoDetails)?.lengthSeconds?.toInt()
            ?: -1
        database.query {
            if (song == null) insert(mediaMetadata.copy(duration = duration))
            else if (song.song.duration == -1) update(song.song.copy(duration = duration))
        }
        if (!database.hasRelatedSongs(mediaId)) {
            val relatedEndpoint =
                YouTube.next(WatchEndpoint(videoId = mediaId)).getOrNull()?.relatedEndpoint
                    ?: return
            val relatedPage = YouTube.related(relatedEndpoint).getOrNull() ?: return
            database.query {
                relatedPage.songs
                    .map(SongItem::toMediaMetadata)
                    .onEach(::insert)
                    .map {
                        RelatedSongMap(
                            songId = mediaId,
                            relatedSongId = it.id
                        )
                    }
                    .forEach(::insert)
            }
        }
    }

    fun playQueue(
        queue: Queue,
        playWhenReady: Boolean = true,
    ) {
        if (!scope.isActive) scope = CoroutineScope(Dispatchers.Main) + Job()
        currentQueue = queue
        queueTitle = null
        player.shuffleModeEnabled = false
        if (queue.preloadItem != null) {
            player.setMediaItem(queue.preloadItem!!.toMediaItem())
            player.prepare()
            player.playWhenReady = playWhenReady
        }
        scope.launch(SilentHandler) {
            val initialStatus =
                withContext(Dispatchers.IO) {
                    queue.getInitialStatus().filterExplicit(dataStore.get(HideExplicitKey, false))
                }
            if (queue.preloadItem != null && player.playbackState == STATE_IDLE) return@launch
            if (initialStatus.title != null) {
                queueTitle = initialStatus.title
            }
            if (initialStatus.items.isEmpty()) return@launch
            if (queue.preloadItem != null) {
                player.addMediaItems(
                    0,
                    initialStatus.items.subList(0, initialStatus.mediaItemIndex)
                )
                player.addMediaItems(
                    initialStatus.items.subList(
                        initialStatus.mediaItemIndex + 1,
                        initialStatus.items.size
                    )
                )
            } else {
                player.setMediaItems(
                    initialStatus.items,
                    if (initialStatus.mediaItemIndex >
                        0
                    ) {
                        initialStatus.mediaItemIndex
                    } else {
                        0
                    },
                    initialStatus.position,
                )
                player.prepare()
                player.playWhenReady = playWhenReady
            }
        }
    }

    fun startRadioSeamlessly() {
        val currentMediaMetadata = player.currentMetadata ?: return
        if (player.currentMediaItemIndex > 0) player.removeMediaItems(
            0,
            player.currentMediaItemIndex
        )
        if (player.currentMediaItemIndex <
            player.mediaItemCount - 1
        ) {
            player.removeMediaItems(player.currentMediaItemIndex + 1, player.mediaItemCount)
        }
        scope.launch(SilentHandler) {
            val radioQueue =
                YouTubeQueue(endpoint = WatchEndpoint(videoId = currentMediaMetadata.id))
            val initialStatus = radioQueue.getInitialStatus()
            if (initialStatus.title != null) {
                queueTitle = initialStatus.title
            }
            player.addMediaItems(initialStatus.items.drop(1))
            currentQueue = radioQueue
        }
    }

    fun getAutomixAlbum(albumId: String) {
        scope.launch(SilentHandler) {
            YouTube
                .album(albumId)
                .onSuccess {
                    getAutomix(it.album.playlistId)
                }
        }
    }

    fun getAutomix(playlistId: String) {
        if (dataStore[SimilarContent] == true) {
            scope.launch(SilentHandler) {
                YouTube
                    .next(WatchEndpoint(playlistId = playlistId))
                    .onSuccess {
                        YouTube
                            .next(WatchEndpoint(playlistId = it.endpoint.playlistId))
                            .onSuccess {
                                automixItems.value =
                                    it.items.map { song ->
                                        song.toMediaItem()
                                    }
                            }
                    }
            }
        }
    }

    fun addToQueueAutomix(
        item: MediaItem,
        position: Int,
    ) {
        automixItems.value =
            automixItems.value.toMutableList().apply {
                removeAt(position)
            }
        addToQueue(listOf(item))
    }

    fun playNextAutomix(
        item: MediaItem,
        position: Int,
    ) {
        automixItems.value =
            automixItems.value.toMutableList().apply {
                removeAt(position)
            }
        playNext(listOf(item))
    }

    fun clearAutomix() {
        filesDir.resolve(PERSISTENT_QUEUE_FILE).delete()
        automixItems.value = emptyList()
    }

    fun playNext(items: List<MediaItem>) {
        player.addMediaItems(
            if (player.mediaItemCount == 0) 0 else player.currentMediaItemIndex + 1,
            items
        )
        player.prepare()
    }

    fun addToQueue(items: List<MediaItem>) {
        player.addMediaItems(items)
        player.prepare()
    }

    private fun toggleLibrary() {
        database.query {
            currentSong.value?.let {
                update(it.song.toggleLibrary())
            }
        }
    }



    @RequiresApi(Build.VERSION_CODES.O)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == null) {
            return super.onStartCommand(intent, flags, startId)
        }


        when (intent.action) {
            ACTION_PLAY_RECOMMENDATION -> {
                val songId = intent.getStringExtra(EXTRA_WIDGET_RECOMMENDATION_ID)
                if (!songId.isNullOrBlank()) {
                    scope.launch(Dispatchers.IO + kotlinx.coroutines.SupervisorJob()) {
                        try {
                            val song = database.song(songId).firstOrNull()
                            if (song != null) {
                                val mediaItem = song.toMediaMetadata().toMediaItem()
                                withContext(Dispatchers.Main) {
                                    try {
                                        player.setMediaItem(mediaItem)
                                        player.prepare()
                                        player.playWhenReady = true
                                    } catch (e: Exception) {
                                        Timber.tag("MusicService").e(e, "Error to set media item")
                                    }
                                }
                            } else {
                                Timber.tag("MusicService").e("non existent songId: $songId")
                            }
                        } catch (e: Exception) {
                            Timber.tag("MusicService").e(e, "Error to play recommendation")
                        }
                    }
                } else {
                    Timber.tag("MusicService").e("songId is null or blank")
                }
            }
            ACTION_DOWNLOAD_LYRICS -> {
                val songId = intent.getStringExtra(EXTRA_SONG_ID)
                if (songId != null) {
                    scope.launch {
                        downloadLyricsForSong(songId)
                    }
                }
            }
            "com.anitail.music.action.UPDATE_WIDGET" -> {
                sendWidgetUpdateBroadcast()
            }
            ACTION_PLAY_PAUSE -> {

                val wasPlaying = player.isPlaying

                if (wasPlaying) {
                    player.pause()
                } else {
                    player.play()
                }

                sendWidgetUpdateBroadcast()
            }
            ACTION_NEXT -> {
                player.seekToNext()
                player.playWhenReady = true
            }
            ACTION_PREV -> {
                player.seekToPrevious()
                player.playWhenReady = true
            }
        }

        return super.onStartCommand(intent, flags, startId)
    }

    fun toggleLike() {
         database.query {
             currentSong.value?.let {
                 val song = it.song.toggleLike()
                 update(song)
                 syncUtils.likeSong(song)

                 // Check if auto-download on like is enabled and the song is now liked
                 if (dataStore.get(AutoDownloadOnLikeKey, false) && song.liked) {
                     // Trigger download for the liked song
                     val downloadRequest = androidx.media3.exoplayer.offline.DownloadRequest
                         .Builder(song.id, song.id.toUri())
                         .setCustomCacheKey(song.id)
                         .setData(song.title.toByteArray())
                         .build()
                     androidx.media3.exoplayer.offline.DownloadService.sendAddDownload(
                         this@MusicService,
                         ExoDownloadService::class.java,
                         downloadRequest,
                         false
                     )

                     // Download lyrics if auto-download lyrics is enabled
                     if (dataStore.get(AutoDownloadLyricsKey, false)) {
                         scope.launch {
                             downloadLyricsForSong(song.id)
                         }
                     }
                 }
             }
         }
     }

     /**
      * Downloads lyrics for a specific song
      */
     suspend fun downloadLyricsForSong(songId: String) {
         val mediaMetadata = database.song(songId).firstOrNull()?.toMediaMetadata() ?: return
         if (database.lyrics(songId).firstOrNull() == null) {
             val lyrics = lyricsHelper.getLyrics(mediaMetadata)
             database.query {
                 upsert(
                     LyricsEntity(
                         id = songId,
                         lyrics = lyrics,
                     ),
                 )
             }
         }
     }

    private fun openAudioEffectSession() {
        if (isAudioEffectSessionOpened) return
        isAudioEffectSessionOpened = true
        sendBroadcast(
            Intent(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION).apply {
                putExtra(AudioEffect.EXTRA_AUDIO_SESSION, player.audioSessionId)
                putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
                putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
            },
        )
    }

    private fun closeAudioEffectSession() {
        if (!isAudioEffectSessionOpened) return
        isAudioEffectSessionOpened = false
        sendBroadcast(
            Intent(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION).apply {
                putExtra(AudioEffect.EXTRA_AUDIO_SESSION, player.audioSessionId)
                putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
            },
        )
    }

    override fun onMediaItemTransition(
        mediaItem: MediaItem?,
        reason: Int,
    ) {
        // Auto load more songs
        if (dataStore.get(AutoLoadMoreKey, true) &&
            reason != Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT &&
            player.mediaItemCount - player.currentMediaItemIndex <= 5 &&
            currentQueue.hasNextPage()
        ) {
            scope.launch(SilentHandler) {
                val mediaItems =
                    currentQueue.nextPage().filterExplicit(dataStore.get(HideExplicitKey, false))
                if (player.playbackState != STATE_IDLE) {
                    player.addMediaItems(mediaItems.drop(1))
                }
            }
        }
    }

    private fun sendWidgetUpdateBroadcast() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            scope.launch(Dispatchers.Main) {
                sendWidgetUpdateBroadcast()
            }
            return
        }

        val meta = currentMediaMetadata.value

        if (meta?.id != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    val recommendationTitle = database.getRelatedSongs(meta.id).firstOrNull()?.firstOrNull()?.song?.title ?: ""
                    withContext(Dispatchers.Main) {
                        sendWidgetUpdateInternal(recommendationTitle)
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Error obteniendo recomendaciones: ${e.message}")
                    withContext(Dispatchers.Main) {
                        sendWidgetUpdateInternal("")
                    }
                }
            }
        } else {
            sendWidgetUpdateInternal("")
        }
    }

    private fun sendWidgetUpdateInternal(recommendationTitle: String) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            scope.launch(Dispatchers.Main) {
                sendWidgetUpdateInternal(recommendationTitle)
            }
            return
        }
        
        val meta = currentMediaMetadata.value
        val song = currentSong.value
        val isPlaying = player.isPlaying
        val songTitle = meta?.title ?: song?.song?.title ?: ""

        var artistName = meta?.artistName ?: song?.song?.artistName ?: ""

        if (artistName.isBlank() && meta?.artists?.isNotEmpty() == true) {
            artistName = meta.artists.joinToString(", ") { it.name }
        }

        val coverUrl = meta?.thumbnailUrl ?: song?.song?.thumbnailUrl ?: ""

        val defaultColor = com.anitail.music.ui.theme.DefaultThemeColor.toArgb()

        if (coverUrl.isNotBlank()) {
            scope.launch(Dispatchers.IO) {
                try {
                    val loader = coil.ImageLoader(this@MusicService)
                    val req = coil.request.ImageRequest.Builder(this@MusicService)
                        .data(coverUrl)
                        .allowHardware(false)
                        .build()
                    val result = loader.execute(req)
                    val bmp = (result.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap

                    val finalColor = if (bmp != null) {
                        try {
                            val palette = androidx.palette.graphics.Palette.Builder(bmp)
                                .maximumColorCount(32)
                                .generate()

                            palette.vibrantSwatch?.rgb
                                ?: palette.mutedSwatch?.rgb
                                ?: palette.darkVibrantSwatch?.rgb
                                ?: palette.darkMutedSwatch?.rgb
                                ?: defaultColor
                        } catch (e: Exception) {
                            Timber.tag("MusicService").e(e, "Error generating palette from bitmap")
                            defaultColor
                        }
                    } else {
                        defaultColor
                    }

                    sendWidgetBroadcast(
                        songTitle, 
                        artistName, 
                        recommendationTitle, 
                        isPlaying, 
                        finalColor,
                        coverUrl, 
                        finalColor
                    )                } catch (e: Exception) {
                    Timber.tag("MusicService").e(e, "Error extracting dominant color, using default color")

                    withContext(Dispatchers.Main) {
                        sendWidgetBroadcast(songTitle, artistName, recommendationTitle, isPlaying, 
                                       defaultColor, coverUrl, defaultColor)
                    }
                }
            }
        } else {
            scope.launch(Dispatchers.IO) {
                sendWidgetBroadcast(songTitle, artistName, recommendationTitle, isPlaying, defaultColor, coverUrl, defaultColor)
            }
        }
    }
    private suspend fun sendWidgetBroadcast(
        songTitle: String,
        artistName: String,
        recommendationTitle: String,
        isPlaying: Boolean,
        themeColor: Int,
        coverUrl: String,
        dominantColor: Int
    ) {
        val meta = currentMediaMetadata.value
        val song = currentSong.value

        val related = database.getRelatedSongs(song?.song?.id ?: meta?.id ?: "").firstOrNull()?.take(4) ?: emptyList()
        val finalArtistName = artistName.ifBlank { getString(R.string.unknown_artist) }
        val (duration, position) = withContext(Dispatchers.Main) {
            player.duration.coerceAtLeast(1L) to player.currentPosition.coerceAtLeast(0L)
        }
        val progress = ((position.toFloat() / duration.toFloat()) * 100).toInt().coerceIn(0, 100)

        val intent = Intent(ACTION_WIDGET_UPDATE).apply {
            component = ComponentName(this@MusicService, com.anitail.music.ui.component.MusicWidgetProvider::class.java)
            putExtra(EXTRA_WIDGET_SONG_TITLE, songTitle)
            putExtra(EXTRA_WIDGET_ARTIST, finalArtistName)
            putExtra(EXTRA_WIDGET_RECOMMENDATION, recommendationTitle)
            putExtra(EXTRA_WIDGET_IS_PLAYING, isPlaying)
            putExtra(EXTRA_WIDGET_THEME_COLOR, themeColor)
            putExtra(EXTRA_WIDGET_COVER_URL, coverUrl)
            putExtra(EXTRA_WIDGET_DOMINANT_COLOR, dominantColor)
            putExtra(EXTRA_WIDGET_PROGRESS, progress)
            putExtra(EXTRA_WIDGET_CURRENT_POSITION, position)
            putExtra(EXTRA_WIDGET_DURATION, duration)
            // Add up to 4 recommendations (title, cover, id)
            related.getOrNull(0)?.let {
                putExtra(EXTRA_WIDGET_RECOMMENDATION_1_TITLE, it.song.title)
                putExtra(EXTRA_WIDGET_RECOMMENDATION_1_COVER_URL, it.song.thumbnailUrl ?: "")
                putExtra(EXTRA_WIDGET_RECOMMENDATION_1_ID, it.song.id)
            }
            related.getOrNull(1)?.let {
                putExtra(EXTRA_WIDGET_RECOMMENDATION_2_TITLE, it.song.title)
                putExtra(EXTRA_WIDGET_RECOMMENDATION_2_COVER_URL, it.song.thumbnailUrl ?: "")
                putExtra(EXTRA_WIDGET_RECOMMENDATION_2_ID, it.song.id)
            }
            related.getOrNull(2)?.let {
                putExtra(EXTRA_WIDGET_RECOMMENDATION_3_TITLE, it.song.title)
                putExtra(EXTRA_WIDGET_RECOMMENDATION_3_COVER_URL, it.song.thumbnailUrl ?: "")
                putExtra(EXTRA_WIDGET_RECOMMENDATION_3_ID, it.song.id)
            }
            related.getOrNull(3)?.let {
                putExtra(EXTRA_WIDGET_RECOMMENDATION_4_TITLE, it.song.title)
                putExtra(EXTRA_WIDGET_RECOMMENDATION_4_COVER_URL, it.song.thumbnailUrl ?: "")
                putExtra(EXTRA_WIDGET_RECOMMENDATION_4_ID, it.song.id)
            }
            addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
            addCategory(Intent.CATEGORY_DEFAULT)
        }

        try {
            sendBroadcast(intent)
        } catch (e: Exception) {
            Timber.tag("MusicService").e(e, "Error sending widget update broadcast")
        }
    }


    override fun onPlaybackStateChanged(
        @Player.State playbackState: Int,
    ) {
        if (playbackState == STATE_IDLE) {
            currentQueue = EmptyQueue
            player.shuffleModeEnabled = false
            queueTitle = null
        }
    }    override fun onEvents(
        player: Player,
        events: Player.Events,
    ) {        if (events.containsAny(
                Player.EVENT_PLAYBACK_STATE_CHANGED,
                Player.EVENT_PLAY_WHEN_READY_CHANGED
            )
        ) {            
            val isBufferingOrReady =
                player.playbackState == Player.STATE_BUFFERING || player.playbackState == STATE_READY
            if (isBufferingOrReady && player.playWhenReady) {
                openAudioEffectSession()
            } else {
                closeAudioEffectSession()
            }
            val isPlayingNow = player.isPlaying
            val hasDuration = player.duration > 0
            
            scope.launch(Dispatchers.Main) {
                delay(50)
                sendWidgetUpdateBroadcast()
                if (isPlayingNow && hasDuration) {
                    startPeriodicWidgetUpdates()
                } else {
                    stopPeriodicWidgetUpdates()
                }
            }
        }
        
        if (events.containsAny(EVENT_TIMELINE_CHANGED, EVENT_POSITION_DISCONTINUITY)) {
            currentMediaMetadata.value = player.currentMetadata
            scope.launch(Dispatchers.Main) {
                delay(100)
                sendWidgetUpdateBroadcast()
            }
        }
    }

    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
        updateNotification()
        if (shuffleModeEnabled) {
            val shuffledIndices = IntArray(player.mediaItemCount) { it }
            shuffledIndices.shuffle()
            shuffledIndices[shuffledIndices.indexOf(player.currentMediaItemIndex)] =
                shuffledIndices[0]
            shuffledIndices[0] = player.currentMediaItemIndex
            player.setShuffleOrder(DefaultShuffleOrder(shuffledIndices, System.currentTimeMillis()))
        }
    }

    override fun onRepeatModeChanged(repeatMode: Int) {
        updateNotification()
        scope.launch {
            dataStore.edit { settings ->
                settings[RepeatModeKey] = repeatMode
            }
        }
    }

    override fun onPlayerError(error: PlaybackException) {
        if (dataStore.get(AutoSkipNextOnErrorKey, false) &&
            isInternetAvailable(this) &&
            player.hasNextMediaItem()
        ) {
            player.seekToNext()
            player.prepare()
            player.playWhenReady = true
        }
    }

    private fun createCacheDataSource(): CacheDataSource.Factory =
        CacheDataSource
            .Factory()
            .setCache(downloadCache)
            .setUpstreamDataSourceFactory(
                CacheDataSource
                    .Factory()
                    .setCache(playerCache)
                    .setUpstreamDataSourceFactory(
                        DefaultDataSource.Factory(
                            this,
                            OkHttpDataSource.Factory(
                                OkHttpClient
                                    .Builder()
                                    .proxy(YouTube.proxy)
                                    .build(),
                            ),
                        ),
                    ),
            ).setCacheWriteDataSinkFactory(null)
            .setFlags(FLAG_IGNORE_CACHE_ON_ERROR)

    private fun createDataSourceFactory(): DataSource.Factory {
        return ResolvingDataSource.Factory(createCacheDataSource()) { dataSpec ->
            val mediaId = dataSpec.key ?: error("No media id")

            if (downloadCache.isCached(
                    mediaId,
                    dataSpec.position,
                    if (dataSpec.length >= 0) dataSpec.length else 1
                ) ||
                playerCache.isCached(mediaId, dataSpec.position, CHUNK_LENGTH)
            ) {
                scope.launch(Dispatchers.IO) { recoverSong(mediaId) }
                return@Factory dataSpec
            }

            val now = System.currentTimeMillis()
            songUrlCache[mediaId]?.let { (url, expiry) ->
                if (expiry > now + 10_000) {
                    scope.launch(Dispatchers.IO) { recoverSong(mediaId) }
                    return@Factory dataSpec.withUri(url.toUri())
                }
            }


            // Check whether to use an alternative source
            val useAlternativeSource = runBlocking {
                dataStore.data.map { preferences ->
                    preferences[JossRedMultimedia] ?: false
                }.first()
            }

            // Alternative source: JossRed
            if (useAlternativeSource) {
                try {
                    val alternativeUrl = JossRedClient.getStreamingUrl(mediaId)
                    Timber.i("Usando Joss Red para reproducción")
                    Timber.i("URL alternativa: $alternativeUrl")
                    scope.launch(Dispatchers.IO) { recoverSong(mediaId) }
                    return@Factory dataSpec.withUri(alternativeUrl.toUri())
                } catch (e: Exception) {
                    when {
                        e is JossRedClient.JossRedException && e.statusCode == 403 -> {
                            Timber.w("Error 403 en JossRed, continuando con YouTube")
                        }
                        e is JossRedClient.JossRedException && e.statusCode in 400..499 -> {
                            Timber.w("Error ${e.statusCode} en JossRed, continuando con YouTube")
                            // Throw error for 4xx other than 403, similar to source repo
                            throw PlaybackException("Error en fuente alternativa (${e.statusCode})", e, PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS)
                        }
                        else -> {
                            Timber.e(e, "Error con fuente alternativa, intentando YouTube")
                            // Fall through to YouTube logic
                        }
                    }
                }
            }

            // Default source: YouTube
            val playbackData = runBlocking(Dispatchers.IO) {
                YTPlayerUtils.playerResponseForPlayback(
                    mediaId,
                    audioQuality = audioQuality,
                    connectivityManager = connectivityManager,
                )
            }.getOrElse { throwable ->
                when (throwable) {
                    is PlaybackException -> throw throwable
                    is ConnectException, is UnknownHostException -> {
                        throw PlaybackException(
                            getString(R.string.error_no_internet),
                            throwable,
                            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                        )
                    }
                    is SocketTimeoutException -> {
                        throw PlaybackException(
                            getString(R.string.error_timeout),
                            throwable,
                            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
                        )
                    }
                    else -> throw PlaybackException(
                        getString(R.string.error_unknown),
                        throwable,
                        PlaybackException.ERROR_CODE_REMOTE_ERROR,
                    )
                }
            }

            val format = playbackData.format

            database.query {
                upsert(
                    FormatEntity(
                        id = mediaId,
                        itag = format.itag,
                        mimeType = format.mimeType.split(";")[0],
                        codecs = format.mimeType.split("codecs=")[1].removeSurrounding("\""),
                        bitrate = format.bitrate,
                        sampleRate = format.audioSampleRate,
                        contentLength = format.contentLength!!,
                        loudnessDb = playbackData.audioConfig?.loudnessDb,
                        playbackUrl = playbackData.playbackTracking?.videostatsPlaybackUrl?.baseUrl
                    )
                )
            }
            scope.launch(Dispatchers.IO) { recoverSong(mediaId, playbackData) }

            val streamUrl = playbackData.streamUrl
            val expiry = now + (playbackData.streamExpiresInSeconds * 1000L)
            songUrlCache[mediaId] = streamUrl to expiry
            dataSpec.withUri(streamUrl.toUri()).subrange(dataSpec.uriPositionOffset, CHUNK_LENGTH)
        }
    }

    private fun createMediaSourceFactory() =
        DefaultMediaSourceFactory(
            createDataSourceFactory(),
            ExtractorsFactory {
                arrayOf(MatroskaExtractor(), FragmentedMp4Extractor())
            },
        )

    private fun createRenderersFactory() =
        object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean,
            ) = DefaultAudioSink
                .Builder(this@MusicService)
                .setEnableFloatOutput(enableFloatOutput)
                .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                .setAudioProcessorChain(
                    DefaultAudioSink.DefaultAudioProcessorChain(
                        emptyArray(),
                        SilenceSkippingAudioProcessor(2_000_000, 20_000, 256),
                        SonicAudioProcessor(),
                    ),
                ).build()
        }

    override fun onPlaybackStatsReady(
        eventTime: AnalyticsListener.EventTime,
        playbackStats: PlaybackStats,
    ) {
        val mediaItem = eventTime.timeline.getWindow(eventTime.windowIndex, Timeline.Window()).mediaItem

        if (playbackStats.totalPlayTimeMs >= (
                if ((dataStore[HistoryDuration] ?: 30f) == 0f) 0f else dataStore[HistoryDuration]?.times(1000f) ?: 30000f
            ) &&
            !dataStore.get(PauseListenHistoryKey, false)
        ) {
            database.query {
                incrementTotalPlayTime(mediaItem.mediaId, playbackStats.totalPlayTimeMs)
                try {
                    insert(
                        Event(
                            songId = mediaItem.mediaId,
                            timestamp = LocalDateTime.now(),
                            playTime = playbackStats.totalPlayTimeMs,
                        ),
                    )
                } catch (_: SQLException) {
            }
        }
        if (!dataStore.get(PauseRemoteListenHistoryKey, false)) {
            CoroutineScope(Dispatchers.IO).launch {
                val playbackUrl = database.format(mediaItem.mediaId).first()?.playbackUrl
                    ?: YTPlayerUtils.playerResponseForMetadata(mediaItem.mediaId, null)
                        .getOrNull()?.playbackTracking?.videostatsPlaybackUrl?.baseUrl
                playbackUrl?.let {
                    YouTube.registerPlayback(null, playbackUrl)
                        .onFailure {
                            reportException(it)
                        }
                    }
                }
            }
        }
    }

    private fun saveQueueToDisk() {
        if (player.playbackState == STATE_IDLE) {
            filesDir.resolve(PERSISTENT_AUTOMIX_FILE).delete()
            filesDir.resolve(PERSISTENT_QUEUE_FILE).delete()
            return
        }
        val persistQueue =
            PersistQueue(
                title = queueTitle,
                items = player.mediaItems.mapNotNull { it.metadata },
                mediaItemIndex = player.currentMediaItemIndex,
                position = player.currentPosition,
            )
        val persistAutomix =
            PersistQueue(
                title = "automix",
                items = automixItems.value.mapNotNull { it.metadata },
                mediaItemIndex = 0,
                position = 0,
            )
        runCatching {
            filesDir.resolve(PERSISTENT_QUEUE_FILE).outputStream().use { fos ->
                ObjectOutputStream(fos).use { oos ->
                    oos.writeObject(persistQueue)
                }
            }
        }.onFailure {
            reportException(it)
        }
        runCatching {
            filesDir.resolve(PERSISTENT_AUTOMIX_FILE).outputStream().use { fos ->
                ObjectOutputStream(fos).use { oos ->
                    oos.writeObject(persistAutomix)
                }
            }
        }.onFailure {
            reportException(it)
        }
    }
    override fun onDestroy() {
        stopPeriodicWidgetUpdates()
        widgetUpdateJob?.cancel()
        
        if (dataStore.get(PersistentQueueKey, true)) {
            saveQueueToDisk()
        }
        if (discordRpc?.isRpcRunning() == true) {
            discordRpc?.closeRPC()
        }
        discordRpc = null
        mediaSession.release()
        player.removeListener(this)
        player.removeListener(sleepTimer)
        player.release()
        instance = null
        
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = super.onBind(intent) ?: binder

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        stopSelf()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = mediaSession

    inner class MusicBinder : Binder() {
        val service: MusicService
            get() = this@MusicService
    }

    companion object {
        const val EXTRA_WIDGET_RECOMMENDATION_ID = "com.anitail.music.widget.RECOMMENDATION_ID"
        // Widget recommendation actions and extras
        const val ACTION_PLAY_RECOMMENDATION = "com.anitail.music.widget.ACTION_PLAY_RECOMMENDATION"
        const val EXTRA_WIDGET_RECOMMENDATION_1_TITLE = "com.anitail.music.widget.RECOMMENDATION_1_TITLE"
        const val EXTRA_WIDGET_RECOMMENDATION_1_COVER_URL = "com.anitail.music.widget.RECOMMENDATION_1_COVER_URL"
        const val EXTRA_WIDGET_RECOMMENDATION_1_ID = "com.anitail.music.widget.RECOMMENDATION_1_ID"
        const val EXTRA_WIDGET_RECOMMENDATION_2_TITLE = "com.anitail.music.widget.RECOMMENDATION_2_TITLE"
        const val EXTRA_WIDGET_RECOMMENDATION_2_COVER_URL = "com.anitail.music.widget.RECOMMENDATION_2_COVER_URL"
        const val EXTRA_WIDGET_RECOMMENDATION_2_ID = "com.anitail.music.widget.RECOMMENDATION_2_ID"
        const val EXTRA_WIDGET_RECOMMENDATION_3_TITLE = "com.anitail.music.widget.RECOMMENDATION_3_TITLE"
        const val EXTRA_WIDGET_RECOMMENDATION_3_COVER_URL = "com.anitail.music.widget.RECOMMENDATION_3_COVER_URL"
        const val EXTRA_WIDGET_RECOMMENDATION_3_ID = "com.anitail.music.widget.RECOMMENDATION_3_ID"
        const val EXTRA_WIDGET_RECOMMENDATION_4_TITLE = "com.anitail.music.widget.RECOMMENDATION_4_TITLE"
        const val EXTRA_WIDGET_RECOMMENDATION_4_COVER_URL = "com.anitail.music.widget.RECOMMENDATION_4_COVER_URL"
        const val EXTRA_WIDGET_RECOMMENDATION_4_ID = "com.anitail.music.widget.RECOMMENDATION_4_ID"
        const val ROOT = "root"
        // --- Widget Broadcast constants ---
        const val ACTION_WIDGET_UPDATE = "com.anitail.music.widget.ACTION_WIDGET_UPDATE"
        const val EXTRA_WIDGET_SONG_TITLE = "com.anitail.music.widget.EXTRA_WIDGET_SONG_TITLE"
        const val EXTRA_WIDGET_ARTIST = "com.anitail.music.widget.EXTRA_WIDGET_ARTIST"
        const val EXTRA_WIDGET_RECOMMENDATION = "com.anitail.music.widget.EXTRA_WIDGET_RECOMMENDATION"
        const val EXTRA_WIDGET_IS_PLAYING = "com.anitail.music.widget.IS_PLAYING"
        const val EXTRA_WIDGET_THEME_COLOR = "com.anitail.music.widget.THEME_COLOR"
        const val EXTRA_WIDGET_COVER_URL = "com.anitail.music.widget.COVER_URL"
        const val EXTRA_WIDGET_DOMINANT_COLOR = "com.anitail.music.widget.DOMINANT_COLOR"
        const val EXTRA_WIDGET_PROGRESS = "com.anitail.music.widget.PROGRESS"
        const val EXTRA_WIDGET_CURRENT_POSITION = "com.anitail.music.widget.CURRENT_POSITION"
        const val EXTRA_WIDGET_DURATION = "com.anitail.music.widget.DURATION"
        const val SONG = "song"
        const val ARTIST = "artist"
        const val ALBUM = "album"
        const val PLAYLIST = "playlist"

        const val CHANNEL_ID = "music_channel_01"
        const val NOTIFICATION_ID = 888
        const val ERROR_CODE_NO_STREAM = 1000001
        const val CHUNK_LENGTH = 512 * 1024L
        const val PERSISTENT_QUEUE_FILE = "persistent_queue.data"
        const val PERSISTENT_AUTOMIX_FILE = "persistent_automix.data"

        // Constants for lyrics download action
        const val ACTION_DOWNLOAD_LYRICS = "com.anitail.music.action.DOWNLOAD_LYRICS"
        const val EXTRA_SONG_ID = "com.anitail.music.extra.SONG_ID"
        // Static instance to access the service from callbacks
        var instance: MusicService? = null
            private set

    }    /**
     * Closes the player when the X button is clicked either from the miniplayer or notification
     * Stops playback, saves state, and cleans up resources
     */
    fun closePlayer() {
        player.pause()

        if (dataStore.get(PersistentQueueKey, true)) {
            saveQueueToDisk()
        }

        player.stop()
        player.clearMediaItems()
        currentQueue = EmptyQueue
        queueTitle = null
        currentMediaMetadata.value = null
        discordRpc?.closeRPC()

        if (isAudioEffectSessionOpened) {
            closeAudioEffectSession()
        }

        mediaSession.setCustomLayout(emptyList())
        updateNotification()
        stopSelf()
    }

    /**
     * Starts periodic widget updates to show song progress
     */
    private fun startPeriodicWidgetUpdates() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            scope.launch(Dispatchers.Main) {
                startPeriodicWidgetUpdates()
            }
            return
        }

        widgetUpdateJob?.cancel()

        widgetUpdateJob = scope.launch(Dispatchers.Main) {
            try {
                while (isActive) {
                    if (player.isPlaying && player.playbackState == STATE_READY) {
                        sendWidgetUpdateBroadcast()
                    }
                    delay(2000)
                }
            } catch (e: Exception) {
                Timber.e(e, "Error in periodic widget updates")
            }
        }
    }

    /**
     * Stops periodic widget updates
     */

    private fun stopPeriodicWidgetUpdates() {
        widgetUpdateJob?.cancel()
        widgetUpdateJob = null
    }
}
