package com.tpk.widgetspro.services

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.widget.RemoteViews
import com.tpk.widgetspro.R
import com.tpk.widgetspro.widgets.photo.GifWidgetProvider
import pl.droidsonroids.gif.GifDrawable
import java.io.BufferedInputStream
import java.util.concurrent.ConcurrentHashMap

class AnimationService : BaseMonitorService() {

    private val handler = Handler(Looper.getMainLooper())
    private val widgetData = ConcurrentHashMap<Int, WidgetAnimationData>()
    private val syncGroups = ConcurrentHashMap<String, SyncGroupData>()
    private val individualRunnables = ConcurrentHashMap<Int, Runnable>()

    companion object {
        private const val SYNC_TICK_INTERVAL_MS = 50L
        private const val MIN_FRAME_DURATION_MS = 20L
    }

    data class Frame(val bitmap: Bitmap, val duration: Int)

    data class WidgetAnimationData(
        var frames: List<Frame>? = null,
        var currentFrame: Int = 0,
        var uriString: String? = null,
        var syncGroupId: String? = null,
        var timeAccumulatedForCurrentFrameMs: Long = 0
    )

    data class SyncGroupData(
        val widgetIds: MutableSet<Int> = ConcurrentHashMap.newKeySet(),
        var runnable: Runnable? = null,
        var lastUpdateTimeMs: Long = 0
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
                    handleAddWidgetInternal(appWidgetId, uriString)
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
                            handleAddWidgetInternal(appWidgetId, uriString)
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
                            handleUpdateFileInternal(appWidgetId, uriString)
                        }
                    }
                }
                "SYNC_WIDGETS" -> {
                    val syncGroupId = intent.getStringExtra("sync_group_id")
                    val widgetIds = intent.getIntArrayExtra("sync_widget_ids")?.toSet() ?: emptySet()
                    if (syncGroupId != null && widgetIds.isNotEmpty()) {
                        handleSyncWidgetsInternal(syncGroupId, widgetIds)
                    }
                }
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun handleAddWidgetInternal(appWidgetId: Int, uriString: String) {
        if (widgetData.containsKey(appWidgetId)) {
            if (widgetData[appWidgetId]?.uriString != uriString) {
                handleUpdateFileInternal(appWidgetId, uriString)
            }
            return
        }

        stopAnimation(appWidgetId)

        val frames = getFrames(Uri.parse(uriString))

        if (frames.isNotEmpty()) {
            val prefs = getSharedPreferences("gif_widget_prefs", MODE_PRIVATE)
            val syncGroupId = prefs.getString("sync_group_$appWidgetId", null)
            val newData = WidgetAnimationData(
                frames = frames,
                uriString = uriString,
                syncGroupId = syncGroupId
            )
            widgetData[appWidgetId] = newData

            if (syncGroupId != null) {
                val group = syncGroups.getOrPut(syncGroupId) { SyncGroupData() }
                group.widgetIds.add(appWidgetId)
                newData.timeAccumulatedForCurrentFrameMs = 0
                ensureSyncAnimationStarted(syncGroupId)
            } else {
                startAnimation(appWidgetId)
            }
        }
        checkServiceStopCondition()
    }

    private fun handleRemoveWidget(appWidgetId: Int) {
        stopAnimation(appWidgetId)

        widgetData.remove(appWidgetId)?.let { removedData ->
            removedData.frames?.forEach { it.bitmap.recycle() }
            val syncGroupId = removedData.syncGroupId
            if (syncGroupId != null) {
                syncGroups[syncGroupId]?.let { group ->
                    group.widgetIds.remove(appWidgetId)
                    if (group.widgetIds.isEmpty()) {
                        stopSyncAnimation(syncGroupId)
                        syncGroups.remove(syncGroupId)
                    }
                }
            }
        }
        checkServiceStopCondition()
    }

    private fun handleUpdateFileInternal(appWidgetId: Int, uriString: String) {
        stopAnimation(appWidgetId)

        val existingData = widgetData[appWidgetId]
        val oldSyncGroupId = existingData?.syncGroupId

        if (oldSyncGroupId != null) {
            syncGroups[oldSyncGroupId]?.let { group ->
                group.widgetIds.remove(appWidgetId)
                if (group.widgetIds.isEmpty()) {
                    stopSyncAnimation(oldSyncGroupId)
                    syncGroups.remove(oldSyncGroupId)
                }
            }
        }

        existingData?.frames?.forEach { it.bitmap.recycle() }

        val newFrames = getFrames(Uri.parse(uriString))

        if (newFrames.isNotEmpty()) {
            val prefs = getSharedPreferences("gif_widget_prefs", MODE_PRIVATE)
            val newSyncGroupId = prefs.getString("sync_group_$appWidgetId", null)
            val updatedData = existingData ?: WidgetAnimationData()
            updatedData.frames = newFrames
            updatedData.currentFrame = 0
            updatedData.uriString = uriString
            updatedData.syncGroupId = newSyncGroupId
            updatedData.timeAccumulatedForCurrentFrameMs = 0

            widgetData[appWidgetId] = updatedData

            if (newSyncGroupId != null) {
                val group = syncGroups.getOrPut(newSyncGroupId) { SyncGroupData() }
                group.widgetIds.add(appWidgetId)
                ensureSyncAnimationStarted(newSyncGroupId)
            } else {
                startAnimation(appWidgetId)
            }
        } else {
            widgetData.remove(appWidgetId)
        }
        checkServiceStopCondition()
    }

    private fun handleSyncWidgetsInternal(syncGroupId: String, widgetIdsToSync: Set<Int>) {
        val affectedOldGroups = mutableSetOf<String>()

        widgetIdsToSync.forEach { appWidgetId ->
            stopAnimation(appWidgetId)

            widgetData[appWidgetId]?.let { data ->
                val oldSyncGroupId = data.syncGroupId
                if (oldSyncGroupId != null && oldSyncGroupId != syncGroupId) {
                    affectedOldGroups.add(oldSyncGroupId)
                    syncGroups[oldSyncGroupId]?.widgetIds?.remove(appWidgetId)
                }
                data.syncGroupId = syncGroupId
                data.timeAccumulatedForCurrentFrameMs = 0
            }
        }

        affectedOldGroups.forEach { oldGroupId ->
            syncGroups[oldGroupId]?.let { group ->
                if (group.widgetIds.isEmpty()) {
                    stopSyncAnimation(oldGroupId)
                    syncGroups.remove(oldGroupId)
                }
            }
        }

        val targetGroup = syncGroups.getOrPut(syncGroupId) { SyncGroupData() }
        targetGroup.widgetIds.clear()
        targetGroup.widgetIds.addAll(widgetIdsToSync)

        val prefs = getSharedPreferences("gif_widget_prefs", MODE_PRIVATE)
        val editor = prefs.edit()
        widgetIdsToSync.forEach { appWidgetId ->
            editor.putString("sync_group_$appWidgetId", syncGroupId)
        }
        editor.apply()

        ensureSyncAnimationStarted(syncGroupId)
        checkServiceStopCondition()
    }

    private fun startAnimation(appWidgetId: Int) {
        stopAnimation(appWidgetId)

        widgetData[appWidgetId]?.let { data ->
            if (data.frames?.isNotEmpty() == true && data.syncGroupId == null) {
                val runnable = object : Runnable {
                    override fun run() {
                        val currentData = widgetData[appWidgetId]

                        if (currentData == null || currentData.syncGroupId != null || individualRunnables[appWidgetId] != this) {
                            individualRunnables.remove(appWidgetId)
                            return
                        }

                        if (shouldUpdate() && currentData.frames?.isNotEmpty() == true) {
                            val frames = currentData.frames!!
                            val frameToDisplay = frames[currentData.currentFrame]

                            updateWidgetView(appWidgetId, frameToDisplay.bitmap)

                            currentData.currentFrame = (currentData.currentFrame + 1) % frames.size

                            val nextDuration = frames[currentData.currentFrame].duration.toLong()
                                .coerceAtLeast(MIN_FRAME_DURATION_MS)
                            handler.postDelayed(this, nextDuration)
                        } else if (currentData.frames?.isEmpty() == true) {
                            individualRunnables.remove(appWidgetId)
                        } else {
                            handler.postDelayed(this, 1000L)
                        }
                    }
                }
                individualRunnables[appWidgetId] = runnable
                handler.post(runnable)
            }
        }
    }

    private fun stopAnimation(appWidgetId: Int) {
        individualRunnables.remove(appWidgetId)?.let { runnable ->
            handler.removeCallbacks(runnable)
        }
    }

    private fun ensureSyncAnimationStarted(syncGroupId: String) {
        syncGroups[syncGroupId]?.let { group ->
            if (group.runnable == null && group.widgetIds.isNotEmpty()) {
                group.lastUpdateTimeMs = SystemClock.uptimeMillis()
                group.widgetIds.forEach { widgetId -> widgetData[widgetId]?.timeAccumulatedForCurrentFrameMs = 0 }

                val runnable = object : Runnable {
                    override fun run() {
                        val currentGroup = syncGroups[syncGroupId]
                        if (currentGroup == null || currentGroup.runnable != this) {
                            return
                        }
                        if (!shouldUpdate() || currentGroup.widgetIds.isEmpty()) {
                            handler.postDelayed(this, 1000L)
                            return
                        }

                        val now = SystemClock.uptimeMillis()
                        val deltaMs = now - currentGroup.lastUpdateTimeMs
                        currentGroup.lastUpdateTimeMs = now

                        if (deltaMs > 0) {
                            updateSyncGroupWidgets(syncGroupId, deltaMs)
                        }

                        if (syncGroups.containsKey(syncGroupId) && syncGroups[syncGroupId]?.widgetIds?.isNotEmpty() == true) {
                            handler.postDelayed(this, SYNC_TICK_INTERVAL_MS)
                        } else {
                            syncGroups.remove(syncGroupId)
                        }
                    }
                }
                group.runnable = runnable
                handler.post(runnable)
            } else {
                stopSyncAnimation(syncGroupId)
                syncGroups.remove(syncGroupId)
            }
        }
    }

    private fun stopSyncAnimation(syncGroupId: String) {
        syncGroups[syncGroupId]?.runnable?.let {
            handler.removeCallbacks(it)
        }
    }

    private fun updateSyncGroupWidgets(syncGroupId: String, deltaMs: Long) {
        syncGroups[syncGroupId]?.let { group ->
            val widgetsToUpdate = mutableMapOf<Int, Bitmap>()
            val widgetsToRemoveFromGroup = mutableListOf<Int>()

            group.widgetIds.forEach { appWidgetId ->
                widgetData[appWidgetId]?.let { data ->
                    val frames = data.frames
                    if (frames.isNullOrEmpty()) {
                        widgetsToRemoveFromGroup.add(appWidgetId)
                        return@forEach
                    }

                    data.timeAccumulatedForCurrentFrameMs += deltaMs
                    var frameAdvanced = false

                    while (true) {
                        if (data.currentFrame >= frames.size) data.currentFrame = 0
                        val currentFrameDuration = frames[data.currentFrame].duration.toLong()
                            .coerceAtLeast(MIN_FRAME_DURATION_MS)

                        if (data.timeAccumulatedForCurrentFrameMs >= currentFrameDuration) {
                            data.timeAccumulatedForCurrentFrameMs -= currentFrameDuration
                            data.currentFrame = (data.currentFrame + 1) % frames.size
                            frameAdvanced = true
                            if (frames.size <= 1) break
                        } else {
                            break
                        }
                        if (!frameAdvanced && currentFrameDuration <= MIN_FRAME_DURATION_MS) break
                    }

                    if (frameAdvanced) {
                        if (data.currentFrame < frames.size) {
                            widgetsToUpdate[appWidgetId] = frames[data.currentFrame].bitmap
                        } else {
                            widgetsToRemoveFromGroup.add(appWidgetId)
                        }
                    }
                } ?: run {
                    widgetsToRemoveFromGroup.add(appWidgetId)
                }
            }

            widgetsToUpdate.forEach { (widgetId, bitmap) ->
                updateWidgetView(widgetId, bitmap) { success ->
                    if (!success) widgetsToRemoveFromGroup.add(widgetId)
                }
            }

            if (widgetsToRemoveFromGroup.isNotEmpty()) {
                widgetsToRemoveFromGroup.forEach { widgetId -> group.widgetIds.remove(widgetId) }
                if (group.widgetIds.isEmpty()) {
                    stopSyncAnimation(syncGroupId)
                    syncGroups.remove(syncGroupId)
                    checkServiceStopCondition()
                }
            }
        }
    }

    private fun updateWidgetView(appWidgetId: Int, bitmap: Bitmap, callback: ((Boolean) -> Unit)? = null) {
        if (!widgetData.containsKey(appWidgetId)) {
            callback?.invoke(false)
            return
        }
        val remoteViews = RemoteViews(packageName, R.layout.gif_widget_layout)
        remoteViews.setImageViewBitmap(R.id.imageView, bitmap)
        val appWidgetManager = AppWidgetManager.getInstance(this)
        try {
            appWidgetManager.updateAppWidget(appWidgetId, remoteViews)
            callback?.invoke(true)
        } catch (e: Exception) {
            handleRemoveWidget(appWidgetId)
            callback?.invoke(false)
        }
    }

    private fun checkServiceStopCondition() {
        if (widgetData.isEmpty() && syncGroups.isEmpty() && individualRunnables.isEmpty()) {
            stopForeground(true)
            stopSelf()
        }
    }

    private fun getFrames(uri: Uri): List<Frame> {
        val mimeType = contentResolver.getType(uri) ?: return emptyList()
        if (mimeType != "image/gif") return emptyList()
        return decodeGif(uri)
    }

    private fun decodeGif(uri: Uri): List<Frame> {
        return try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                val bufferedInputStream = BufferedInputStream(inputStream)
                val gifDrawable = GifDrawable(bufferedInputStream)
                val frames = mutableListOf<Frame>()
                val frameCount = gifDrawable.numberOfFrames
                if (frameCount > 0) {
                    for (i in 0 until frameCount) {
                        val bitmap = gifDrawable.seekToFrameAndGet(i)
                        val duration = gifDrawable.getFrameDuration(i).coerceAtLeast(MIN_FRAME_DURATION_MS.toInt())
                        frames.add(Frame(bitmap.copy(Bitmap.Config.ARGB_8888, false), duration))
                    }
                }
                frames
            } ?: emptyList()
        } catch (oom: OutOfMemoryError) {
            System.gc()
            emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)

        widgetData.forEach { (_, data) ->
            data.frames?.forEach { frame ->
                if (!frame.bitmap.isRecycled) {
                    frame.bitmap.recycle()
                }
            }
        }
        widgetData.clear()
        syncGroups.clear()
        individualRunnables.clear()
        super.onDestroy()
    }
}