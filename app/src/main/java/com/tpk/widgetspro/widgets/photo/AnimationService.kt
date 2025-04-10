package com.tpk.widgetspro.widgets.photo

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.preference.PreferenceManager
import com.tpk.widgetspro.R
import pl.droidsonroids.gif.GifDrawable
import java.io.BufferedInputStream

class AnimationService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var frames: List<Frame>? = null
    private var currentFrame = 0
    private val activeWidgets = mutableSetOf<Int>()

    data class Frame(val bitmap: Bitmap, val duration: Int)

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "animation_service_channel",
                "Animation Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            val appWidgetManager = AppWidgetManager.getInstance(this)
            val widgetIds = appWidgetManager.getAppWidgetIds(
                ComponentName(this, GifAppWidgetProvider::class.java)
            )
            if (widgetIds.isNotEmpty()) {
                activeWidgets.addAll(widgetIds.toList())
                val prefs = PreferenceManager.getDefaultSharedPreferences(this)
                val uriString = prefs.getString("selected_file_uri", null)
                if (uriString != null) {
                    val uri = Uri.parse(uriString)
                    frames = getFrames(uri)
                    startAnimation()
                    startForegroundService()
                }
            }
        } else {
            val action = intent.getStringExtra("action")
            when (action) {
                "ADD_WIDGET" -> {
                    val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
                    if (appWidgetId != -1) {
                        activeWidgets.add(appWidgetId)
                        val uriString = intent.getStringExtra("file_uri")
                        if (uriString != null) {
                            val uri = Uri.parse(uriString)
                            frames = getFrames(uri)
                            if (activeWidgets.size == 1) {
                                startAnimation()
                                startForegroundService()
                            }
                        }
                    }
                }
                "REMOVE_WIDGET" -> {
                    val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
                    if (appWidgetId != -1) {
                        activeWidgets.remove(appWidgetId)
                        if (activeWidgets.isEmpty()) {
                            stopAnimation()
                            stopForeground(true)
                            stopSelf()
                        }
                    }
                }
                "UPDATE_FILE" -> {
                    val uriString = intent.getStringExtra("file_uri")
                    if (uriString != null) {
                        val uri = Uri.parse(uriString)
                        frames = getFrames(uri)
                        currentFrame = 0
                        if (activeWidgets.isNotEmpty()) {
                            stopAnimation()
                            startAnimation()
                        }
                    }
                }
            }
        }
        return START_STICKY
    }

    private fun startForegroundService() {
        val notification = NotificationCompat.Builder(this, "animation_service_channel")
            .setContentTitle("Animation Service")
            .setContentText("Running animations for widgets")
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        startForeground(7, notification)
    }

    private fun startAnimation() {
        if (frames?.isNotEmpty() == true) {
            updateAllWidgets()
        }
    }

    private fun updateAllWidgets() {
        val frame = frames!![currentFrame]
        val appWidgetManager = AppWidgetManager.getInstance(this)
        activeWidgets.forEach { appWidgetId ->
            val remoteViews = RemoteViews(packageName, R.layout.gif_widget_layout)
            remoteViews.setImageViewBitmap(R.id.imageView, frame.bitmap)
            appWidgetManager.updateAppWidget(appWidgetId, remoteViews)
        }
        currentFrame = (currentFrame + 1) % frames!!.size
        handler.postDelayed({ updateAllWidgets() }, frame.duration.toLong())
    }

    private fun stopAnimation() {
        handler.removeCallbacksAndMessages(null)
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

    private fun recycleFrames(frames: List<Frame>) {
        frames.forEach { it.bitmap.recycle() }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopAnimation()
        frames?.let { recycleFrames(it) }
        super.onDestroy()
    }
}