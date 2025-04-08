package com.tpk.widgetspro.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.tpk.widgetspro.R

class CaffeineService : Service() {
    private var wakeLock: PowerManager.WakeLock? = null

    companion object {
        private const val WIDGETS_PRO_NOTIFICATION_ID = 100
        private const val CHANNEL_ID = "widgets_pro_channel"
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(WIDGETS_PRO_NOTIFICATION_ID, createNotification())
        wakeLock = (getSystemService(POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ON_AFTER_RELEASE, "Caffeine::WakeLock")
            .apply { acquire() }
        return START_STICKY
    }

    private fun createNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel(CHANNEL_ID, "Widgets Pro Channel", NotificationManager.IMPORTANCE_MIN).apply {
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                description = "Channel for keeping Widgets Pro services running"
                (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(this)
            }
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.widgets_pro_running))
            .setContentText(getString(R.string.widgets_pro_active_text))
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}