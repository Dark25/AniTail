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
import com.anitail.music.constants.JamConnectionHistoryKey
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
import com.anitail.music.db.entities.Song
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
import com.anitail.music.utils.LanJamClient
import com.anitail.music.utils.LanJamCommands
import com.anitail.music.utils.LanJamQueueSync
import com.anitail.music.utils.LanJamServer
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
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import timber.log.Timber
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
    var queueTitle: String? = null    // LAN JAM: Server/client for queue sync
    var lanJamServer: LanJamServer? = null
    private var lanJamClient: LanJamClient? = null
    var isJamHost: Boolean = false
        private set
    var isJamEnabled: Boolean = false
        private set
    private var ignoreNextRemoteQueue: Boolean = false

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

        // Inicializa connectivityManager primero
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        // Inicializa player y mediaSession ANTES de exponer el servicio
        player = ExoPlayer
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
                updateDiscordPresence(song)
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
            if (mediaMetadata != null) {
                // Cargar las letras en segundo plano para la canción actual
                scope.launch(Dispatchers.IO) {
                    try {
                        // Verificar si ya tenemos las letras en la base de datos
                        val existingLyrics = database.lyrics(mediaMetadata.id).firstOrNull()
                        
                        if (existingLyrics == null) {
                            Timber.d("Descargando letras para ${mediaMetadata.title}")
                            val lyrics = lyricsHelper.getLyrics(mediaMetadata)
                            
                            // Guardar en la base de datos
                            database.query {
                                upsert(
                                    LyricsEntity(
                                        id = mediaMetadata.id,
                                        lyrics = lyrics,
                                    ),
                                )
                            }
                            Timber.d("Letras guardadas para ${mediaMetadata.title}")
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "Error al obtener letras para ${mediaMetadata.title}")
                    }
                }
                
                // Precargar las letras de las próximas canciones en la cola
                scope.launch(Dispatchers.IO) {
                    try {
                        val nextItems = getNextQueueItems(3) // Obtener las próximas 3 canciones
                        nextItems.forEach { nextItem ->
                            val nextId = nextItem.mediaId
                            val metadata = nextItem.metadata ?: return@forEach
                            
                            if (database.lyrics(nextId).firstOrNull() == null) {
                                Timber.d("Precargando letras para próxima canción: ${metadata.title}")
                                try {
                                    val lyrics = lyricsHelper.getLyrics(metadata)
                                    database.query {
                                        upsert(
                                            LyricsEntity(
                                                id = nextId,
                                                lyrics = lyrics,
                                            ),
                                        )
                                    }
                                } catch(e: Exception) {
                                    Timber.e(e, "Error precargando letras: ${e.message}")
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "Error precargando letras de canciones próximas")
                    }
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
                if (key != null && enabled) {
                    if (discordRpc == null || dataStore[DiscordTokenKey] != key) {
                        // Solo cerrar y recrear si no existe o cambió el token
                        if (discordRpc?.isRpcRunning() == true) {
                            discordRpc?.closeRPC()
                        }
                        discordRpc = DiscordRPC(this, key)
                        
                        // Inicializar con la canción actual
                        currentSong.value?.let {
                            updateDiscordPresence(it)
                        }
                    }
                } else if (discordRpc?.isRpcRunning() == true) {
                    discordRpc?.closeRPC()
                    discordRpc = null
                }
            }

        if (dataStore.get(PersistentQueueKey, true)) {
            runCatching {
                val file = filesDir.resolve(PERSISTENT_QUEUE_FILE)
                if (file.exists()) {
                    val json = file.readText(Charsets.UTF_8)
                    Json.decodeFromString<PersistQueue>(json)
                } else null
            }.onSuccess { queue ->
                if (queue != null) {
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
            }
            runCatching {
                val file = filesDir.resolve(PERSISTENT_AUTOMIX_FILE)
                if (file.exists()) {
                    val json = file.readText(Charsets.UTF_8)
                    Json.decodeFromString<PersistQueue>(json)
                } else null
            }.onSuccess { queue ->
                if (queue != null) {
                    automixItems.value = queue.items.map { it.toMediaItem() }
                }
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

    /**
     * Actualiza la configuración de LAN JAM y gestiona los ciclos de vida de servidor/cliente
     * Llamar desde MainActivity o donde se observe el JamViewModel
     *
     * @param enabled Si LAN JAM está activado
     * @param isHost Si este dispositivo es el host (servidor)
     * @param hostIp IP del host al que conectar si este dispositivo es cliente
     */
    fun updateJamSettings(enabled: Boolean, isHost: Boolean, hostIp: String) {
        // Previene crash si player no está inicializado
        if (!this::player.isInitialized) {
            Timber.tag("LanJam").d("Player no inicializado, posponiendo configuración JAM")
            return
        }

        // Verificar conectividad de red si se está activando JAM
        if (enabled && !isJamEnabled) {
            val hasNetwork = isInternetAvailable(this)
            if (!hasNetwork) {
                Timber.tag("LanJam").w("No se puede activar JAM: no hay conectividad de red")
                return
            }
        }

        // Solo reiniciar JAM si hay un cambio real de estado o de hostIp
        val currentHostIp = lanJamClient?.host ?: ""
        val shouldReconnectClient = isJamEnabled && !isJamHost && enabled && !isHost && currentHostIp != hostIp
        val settingsChanged = isJamEnabled != enabled || isJamHost != isHost ||
                (!isJamHost && enabled && hostIp != currentHostIp && hostIp.isNotBlank())

        if (!settingsChanged && !shouldReconnectClient) {
            // No hay cambios significativos
            Timber.tag("LanJam").d("No hay cambios en configuración JAM, manteniendo estado actual")
            return
        }

        scope.launch {
            // Siempre realizar limpieza en un hilo separado para evitar bloqueos en UI
            withContext(Dispatchers.IO) {
                // Limpiar los recursos actuales
                if (lanJamServer != null) {
                    Timber.tag("LanJam").d("Deteniendo JAM server")
                    try {
                        lanJamServer?.stop()
                    } catch (e: Exception) {
                        Timber.tag("LanJam").e(e, "Error al detener JAM server: ${e.message}")
                    } finally {
                        lanJamServer = null
                    }
                }

                if (lanJamClient != null) {
                    Timber.tag("LanJam").d("Desconectando JAM client")
                    try {
                        lanJamClient?.disconnect()
                    } catch (e: Exception) {
                        Timber.tag("LanJam").e(e, "Error al desconectar JAM client: ${e.message}")
                    } finally {
                        lanJamClient = null
                    }
                }
            }

            isJamEnabled = enabled
            isJamHost = isHost

            if (isJamEnabled) {
                if (isJamHost) {
                    Timber.tag("LanJam").d("Iniciando JAM server (host)")
                    lanJamServer = LanJamServer(
                        onMessage = { msg ->
                            // Verificar si es un mensaje de ping
                            if (msg == "PING") {
                                Timber.tag("LanJam").d("Recibido PING de cliente, enviando PONG")
                                scope.launch(Dispatchers.IO) {
                                    try {
                                        lanJamServer?.send("PONG")
                                    } catch (e: Exception) {
                                        Timber.tag("LanJam").e(e, "Error enviando PONG: ${e.message}")
                                    }
                                }
                                return@LanJamServer
                            }

                            if (!ignoreNextRemoteQueue) {
                                try {
                                    Timber.tag("LanJam").d("Servidor recibió mensaje de cliente, procesando...")
                                    val remoteQueue = LanJamQueueSync.deserializeQueue(msg)
                                    ignoreNextRemoteQueue = true
                                    playRemoteQueue(remoteQueue)
                                } catch (e: Exception) {
                                    Timber.tag("LanJam").e(e, "Error procesando cola remota: ${e.message}")
                                }
                            } else {
                                ignoreNextRemoteQueue = false
                            }
                        },
                        onClientConnected = { clientIp, timestamp ->
                            // Registrar conexión en las preferencias
                            scope.launch {
                                saveJamConnection(clientIp, timestamp)
                            }

                            // Enviar la cola actual al cliente que se acaba de conectar
                            scope.launch {
                                delay(1000) // Dar tiempo al cliente para establecer bien la conexión

                                // Verificar si hay elementos en la cola
                                val isEmpty = player.mediaItems.isEmpty()
                                if (!isEmpty) {
                                    Timber.tag("LanJam").d("Enviando cola actual al cliente recién conectado")

                                    // Obtener datos del player en el hilo principal
                                    val title = queueTitle
                                    val items = player.mediaItems.mapNotNull { it.metadata }
                                    val currentIndex = player.currentMediaItemIndex
                                    val position = player.currentPosition

                                    // Mostrar detalles de la cola para depuración
                                    Timber.tag("LanJam").d("Cola para cliente nuevo - Elementos: ${items.size}, Índice: $currentIndex, Posición: $position")

                                    // Ahora podemos cambiar al hilo IO para la operación de red
                                    withContext(Dispatchers.IO) {
                                        try {
                                            val persistQueue = PersistQueue(
                                                title = title,
                                                items = items,
                                                mediaItemIndex = currentIndex,
                                                position = position,
                                            )
                                            val queueMessage = LanJamQueueSync.serializeQueue(persistQueue)
                                            val success = lanJamServer?.sendWithRetry(queueMessage, 3) ?: 0

                                            if (success > 0) {
                                                Timber.tag("LanJam").d("Cola enviada al cliente nuevo correctamente")

                                                // También enviar el estado actual de reproducción
                                                delay(200) // Una pequeña pausa para asegurar que se procese la cola primero
                                                val playStateCommand = LanJamCommands.serialize(
                                                    LanJamCommands.Command(
                                                        type = if (player.isPlaying)
                                                            LanJamCommands.CommandType.PLAY
                                                        else LanJamCommands.CommandType.PAUSE
                                                    )
                                                )
                                                lanJamServer?.sendWithRetry(playStateCommand, 2)

                                                // Enviar también configuración de repetición y aleatorio
                                                delay(100)
                                                lanJamServer?.sendWithRetry(LanJamCommands.serialize(
                                                    LanJamCommands.Command(
                                                        type = LanJamCommands.CommandType.TOGGLE_REPEAT,
                                                        repeatMode = player.repeatMode
                                                    )
                                                ), 2)

                                                if (player.shuffleModeEnabled) {
                                                    delay(100)
                                                    lanJamServer?.sendWithRetry(LanJamCommands.serialize(
                                                        LanJamCommands.Command(
                                                            type = LanJamCommands.CommandType.TOGGLE_SHUFFLE
                                                        )
                                                    ), 2)
                                                }
                                            } else {
                                                Timber.tag("LanJam").w("No se pudo enviar cola al cliente recién conectado")
                                            }
                                        } catch (e: Exception) {
                                            Timber.tag("LanJam").e(e, "Error enviando cola al cliente: ${e.message}")
                                        }
                                    }
                                }
                            }
                        }
                    )

                    try {
                        lanJamServer?.start()
                    } catch (e: Exception) {
                        Timber.tag("LanJam").e(e, "Error iniciando servidor JAM: ${e.message}")
                    }
                } else {
                    if (hostIp.isBlank()) {
                        Timber.tag("LanJam").e("No se puede conectar: IP del host está vacía")
                        return@launch
                    }

                    Timber.tag("LanJam").d("Conectando JAM client a $hostIp")
                    lanJamClient = LanJamClient(hostIp, onMessage = { msg ->
                        // Verificar si el mensaje es un PONG (respuesta a ping)
                        if (msg == "PONG") {
                            Timber.tag("LanJam").d("Recibido PONG del servidor - conexión estable")
                            return@LanJamClient
                        }

                        // Verificar si es un comando JAM
                        if (LanJamCommands.isCommand(msg)) {
                            try {
                                val command = LanJamCommands.deserialize(msg)
                                command?.let {
                                    scope.launch(Dispatchers.Main) {
                                        Timber.tag("LanJam").d("Cliente procesando comando: ${command.type}")
                                        when (command.type) {
                                            LanJamCommands.CommandType.PLAY -> {
                                                // Asegurarnos de que el reproductor esté preparado antes de reproducir
                                                if (player.playbackState == STATE_IDLE) {
                                                    player.prepare()
                                                }
                                                player.playWhenReady = true
                                                Timber.tag("LanJam").d("Cliente ejecutando comando PLAY")
                                            }
                                            LanJamCommands.CommandType.PAUSE -> {
                                                player.playWhenReady = false
                                                Timber.tag("LanJam").d("Cliente ejecutando comando PAUSE")
                                            }
                                            LanJamCommands.CommandType.NEXT -> {
                                                player.seekToNext()
                                                player.prepare()
                                                player.playWhenReady = true
                                                Timber.tag("LanJam").d("Cliente ejecutando comando NEXT")
                                            }
                                            LanJamCommands.CommandType.PREVIOUS -> {
                                                player.seekToPrevious()
                                                player.prepare()
                                                player.playWhenReady = true
                                                Timber.tag("LanJam").d("Cliente ejecutando comando PREVIOUS")
                                            }
                                            LanJamCommands.CommandType.SEEK -> {
                                                player.seekTo(command.position)
                                                Timber.tag("LanJam").d("Cliente ejecutando comando SEEK a posición ${command.position}")
                                            }
                                            LanJamCommands.CommandType.TOGGLE_REPEAT -> {
                                                player.repeatMode = command.repeatMode
                                                Timber.tag("LanJam").d("Cliente ejecutando comando TOGGLE_REPEAT: ${command.repeatMode}")
                                            }
                                            LanJamCommands.CommandType.TOGGLE_SHUFFLE -> {
                                                player.shuffleModeEnabled = !player.shuffleModeEnabled
                                                Timber.tag("LanJam").d("Cliente ejecutando comando TOGGLE_SHUFFLE")
                                            }
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                Timber.tag("LanJam").e(e, "Error procesando comando JAM: ${e.message}")
                            }
                        } else if (!ignoreNextRemoteQueue) {
                            try {
                                Timber.tag("LanJam").d("Cliente recibió mensaje del servidor: procesando cola...")
                                val remoteQueue = LanJamQueueSync.deserializeQueue(msg)
                                ignoreNextRemoteQueue = true
                                playRemoteQueue(remoteQueue)
                            } catch (e: Exception) {
                                Timber.tag("LanJam").e(e, "Error procesando cola remota: ${e.message}")
                            }
                        } else {
                            ignoreNextRemoteQueue = false
                        }
                    })

                    try {
                        lanJamClient?.connect()
                    } catch (e: Exception) {
                        Timber.tag("LanJam").e(e, "Error conectando cliente JAM: ${e.message}")
                    }

                    // Configurar ping periódico para verificar la conexión
                    scope.launch {
                        delay(1000) // Esperar un segundo para que se establezca la conexión inicial

                        while (isJamEnabled && !isJamHost && lanJamClient != null) {
                            try {
                                withContext(Dispatchers.IO) {
                                    if (lanJamClient?.isConnected() == true) {
                                        lanJamClient?.send("PING")
                                        Timber.tag("LanJam").d("Ping enviado al servidor")
                                    }
                                }
                            } catch (e: Exception) {
                                Timber.tag("LanJam").e(e, "Error enviando ping al servidor: ${e.message}")
                            }

                            delay(15000) // Enviar ping cada 15 segundos
                        }
                    }
                }
            } else {
                Timber.tag("LanJam").d("JAM desactivado")
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
        skipJamBroadcast: Boolean = false
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
                
                // Dar un poco de tiempo para que se actualice la cola en el reproductor
                delay(50)
            }
            
            // JAM: Broadcast queue if host and not from remote
            if (isJamEnabled && isJamHost && !skipJamBroadcast) {
                // Capturar valores del player en hilo principal
                val title = queueTitle
                val items = player.mediaItems.mapNotNull { it.metadata }
                val currentIndex = player.currentMediaItemIndex
                val position = player.currentPosition
                
                // Enviar en un hilo IO
                withContext(Dispatchers.IO) {
                    try {
                        val persistQueue = PersistQueue(
                            title = title,
                            items = items,
                            mediaItemIndex = currentIndex,
                            position = position,
                        )
                        Timber.tag("LanJam").d("Enviando cola a clientes: ${items.size} elementos, índice actual: $currentIndex")
                        lanJamServer?.send(LanJamQueueSync.serializeQueue(persistQueue))
                    } catch (e: Exception) {
                        Timber.tag("LanJam").e(e, "Error enviando cola: ${e.message}")
                    }
                }
            }
        }        // JAM: Broadcast queue if host and not from remote
        if (isJamEnabled && isJamHost && !skipJamBroadcast) {
            // Capturar los datos en el hilo principal
            val title = queueTitle
            val items = player.mediaItems.mapNotNull { it.metadata }
            val currentIndex = player.currentMediaItemIndex
            val position = player.currentPosition
            
            // Enviar en un hilo IO
            scope.launch(Dispatchers.IO) {                val persistQueue = PersistQueue(
                    title = title,
                    items = items,
                    mediaItemIndex = currentIndex,
                    position = position,
                )
                val queueMessage = LanJamQueueSync.serializeQueue(persistQueue)
                // Usar el método con reintentos para mayor robustez
                val successCount = lanJamServer?.sendWithRetry(queueMessage, 2) ?: 0
                Timber.tag("LanJam").d("Cola sincronizada con $successCount clientes")
            }
        }
    }    /**
     * Reproduce una cola recibida de forma remota desde otro dispositivo
     * Implementa validaciones de seguridad y manejo de errores mejorado
     * No rebroadcastea la cola para evitar bucles
     * 
     * @param persistQueue Cola serializada recibida del dispositivo remoto
     */
    private fun playRemoteQueue(persistQueue: PersistQueue) {
        // Validación de entrada
        if (persistQueue.items.isEmpty()) {
            Timber.tag("LanJam").e("No se puede reproducir cola remota: lista de elementos vacía")
            return
        }
        
        // Verificar si estamos en el hilo principal para usar withContext si es necesario
        val isMainThread = Thread.currentThread() == Looper.getMainLooper().thread
        
        try {
            // Validar los elementos de la cola
            val validItems = persistQueue.items.filter { item ->
                if (item.id.isBlank()) {
                    Timber.tag("LanJam").w("Elemento de cola remota con ID en blanco ignorado")
                    false
                } else {
                    true
                }
            }
            
            // Verificar que tengamos elementos válidos después del filtrado
            if (validItems.isEmpty()) {
                Timber.tag("LanJam").e("No quedaron elementos válidos en la cola remota después de filtrar")
                return
            }
            
            // Registrar detalles de la cola para depuración
            Timber.tag("LanJam").d("Cola recibida - Elementos: ${validItems.size}, Índice: ${persistQueue.mediaItemIndex}, Posición: ${persistQueue.position}")
            
            // Calcular índice de inicio seguro
            val safeStartIndex = persistQueue.mediaItemIndex.coerceIn(0, validItems.size - 1)
            
            // Convertir PersistQueue a ListQueue con elementos validados
            val queue = ListQueue(
                title = persistQueue.title ?: "Cola remota",
                items = validItems.map { it.toMediaItem() },
                startIndex = safeStartIndex,
                position = persistQueue.position.coerceAtLeast(0L) // Asegurar posición no negativa
            )
            
            // Ejecutar en el hilo principal si no estamos ya en él
            if (!isMainThread) {
                scope.launch(Dispatchers.Main) {
                    try {
                        Timber.tag("LanJam").d("Reproduciendo cola remota con ${validItems.size} elementos desde índice $safeStartIndex")
                        playQueue(
                            queue = queue,
                            playWhenReady = false, // No iniciar automáticamente - esperar comando PLAY
                            skipJamBroadcast = true // No reenviar para evitar bucles
                        )
                    } catch (e: Exception) {
                        Timber.tag("LanJam").e(e, "Error reproduciendo cola remota: ${e.message}")
                    }
                }
            } else {
                // Ya estamos en el hilo principal
                try {
                    Timber.tag("LanJam").d("Reproduciendo cola remota con ${validItems.size} elementos desde índice $safeStartIndex")
                    playQueue(
                        queue = queue,
                        playWhenReady = false, // No iniciar automáticamente - esperar comando PLAY
                        skipJamBroadcast = true
                    )
                } catch (e: Exception) {
                    Timber.tag("LanJam").e(e, "Error reproduciendo cola remota: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Timber.tag("LanJam").e(e, "Error preparando cola remota: ${e.message}")
            // Reportar excepciones no esperadas
            reportException(e)
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
        
        // JAM: Broadcast queue update
        if (isJamEnabled && isJamHost) {
            // Capturar los datos en el hilo principal
            val title = queueTitle
            
            // Dar un breve tiempo para que el player actualice su estado internamente
            scope.launch {
                delay(50)
                
                val allItems = player.mediaItems.mapNotNull { it.metadata }
                val currentIndex = player.currentMediaItemIndex
                val position = player.currentPosition
                
                // Operaciones de red en hilo background
                withContext(Dispatchers.IO) {
                    try {
                        val persistQueue = PersistQueue(
                            title = title,
                            items = allItems,
                            mediaItemIndex = currentIndex,
                            position = position,
                        )
                        Timber.tag("LanJam").d("Enviando cola con nuevos elementos a clientes: ${allItems.size} elementos")
                        lanJamServer?.send(LanJamQueueSync.serializeQueue(persistQueue))
                    } catch (e: Exception) {
                        Timber.tag("LanJam").e(e, "Error enviando cola con nuevos elementos: ${e.message}")
                    }
                }
            }
        }
    }
        fun addToQueue(items: List<MediaItem>) {
        player.addMediaItems(items)
        player.prepare()
        
        // JAM: Broadcast queue update
        if (isJamEnabled && isJamHost) {
            // Capturar los datos en el hilo principal
            val title = queueTitle
            val allItems = player.mediaItems.mapNotNull { it.metadata }
            val currentIndex = player.currentMediaItemIndex
            val position = player.currentPosition
            
            // Dar un breve tiempo para que el player actualice su estado internamente
            scope.launch {
                delay(50)
                
                // Operaciones de red en hilo background
                withContext(Dispatchers.IO) {
                    try {
                        val persistQueue = PersistQueue(
                            title = title,
                            items = allItems,
                            mediaItemIndex = currentIndex,
                            position = position,
                        )
                        Timber.tag("LanJam").d("Enviando cola actualizada a clientes: ${allItems.size} elementos")
                        lanJamServer?.send(LanJamQueueSync.serializeQueue(persistQueue))
                    } catch (e: Exception) {
                        Timber.tag("LanJam").e(e, "Error enviando cola actualizada: ${e.message}")
                    }
                }
            }
        }
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
    }
    override fun onEvents(
        player: Player,
        events: Player.Events,
    ) {        
        // Detectar cambios en la cola para sincronizar con clientes JAM
        if (events.containsAny(Player.EVENT_TIMELINE_CHANGED) && isJamEnabled && isJamHost) {
            // Evitar sincronización excesiva usando una bandera
            if (!ignoreNextRemoteQueue) {
                // Solo sincronizar si no estamos en medio de procesar una cola remota
                scope.launch(Dispatchers.Main) {
                    // Pequeño delay para permitir que los cambios internos se completen
                    delay(100)
                    syncQueueWithClients()
                }
            }
        }
        
        if (events.containsAny(
                Player.EVENT_PLAYBACK_STATE_CHANGED,
                Player.EVENT_PLAY_WHEN_READY_CHANGED
            )
        ) {            
            val isBufferingOrReady =
                player.playbackState == Player.STATE_BUFFERING || player.playbackState == STATE_READY
            if (isBufferingOrReady && player.playWhenReady) {
                openAudioEffectSession()
                
                // Enviar comando PLAY a los clientes JAM cuando el host comienza a reproducir
                if (isJamEnabled && isJamHost) {
                    sendJamCommand(LanJamCommands.CommandType.PLAY)
                }
            } else {
                closeAudioEffectSession()
                
                // Enviar comando PAUSE a los clientes JAM cuando el host pausa
                if (isJamEnabled && isJamHost && !player.playWhenReady) {
                    sendJamCommand(LanJamCommands.CommandType.PAUSE)
                }
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
        
        // Enviar comando de cambio de modo aleatorio a los clientes JAM
        if (isJamEnabled && isJamHost) {
            sendJamCommand(LanJamCommands.CommandType.TOGGLE_SHUFFLE)
        }
    }
    override fun onRepeatModeChanged(repeatMode: Int) {
        updateNotification()
        scope.launch {
            dataStore.edit { settings ->
                settings[RepeatModeKey] = repeatMode
            }
        }
        
        // Enviar comando de cambio de modo de repetición a los clientes JAM
        if (isJamEnabled && isJamHost) {
            sendJamCommand(
                commandType = LanJamCommands.CommandType.TOGGLE_REPEAT,
                repeatMode = repeatMode
            )
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
            val json = Json.encodeToString(persistQueue)
            filesDir.resolve(PERSISTENT_QUEUE_FILE).writeText(json, Charsets.UTF_8)
        }.onFailure {
            reportException(it)
        }
        runCatching {
            val json = Json.encodeToString(persistAutomix)
            filesDir.resolve(PERSISTENT_AUTOMIX_FILE).writeText(json, Charsets.UTF_8)
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

    // Nueva función para guardar conexiones
    private suspend fun saveJamConnection(clientIp: String, timestamp: String) {
        dataStore.edit { preferences ->
            val existingHistory = preferences[JamConnectionHistoryKey] ?: ""
            val newEntry = "$clientIp|$timestamp"
            
            // Limitar a las últimas 30 conexiones
            val historyEntries = existingHistory.split(";")
                .filter { it.isNotEmpty() }
                .toMutableList()
            
            // Añadir la nueva conexión al principio
            historyEntries.add(0, newEntry)
            
            // Mantener solo las últimas 30 entradas
            val trimmedHistory = historyEntries.take(30).joinToString(";")
            preferences[JamConnectionHistoryKey] = trimmedHistory
        }
    }    /**
     * Envía un comando de control de reproducción a los clientes JAM
     * @param commandType Tipo de comando a enviar
     * @param position Posición de reproducción (para SEEK)
     * @param repeatMode Modo de repetición (para TOGGLE_REPEAT)
     */
    internal fun sendJamCommand(commandType: LanJamCommands.CommandType, position: Long = 0, repeatMode: Int = 0) {
        if (!isJamEnabled || !isJamHost) {
            return
        }
        
        scope.launch(Dispatchers.IO) {
            try {                val command = LanJamCommands.Command(
                    type = commandType,
                    position = position,
                    repeatMode = repeatMode
                )
                val commandMessage = LanJamCommands.serialize(command)
                
                // Usar el método con reintentos para mayor robustez
                val successCount = lanJamServer?.sendWithRetry(commandMessage, 2) ?: 0
                Timber.tag("LanJam").d("Comando $commandType enviado a $successCount clientes")
            } catch (e: Exception) {
                Timber.tag("LanJam").e(e, "Error enviando comando JAM: ${e.message}")
            }
        }
    }

    /**
     * Función utilitaria para sincronizar la cola actual con los clientes JAM
     * Se debe llamar después de cualquier modificación a la cola (eliminar, reorganizar, etc.)
     */
    fun syncQueueWithClients() {
        if (!isJamEnabled || !isJamHost || player.mediaItemCount == 0) {
            return
        }
        
        scope.launch(Dispatchers.IO) {
            try {
                val title = queueTitle
                val items = player.mediaItems.mapNotNull { it.metadata }
                val currentIndex = player.currentMediaItemIndex
                val position = player.currentPosition
                  val persistQueue = PersistQueue(
                    title = title,
                    items = items,
                    mediaItemIndex = currentIndex,
                    position = position,
                )
                val queueMessage = LanJamQueueSync.serializeQueue(persistQueue)
                
                Timber.tag("LanJam").d("Sincronizando cola modificada: ${items.size} elementos, índice: $currentIndex")
                val successCount = lanJamServer?.sendWithRetry(queueMessage, 2) ?: 0
                Timber.tag("LanJam").d("Cola sincronizada con $successCount clientes")
            } catch (e: Exception) {
                Timber.tag("LanJam").e(e, "Error sincronizando cola: ${e.message}")
            }
        }
    }

    // Añade este nuevo método para centralizar la lógica de actualizar Discord RPC
    private suspend fun updateDiscordPresence(song: Song) {
        if (discordRpc?.isRpcRunning() == true || discordRpc != null) {
            try {
                discordRpc?.updateSong(
                    song = song,
                    timeStart = if (player.isPlaying)
                        System.currentTimeMillis() - player.currentPosition else 0L,
                    timeEnd = if (player.isPlaying)
                        (System.currentTimeMillis() - player.currentPosition) + player.duration else 0L
                )
                Timber.d("Discord RPC actualizado correctamente")
            } catch (e: Exception) {
                Timber.e(e, "Error actualizando Discord RPC, reintentando en 3 segundos")
                
                // En lugar de cerrar inmediatamente, programar un reintento
                scope.launch {
                    delay(3000)
                    if (discordRpc?.isRpcRunning() != true) {
                        try {
                            val token = dataStore[DiscordTokenKey]
                            discordRpc?.closeRPC()
                            if (token != null) {
                                discordRpc = DiscordRPC(this@MusicService, token)
                                delay(500) // Pequeña espera para inicialización
                                updateDiscordPresence(song)
                            }
                        } catch (e: Exception) {
                            Timber.e(e, "Falló la reconexión de Discord RPC")
                        }
                    }
                }
            }
        }
    }

    // Método para obtener las próximas canciones en la cola
    private fun getNextQueueItems(count: Int): List<MediaItem> {
        val result = mutableListOf<MediaItem>()
        val currentIndex = player.currentMediaItemIndex
        val totalItems = player.mediaItemCount
        
        for (i in 1..count) {
            val nextIndex = currentIndex + i
            if (nextIndex < totalItems) {
                player.getMediaItemAt(nextIndex)?.let {
                    result.add(it)
                }
            }
        }
        
        return result
    }
}

