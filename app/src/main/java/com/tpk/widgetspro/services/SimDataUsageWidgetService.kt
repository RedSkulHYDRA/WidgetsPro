package com.tpk.widgetspro.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.tpk.widgetspro.R
import com.tpk.widgetspro.widgets.datausage.SimDataUsageWidgetProvider
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

class SimDataUsageWidgetService : Service() {
    private val executorService: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private var scheduledFuture: ScheduledFuture<*>? = null
    private lateinit var prefs: SharedPreferences

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "sim_data_interval") {
            val newInterval = prefs.getInt("sim_data_interval", 60).coerceAtLeast(1)
            startMonitoring(newInterval)
        }
    }

    override fun onCreate() {
        super.onCreate()
        startForeground(5, createNotification())
        prefs = getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
        prefs.registerOnSharedPreferenceChangeListener(prefListener)
        val interval = prefs.getInt("sim_data_interval", 60).coerceAtLeast(1)
        startMonitoring(interval)
    }

    override fun onDestroy() {
        prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
        scheduledFuture?.cancel(false)
        executorService.shutdown()
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startMonitoring(intervalMinutes: Int) {
        scheduledFuture?.cancel(false)
        scheduledFuture = executorService.scheduleAtFixedRate({
            handler.post {
                val appWidgetManager = AppWidgetManager.getInstance(applicationContext)
                val thisWidget = ComponentName(applicationContext, SimDataUsageWidgetProvider::class.java)
                val appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
                appWidgetIds.forEach { appWidgetId ->
                    SimDataUsageWidgetProvider.updateAppWidget(applicationContext, appWidgetManager, appWidgetId)
                }
            }
        }, 0, intervalMinutes.toLong(), TimeUnit.MINUTES)
    }

    private fun createNotification(): Notification {
        val channelId = "SIM_DATA_USAGE_CHANNEL"
        val channel = NotificationChannel(
            channelId,
            "SIM Data Usage Updates",
            NotificationManager.IMPORTANCE_LOW
        )
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, SimDataUsageWidgetProvider::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("SIM Data Usage Widget Running")
            .setContentText("Monitoring SIM data usage")
            .setContentIntent(pendingIntent)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .build()
    }
}