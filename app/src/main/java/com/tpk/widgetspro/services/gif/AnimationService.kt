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
import java.io.FileNotFoundException
import java.util.*
import kotlin.collections.set

class AnimationService : BaseMonitorService() {
    private val handler = Handler(Looper.getMainLooper())
    private val widgetData = mutableMapOf<Int, WidgetAnimationData>()
    private val syncGroups = mutableMapOf<String, SyncGroupData>()

    data class Frame(val bitmap: Bitmap, val duration: Int)

    data class WidgetAnimationData(
        var frames: List<Frame>? = null,
        var currentFrame: Int = 0,
        var uriString: String? = null,
        var syncGroupId: String? = null,
        var runnable: Runnable? = null
    )

    data class SyncGroupData(
        val widgetIds: MutableSet<Int> = mutableSetOf(),
        var currentFrame: Int = 0,
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
                uriString?.let { handleAddWidget(appWidgetId, it) }
            }
        } else {
            val action = intent.getStringExtra("action")
            val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
            when (action) {
                "ADD_WIDGET" -> {
                    if (appWidgetId != -1) {
                        val uriString = intent.getStringExtra("file_uri")
                        uriString?.let { handleAddWidget(appWidgetId, it) }
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
                        uriString?.let { handleUpdateFile(appWidgetId, it) }
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

        val frames = decodeGifToFrames(Uri.parse(uriString))
        if (frames.isNotEmpty()) {
            val prefs = getSharedPreferences("gif_widget_prefs", MODE_PRIVATE)
            val syncGroupId = prefs.getString("sync_group_$appWidgetId", null)
            val data = WidgetAnimationData(
                frames = frames,
                uriString = uriString,
                syncGroupId = syncGroupId
            )
            widgetData[appWidgetId] = data

            if (syncGroupId != null) {
                val group = syncGroups.getOrPut(syncGroupId) { SyncGroupData() }
                group.widgetIds.add(appWidgetId)
                group.runnable?.let { handler.removeCallbacks(it) }
                startSyncAnimation(syncGroupId)
            } else {
                startAnimation(appWidgetId)
            }
        }
    }

    private fun handleRemoveWidget(appWidgetId: Int) {
        widgetData[appWidgetId]?.let { data ->
            data.runnable?.let { handler.removeCallbacks(it) }
            recycleFrames(data.frames)

            data.syncGroupId?.let { syncGroupId ->
                syncGroups[syncGroupId]?.let { group ->
                    group.widgetIds.remove(appWidgetId)
                    if (group.widgetIds.isEmpty()) {
                        group.runnable?.let { handler.removeCallbacks(it) }
                        syncGroups.remove(syncGroupId)
                    }
                }
            }
            widgetData.remove(appWidgetId)
        }

        if (widgetData.isEmpty() && syncGroups.isEmpty()) {
            stopForeground(true)
            stopSelf()
        }
    }

    private fun handleUpdateFile(appWidgetId: Int, uriString: String) {
        val oldData = widgetData[appWidgetId]

        oldData?.runnable?.let { handler.removeCallbacks(it) }
        recycleFrames(oldData?.frames)

        oldData?.syncGroupId?.let { oldSyncId ->
            syncGroups[oldSyncId]?.let { group ->
                group.widgetIds.remove(appWidgetId)
                if (group.widgetIds.isEmpty()) {
                    group.runnable?.let { handler.removeCallbacks(it) }
                    syncGroups.remove(oldSyncId)
                }
            }
        }

        val newFrames = decodeGifToFrames(Uri.parse(uriString))
        if (newFrames.isNotEmpty()) {
            val prefs = getSharedPreferences("gif_widget_prefs", MODE_PRIVATE)
            val newSyncGroupId = prefs.getString("sync_group_$appWidgetId", null)
            val newData = WidgetAnimationData(
                frames = newFrames,
                uriString = uriString,
                syncGroupId = newSyncGroupId
            )
            widgetData[appWidgetId] = newData

            if (newSyncGroupId != null) {
                val group = syncGroups.getOrPut(newSyncGroupId) { SyncGroupData() }
                group.widgetIds.add(appWidgetId)
                group.runnable?.let { handler.removeCallbacks(it) }
                startSyncAnimation(newSyncGroupId)
            } else {
                startAnimation(appWidgetId)
            }
        } else {
            widgetData.remove(appWidgetId)
            if (widgetData.isEmpty() && syncGroups.isEmpty()) {
                stopForeground(true)
                stopSelf()
            }
        }
    }

    private fun handleSyncWidgets(syncGroupId: String, widgetIdsToSync: Set<Int>) {
        val affectedSyncGroups = mutableSetOf<String>()

        widgetIdsToSync.forEach { appWidgetId ->
            widgetData[appWidgetId]?.let { data ->

                data.runnable?.let { handler.removeCallbacks(it) }
                data.runnable = null

                if (data.syncGroupId != null && data.syncGroupId != syncGroupId) {
                    affectedSyncGroups.add(data.syncGroupId!!)
                    syncGroups[data.syncGroupId]?.widgetIds?.remove(appWidgetId)
                }
                data.syncGroupId = syncGroupId
            }
        }

        affectedSyncGroups.forEach { oldSyncId ->
            syncGroups[oldSyncId]?.let { group ->
                if (group.widgetIds.isEmpty()) {
                    group.runnable?.let { handler.removeCallbacks(it) }
                    syncGroups.remove(oldSyncId)
                } else {
                    group.runnable?.let { handler.removeCallbacks(it) }
                    startSyncAnimation(oldSyncId)
                }
            }
        }

        val prefs = getSharedPreferences("gif_widget_prefs", MODE_PRIVATE).edit()
        widgetIdsToSync.forEach { appWidgetId ->
            prefs.putString("sync_group_$appWidgetId", syncGroupId)
        }
        prefs.apply()

        val syncGroup = syncGroups.getOrPut(syncGroupId) { SyncGroupData() }
        syncGroup.widgetIds.clear()
        syncGroup.widgetIds.addAll(widgetIdsToSync)
        syncGroup.currentFrame = 0
        syncGroup.runnable?.let { handler.removeCallbacks(it) }
        startSyncAnimation(syncGroupId)

        widgetData.keys.forEach { appWidgetId ->
            if (widgetData[appWidgetId]?.syncGroupId == null && widgetData[appWidgetId]?.runnable == null) {
                startAnimation(appWidgetId)
            }
        }
    }

    private fun startAnimation(appWidgetId: Int) {
        widgetData[appWidgetId]?.let { data ->
            val frames = data.frames
            if (frames.isNullOrEmpty()) return
            data.runnable?.let { handler.removeCallbacks(it) }

            val runnable = object : Runnable {
                override fun run() {
                    if (widgetData.containsKey(appWidgetId)) {
                        if (shouldUpdate()) {
                            updateWidget(appWidgetId)
                        }
                        val frameDuration = data.frames?.getOrNull(data.currentFrame)?.duration?.toLong() ?: 100L
                        if (data.runnable == this) {
                            handler.postDelayed(this, frameDuration.coerceAtLeast(1L))
                        }
                    }
                }
            }
            data.runnable = runnable
            handler.post(runnable)
        }
    }

    private fun startSyncAnimation(syncGroupId: String) {
        syncGroups[syncGroupId]?.let { group ->
            if (group.widgetIds.isEmpty()){
                syncGroups.remove(syncGroupId)
                return
            }
            group.runnable?.let { handler.removeCallbacks(it) }

            val runnable = object : Runnable {
                override fun run() {
                    if (syncGroups.containsKey(syncGroupId)) {
                        if (shouldUpdate()) {
                            updateSyncGroup(syncGroupId)
                        }

                        val minFrameDuration = group.widgetIds
                            .mapNotNull { widgetData[it]?.frames?.getOrNull(group.currentFrame)?.duration?.toLong() }
                            .minOrNull() ?: 100L

                        if (group.runnable == this) {
                            handler.postDelayed(this, minFrameDuration.coerceAtLeast(1L))
                        }
                    }
                }
            }
            group.runnable = runnable
            handler.post(runnable)
        }
    }

    private fun updateWidget(appWidgetId: Int) {
        widgetData[appWidgetId]?.let { data ->
            val frames = data.frames
            if (frames.isNullOrEmpty()) {
                handleRemoveWidget(appWidgetId)
                return
            }

            if (data.currentFrame < 0 || data.currentFrame >= frames.size) {
                data.currentFrame = 0
            }
            if (frames.isEmpty()) return

            val frameIndexToShow = data.currentFrame
            val frame = frames[frameIndexToShow]

            if (!frame.bitmap.isRecycled) {
                val appWidgetManager = AppWidgetManager.getInstance(this)
                val remoteViews = RemoteViews(packageName, R.layout.gif_widget_layout)
                remoteViews.setImageViewBitmap(R.id.imageView, frame.bitmap)
                try {
                    appWidgetManager.updateAppWidget(appWidgetId, remoteViews)
                } catch (e: Exception) {
                    handleRemoveWidget(appWidgetId)
                    return
                }
            } else {
                handleRemoveWidget(appWidgetId)
                return
            }

            data.currentFrame = (data.currentFrame + 1) % frames.size
        }
    }

    private fun updateSyncGroup(syncGroupId: String) {
        syncGroups[syncGroupId]?.let { group ->
            if (group.widgetIds.isEmpty()) {
                syncGroups.remove(syncGroupId)
                return
            }
            val appWidgetManager = AppWidgetManager.getInstance(this)
            var maxFrameCount = 0
            val widgetsToRemove = mutableListOf<Int>()

            group.widgetIds.forEach { appWidgetId ->
                widgetData[appWidgetId]?.let { data ->
                    val frames = data.frames
                    if (frames.isNullOrEmpty()) {
                        widgetsToRemove.add(appWidgetId)
                        return@forEach
                    }

                    maxFrameCount = maxOf(maxFrameCount, frames.size)
                    if (group.currentFrame < 0) group.currentFrame = 0
                    if (frames.isEmpty()) {
                        widgetsToRemove.add(appWidgetId)
                        return@forEach
                    }

                    val frameIndexToShow = group.currentFrame % frames.size
                    val frame = frames[frameIndexToShow]

                    if (!frame.bitmap.isRecycled) {
                        val remoteViews = RemoteViews(packageName, R.layout.gif_widget_layout)
                        remoteViews.setImageViewBitmap(R.id.imageView, frame.bitmap)
                        try {
                            appWidgetManager.updateAppWidget(appWidgetId, remoteViews)
                        } catch (e: Exception) {
                            widgetsToRemove.add(appWidgetId)
                            return@forEach
                        }
                    } else {
                        widgetsToRemove.add(appWidgetId)
                        return@forEach
                    }
                    data.currentFrame = frameIndexToShow
                } ?: widgetsToRemove.add(appWidgetId)
            }

            widgetsToRemove.forEach { appWidgetId ->
                handleRemoveWidget(appWidgetId)
            }


            if (maxFrameCount > 0 && syncGroups.containsKey(syncGroupId)) {
                if (group.widgetIds.isNotEmpty()) {
                    group.currentFrame = (group.currentFrame + 1) % maxFrameCount
                } else {
                    group.runnable?.let { handler.removeCallbacks(it) }
                    syncGroups.remove(syncGroupId)
                }

            } else if (syncGroups.containsKey(syncGroupId)) {
                group.currentFrame = 0
                if (group.widgetIds.isEmpty()) {
                    group.runnable?.let { handler.removeCallbacks(it) }
                    syncGroups.remove(syncGroupId)
                }
            }
        }
    }

    private fun decodeGifToFrames(uri: Uri): List<Frame> {
        return try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                val bufferedInputStream = BufferedInputStream(inputStream)
                if (!bufferedInputStream.markSupported()) {
                }
                val gifDrawable = GifDrawable(bufferedInputStream)
                val frames = mutableListOf<Frame>()
                val numberOfFrames = gifDrawable.numberOfFrames
                if (numberOfFrames <= 0) return emptyList()

                for (i in 0 until numberOfFrames) {
                    val bitmap = gifDrawable.seekToFrameAndGet(i)?.copy(Bitmap.Config.ARGB_8888, false)
                    val duration = gifDrawable.getFrameDuration(i)
                    if (bitmap != null) {
                        frames.add(Frame(bitmap, duration))
                    } else {
                    }
                }
                gifDrawable.recycle()
                frames
            } ?: emptyList()
        } catch (e: FileNotFoundException) {
            emptyList()
        } catch (e: OutOfMemoryError) {
            emptyList()
        }
        catch (e: Exception) {
            emptyList()
        }
    }

    private fun recycleFrames(frames: List<Frame>?) {
        frames?.forEach {
            if (!it.bitmap.isRecycled) {
                it.bitmap.recycle()
            }
        }
    }


    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        widgetData.values.forEach { data ->
            data.runnable?.let { handler.removeCallbacks(it) }
            recycleFrames(data.frames)
        }
        syncGroups.values.forEach { group ->
            group.runnable?.let { handler.removeCallbacks(it) }
        }
        widgetData.clear()
        syncGroups.clear()
        super.onDestroy()
    }
}