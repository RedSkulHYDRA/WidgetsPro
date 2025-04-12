package com.tpk.widgetspro.services

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.widget.RemoteViews
import com.tpk.widgetspro.R
import com.tpk.widgetspro.widgets.photo.GifWidgetProvider
import pl.droidsonroids.gif.GifDrawable
import java.io.BufferedInputStream

class AnimationService : BaseMonitorService() {
    private val handler = Handler(Looper.getMainLooper())
    private val widgetData = mutableMapOf<Int, WidgetAnimationData>()

    data class Frame(val bitmap: Bitmap, val duration: Int)
    data class WidgetAnimationData(
        var frames: List<Frame>? = null,
        var currentFrame: Int = 0,
        var uriString: String? = null,
        var runnable: Runnable? = null
    )

    override fun onCreate() {
        super.onCreate()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            val appWidgetManager = AppWidgetManager.getInstance(this)
            val widgetIds = appWidgetManager.getAppWidgetIds(
                ComponentName(this, GifWidgetProvider::class.java)
            )
            widgetIds.forEach { appWidgetId ->
                val prefs = getSharedPreferences("gif_widget_prefs", MODE_PRIVATE)
                val uriString = prefs.getString("file_uri_$appWidgetId", null)
                if (uriString != null) {
                    handleAddWidget(appWidgetId, uriString)
                }
            }
        } else {
            val action = intent.getStringExtra("action")
            val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
            when (action) {
                "ADD_WIDGET" -> {
                    if (appWidgetId != -1) {
                        val uriString = intent.getStringExtra("file_uri")
                        if (uriString != null) {
                            handleAddWidget(appWidgetId, uriString)
                        }
                    }
                }
                "REMOVE_WIDGET" -> {
                    if (appWidgetId != -1) {
                        handleRemoveWidget(appWidgetId)
                    }
                }
                "UPDATE_FILE" -> {
                    if (appWidgetId != -1) {
                        val uriString = intent.getStringExtra("file_uri")
                        if (uriString != null) {
                            handleUpdateFile(appWidgetId, uriString)
                        }
                    }
                }
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun handleAddWidget(appWidgetId: Int, uriString: String) {
        if (widgetData.containsKey(appWidgetId)) {
            if (widgetData[appWidgetId]?.uriString != uriString) {
                handleUpdateFile(appWidgetId, uriString)
            }
            return
        }

        val frames = getFrames(Uri.parse(uriString))
        if (frames.isNotEmpty()) {
            widgetData[appWidgetId] = WidgetAnimationData(
                frames = frames,
                uriString = uriString
            )
            startAnimation(appWidgetId)
        }
    }

    private fun handleRemoveWidget(appWidgetId: Int) {
        widgetData[appWidgetId]?.let { data ->
            stopAnimation(appWidgetId)
            data.frames?.forEach { it.bitmap.recycle() }
            widgetData.remove(appWidgetId)
        }
        if (widgetData.isEmpty()) {
            stopForeground(true)
            stopSelf()
        }
    }

    private fun handleUpdateFile(appWidgetId: Int, uriString: String) {
        widgetData[appWidgetId]?.let { data ->
            stopAnimation(appWidgetId)
            data.frames?.forEach { it.bitmap.recycle() }
            val newFrames = getFrames(Uri.parse(uriString))
            if (newFrames.isNotEmpty()) {
                data.frames = newFrames
                data.currentFrame = 0
                data.uriString = uriString
                startAnimation(appWidgetId)
            } else {
                widgetData.remove(appWidgetId)
            }
        } ?: run {
            handleAddWidget(appWidgetId, uriString)
        }
    }

    private fun startAnimation(appWidgetId: Int) {
        widgetData[appWidgetId]?.let { data ->
            if (data.frames?.isNotEmpty() == true) {
                val runnable = object : Runnable {
                    override fun run() {
                        updateWidget(appWidgetId)
                        data.runnable = this
                        handler.postDelayed(this, data.frames!![data.currentFrame].duration.toLong())
                    }
                }
                data.runnable = runnable
                handler.post(runnable)
            }
        }
    }

    private fun updateWidget(appWidgetId: Int) {
        widgetData[appWidgetId]?.let { data ->
            val frames = data.frames ?: return
            val frame = frames[data.currentFrame]
            val appWidgetManager = AppWidgetManager.getInstance(this)
            val remoteViews = RemoteViews(packageName, R.layout.gif_widget_layout)
            remoteViews.setImageViewBitmap(R.id.imageView, frame.bitmap)
            appWidgetManager.updateAppWidget(appWidgetId, remoteViews)
            data.currentFrame = (data.currentFrame + 1) % frames.size
        }
    }

    private fun stopAnimation(appWidgetId: Int) {
        widgetData[appWidgetId]?.let { data ->
            data.runnable?.let { handler.removeCallbacks(it) }
            data.runnable = null
        }
    }

    private fun getFrames(uri: Uri): List<Frame> {
        val mimeType = contentResolver.getType(uri) ?: return emptyList()
        return when {
            mimeType == "image/gif" -> decodeGif(uri)
            mimeType.startsWith("video/") -> decodeVideo(uri)
            else -> emptyList()
        }
    }

    private fun decodeGif(uri: Uri): List<Frame> {
        return contentResolver.openInputStream(uri)?.use { inputStream ->
            val bufferedInputStream = BufferedInputStream(inputStream)
            val gifDrawable = GifDrawable(bufferedInputStream)
            val frames = mutableListOf<Frame>()
            for (i in 0 until gifDrawable.numberOfFrames) {
                val bitmap = gifDrawable.seekToFrameAndGet(i)
                val duration = gifDrawable.getFrameDuration(i)
                frames.add(Frame(bitmap, duration))
            }
            frames
        } ?: emptyList()
    }

    private fun decodeVideo(uri: Uri): List<Frame> {
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(contentResolver.openFileDescriptor(uri, "r")?.fileDescriptor)
        val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0L
        val frameInterval = 100L
        val frames = mutableListOf<Frame>()
        for (time in 0 until durationMs step frameInterval) {
            val bitmap = retriever.getFrameAtTime(time * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            bitmap?.let { frames.add(Frame(it, frameInterval.toInt())) }
        }
        retriever.release()
        return frames
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        widgetData.forEach { (appWidgetId, data) ->
            stopAnimation(appWidgetId)
            data.frames?.forEach { it.bitmap.recycle() }
        }
        widgetData.clear()
        super.onDestroy()
    }
}