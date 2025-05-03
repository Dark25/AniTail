package com.anitail.music.ui.component

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import androidx.annotation.RequiresApi
import androidx.core.graphics.drawable.toBitmap
import coil.ImageLoader
import coil.request.ImageRequest
import com.anitail.music.R
import com.anitail.music.playback.MusicService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

class MusicWidgetProvider : AppWidgetProvider() {
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        updateWidgets(context, appWidgetManager, appWidgetIds)
        requestMusicServiceUpdate(context)
    }
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val thisWidget = ComponentName(context, MusicWidgetProvider::class.java)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)

        when (intent.action) {
            MusicService.ACTION_WIDGET_UPDATE -> {

                updateWidgets(context, appWidgetManager, appWidgetIds, intent)
            }
            ACTION_PLAY_PAUSE, ACTION_NEXT, ACTION_PREV -> {
                forwardActionToService(context, intent.action ?: return)

                if (intent.action == ACTION_PLAY_PAUSE) {
                    CoroutineScope(Dispatchers.Main).launch {
                        kotlinx.coroutines.delay(200)
                        requestMusicServiceUpdate(context)

                        kotlinx.coroutines.delay(500)
                        requestMusicServiceUpdate(context)
                    }
                } else {
                    CoroutineScope(Dispatchers.Main).launch {
                        kotlinx.coroutines.delay(300)
                        requestMusicServiceUpdate(context)
                    }
                }
            }
        }
    }    private fun updateWidgets(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray, updateIntent: Intent? = null) {

        if (appWidgetIds.isEmpty()) {
            Timber.tag(TAG).w("No widget instances found")
            return
        }

        val views = RemoteViews(context.packageName, R.layout.widget_music)

        // Set click listeners for controls
        setupWidgetControls(context, views)

        // Update widget content if we have update data
        if (updateIntent != null && updateIntent.action == MusicService.ACTION_WIDGET_UPDATE) {

            val song = updateIntent.getStringExtra(MusicService.EXTRA_WIDGET_SONG_TITLE) ?: ""
            val artist = updateIntent.getStringExtra(MusicService.EXTRA_WIDGET_ARTIST) ?: ""
            val recommendation =
                updateIntent.getStringExtra(MusicService.EXTRA_WIDGET_RECOMMENDATION) ?: ""
            val isPlaying =
                updateIntent.getBooleanExtra(MusicService.EXTRA_WIDGET_IS_PLAYING, false)
            val themeColor =
                updateIntent.getIntExtra(MusicService.EXTRA_WIDGET_THEME_COLOR, 0xFFED5564.toInt())
            val coverUrl = updateIntent.getStringExtra(MusicService.EXTRA_WIDGET_COVER_URL) ?: ""
            val progress = updateIntent.getIntExtra(MusicService.EXTRA_WIDGET_PROGRESS, 0)
            val dominantColor =
                updateIntent.getIntExtra(MusicService.EXTRA_WIDGET_DOMINANT_COLOR, themeColor)
                    .let { if (it == 0) themeColor else it }

            Timber.tag(TAG)
                .d("Updating widget with song: '$song', artist: '$artist', isPlaying: $isPlaying, recommendation: '$recommendation', coverUrl: $coverUrl")            // Configuración inicial del widget
            views.setTextViewText(R.id.widget_title, song)

            val artistText = artist.ifBlank { context.getString(R.string.unknown_artist) }
            views.setTextViewText(R.id.widget_artist, artistText)

            val playPauseIcon = if (isPlaying) R.drawable.pause else R.drawable.play
            views.setImageViewResource(R.id.widget_play_pause, playPauseIcon)

            views.setProgressBar(R.id.widget_song_progress, 100, progress, false)

            for (appWidgetId in appWidgetIds) {
                appWidgetManager.updateAppWidget(appWidgetId, views)
            }

            if (coverUrl.isNotBlank()) {
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val imageLoader = ImageLoader.Builder(context)
                            .crossfade(true)
                            .build()
                val request = ImageRequest.Builder(context)
                            .data(coverUrl)
                            .size(135, 135)
                            .allowHardware(false)
                            .crossfade(true)
                            .build()
                            
                        val result = imageLoader.execute(request)
                        val bitmap = result.drawable?.toBitmap()

                        if (bitmap != null) {
                            withContext(Dispatchers.Main) {
                                try {
                                    val updatedViews = RemoteViews(context.packageName, R.layout.widget_music)
                                    setupWidgetControls(context, updatedViews)
                                    updatedViews.setTextViewText(R.id.widget_title, song)

                                    val artistText = artist.ifBlank { context.getString(R.string.unknown_artist) }
                                    updatedViews.setTextViewText(R.id.widget_artist, artistText)

                                    val playPauseIcon = if (isPlaying) R.drawable.pause else R.drawable.play
                                    updatedViews.setImageViewResource(R.id.widget_play_pause, playPauseIcon)

                                    updatedViews.setInt(R.id.widget_root, "setBackgroundColor", dominantColor)

                                    updatedViews.setImageViewBitmap(R.id.widget_cover, bitmap)

                                    updatedViews.setProgressBar(R.id.widget_song_progress, 100, progress, false)

                                    for (appWidgetId in appWidgetIds) {
                                        appWidgetManager.updateAppWidget(appWidgetId, updatedViews)
                                    }
                                } catch (e: Exception) {
                                    Timber.tag(TAG).e(e, "Error updating widget with bitmap")
                                }
                            }
                        } else {
                            Timber.tag(TAG).w("Failed to load cover image, bitmap is null")
                        }
                    } catch (e: Exception) {
                        Timber.tag(TAG).e(e, "Error loading cover image")
                    }
                }
            } else {
                views.setImageViewResource(R.id.widget_cover, R.drawable.ic_music_placeholder)

                for (appWidgetId in appWidgetIds) {
                    appWidgetManager.updateAppWidget(appWidgetId, views)
                }
            }
        } else {
            views.setTextViewText(R.id.widget_title, context.getString(R.string.app_name))
            views.setTextViewText(R.id.widget_artist, context.getString(R.string.song_notplaying))
            views.setImageViewResource(R.id.widget_play_pause, R.drawable.play)
            views.setImageViewResource(R.id.widget_cover, R.drawable.ic_music_placeholder)
            views.setProgressBar(R.id.widget_song_progress, 100, 0, false)

            for (appWidgetId in appWidgetIds) {
                appWidgetManager.updateAppWidget(appWidgetId, views)
            }
        }
    }

    private fun setupWidgetControls(context: Context, views: RemoteViews) {
        val openPlayerIntent = Intent(context, com.anitail.music.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPlayerPendingIntent = PendingIntent.getActivity(
            context, 3, openPlayerIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_cover, openPlayerPendingIntent)
        // Play/Pause intent
        val playPauseIntent = Intent(context, MusicWidgetProvider::class.java).apply {
            action = ACTION_PLAY_PAUSE
        }
        val playPausePendingIntent = PendingIntent.getBroadcast(
            context, 0, playPauseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_play_pause, playPausePendingIntent)

        // Next track intent
        val nextIntent = Intent(context, MusicWidgetProvider::class.java).apply {
            action = ACTION_NEXT
        }
        val nextPendingIntent = PendingIntent.getBroadcast(
            context, 1, nextIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_next, nextPendingIntent)

        // Previous track intent
        val prevIntent = Intent(context, MusicWidgetProvider::class.java).apply {
            action = ACTION_PREV
        }
        val prevPendingIntent = PendingIntent.getBroadcast(
            context, 2, prevIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_prev, prevPendingIntent)
    }    @RequiresApi(Build.VERSION_CODES.O)
    private fun forwardActionToService(context: Context, action: String) {

        if (action == ACTION_PLAY_PAUSE) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val thisWidget = ComponentName(context, MusicWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
            
            for (appWidgetId in appWidgetIds) {
                try {
                    val views = RemoteViews(context.packageName, R.layout.widget_music)
                    setupWidgetControls(context, views)

                    views.setImageViewResource(R.id.widget_play_pause, R.drawable.equalizer)
                    appWidgetManager.updateAppWidget(appWidgetId, views)
                } catch (e: Exception) {
                    Timber.tag(TAG).e(e, "Error updating widget temporarily")
                }
            }
        }
        
        val serviceIntent = Intent(context, MusicService::class.java).apply {
            this.action = action
        }
        try {
            // Use PendingIntent.getForegroundService() to properly handle background execution limits
            val pendingIntent = PendingIntent.getForegroundService(
                context,
                action.hashCode(),
                serviceIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            pendingIntent.send()
            Timber.tag(TAG).d("Action forwarded to service via PendingIntent")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error forwarding action to service")
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun requestMusicServiceUpdate(context: Context) {

        val serviceIntent = Intent(context, MusicService::class.java).apply {
            action = "com.anitail.music.action.UPDATE_WIDGET"
        }
        try {
            // Use PendingIntent.getForegroundService() to properly handle background execution limits
            val pendingIntent = PendingIntent.getForegroundService(
                context,
                0,
                serviceIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            pendingIntent.send()
            Timber.tag(TAG).d("Service intent sent for widget update via PendingIntent")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error sending service intent for update")
        }
    }

    companion object {
        private const val TAG = "MusicWidgetProvider"
        const val ACTION_PLAY_PAUSE = "com.anitail.music.widget.PLAY_PAUSE"
        const val ACTION_NEXT = "com.anitail.music.widget.NEXT"
        const val ACTION_PREV = "com.anitail.music.widget.PREV"
    }
}
