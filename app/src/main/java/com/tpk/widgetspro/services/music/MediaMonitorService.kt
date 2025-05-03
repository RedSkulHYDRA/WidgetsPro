package com.tpk.widgetspro.services.music

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.tpk.widgetspro.services.BaseMonitorService
import com.tpk.widgetspro.widgets.music.MusicSimpleWidgetProvider

class MediaMonitorService : BaseMonitorService() {

    private lateinit var mediaSessionManager: MediaSessionManager
    private var activeSessionListener: MediaSessionManager.OnActiveSessionsChangedListener? = null
    private val handler = Handler(Looper.getMainLooper())
    private val activeControllers = mutableMapOf<MediaController, MediaController.Callback>()

    override fun onCreate() {
        super.onCreate()

        mediaSessionManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        val componentName = ComponentName(this, "com.example.musicsimplewidget.NotificationListener")

        activeSessionListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
            updateControllerCallbacks(controllers ?: emptyList())
            sendUpdateBroadcast()
        }

        try {
            mediaSessionManager.addOnActiveSessionsChangedListener(activeSessionListener!!, componentName, handler)
            updateControllerCallbacks(mediaSessionManager.getActiveSessions(componentName) ?: emptyList())
            sendUpdateBroadcast()
        } catch (e: SecurityException) {
            stopSelf()
        }
    }

    private fun updateControllerCallbacks(controllers: List<MediaController>) {
        val currentControllers = controllers.toSet()
        val removedControllers = activeControllers.keys - currentControllers
        removedControllers.forEach { controller ->
            val callback = activeControllers.remove(controller)
            callback?.let { controller.unregisterCallback(it) }
        }

        controllers.forEach { controller ->
            if (!activeControllers.containsKey(controller)) {
                val callback = object : MediaController.Callback() {
                    override fun onPlaybackStateChanged(state: android.media.session.PlaybackState?) {
                        sendUpdateBroadcast()
                    }

                    override fun onMetadataChanged(metadata: android.media.MediaMetadata?) {
                        sendUpdateBroadcast()
                    }

                    override fun onSessionDestroyed() {
                        val cb = activeControllers.remove(controller)
                        cb?.let { controller.unregisterCallback(it) }
                        sendUpdateBroadcast()
                    }
                }
                controller.registerCallback(callback, handler)
                activeControllers[controller] = callback
            }
        }
        if(controllers.isEmpty() && removedControllers.isNotEmpty()) {
            sendUpdateBroadcast()
        }
    }

    private fun sendUpdateBroadcast() {
        val updateIntent = Intent(this, MusicSimpleWidgetProvider::class.java).apply {
            action = MusicSimpleWidgetProvider.ACTION_MEDIA_UPDATE
        }
        sendBroadcast(updateIntent)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    override fun onDestroy() {
        activeSessionListener?.let {
            try {
                mediaSessionManager.removeOnActiveSessionsChangedListener(it)
            } catch (e: Exception) {
            }
        }
        activeControllers.forEach { (controller, callback) ->
            try {
                controller.unregisterCallback(callback)
            } catch (e: Exception) {
            }
        }
        activeControllers.clear()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}