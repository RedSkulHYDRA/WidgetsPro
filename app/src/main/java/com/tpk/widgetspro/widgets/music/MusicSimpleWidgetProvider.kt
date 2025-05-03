package com.tpk.widgetspro.widgets.music

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.tpk.widgetspro.R
import com.tpk.widgetspro.services.music.MediaMonitorService

class MusicSimpleWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_PLAY_PAUSE = "com.example.musicsimplewidget.ACTION_PLAY_PAUSE"
        const val ACTION_NEXT = "com.example.musicsimplewidget.ACTION_NEXT"
        const val ACTION_PREVIOUS = "com.example.musicsimplewidget.ACTION_PREVIOUS"
        const val ACTION_MEDIA_UPDATE = "com.example.musicsimplewidget.ACTION_MEDIA_UPDATE"
        const val PREFS_NAME = "music_widget_prefs"
        const val PREF_PREFIX_KEY = "music_app_"
        private var lastAppLaunchTime: Long = 0
        private const val APP_LAUNCH_COOLDOWN_MS: Long = 5000
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { appWidgetId ->
            updateAppWidget(context, appWidgetManager, appWidgetId)
            startMediaMonitorService(context)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action ?: return
        val mediaController = getMediaController(context)
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val componentName = ComponentName(context, MusicSimpleWidgetProvider::class.java)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)

        if (appWidgetIds.isEmpty()) return
        val appWidgetId = appWidgetIds[0]

        when (action) {
            ACTION_PLAY_PAUSE -> handlePlayPause(context, mediaController, appWidgetId)
            ACTION_NEXT -> mediaController?.transportControls?.skipToNext()
            ACTION_PREVIOUS -> mediaController?.transportControls?.skipToPrevious()
            ACTION_MEDIA_UPDATE -> { }
            else -> return
        }

        appWidgetIds.forEach { id ->
            updateAppWidget(context, appWidgetManager, id)
        }
    }

    private fun updateAppWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        val views = RemoteViews(context.packageName, R.layout.music_simple_widget)
        val mediaController = getMediaController(context)

        if (mediaController != null) {
            val metadata = mediaController.metadata
            val playbackState = mediaController.playbackState

            if (metadata != null && playbackState != null) {
                views.setTextViewText(
                    R.id.text_title,
                    metadata.getString(android.media.MediaMetadata.METADATA_KEY_TITLE) ?: "Unknown Title"
                )
                views.setTextViewText(
                    R.id.text_artist,
                    metadata.getString(android.media.MediaMetadata.METADATA_KEY_ARTIST) ?: "Unknown Artist"
                )

                val albumArt = metadata.getBitmap(android.media.MediaMetadata.METADATA_KEY_ALBUM_ART)
                if (albumArt != null) {
                    views.setImageViewBitmap(R.id.image_album_art, albumArt)
                } else {
                    views.setImageViewResource(R.id.image_album_art, R.drawable.ic_default_album_art)
                }

                val isPlaying = playbackState.state == PlaybackState.STATE_PLAYING
                views.setImageViewResource(
                    R.id.button_play_pause,
                    if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow
                )

            } else {
                setNoMusicPlaying(views)
            }
        } else {
            setNoMusicPlaying(views)
        }

        setupPendingIntents(context, views, appWidgetId)
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    private fun setNoMusicPlaying(views: RemoteViews) {
        views.setTextViewText(R.id.text_title, "No music playing")
        views.setTextViewText(R.id.text_artist, "")
        views.setImageViewResource(R.id.image_album_art, R.drawable.ic_default_album_art)
        views.setImageViewResource(R.id.button_play_pause, R.drawable.ic_play_arrow)
    }

    private fun setupPendingIntents(context: Context, views: RemoteViews, appWidgetId: Int) {
        val playPauseIntent = Intent(context, MusicSimpleWidgetProvider::class.java).apply {
            action = ACTION_PLAY_PAUSE
        }
        val playPausePendingIntent = PendingIntent.getBroadcast(
            context, 0, playPauseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val nextIntent = Intent(context, MusicSimpleWidgetProvider::class.java).apply {
            action = ACTION_NEXT
        }
        val nextPendingIntent = PendingIntent.getBroadcast(
            context, 1, nextIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val prevIntent = Intent(context, MusicSimpleWidgetProvider::class.java).apply {
            action = ACTION_PREVIOUS
        }
        val prevPendingIntent = PendingIntent.getBroadcast(
            context, 2, prevIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val launchIntent = getLaunchMusicAppIntent(context, appWidgetId)
        val launchPendingIntent = PendingIntent.getActivity(
            context, appWidgetId, launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        views.setOnClickPendingIntent(R.id.button_play_pause, playPausePendingIntent)
        views.setOnClickPendingIntent(R.id.button_next, nextPendingIntent)
        views.setOnClickPendingIntent(R.id.button_previous, prevPendingIntent)
        views.setOnClickPendingIntent(R.id.widget_container, launchPendingIntent)
    }

    private fun getMediaController(context: Context): MediaController? {
        val sessionManager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager?
        val componentName = ComponentName(context, "com.example.musicsimplewidget.NotificationListener")
        try {
            val controllers = sessionManager?.getActiveSessions(componentName)
            return controllers?.firstOrNull()
        } catch (e: SecurityException) {
            return null
        }
    }

    private fun getLaunchMusicAppIntent(context: Context, appWidgetId: Int): Intent {
        val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val packageName = prefs.getString(PREF_PREFIX_KEY + appWidgetId, null)

        return if (packageName != null) {
            context.packageManager.getLaunchIntentForPackage(packageName)
                ?: getDefaultMusicIntent()
        } else {
            getDefaultMusicIntent()
        }
    }

    private fun getDefaultMusicIntent(): Intent {
        return Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_APP_MUSIC)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }

    private fun handlePlayPause(context: Context, mediaController: MediaController?, appWidgetId: Int) {
        if (mediaController != null) {
            val state = mediaController.playbackState?.state
            if (state == PlaybackState.STATE_PLAYING) {
                mediaController.transportControls?.pause()
            } else if (state == PlaybackState.STATE_PAUSED || state == PlaybackState.STATE_STOPPED) {
                mediaController.transportControls?.play()
            } else {
                launchMusicApp(context, appWidgetId)
            }
        } else {
            launchMusicApp(context, appWidgetId)
        }
    }

    private fun launchMusicApp(context: Context, appWidgetId: Int) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastAppLaunchTime < APP_LAUNCH_COOLDOWN_MS) {
            return
        }
        try {
            val intent = getLaunchMusicAppIntent(context, appWidgetId)
            context.startActivity(intent)
            lastAppLaunchTime = currentTime
        } catch (e: Exception) {
        }
    }

    private fun startMediaMonitorService(context: Context) {
        val serviceIntent = Intent(context, MediaMonitorService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.startForegroundService(context, serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
        appWidgetIds.forEach { appWidgetId ->
            prefs.remove(PREF_PREFIX_KEY + appWidgetId)
        }
        prefs.apply()
        super.onDeleted(context, appWidgetIds)
    }

    override fun onDisabled(context: Context) {
        context.stopService(Intent(context, MediaMonitorService::class.java))
        super.onDisabled(context)
    }
}