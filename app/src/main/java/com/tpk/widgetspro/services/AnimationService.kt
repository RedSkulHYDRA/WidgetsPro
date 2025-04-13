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
    private val syncGroups = mutableMapOf<String, SyncGroupData>()

    data class Frame(val bitmap: Bitmap, val duration: Int)
    data class WidgetAnimationData(
        var frames: List<Frame>? = null,
        var currentFrame: Int = 0,
        var uriString: String? = null,
        var syncGroupId: String? = null
    )
    data class SyncGroupData(
        val widgetIds: MutableSet<Int> = mutableSetOf(),
        var currentFrame: Int = 0,
        var totalDuration: Long = 0,
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
                "SYNC_WIDGETS" -> {
                    val syncGroupId = intent.getStringExtra("sync_group_id")
                    val widgetIds = intent.getIntArrayExtra("sync_widget_ids")?.toSet() ?: emptySet()
                    if (syncGroupId != null && widgetIds.isNotEmpty()) {
                        handleSyncWidgets(syncGroupId, widgetIds)
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
            val prefs = getSharedPreferences("gif_widget_prefs", MODE_PRIVATE)
            val syncGroupId = prefs.getString("sync_group_$appWidgetId", null)
            widgetData[appWidgetId] = WidgetAnimationData(
                frames = frames,
                uriString = uriString,
                syncGroupId = syncGroupId
            )
            if (syncGroupId != null && syncGroups.containsKey(syncGroupId)) {
                syncGroups[syncGroupId]?.widgetIds?.add(appWidgetId)
            } else {
                startAnimation(appWidgetId)
            }
        }
    }

    private fun handleRemoveWidget(appWidgetId: Int) {
        widgetData[appWidgetId]?.let { data ->
            if (data.syncGroupId != null) {
                syncGroups[data.syncGroupId]?.let { group ->
                    group.widgetIds.remove(appWidgetId)
                    if (group.widgetIds.isEmpty()) {
                        group.runnable?.let { handler.removeCallbacks(it) }
                        syncGroups.remove(data.syncGroupId)
                    }
                }
            } else {
                stopAnimation(appWidgetId)
            }
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
            if (data.syncGroupId != null) {
                syncGroups[data.syncGroupId]?.let { group ->
                    group.widgetIds.remove(appWidgetId)
                    if (group.widgetIds.isEmpty()) {
                        group.runnable?.let { handler.removeCallbacks(it) }
                        syncGroups.remove(data.syncGroupId)
                    }
                }
                data.syncGroupId = null
            } else {
                stopAnimation(appWidgetId)
            }
            data.frames?.forEach { it.bitmap.recycle() }
            val newFrames = getFrames(Uri.parse(uriString))
            if (newFrames.isNotEmpty()) {
                data.frames = newFrames
                data.currentFrame = 0
                data.uriString = uriString
                val prefs = getSharedPreferences("gif_widget_prefs", MODE_PRIVATE)
                data.syncGroupId = prefs.getString("sync_group_$appWidgetId", null)
                if (data.syncGroupId != null && syncGroups.containsKey(data.syncGroupId)) {
                    syncGroups[data.syncGroupId]?.widgetIds?.add(appWidgetId)
                } else {
                    startAnimation(appWidgetId)
                }
            } else {
                widgetData.remove(appWidgetId)
            }
        } ?: run {
            handleAddWidget(appWidgetId, uriString)
        }
    }

    private fun handleSyncWidgets(syncGroupId: String, widgetIds: Set<Int>) {
        widgetIds.forEach { appWidgetId ->
            widgetData[appWidgetId]?.let { data ->
                if (data.syncGroupId != null && data.syncGroupId != syncGroupId) {
                    syncGroups[data.syncGroupId]?.widgetIds?.remove(appWidgetId)
                    if (syncGroups[data.syncGroupId]?.widgetIds?.isEmpty() == true) {
                        syncGroups[data.syncGroupId]?.runnable?.let { handler.removeCallbacks(it) }
                        syncGroups.remove(data.syncGroupId)
                    }
                }
                data.syncGroupId = syncGroupId
            }
        }
        val prefs = getSharedPreferences("gif_widget_prefs", MODE_PRIVATE)
        val editor = prefs.edit()
        widgetIds.forEach { appWidgetId ->
            editor.putString("sync_group_$appWidgetId", syncGroupId)
        }
        editor.apply()

        val syncGroup = syncGroups.getOrPut(syncGroupId) { SyncGroupData() }
        syncGroup.widgetIds.addAll(widgetIds)

        val totalDuration = widgetIds
            .mapNotNull { widgetData[it]?.frames }
            .maxOfOrNull { frames -> frames.sumOf { it.duration.toLong() } } ?: 0L
        syncGroup.totalDuration = totalDuration

        syncGroup.runnable?.let { handler.removeCallbacks(it) }
        startSyncAnimation(syncGroupId)
    }

    private fun startAnimation(appWidgetId: Int) {
        widgetData[appWidgetId]?.let { data ->
            if (data.frames?.isNotEmpty() == true) {
                val runnable = object : Runnable {
                    override fun run() {
                        updateWidget(appWidgetId)
                        val frameDuration = data.frames!![data.currentFrame].duration.toLong()
                        handler.postDelayed(this, frameDuration)
                    }
                }
                handler.post(runnable)
            }
        }
    }

    private fun startSyncAnimation(syncGroupId: String) {
        syncGroups[syncGroupId]?.let { group ->
            if (group.widgetIds.isNotEmpty()) {
                val runnable = object : Runnable {
                    override fun run() {
                        updateSyncGroup(syncGroupId)
                        val currentFrameTimes = group.widgetIds
                            .mapNotNull { widgetData[it]?.frames?.getOrNull(widgetData[it]?.currentFrame ?: 0)?.duration }
                        val minFrameDuration = currentFrameTimes.minOrNull()?.toLong() ?: 100L
                        group.runnable = this
                        handler.postDelayed(this, minFrameDuration)
                    }
                }
                group.runnable = runnable
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

    private fun updateSyncGroup(syncGroupId: String) {
        syncGroups[syncGroupId]?.let { group ->
            group.widgetIds.forEach { appWidgetId ->
                widgetData[appWidgetId]?.let { data ->
                    val frames = data.frames ?: return@forEach
                    val appWidgetManager = AppWidgetManager.getInstance(this)
                    val remoteViews = RemoteViews(packageName, R.layout.gif_widget_layout)
                    val frame = frames[group.currentFrame % frames.size]
                    remoteViews.setImageViewBitmap(R.id.imageView, frame.bitmap)
                    appWidgetManager.updateAppWidget(appWidgetId, remoteViews)
                    data.currentFrame = group.currentFrame % frames.size
                }
            }
            group.currentFrame = (group.currentFrame + 1)
            val maxFrameCount = group.widgetIds
                .mapNotNull { widgetData[it]?.frames?.size }
                .maxOrNull() ?: 1
            if (group.currentFrame >= maxFrameCount) {
                group.currentFrame = 0
            }
        }
    }

    private fun stopAnimation(appWidgetId: Int) {
        widgetData[appWidgetId]?.let { data ->
            if (data.syncGroupId == null) {
            }
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
            if (data.syncGroupId != null) {
                syncGroups[data.syncGroupId]?.widgetIds?.remove(appWidgetId)
            }
            data.frames?.forEach { it.bitmap.recycle() }
        }
        syncGroups.forEach { (_, group) ->
            group.runnable?.let { handler.removeCallbacks(it) }
        }
        widgetData.clear()
        syncGroups.clear()
        super.onDestroy()
    }
}