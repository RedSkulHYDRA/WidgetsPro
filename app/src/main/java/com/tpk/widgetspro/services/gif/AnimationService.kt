package com.tpk.widgetspro.services.gif

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.widget.RemoteViews
import com.tpk.widgetspro.R
import com.tpk.widgetspro.services.BaseMonitorService
import com.tpk.widgetspro.widgets.gif.GifWidgetProvider
import pl.droidsonroids.gif.GifDrawable
import java.io.BufferedInputStream
import java.util.UUID

class AnimationService : BaseMonitorService() {
    private val handler = Handler(Looper.getMainLooper())
    private val widgetData = mutableMapOf<Int, WidgetAnimationData>()
    private val syncGroups = mutableMapOf<String, SyncGroupData>()
    private val widgetRunnables = mutableMapOf<Int, Runnable>()

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
                val syncGroupId = prefs.getString("sync_group_$appWidgetId", null)
                if (uriString != null) {
                    handleAddWidget(appWidgetId, uriString, syncGroupId)
                }
            }
        } else {
            val action = intent.getStringExtra("action")
            val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            when (action) {
                "ADD_WIDGET" -> {
                    if (appWidgetId != -1) {
                        val uriString = intent.getStringExtra("file_uri")
                        val syncGroupId = getSharedPreferences("gif_widget_prefs", MODE_PRIVATE)
                            .getString("sync_group_$appWidgetId", null)
                        if (uriString != null) {
                            handleAddWidget(appWidgetId, uriString, syncGroupId)
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
                        val syncGroupId = getSharedPreferences("gif_widget_prefs", MODE_PRIVATE)
                            .getString("sync_group_$appWidgetId", null)
                        if (uriString != null) {
                            handleUpdateFile(appWidgetId, uriString, syncGroupId)
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
                "UNSYNC_WIDGET" -> {
                    if (appWidgetId != -1) {
                        handleUnsyncWidget(appWidgetId)
                    }
                }
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun handleAddWidget(appWidgetId: Int, uriString: String, syncGroupId: String?) {
        if (widgetData.containsKey(appWidgetId)) {
            if (widgetData[appWidgetId]?.uriString != uriString || widgetData[appWidgetId]?.syncGroupId != syncGroupId) {
                handleUpdateFile(appWidgetId, uriString, syncGroupId)
            }
            return
        }

        val frames = getFrames(Uri.parse(uriString))
        if (frames.isNotEmpty()) {
            widgetData[appWidgetId] = WidgetAnimationData(
                frames = frames,
                uriString = uriString,
                syncGroupId = syncGroupId
            )
            if (syncGroupId != null) {
                val group = syncGroups.getOrPut(syncGroupId) { SyncGroupData() }
                group.widgetIds.add(appWidgetId)
                startSyncAnimation(syncGroupId)
            } else {
                startAnimation(appWidgetId)
            }
        }
        updateWidgetCount()
    }


    private fun handleRemoveWidget(appWidgetId: Int) {
        widgetData[appWidgetId]?.let { data ->
            widgetRunnables.remove(appWidgetId)?.let { handler.removeCallbacks(it) }

            if (data.syncGroupId != null) {
                syncGroups[data.syncGroupId]?.let { group ->
                    group.widgetIds.remove(appWidgetId)
                    if (group.widgetIds.isEmpty()) {
                        group.runnable?.let { handler.removeCallbacks(it) }
                        syncGroups.remove(data.syncGroupId)
                    } else {
                        startSyncAnimation(data.syncGroupId!!)
                    }
                }
            }
            data.frames?.forEach { it.bitmap.recycle() }
            widgetData.remove(appWidgetId)
        }
        val prefs = getSharedPreferences("gif_widget_prefs", MODE_PRIVATE)
        prefs.edit()
            .remove("file_uri_$appWidgetId")
            .remove("widget_index_$appWidgetId")
            .remove("sync_group_$appWidgetId")
            .apply()

        updateWidgetCount()
    }

    private fun handleUpdateFile(appWidgetId: Int, uriString: String, syncGroupId: String?) {
        widgetData[appWidgetId]?.let { data ->
            widgetRunnables.remove(appWidgetId)?.let { handler.removeCallbacks(it) }
            if (data.syncGroupId != null) {
                syncGroups[data.syncGroupId]?.let { group ->
                    group.widgetIds.remove(appWidgetId)
                    if (group.widgetIds.isEmpty()) {
                        group.runnable?.let { handler.removeCallbacks(it) }
                        syncGroups.remove(data.syncGroupId)
                    } else {
                        startSyncAnimation(data.syncGroupId!!)
                    }
                }
            }

            data.frames?.forEach { it.bitmap.recycle() }

            val newFrames = getFrames(Uri.parse(uriString))
            if (newFrames.isNotEmpty()) {
                data.frames = newFrames
                data.currentFrame = 0
                data.uriString = uriString
                data.syncGroupId = syncGroupId

                if (syncGroupId != null) {
                    val group = syncGroups.getOrPut(syncGroupId) { SyncGroupData() }
                    group.widgetIds.add(appWidgetId)
                    startSyncAnimation(syncGroupId)
                } else {
                    startAnimation(appWidgetId)
                }
            } else {
                widgetData.remove(appWidgetId)
                updateWidgetCount()
            }
        } ?: run {
            handleAddWidget(appWidgetId, uriString, syncGroupId)
        }
    }

    private fun handleSyncWidgets(syncGroupId: String, widgetIds: Set<Int>) {
        widgetIds.forEach { appWidgetId ->
            widgetData[appWidgetId]?.let { data ->
                widgetRunnables.remove(appWidgetId)?.let { handler.removeCallbacks(it) }

                if (data.syncGroupId != null && data.syncGroupId != syncGroupId) {
                    syncGroups[data.syncGroupId]?.let { oldGroup ->
                        oldGroup.widgetIds.remove(appWidgetId)
                        if (oldGroup.widgetIds.isEmpty()) {
                            oldGroup.runnable?.let { handler.removeCallbacks(it) }
                            syncGroups.remove(data.syncGroupId)
                        } else {
                            startSyncAnimation(data.syncGroupId!!)
                        }
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

        syncGroups.filterKeys { it != syncGroupId }.forEach { (key, group) ->
            group.widgetIds.removeAll(widgetIds)
            if (group.widgetIds.isEmpty()) {
                group.runnable?.let { handler.removeCallbacks(it) }
                syncGroups.remove(key)
            }
        }

        syncGroup.runnable?.let { handler.removeCallbacks(it) }
        syncGroup.currentFrame = 0
        startSyncAnimation(syncGroupId)
    }

    private fun handleUnsyncWidget(appWidgetId: Int) {
        widgetData[appWidgetId]?.let { data ->
            if (data.syncGroupId != null) {
                val oldSyncGroupId = data.syncGroupId!!
                syncGroups[oldSyncGroupId]?.let { group ->
                    group.widgetIds.remove(appWidgetId)
                    if (group.widgetIds.isEmpty()) {
                        group.runnable?.let { handler.removeCallbacks(it) }
                        syncGroups.remove(oldSyncGroupId)
                    } else {
                        startSyncAnimation(oldSyncGroupId)
                    }
                }
                data.syncGroupId = null

                val prefs = getSharedPreferences("gif_widget_prefs", MODE_PRIVATE)
                prefs.edit().remove("sync_group_$appWidgetId").apply()

                startAnimation(appWidgetId)
            }
        }
    }


    private fun startAnimation(appWidgetId: Int) {
        widgetRunnables.remove(appWidgetId)?.let { handler.removeCallbacks(it) }

        widgetData[appWidgetId]?.let { data ->
            if (data.frames?.isNotEmpty() == true && data.syncGroupId == null) {
                val runnable = object : Runnable {
                    override fun run() {
                        widgetData[appWidgetId]?.takeIf { it.syncGroupId == null }?.let { currentData ->
                            val shouldUpdateNow = shouldUpdate()
                            if (shouldUpdateNow) {
                                updateWidget(appWidgetId)
                            }
                            val currentFrames = currentData.frames
                            if (currentFrames != null && currentFrames.isNotEmpty()) {
                                if (currentData.currentFrame >= currentFrames.size) currentData.currentFrame = 0 // Bounds check
                                val frameIndex = currentData.currentFrame
                                val frameDuration = currentFrames[frameIndex].duration.toLong()
                                val nextDelay = if (shouldUpdateNow) frameDuration.coerceAtLeast(16) else CHECK_INTERVAL_INACTIVE_MS
                                handler.postDelayed(this, nextDelay)
                            }
                        } ?: run {
                            widgetRunnables.remove(appWidgetId)
                        }
                    }
                }
                widgetRunnables[appWidgetId] = runnable
                handler.post(runnable)
            }
        }
    }


    private fun startSyncAnimation(syncGroupId: String) {
        syncGroups[syncGroupId]?.let { group ->
            group.runnable?.let { handler.removeCallbacks(it) }

            if (group.widgetIds.isNotEmpty()) {
                val activeWidgetIds = group.widgetIds.filter { widgetData.containsKey(it) }
                if (activeWidgetIds.isEmpty()) {
                    syncGroups.remove(syncGroupId)
                    updateWidgetCount()
                    return
                }
                group.widgetIds.retainAll(activeWidgetIds.toSet())


                val runnable = object : Runnable {
                    override fun run() {
                        syncGroups[syncGroupId]?.takeIf { it.widgetIds.isNotEmpty() }?.let { currentGroup ->
                            val shouldUpdateNow = shouldUpdate()
                            if (shouldUpdateNow) {
                                updateSyncGroup(syncGroupId)
                            }

                            val maxFrameCountInGroup = currentGroup.widgetIds
                                .mapNotNull { widgetData[it]?.frames?.size }
                                .maxOrNull() ?: 0

                            if (maxFrameCountInGroup == 0) {
                                // No frames in any widget, stop runnable for this group
                                syncGroups.remove(syncGroupId)
                                updateWidgetCount()
                                return@let
                            }

                            // Calculate the frame index for the *next* cycle, wrapping around max frame count
                            val nextFrameIndex = (currentGroup.currentFrame + 1) % maxFrameCountInGroup

                            val currentFrameTimes = currentGroup.widgetIds
                                .mapNotNull { widgetId ->
                                    widgetData[widgetId]?.let { wd ->
                                        wd.frames?.let { frames ->
                                            if (frames.isNotEmpty()) {
                                                // Get duration of the frame that *will be shown* next
                                                frames[nextFrameIndex % frames.size].duration
                                            } else null
                                        }
                                    }
                                }

                            val minFrameDuration = currentFrameTimes.minOrNull()?.toLong() ?: 100L
                            val nextDelay = if (shouldUpdateNow) minFrameDuration.coerceAtLeast(16) else CHECK_INTERVAL_INACTIVE_MS
                            currentGroup.runnable = this
                            handler.postDelayed(this, nextDelay)

                        }
                    }
                }
                group.runnable = runnable
                handler.post(runnable)
            } else {
                syncGroups.remove(syncGroupId)
                updateWidgetCount()
            }
        }
    }

    private fun updateWidget(appWidgetId: Int) {
        widgetData[appWidgetId]?.let { data ->
            val frames = data.frames ?: return
            if (frames.isEmpty()) return
            if (data.currentFrame >= frames.size) {
                data.currentFrame = 0
            }
            val frame = frames[data.currentFrame]
            val appWidgetManager = AppWidgetManager.getInstance(this)
            val remoteViews = RemoteViews(packageName, R.layout.gif_widget_layout)
            if (!frame.bitmap.isRecycled) {
                remoteViews.setImageViewBitmap(R.id.imageView, frame.bitmap)
                appWidgetManager.partiallyUpdateAppWidget(appWidgetId, remoteViews) // Use partial update
            }
            data.currentFrame = (data.currentFrame + 1) % frames.size
        }
    }

    private fun updateSyncGroup(syncGroupId: String) {
        syncGroups[syncGroupId]?.let { group ->
            val activeWidgetIds = group.widgetIds.filter { widgetData.containsKey(it) }
            if (activeWidgetIds.isEmpty()) {
                group.runnable?.let { handler.removeCallbacks(it) }
                syncGroups.remove(syncGroupId)
                updateWidgetCount()
                return
            }

            val maxFrameCount = activeWidgetIds
                .mapNotNull { widgetData[it]?.frames?.size }
                .maxOrNull() ?: 0

            if (maxFrameCount == 0) return

            group.currentFrame %= maxFrameCount

            activeWidgetIds.forEach { appWidgetId ->
                widgetData[appWidgetId]?.let { data ->
                    val frames = data.frames ?: return@forEach
                    if (frames.isEmpty()) return@forEach

                    val frameIndex = group.currentFrame % frames.size
                    val frame = frames[frameIndex]
                    val appWidgetManager = AppWidgetManager.getInstance(this)
                    val remoteViews = RemoteViews(packageName, R.layout.gif_widget_layout)
                    if (!frame.bitmap.isRecycled) {
                        remoteViews.setImageViewBitmap(R.id.imageView, frame.bitmap)
                        appWidgetManager.partiallyUpdateAppWidget(appWidgetId, remoteViews) // Use partial update
                    }
                }
            }
            group.currentFrame = (group.currentFrame + 1) % maxFrameCount
        }
    }

    private fun stopAnimation(appWidgetId: Int) {
        widgetRunnables.remove(appWidgetId)?.let { handler.removeCallbacks(it) }
    }


    private fun getFrames(uri: Uri): List<Frame> {
        val mimeType = contentResolver.getType(uri) ?: return emptyList()
        return when {
            mimeType == "image/gif" -> decodeGif(uri)
            else -> emptyList()
        }
    }

    private fun decodeGif(uri: Uri): List<Frame> {
        return try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                val bufferedInputStream = BufferedInputStream(inputStream)
                var gifDrawable: GifDrawable? = null
                try {
                    gifDrawable = GifDrawable(bufferedInputStream)
                    val frames = mutableListOf<Frame>()
                    val frameCount = gifDrawable.numberOfFrames
                    if (frameCount > 0) {
                        for (i in 0 until frameCount) {
                            val bitmap = gifDrawable.seekToFrameAndGet(i)?.copy(Bitmap.Config.ARGB_8888, false)
                            val duration = gifDrawable.getFrameDuration(i)
                            if (bitmap != null) {
                                frames.add(Frame(bitmap, duration))
                            }
                        }
                    }
                    frames
                } finally {
                    gifDrawable?.recycle()
                }
            } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun updateWidgetCount() {
        val appWidgetManager = AppWidgetManager.getInstance(this)
        val componentName = ComponentName(this, GifWidgetProvider::class.java)
        val currentWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
        val activeManagedWidgets = widgetData.keys.intersect(currentWidgetIds.toSet())
        val activeSyncedWidgets = syncGroups.values.flatMap { it.widgetIds }.intersect(currentWidgetIds.toSet())

        if (activeManagedWidgets.isEmpty() && activeSyncedWidgets.isEmpty() && widgetData.isEmpty() && syncGroups.isEmpty()) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        widgetRunnables.values.forEach { handler.removeCallbacks(it) }
        widgetRunnables.clear()
        syncGroups.values.forEach { it.runnable?.let { r -> handler.removeCallbacks(r) } }
        syncGroups.clear()

        widgetData.values.forEach { data ->
            data.frames?.forEach {
                if (!it.bitmap.isRecycled){
                    it.bitmap.recycle()
                }
            }
        }
        widgetData.clear()

        super.onDestroy()
    }
}