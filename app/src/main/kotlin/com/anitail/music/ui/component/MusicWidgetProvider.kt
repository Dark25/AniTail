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
import coil.request.CachePolicy
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
    }    @RequiresApi(Build.VERSION_CODES.O)
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val thisWidget = ComponentName(context, MusicWidgetProvider::class.java)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)

        when (intent.action) {
            MusicService.ACTION_WIDGET_UPDATE -> {
                updateWidgets(context, appWidgetManager, appWidgetIds, intent)
            }            ACTION_PLAY_PAUSE, ACTION_NEXT, ACTION_PREV, MusicService.ACTION_PLAY_RECOMMENDATION -> {
                if (intent.action == MusicService.ACTION_PLAY_RECOMMENDATION) {
                    val songId = intent.getStringExtra(MusicService.EXTRA_WIDGET_RECOMMENDATION_ID)

                    val serviceIntent = Intent(context, MusicService::class.java).apply {
                        this.action = MusicService.ACTION_PLAY_RECOMMENDATION
                        putExtra(MusicService.EXTRA_WIDGET_RECOMMENDATION_ID, songId)
                        addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                    }
                    context.startForegroundService(serviceIntent)
                    return
                }
                
                forwardActionToService(context, intent)
            if (intent.action == ACTION_PLAY_PAUSE) {
                }else if (intent.action == ACTION_NEXT || intent.action == ACTION_PREV) {
                    CoroutineScope(Dispatchers.IO).launch {
                        kotlinx.coroutines.delay(300)
                        requestMusicServiceUpdate(context)
                    }
                }
            }
        }
    }
    private fun updateWidgets(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray, updateIntent: Intent? = null) {

        if (appWidgetIds.isEmpty()) {
            Timber.tag(TAG).w("No widget instances found")
            return
        }

        val views = RemoteViews(context.packageName, R.layout.widget_music)

        // Set click listeners for controls
        setupWidgetControls(context, views)

        // Update widget content if we have update data
        if (updateIntent != null && updateIntent.action == MusicService.ACTION_WIDGET_UPDATE) {

            // Read up to 4 recommendations
            val recommendations = listOf(
                Recommendation(
                    updateIntent.getStringExtra(MusicService.EXTRA_WIDGET_RECOMMENDATION_1_TITLE) ?: "",
                    updateIntent.getStringExtra(MusicService.EXTRA_WIDGET_RECOMMENDATION_1_COVER_URL) ?: "",
                    updateIntent.getStringExtra(MusicService.EXTRA_WIDGET_RECOMMENDATION_1_ID) ?: ""
                ),
                Recommendation(
                    updateIntent.getStringExtra(MusicService.EXTRA_WIDGET_RECOMMENDATION_2_TITLE) ?: "",
                    updateIntent.getStringExtra(MusicService.EXTRA_WIDGET_RECOMMENDATION_2_COVER_URL) ?: "",
                    updateIntent.getStringExtra(MusicService.EXTRA_WIDGET_RECOMMENDATION_2_ID) ?: ""
                ),
                Recommendation(
                    updateIntent.getStringExtra(MusicService.EXTRA_WIDGET_RECOMMENDATION_3_TITLE) ?: "",
                    updateIntent.getStringExtra(MusicService.EXTRA_WIDGET_RECOMMENDATION_3_COVER_URL) ?: "",
                    updateIntent.getStringExtra(MusicService.EXTRA_WIDGET_RECOMMENDATION_3_ID) ?: ""
                ),
                Recommendation(
                    updateIntent.getStringExtra(MusicService.EXTRA_WIDGET_RECOMMENDATION_4_TITLE) ?: "",
                    updateIntent.getStringExtra(MusicService.EXTRA_WIDGET_RECOMMENDATION_4_COVER_URL) ?: "",
                    updateIntent.getStringExtra(MusicService.EXTRA_WIDGET_RECOMMENDATION_4_ID) ?: ""
                )
            )

            val song = try {
                updateIntent.getStringExtra(MusicService.EXTRA_WIDGET_SONG_TITLE) ?: ""
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Error obtaining song title")
                ""
            }
            
            val artist = try {
                updateIntent.getStringExtra(MusicService.EXTRA_WIDGET_ARTIST) ?:
                updateIntent.getIntExtra(MusicService.EXTRA_WIDGET_ARTIST, 0).let { 
                    if (it != 0) try { context.getString(it) } catch (e: Exception) { "" } else ""
                }
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Error obtaining artist name")
                ""
            }
            
            val recommendation = try {
                updateIntent.getStringExtra(MusicService.EXTRA_WIDGET_RECOMMENDATION) ?: ""
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Error obtaining recommendation")
                ""
            }
            
            val isPlaying = try {
                updateIntent.getBooleanExtra(MusicService.EXTRA_WIDGET_IS_PLAYING, false)
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Error obtaining play state")
                false
            }
            val themeColor = try {
                updateIntent.getIntExtra(MusicService.EXTRA_WIDGET_THEME_COLOR, 0xFFED5564.toInt())
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Error obtaining theme color")
                0xFFED5564.toInt()
            }
            
            val coverUrl = try {
                updateIntent.getStringExtra(MusicService.EXTRA_WIDGET_COVER_URL) ?: ""
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Error obtaining cover URL")
                ""
            }
            
            val progress = try {
                updateIntent.getIntExtra(MusicService.EXTRA_WIDGET_PROGRESS, 0)
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Error obtaining progress")
                0
            }
            
            val dominantColor = try {
                updateIntent.getIntExtra(MusicService.EXTRA_WIDGET_DOMINANT_COLOR, themeColor)
                    .let { if (it == 0) themeColor else it }
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Error obtaining dominant color")
                themeColor
            }

            views.setTextViewText(R.id.widget_title, song)
            views.setInt(R.id.widget_root, "setBackgroundColor", dominantColor)

            val artistText = artist.ifBlank { context.getString(R.string.unknown_artist) }
            views.setTextViewText(R.id.widget_artist, artistText)

            val playPauseIcon = if (isPlaying) R.drawable.pause else R.drawable.play
            views.setImageViewResource(R.id.widget_play_pause, playPauseIcon)

            views.setProgressBar(R.id.widget_song_progress, 100, progress, false)

            for (appWidgetId in appWidgetIds) {
                appWidgetManager.updateAppWidget(appWidgetId, views)
            }

            // Set recommendations (image, title, click)
            recommendations.forEachIndexed { idx, rec ->
                val coverId = context.resources.getIdentifier("widget_recommendation_cover_${idx+1}", "id", context.packageName)
                val containerId = context.resources.getIdentifier("widget_recommendation_${idx+1}", "id", context.packageName)
                val titleId = context.resources.getIdentifier("widget_recommendation_title_${idx+1}", "id", context.packageName)
                // Set title
                views.setTextViewText(titleId, rec.title)
                // Set click intent if id is present
                if (rec.id.isNotBlank()) {
                    val intent = Intent(context, MusicWidgetProvider::class.java).apply {
                        action = MusicService.ACTION_PLAY_RECOMMENDATION
                        putExtra(MusicService.EXTRA_WIDGET_RECOMMENDATION_ID, rec.id)
                    }

                    val pendingIntent = PendingIntent.getBroadcast(
                        context, 100 + idx, intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )

                    views.setOnClickPendingIntent(coverId, pendingIntent)
                    views.setOnClickPendingIntent(containerId, pendingIntent)
                } else {
                    Timber.tag(TAG).w("[WIDGET] Recommendation idx=$idx has blank id, no click intent set. Title='${rec.title}'")
                }
                // Load image async if url present
                if (rec.coverUrl.isNotBlank()) {
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val imageLoader = ImageLoader.Builder(context)
                                .memoryCachePolicy(CachePolicy.ENABLED)
                                .crossfade(true)
                                .build()
                            val request = ImageRequest.Builder(context)
                                .data(rec.coverUrl)
                                .size(96, 96)
                                .allowHardware(false)
                                .crossfade(true)
                                .build()
                            val result = imageLoader.execute(request)
                            val bitmap = result.drawable?.toBitmap()
                            if (bitmap != null) {
                                withContext(Dispatchers.IO) {
                                    views.setImageViewBitmap(coverId, bitmap)
                                    
                                    try {
                                        views.setBoolean(coverId, "setClipToOutline", true)
                                    } catch (e: Exception) {
                                        Timber.tag(TAG).e(e, "Error setting clip to outline for recommendation cover")
                                    }
                                    
                                    if (rec.id.isNotBlank()) {
                                        val intent = Intent(context, MusicWidgetProvider::class.java).apply {
                                            action = MusicService.ACTION_PLAY_RECOMMENDATION
                                            putExtra(MusicService.EXTRA_WIDGET_RECOMMENDATION_ID, rec.id)
                                        }
                                        val pendingIntent = PendingIntent.getBroadcast(
                                            context, 100 + idx, intent,
                                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                                        )
                                        val containerId = context.resources.getIdentifier("widget_recommendation_${idx+1}", "id", context.packageName)
                                        views.setOnClickPendingIntent(coverId, pendingIntent)
                                        views.setOnClickPendingIntent(containerId, pendingIntent)
                                    } else {
                                        Timber.tag(TAG).w("[WIDGET-ASYNC] Recommendation idx=$idx has blank id, no click intent set. Title='${rec.title}' (async)")
                                    }
                                    for (appWidgetId in appWidgetIds) {
                                        appWidgetManager.updateAppWidget(appWidgetId, views)
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Timber.tag(TAG).e(e, "Error loading recommendation cover image")
                        }
                    }
                } else {
                    views.setImageViewResource(coverId, R.drawable.ic_music_placeholder)
                }
            }

            if (coverUrl.isNotBlank()) {
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val imageLoader = ImageLoader.Builder(context)
                            .memoryCachePolicy(CachePolicy.ENABLED)
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
                            withContext(Dispatchers.IO) {
                                views.setImageViewBitmap(R.id.widget_cover, bitmap)
                                views.setInt(R.id.widget_root, "setBackgroundColor", dominantColor)
                                for (appWidgetId in appWidgetIds) {
                                    appWidgetManager.updateAppWidget(appWidgetId, views)
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
    }
    @RequiresApi(Build.VERSION_CODES.O)
    private fun forwardActionToService(context: Context, intent: Intent) {
        val action = intent.action ?: return

        if (action == ACTION_PLAY_PAUSE) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val appWidgetManager = AppWidgetManager.getInstance(context)
                    val thisWidget = ComponentName(context, MusicWidgetProvider::class.java)
                    
                    val views = RemoteViews(context.packageName, R.layout.widget_music)
                    setupWidgetControls(context, views)

                    val isPlaying = intent.getBooleanExtra("current_is_playing", false)

                    views.setImageViewResource(R.id.widget_play_pause,
                        if (isPlaying) R.drawable.play else R.drawable.pause)

                    appWidgetManager.updateAppWidget(thisWidget, views)

                    kotlinx.coroutines.delay(800)
                    requestMusicServiceUpdate(context)
                } catch (e: Exception) {
                    Timber.tag(TAG).e(e, "Error updating widget temporarily")
                }
            }
        }

        val serviceIntent = Intent(context, MusicService::class.java).apply {
            this.action = action

            intent.extras?.let { bundleExtras ->
                putExtras(bundleExtras)
            }

            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        }

        try {
            val pendingIntent = PendingIntent.getForegroundService(
                context,
                action.hashCode(),
                serviceIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            pendingIntent.send()
            Timber.tag(TAG).d("Action forwarded to service via PendingIntent: $action")
        } catch (_: Exception) {
            try {
                context.startForegroundService(serviceIntent)
            } catch (e2: Exception) {
                Timber.tag(TAG).e(e2, "Error starting service for action: $action")
            }
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
    // Helper for recommendation
    data class Recommendation(val title: String, val coverUrl: String, val id: String)

    companion object {
        private const val TAG = "MusicWidgetProvider"
        const val ACTION_PLAY_PAUSE = "com.anitail.music.widget.PLAY_PAUSE"
        const val ACTION_NEXT = "com.anitail.music.widget.NEXT"
        const val ACTION_PREV = "com.anitail.music.widget.PREV"
    }
}
