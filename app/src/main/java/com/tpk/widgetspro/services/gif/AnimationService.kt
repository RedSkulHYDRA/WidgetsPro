package com.tpk.widgetspro.services.gif

import android.appwidget.AppWidgetManager
import android.content.*
import android.graphics.Bitmap
import android.net.Uri
import android.os.IBinder
import android.widget.RemoteViews
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.tpk.widgetspro.R
import com.tpk.widgetspro.services.BaseMonitorService
import com.tpk.widgetspro.widgets.gif.GifWidgetProvider
import pl.droidsonroids.gif.GifDrawable
import java.io.BufferedInputStream
import java.io.FileNotFoundException
import java.util.*
import kotlinx.coroutines.*

class AnimationService : BaseMonitorService() {
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val widgetData = mutableMapOf<Int, WidgetAnimationData>()
    private val syncGroups = mutableMapOf<String, SyncGroupData>()
    private val idleUpdateInterval = CHECK_INTERVAL_INACTIVE_MS

    data class Frame(val bitmap: Bitmap, val duration: Int)

    data class WidgetAnimationData(
        var frames: List<Frame>? = null,
        var currentFrame: Int = 0,
        var uriString: String? = null,
        var syncGroupId: String? = null,
        var job: Job? = null
    )

    data class SyncGroupData(
        val widgetIds: MutableSet<Int> = mutableSetOf(),
        var currentFrame: Int = 0,
        var job: Job? = null
    )

    private val visibilityResumedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == ACTION_VISIBILITY_RESUMED) {
                restartAllAnimations()
            }
        }
    }

    private val userPresentReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_USER_PRESENT) {
                restartAllAnimations()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        LocalBroadcastManager.getInstance(this).registerReceiver(
            visibilityResumedReceiver,
            IntentFilter(ACTION_VISIBILITY_RESUMED)
        )
        registerReceiver(userPresentReceiver, IntentFilter(Intent.ACTION_USER_PRESENT))
    }

    private fun restartAllAnimations() {
        // Cancel all existing jobs
        widgetData.values.forEach { data ->
            data.job?.cancel()
        }
        syncGroups.values.forEach { group ->
            group.job?.cancel()
        }

        // Restart animations for individual widgets not in sync groups
        widgetData.forEach { (appWidgetId, data) ->
            if (data.syncGroupId == null) {
                startAnimation(appWidgetId)
            }
        }

        // Restart animations for sync groups
        syncGroups.keys.forEach { syncGroupId ->
            startSyncAnimation(syncGroupId)
        }
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
                group.job?.cancel()
                startSyncAnimation(syncGroupId)
            } else {
                startAnimation(appWidgetId)
            }
        }
    }

    private fun handleRemoveWidget(appWidgetId: Int) {
        widgetData[appWidgetId]?.let { data ->
            data.job?.cancel()
            recycleFrames(data.frames)
            data.syncGroupId?.let { syncGroupId ->
                syncGroups[syncGroupId]?.let { group ->
                    group.widgetIds.remove(appWidgetId)
                    if (group.widgetIds.isEmpty()) {
                        group.job?.cancel()
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
        widgetData[appWidgetId]?.let { oldData ->
            oldData.job?.cancel()
            recycleFrames(oldData.frames)
            oldData.syncGroupId?.let { oldSyncId ->
                syncGroups[oldSyncId]?.let { group ->
                    group.widgetIds.remove(appWidgetId)
                    if (group.widgetIds.isEmpty()) {
                        group.job?.cancel()
                        syncGroups.remove(oldSyncId)
                    } else {
                        group.job?.cancel()
                        startSyncAnimation(oldSyncId)
                    }
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
                group.job?.cancel()
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
                data.job?.cancel()
                data.job = null
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
                    group.job?.cancel()
                    syncGroups.remove(oldSyncId)
                } else {
                    group.job?.cancel()
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
        syncGroup.job?.cancel()
        startSyncAnimation(syncGroupId)

        widgetData.keys.forEach { appWidgetId ->
            if (widgetData[appWidgetId]?.syncGroupId == null && widgetData[appWidgetId]?.job == null) {
                startAnimation(appWidgetId)
            }
        }
    }

    private fun startAnimation(appWidgetId: Int) {
        widgetData[appWidgetId]?.let { data ->
            val frames = data.frames
            if (frames.isNullOrEmpty()) return
            data.job?.cancel()
            data.job = serviceScope.launch {
                while (isActive) {
                    if (shouldUpdate()) {
                        updateWidget(appWidgetId)
                        val frameDuration = data.frames?.getOrNull(data.currentFrame)?.duration?.toLong() ?: 100L
                        delay(frameDuration.coerceAtLeast(1L))
                    } else {
                        delay(idleUpdateInterval)
                    }
                }
            }
        }
    }

    private fun startSyncAnimation(syncGroupId: String) {
        syncGroups[syncGroupId]?.let { group ->
            if (group.widgetIds.isEmpty()) {
                syncGroups.remove(syncGroupId)
                return
            }
            group.job?.cancel()
            group.job = serviceScope.launch {
                while (isActive) {
                    if (shouldUpdate()) {
                        updateSyncGroup(syncGroupId)
                        val minFrameDuration = group.widgetIds
                            .mapNotNull { widgetData[it]?.frames?.getOrNull(group.currentFrame)?.duration?.toLong() }
                            .minOrNull() ?: 100L
                        delay(minFrameDuration.coerceAtLeast(1L))
                    } else {
                        delay(idleUpdateInterval)
                    }
                }
            }
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
                    syncGroups.remove(syncGroupId)
                }
            } else if (syncGroups.containsKey(syncGroupId)) {
                group.currentFrame = 0
                if (group.widgetIds.isEmpty()) {
                    syncGroups.remove(syncGroupId)
                } else {
                }
            } else {
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
                    }
                }
                gifDrawable.recycle()
                frames
            } ?: emptyList()
        } catch (e: FileNotFoundException) {
            emptyList()
        } catch (e: OutOfMemoryError) {
            emptyList()
        } catch (e: Exception) {
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

    override fun onDestroy() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(visibilityResumedReceiver)
        unregisterReceiver(userPresentReceiver)
        serviceScope.cancel()
        widgetData.values.forEach { data ->
            recycleFrames(data.frames)
        }
        widgetData.clear()
        syncGroups.clear()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}