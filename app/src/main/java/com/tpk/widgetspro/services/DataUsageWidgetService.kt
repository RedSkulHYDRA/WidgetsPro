package com.tpk.widgetspro.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.tpk.widgetspro.R
import com.tpk.widgetspro.widgets.datausage.DataUsageWidgetProvider

class DataUsageWidgetService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val updateInterval = 1000L

    private val updateRunnable = object : Runnable {
        override fun run() {
            DataUsageWidgetProvider.updateAllWidgets(this@DataUsageWidgetService)
            handler.postDelayed(this, updateInterval)
        }
    }

    override fun onCreate() {
        super.onCreate()
        startForeground(
            5,
            createNotification("data_usage_widget_service_channel", "Data Usage Widget Active")
        )
        handler.post(updateRunnable)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        handler.removeCallbacks(updateRunnable)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotification(channelId: String, title: String): Notification {

        val channel = NotificationChannel(
            channelId,
            "Data Usage Widget Updater Service",
            NotificationManager.IMPORTANCE_LOW
        )
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(
            channel
        )

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle(title)
            .setContentText("Widget updater service is running")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .build()
    }
}