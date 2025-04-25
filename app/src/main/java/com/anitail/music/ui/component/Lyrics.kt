package com.anitail.music.ui.component

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.anitail.music.LocalPlayerConnection
import com.anitail.music.R
import com.anitail.music.constants.DarkModeKey
import com.anitail.music.constants.LyricsClickKey
import com.anitail.music.constants.LyricsTextPositionKey
import com.anitail.music.constants.PlayerBackgroundStyle
import com.anitail.music.constants.PlayerBackgroundStyleKey
import com.anitail.music.constants.ShowLyricsKey
import com.anitail.music.db.entities.LyricsEntity.Companion.LYRICS_NOT_FOUND
import com.anitail.music.lyrics.LyricsEntry
import com.anitail.music.lyrics.LyricsEntry.Companion.HEAD_LYRICS_ENTRY
import com.anitail.music.lyrics.LyricsUtils.findCurrentLineIndex
import com.anitail.music.lyrics.LyricsUtils.parseLyrics
import com.anitail.music.ui.component.shimmer.ShimmerHost
import com.anitail.music.ui.component.shimmer.TextPlaceholder
import com.anitail.music.ui.menu.LyricsMenu
import com.anitail.music.ui.screens.settings.DarkMode
import com.anitail.music.ui.screens.settings.LyricsPosition
import com.anitail.music.ui.utils.fadingEdge
import com.anitail.music.utils.ComposeToImage
import com.anitail.music.utils.rememberEnumPreference
import com.anitail.music.utils.rememberPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalFoundationApi::class)
@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun Lyrics(
    sliderPositionProvider: () -> Long?,
    modifier: Modifier = Modifier,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val menuState = LocalMenuState.current
    val density = LocalDensity.current
    var showLyrics by rememberPreference(ShowLyricsKey, false)
    val context = LocalContext.current  // Añade esta línea

    val landscapeOffset =
        LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    val lyricsTextPosition by rememberEnumPreference(LyricsTextPositionKey, LyricsPosition.CENTER)
    val changeLyrics by rememberPreference(LyricsClickKey, true)
    val scope = rememberCoroutineScope()

    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val lyricsEntity by playerConnection.currentLyrics.collectAsState(initial = null)
    val lyrics = remember(lyricsEntity) { lyricsEntity?.lyrics?.trim() }

    val playerBackground by rememberEnumPreference(
        key = PlayerBackgroundStyleKey,
        defaultValue = PlayerBackgroundStyle.DEFAULT
    )

    val darkTheme by rememberEnumPreference(DarkModeKey, defaultValue = DarkMode.AUTO)
    val isSystemInDarkTheme = isSystemInDarkTheme()
    val useDarkTheme = remember(darkTheme, isSystemInDarkTheme) {
        if (darkTheme == DarkMode.AUTO) isSystemInDarkTheme else darkTheme == DarkMode.ON
    }

    val lines =
        remember(lyrics) {
            if (lyrics == null || lyrics == LYRICS_NOT_FOUND) {
                emptyList()
            } else if (lyrics.startsWith("[")) {
                listOf(HEAD_LYRICS_ENTRY) + parseLyrics(lyrics)
            } else {
                lyrics.lines().mapIndexed { index, line -> LyricsEntry(index * 100L, line) }
            }
        }
    val isSynced =
        remember(lyrics) {
            !lyrics.isNullOrEmpty() && lyrics.startsWith("[")
        }

    val textColor = when (playerBackground) {
        PlayerBackgroundStyle.DEFAULT -> MaterialTheme.colorScheme.secondary
        else ->
            if (useDarkTheme)
                MaterialTheme.colorScheme.onSurface
            else
                MaterialTheme.colorScheme.onPrimary
    }

    var currentLineIndex by remember {
        mutableIntStateOf(-1)
    }
    // Because LaunchedEffect has delay, which leads to inconsistent with current line color and scroll animation,
    // we use deferredCurrentLineIndex when user is scrolling
    var deferredCurrentLineIndex by rememberSaveable {
        mutableIntStateOf(0)
    }

    var previousLineIndex by rememberSaveable {
        mutableIntStateOf(0)
    }

    var lastPreviewTime by rememberSaveable {
        mutableLongStateOf(0L)
    }
    var isSeeking by remember {
        mutableStateOf(false)
    }

    var initialScrollDone by rememberSaveable {
        mutableStateOf(false)
    }

    var shouldScrollToFirstLine by rememberSaveable {
        mutableStateOf(true)
    }

    var isAppMinimized by rememberSaveable {
        mutableStateOf(false)
    }

    val lazyListState = rememberLazyListState()

    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                val visibleItemsInfo = lazyListState.layoutInfo.visibleItemsInfo
                val isCurrentLineVisible = visibleItemsInfo.any { it.index == currentLineIndex }
                if (isCurrentLineVisible) {
                    initialScrollDone = false
                }
                isAppMinimized = true
            } else if(event == Lifecycle.Event.ON_START) {
                isAppMinimized = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(lyrics) {
        if (lyrics.isNullOrEmpty() || !lyrics.startsWith("[")) {
            currentLineIndex = -1
            return@LaunchedEffect
        }
        while (isActive) {
            delay(50)
            val sliderPosition = sliderPositionProvider()
            isSeeking = sliderPosition != null
            currentLineIndex = findCurrentLineIndex(
                lines,
                sliderPosition ?: playerConnection.player.currentPosition
            )
        }
    }

    LaunchedEffect(isSeeking, lastPreviewTime) {
        if (isSeeking) {
            lastPreviewTime = 0L
        } else if (lastPreviewTime != 0L) {
            delay(LyricsPreviewTime)
            lastPreviewTime = 0L
        }
    }

    LaunchedEffect(currentLineIndex, lastPreviewTime, initialScrollDone) {
        /**
         * Count number of new lines in a lyric
         */
        fun countNewLine(str: String) = str.count { it == '\n' }

        /**
         * Calculate the lyric offset Based on how many lines (\n chars)
         */
        fun calculateOffset() = with(density) {
            if (landscapeOffset) {
                16.dp.toPx()
                    .toInt() * countNewLine(lines[currentLineIndex].text) // landscape sits higher by default
            } else {
                20.dp.toPx().toInt() * countNewLine(lines[currentLineIndex].text)
            }
        }

        if (!isSynced) return@LaunchedEffect
        if((currentLineIndex == 0 && shouldScrollToFirstLine) || !initialScrollDone) {
            shouldScrollToFirstLine = false
            lazyListState.scrollToItem(
                currentLineIndex,
                with(density) { 36.dp.toPx().toInt() } + calculateOffset())
            if(!isAppMinimized) {
                initialScrollDone = true
            }
        } else if (currentLineIndex != -1) {
            deferredCurrentLineIndex = currentLineIndex
            if (isSeeking) {
                lazyListState.scrollToItem(
                    currentLineIndex,
                    with(density) { 36.dp.toPx().toInt() } + calculateOffset())
            } else if (lastPreviewTime == 0L || currentLineIndex != previousLineIndex) {
                val visibleItemsInfo = lazyListState.layoutInfo.visibleItemsInfo
                val isCurrentLineVisible = visibleItemsInfo.any { it.index == currentLineIndex }

                if (isCurrentLineVisible) {
                    val viewportStartOffset = lazyListState.layoutInfo.viewportStartOffset
                    val viewportEndOffset = lazyListState.layoutInfo.viewportEndOffset
                    val currentLineOffset = visibleItemsInfo.find { it.index == currentLineIndex }?.offset ?: 0
                    val previousLineOffset = visibleItemsInfo.find { it.index == previousLineIndex }?.offset ?: 0

                    val centerRangeStart = viewportStartOffset + (viewportEndOffset - viewportStartOffset) / 2
                    val centerRangeEnd = viewportEndOffset - (viewportEndOffset - viewportStartOffset) / 8

                    if (currentLineOffset in centerRangeStart..centerRangeEnd ||
                        previousLineOffset in centerRangeStart..centerRangeEnd) {
                        lazyListState.animateScrollToItem(
                            currentLineIndex,
                            with(density) { 36.dp.toPx().toInt() } + calculateOffset())
                    }
                }
            }
        }
        if(currentLineIndex > 0) {
            shouldScrollToFirstLine = true
        }
        previousLineIndex = currentLineIndex
    }

    BoxWithConstraints(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxSize()
            .padding(bottom = 12.dp)
    ) {
        LazyColumn(
            state = lazyListState,
            contentPadding = WindowInsets.systemBars
                .only(WindowInsetsSides.Top)
                .add(WindowInsets(top = maxHeight / 2, bottom = maxHeight / 2))
                .asPaddingValues(),
            modifier = Modifier
                .fadingEdge(vertical = 64.dp)
                .nestedScroll(remember {
                    object : NestedScrollConnection {
                        override fun onPostScroll(
                            consumed: Offset,
                            available: Offset,
                            source: NestedScrollSource
                        ): Offset {
                            lastPreviewTime = System.currentTimeMillis()
                            return super.onPostScroll(consumed, available, source)
                        }

                        override suspend fun onPostFling(
                            consumed: Velocity,
                            available: Velocity
                        ): Velocity {
                            lastPreviewTime = System.currentTimeMillis()
                            return super.onPostFling(consumed, available)
                        }
                    }
                })
        ) {
            val displayedCurrentLineIndex =
                if (isSeeking) deferredCurrentLineIndex else currentLineIndex

            if (lyrics == null) {
                item {
                    ShimmerHost {
                        repeat(10) {
                            Box(
                                contentAlignment = when (lyricsTextPosition) {
                                    LyricsPosition.LEFT -> Alignment.CenterStart
                                    LyricsPosition.CENTER -> Alignment.Center
                                    LyricsPosition.RIGHT -> Alignment.CenterEnd
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp, vertical = 4.dp)
                            ) {
                                TextPlaceholder()
                            }
                        }
                    }
                }
            } else {
                itemsIndexed(
                    items = lines
                ) { index, item ->
                    Text(
                        text = item.text,
                        fontSize = 20.sp,
                        color = textColor,
                        textAlign = when (lyricsTextPosition) {
                            LyricsPosition.LEFT -> TextAlign.Left
                            LyricsPosition.CENTER -> TextAlign.Center
                            LyricsPosition.RIGHT -> TextAlign.Right
                        },
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                enabled = true,
                                onClick = {
                                    if (isSynced && changeLyrics) {
                                        playerConnection.player.seekTo(item.time)
                                        scope.launch {
                                            lazyListState.animateScrollToItem(
                                                index,
                                                with(density) { 36.dp.toPx().toInt() } +
                                                        with(density) {
                                                            val count = item.text.count { it == '\n' }
                                                            (if (landscapeOffset) 16.dp.toPx() else 20.dp.toPx()).toInt() * count
                                                        }
                                            )
                                        }
                                        lastPreviewTime = 0L
                                    }
                                },
                                onLongClick = {
                                    mediaMetadata?.let { metadata ->
                                        // Mostrar opciones para compartir como texto o como imagen
                                        val options = arrayOf(
                                            context.getString(R.string.share_as_text),
                                            context.getString(R.string.share_as_image),
                                            context.getString(R.string.cancel)
                                        )
                                        AlertDialog.Builder(context)
                                            .setTitle(context.getString(R.string.share_lyrics))
                                            .setItems(options) { _, which ->
                                                when (which) {
                                                    0 -> {
                                                        val shareIntent = Intent().apply {
                                                            action = Intent.ACTION_SEND
                                                            type = "text/plain"
                                                            val songTitle = metadata.title
                                                            val artists = metadata.artists.joinToString { it.name }
                                                            val songLink = "https://music.youtube.com/watch?v=${metadata.id}"
                                                            putExtra(Intent.EXTRA_TEXT, "\"${item.text}\"\n\n${songTitle} - ${artists}\n${songLink}")
                                                        }
                                                        context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.share_lyrics)))
                                                    }
                                                    1 -> {

                                                        // Compartir como imagen
                                                        scope.launch {
                                                            try {
                                                                // Obtener la actividad
                                                                val activity = context.findActivity() as? android.app.Activity
                                                                if (activity == null) {
                                                                    Toast.makeText(context, "error: activity is null", Toast.LENGTH_SHORT).show()
                                                                    return@launch
                                                                }

                                                                val progressDialog = AlertDialog.Builder(activity)
                                                                    .setTitle(context.getString(R.string.generating_image))
                                                                    .setMessage(context.getString(R.string.please_wait))
                                                                    .setCancelable(false)
                                                                    .create()

                                                                activity.runOnUiThread {
                                                                    progressDialog.show()
                                                                }
                                                                try {
                                                                    val width = 1080
                                                                    val height = 1920

                                                                    val bitmap = ComposeToImage.createLyricsImage(
                                                                        context = activity,
                                                                        coverArtUrl = metadata.thumbnailUrl,
                                                                        songTitle = metadata.title,
                                                                        artistName = metadata.artists.joinToString { it.name },
                                                                        lyrics = item.text,
                                                                        width = width,
                                                                        height = height,
                                                                        isDarkTheme = useDarkTheme
                                                                    )

                                                                    // Ocultar el diálogo de progreso
                                                                    activity.runOnUiThread {
                                                                        progressDialog.dismiss()
                                                                    }

                                                                    // Guardar y compartir la imagen
                                                                    val fileName = "lyrics_${metadata.id}_${System.currentTimeMillis()}"
                                                                    val imageUri = ComposeToImage.saveBitmapAsFile(activity, bitmap, fileName)

                                                                    // Compartir la imagen
                                                                    val shareIntent = Intent().apply {
                                                                        action = Intent.ACTION_SEND
                                                                        type = "image/png"
                                                                        putExtra(Intent.EXTRA_STREAM, imageUri)
                                                                        putExtra(Intent.EXTRA_SUBJECT, "${metadata.title} - ${metadata.artists.joinToString { it.name }}")
                                                                        flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                                                                    }

                                                                    withContext(Dispatchers.Main) {
                                                                        activity.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.share_lyrics)))
                                                                    }
                                                                } catch (e: Exception) {
                                                                    // Ocultar el diálogo en caso de error
                                                                    withContext(Dispatchers.Main) {
                                                                        progressDialog.dismiss()
                                                                        Toast.makeText(activity, "Error al generar la imagen: ${e.message}", Toast.LENGTH_SHORT).show()
                                                                    }
                                                                    e.printStackTrace()
                                                                }
                                                            } catch (e: Exception) {
                                                                Toast.makeText(context, "Error inesperado: ${e.message}", Toast.LENGTH_SHORT).show()
                                                                e.printStackTrace()
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                            .show()
                                    }
                                }
                            )
                            .padding(horizontal = 24.dp, vertical = 8.dp)
                            .alpha(if (!isSynced || index == displayedCurrentLineIndex) 1f else 0.5f)
                    )
                }
            }
        }

        if (lyrics == LYRICS_NOT_FOUND) {
            Text(
                text = stringResource(R.string.lyrics_not_found),
                fontSize = 20.sp,
                color = MaterialTheme.colorScheme.secondary,
                textAlign = when (lyricsTextPosition) {
                    LyricsPosition.LEFT -> TextAlign.Left
                    LyricsPosition.CENTER -> TextAlign.Center
                    LyricsPosition.RIGHT -> TextAlign.Right
                },
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp)
                    .alpha(0.5f)
            )
        }

        mediaMetadata?.let { mediaMetadata ->
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 12.dp)
            ) {
                IconButton(
                    onClick = {
                        menuState.show {
                            LyricsMenu(
                                lyricsProvider = { lyricsEntity },
                                mediaMetadataProvider = { mediaMetadata },
                                onDismiss = menuState::dismiss
                            )
                        }
                    }
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.more_horiz),
                        contentDescription = null,
                        tint = textColor
                    )
                }
            }
        }
    }
}

private fun Context.findActivity(): Context? {
    var currentContext = this
    while (currentContext is android.content.ContextWrapper) {
        if (currentContext is android.app.Activity) {
            return currentContext
        }
        currentContext = currentContext.baseContext
    }
    return null
}

const val ANIMATE_SCROLL_DURATION = 300L
val LyricsPreviewTime = 2.seconds
