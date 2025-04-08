package com.tpk.widgetspro.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.tpk.widgetspro.R
import com.tpk.widgetspro.widgets.analogclock.AnalogClockWidgetProvider_2

class AnalogClockUpdateService_2 : Service() {

    private val WIDGETS_PRO_NOTIFICATION_ID_2 = 101 // Unique ID to avoid conflict
    private val CHANNEL_ID = "widgets_pro_channel" // Same as original service
    private val updateInterval = 1000L // Update every second
    private lateinit var handler: Handler
    private lateinit var updateRunnable: Runnable
    private var isRunning = false

    override fun onCreate() {
        super.onCreate()
        startForegroundService()
        initializeMonitoring()
    }

    private fun startForegroundService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelName = "Widgets Pro Channel"
            val channel = NotificationChannel(CHANNEL_ID, channelName, NotificationManager.IMPORTANCE_MIN).apply {
                description = "Channel for keeping Widgets Pro services running"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.widgets_pro_running))
            .setContentText(getString(R.string.widgets_pro_active_text))
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)

        val notification = notificationBuilder.build()
        startForeground(WIDGETS_PRO_NOTIFICATION_ID_2, notification)
    }

    private fun initializeMonitoring() {
        handler = Handler(Looper.getMainLooper())
        updateRunnable = object : Runnable {
            override fun run() {
                updateWidgets()
                handler.postDelayed(this, updateInterval)
            }
        }
        startMonitoring()
    }

    private fun startMonitoring() {
        if (!isRunning) {
            isRunning = true
            handler.post(updateRunnable)
            Log.d("AnalogClockService_2", "Started monitoring at ${System.currentTimeMillis()}")
        }
    }

    private fun stopMonitoring() {
        if (isRunning) {
            isRunning = false
            handler.removeCallbacks(updateRunnable)
            Log.d("AnalogClockService_2", "Stopped monitoring at ${System.currentTimeMillis()}")
        }
    }

    private fun updateWidgets() {
        Log.d("AnalogClockService_2", "Updating widget at ${System.currentTimeMillis()}")
        val appWidgetManager = AppWidgetManager.getInstance(this)
        val componentName = ComponentName(this, AnalogClockWidgetProvider_2::class.java)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)

        for (appWidgetId in appWidgetIds) {
            AnalogClockWidgetProvider_2.updateAppWidget(this, appWidgetManager, appWidgetId)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!isRunning) startMonitoring()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopMonitoring()
        stopForeground(STOP_FOREGROUND_REMOVE)
        Log.d("AnalogClockService_2", "Service destroyed at ${System.currentTimeMillis()}")
        super.onDestroy()
    }
}