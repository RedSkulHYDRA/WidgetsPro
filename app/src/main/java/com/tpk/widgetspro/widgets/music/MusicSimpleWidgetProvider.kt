package com.tpk.widgetspro.widgets.music

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper

import android.util.TypedValue
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.tpk.widgetspro.R
import com.tpk.widgetspro.services.music.MediaMonitorService
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

class MusicSimpleWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_PLAY_PAUSE = "com.example.musicsimplewidget.ACTION_PLAY_PAUSE"
        const val ACTION_NEXT = "com.example.musicsimplewidget.ACTION_NEXT"
        const val ACTION_PREVIOUS = "com.example.musicsimplewidget.ACTION_PREVIOUS"
        const val ACTION_MEDIA_UPDATE = "com.example.musicsimplewidget.ACTION_MEDIA_UPDATE"
        const val PREFS_NAME = "music_widget_prefs"
        const val PREF_PREFIX_KEY = "music_app_"


        private var lastAppLaunchTime: Long = 0
        private const val APP_LAUNCH_COOLDOWN_MS: Long = 3000

        private val visualizerDrawers = ConcurrentHashMap<Int, MusicVisualizerDrawer>()
        private val updateHandlers = ConcurrentHashMap<Int, Handler>()
        private val updateRunnables = ConcurrentHashMap<Int, Runnable>()
        private const val VISUALIZER_UPDATE_INTERVAL_MS = 60L

        private fun getWidgetIds(context: Context, appWidgetManager: AppWidgetManager): IntArray {
            return appWidgetManager.getAppWidgetIds(ComponentName(context, MusicSimpleWidgetProvider::class.java))
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle?
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)

        val drawer = visualizerDrawers[appWidgetId]
        if (drawer != null && newOptions != null) {
            val minWidthDp = newOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
            val visualizerWidthPx = minWidthDp.dpToPx(context)
            val visualizerHeightPx = 30.dpToPx(context)

            drawer.updateDimensions(visualizerWidthPx, visualizerHeightPx)
        }
        val drawerInstance = visualizerDrawers.getOrPut(appWidgetId) { MusicVisualizerDrawer(context.applicationContext) }
        updateAppWidgetInternal(context, appWidgetManager, appWidgetId, drawerInstance)
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {

        appWidgetIds.forEach { appWidgetId ->
            val drawer = visualizerDrawers.getOrPut(appWidgetId) {

                MusicVisualizerDrawer(context.applicationContext)
            }
            drawer.updateColor()

            val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
            val minWidthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 150)
            val visualizerWidthPx = minWidthDp.dpToPx(context)
            val visualizerHeightPx = 30.dpToPx(context)
            drawer.updateDimensions(visualizerWidthPx, visualizerHeightPx)

            updateAppWidgetInternal(context, appWidgetManager, appWidgetId, drawer)
        }
        if (appWidgetIds.isNotEmpty()) {
            startMediaMonitorService(context)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return


        val appWidgetManager = AppWidgetManager.getInstance(context)
        val appWidgetIds = getWidgetIds(context, appWidgetManager)

        if (appWidgetIds.isEmpty()) {

            return
        }

        val targetAppWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)

        if (action == ACTION_MEDIA_UPDATE) {

            appWidgetIds.forEach { id ->
                val drawer = visualizerDrawers.getOrPut(id) { MusicVisualizerDrawer(context.applicationContext) }
                updateAppWidgetInternal(context, appWidgetManager, id, drawer)
            }
        } else if (targetAppWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID && targetAppWidgetId in appWidgetIds) {

            val mediaController = getMediaController(context)

            when (action) {
                ACTION_PLAY_PAUSE -> handlePlayPause(context, mediaController, targetAppWidgetId)
                ACTION_NEXT -> mediaController?.transportControls?.skipToNext()
                ACTION_PREVIOUS -> mediaController?.transportControls?.skipToPrevious()
                else -> {

                    super.onReceive(context, intent)
                    return
                }
            }
            val drawer = visualizerDrawers.getOrPut(targetAppWidgetId) { MusicVisualizerDrawer(context.applicationContext) }
            updateAppWidgetInternal(context, appWidgetManager, targetAppWidgetId, drawer)

        } else {

            super.onReceive(context, intent)
        }
    }

    private fun updateAppWidgetInternal(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        visualizerDrawer: MusicVisualizerDrawer
    ) {

        val views = RemoteViews(context.packageName, R.layout.music_simple_widget)
        val mediaController = getMediaController(context)
        var isPlaying = false

        if (mediaController != null) {
            val metadata = mediaController.metadata
            val playbackState = mediaController.playbackState

            if (metadata != null && playbackState != null) {
                views.setTextViewText(R.id.text_title, metadata.getString(android.media.MediaMetadata.METADATA_KEY_TITLE) ?: "Unknown Title")
                views.setTextViewText(R.id.text_artist, metadata.getString(android.media.MediaMetadata.METADATA_KEY_ARTIST) ?: "Unknown Artist")
                val albumArt = metadata.getBitmap(android.media.MediaMetadata.METADATA_KEY_ALBUM_ART)
                    ?: metadata.getBitmap(android.media.MediaMetadata.METADATA_KEY_DISPLAY_ICON)
                if (albumArt != null) {
                    views.setImageViewBitmap(R.id.image_album_art, albumArt)
                } else {
                    views.setImageViewResource(R.id.image_album_art, R.drawable.ic_default_album_art)
                }

                isPlaying = playbackState.state == PlaybackState.STATE_PLAYING ||
                        playbackState.state == PlaybackState.STATE_BUFFERING

                views.setImageViewResource(R.id.button_play_pause, if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow)

            } else {

                setNoMusicPlaying(views)
                isPlaying = false
            }
        } else {

            setNoMusicPlaying(views)
            isPlaying = false
        }

        if (hasRequiredPermissions(context)) {
            visualizerDrawer.linkToGlobalOutput()

            if (isPlaying) {
                visualizerDrawer.resume()
                startVisualizerUpdates(context, appWidgetManager, appWidgetId, visualizerDrawer)
            } else {
                visualizerDrawer.pause()
                stopVisualizerUpdates(appWidgetId)
                views.setImageViewBitmap(R.id.visualizer_image_view, visualizerDrawer.getVisualizerBitmap())
            }
        } else {

            views.setImageViewBitmap(R.id.visualizer_image_view, null)
            visualizerDrawer.release()
            stopVisualizerUpdates(appWidgetId)
        }

        setupPendingIntents(context, views, appWidgetId)

        try {
            appWidgetManager.updateAppWidget(appWidgetId, views)
        } catch (e: Exception) {

        }
    }

    private fun hasRequiredPermissions(context: Context): Boolean {
        val recordAudioGranted = ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val modifyAudioGranted = ContextCompat.checkSelfPermission(context, android.Manifest.permission.MODIFY_AUDIO_SETTINGS) == PackageManager.PERMISSION_GRANTED
        return recordAudioGranted && modifyAudioGranted
    }


    private fun startVisualizerUpdates(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        visualizerDrawer: MusicVisualizerDrawer
    ) {
        if (updateHandlers.containsKey(appWidgetId)) {
            return
        }


        val handler = Handler(Looper.getMainLooper())
        val runnable = object : Runnable {
            override fun run() {
                if (!updateHandlers.containsKey(appWidgetId)) {

                    return
                }

                val bitmap = visualizerDrawer.getVisualizerBitmap()
                if (bitmap != null && !bitmap.isRecycled) {
                    val partialViews = RemoteViews(context.packageName, R.layout.music_simple_widget)
                    partialViews.setImageViewBitmap(R.id.visualizer_image_view, bitmap)
                    try {
                        appWidgetManager.partiallyUpdateAppWidget(appWidgetId, partialViews)
                    } catch (e: Exception) {

                        stopVisualizerUpdates(appWidgetId)
                        return
                    }
                } else {
                }

                handler.postDelayed(this, VISUALIZER_UPDATE_INTERVAL_MS)
            }
        }
        updateHandlers[appWidgetId] = handler
        updateRunnables[appWidgetId] = runnable
        handler.post(runnable)
    }

    private fun stopVisualizerUpdates(appWidgetId: Int) {
        if (updateHandlers.containsKey(appWidgetId)) {

            updateHandlers.remove(appWidgetId)?.removeCallbacks(updateRunnables.remove(appWidgetId) ?: Runnable {})
        }
    }


    private fun setNoMusicPlaying(views: RemoteViews) {
        views.setTextViewText(R.id.text_title, "No music playing")
        views.setTextViewText(R.id.text_artist, "")
        views.setImageViewResource(R.id.image_album_art, R.drawable.ic_default_album_art)
        views.setImageViewResource(R.id.button_play_pause, R.drawable.ic_play_arrow)
        views.setImageViewBitmap(R.id.visualizer_image_view, null)
    }

    private fun setupPendingIntents(context: Context, views: RemoteViews, appWidgetId: Int) {
        val playPauseReqCode = appWidgetId * 10 + 0
        val nextReqCode = appWidgetId * 10 + 1
        val prevReqCode = appWidgetId * 10 + 2
        val launchReqCode = appWidgetId * 10 + 3

        val playPauseIntent = Intent(context, MusicSimpleWidgetProvider::class.java).apply {
            action = ACTION_PLAY_PAUSE
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            data = Uri.parse("widget://playpause/$appWidgetId")
        }
        val playPausePendingIntent = PendingIntent.getBroadcast(
            context, playPauseReqCode, playPauseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val nextIntent = Intent(context, MusicSimpleWidgetProvider::class.java).apply {
            action = ACTION_NEXT
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            data = Uri.parse("widget://next/$appWidgetId")
        }
        val nextPendingIntent = PendingIntent.getBroadcast(
            context, nextReqCode, nextIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val prevIntent = Intent(context, MusicSimpleWidgetProvider::class.java).apply {
            action = ACTION_PREVIOUS
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            data = Uri.parse("widget://previous/$appWidgetId")
        }
        val prevPendingIntent = PendingIntent.getBroadcast(
            context, prevReqCode, prevIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val launchIntent = getLaunchMusicAppIntent(context, appWidgetId)
        val launchPendingIntent = PendingIntent.getActivity(
            context, launchReqCode, launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        views.setOnClickPendingIntent(R.id.button_play_pause, playPausePendingIntent)
        views.setOnClickPendingIntent(R.id.button_next, nextPendingIntent)
        views.setOnClickPendingIntent(R.id.button_previous, prevPendingIntent)
        views.setOnClickPendingIntent(R.id.widget_container, launchPendingIntent)
    }


    private fun getMediaController(context: Context): MediaController? {
        val sessionManager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager?
        val componentName = ComponentName(context, MediaMonitorService::class.java)
        return try {
            sessionManager?.getActiveSessions(componentName)?.firstOrNull()
        } catch (e: SecurityException) {

            null
        } catch (e: Exception) {

            null
        }
    }

    private fun getLaunchMusicAppIntent(context: Context, appWidgetId: Int): Intent {
        val currentMediaAppPackage = getMediaController(context)?.packageName


        return if (currentMediaAppPackage != null) {
            context.packageManager.getLaunchIntentForPackage(currentMediaAppPackage)
                ?.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                ?: getDefaultMusicIntent().also { }
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
        if (mediaController?.playbackState != null) {
            val state = mediaController.playbackState!!.state

            if (state == PlaybackState.STATE_PLAYING || state == PlaybackState.STATE_BUFFERING) {

                mediaController.transportControls?.pause()
            } else {

                mediaController.transportControls?.play()
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
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(context, serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        } catch (e: Exception) {

        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {

        val prefsEdit = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
        appWidgetIds.forEach { appWidgetId ->
            prefsEdit.remove(PREF_PREFIX_KEY + appWidgetId)
            stopVisualizerUpdates(appWidgetId)
            visualizerDrawers.remove(appWidgetId)?.release()
            updateHandlers.remove(appWidgetId)
            updateRunnables.remove(appWidgetId)

        }
        prefsEdit.apply()
        super.onDeleted(context, appWidgetIds)

        val appWidgetManager = AppWidgetManager.getInstance(context)
        if (getWidgetIds(context, appWidgetManager).isEmpty()) {

            try { context.stopService(Intent(context, MediaMonitorService::class.java)) }
            catch (e: Exception) { }
        }
    }

    override fun onDisabled(context: Context) {

        try { context.stopService(Intent(context, MediaMonitorService::class.java)) }
        catch (e: Exception) { }

        visualizerDrawers.keys.forEach { appWidgetId ->
            stopVisualizerUpdates(appWidgetId)
            visualizerDrawers.remove(appWidgetId)?.release()
        }
        updateHandlers.clear()
        updateRunnables.clear()
        super.onDisabled(context)
    }

    fun Int.dpToPx(context: Context): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            this.toFloat(),
            context.resources.displayMetrics
        ).roundToInt()
}