package com.tpk.widgetspro.widgets.datausage

import android.app.*
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.tpk.widgetspro.R

class DataUsageWidgetService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private val updateInterval = 1000L // Update every minute.

    private val updateRunnable = object : Runnable {
        override fun run() {
            updateWidgets()
            handler.postDelayed(this, updateInterval)
        }
    }

    override fun onCreate() {
        super.onCreate()
        startForegroundServiceNotification()
        handler.post(updateRunnable)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Ensure the service is recreated if the system kills it.
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(updateRunnable)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * Updates all widget instances using the provider's existing update method.
     */
    private fun updateWidgets() {
        val appWidgetManager = AppWidgetManager.getInstance(this)
        val componentName = ComponentName(this, DataUsageWidgetProvider::class.java)
        val widgetIds = appWidgetManager.getAppWidgetIds(componentName)
        for (widgetId in widgetIds) {
            DataUsageWidgetProvider.updateAppWidget(this, appWidgetManager, widgetId)
        }
    }

    /**
     * Creates a notification channel (for Android O and above) and starts this service in the foreground.
     */
    private fun startForegroundServiceNotification() {
        val channelId = "data_usage_widget_service_channel"
        val notificationId = 5

        // Create a notification channel on Android O and above.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelName = "Widget Updater Service"
            val channel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_LOW
            )
            val notificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }

        // Build a minimal notification.
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Data Usage Widget Active")
            .setContentText("Widget updater service is running")
            .setSmallIcon(R.drawable.ic_launcher_foreground)  // Ensure this icon resource exists.
            .build()

        // Start as a foreground service.
        startForeground(notificationId, notification)
    }
}
