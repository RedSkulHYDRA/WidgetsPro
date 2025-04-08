package com.tpk.widgetspro.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.tpk.widgetspro.R
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

abstract class WidgetUpdateService : Service() {
    private val executorService: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private var scheduledFuture: ScheduledFuture<*>? = null
    private lateinit var prefs: SharedPreferences
    protected abstract val intervalKey: String
    protected abstract val widgetProviderClass: Class<*>
    private val TAG = "WidgetUpdateService"

    companion object {
        private const val WIDGETS_PRO_NOTIFICATION_ID = 100
        private const val CHANNEL_ID = "widgets_pro_channel"
    }

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == intervalKey) {
            val newInterval = prefs.getInt(intervalKey, 60).coerceAtLeast(1)
            Log.d(TAG, "Interval changed for $intervalKey to $newInterval minutes")
            startMonitoring(newInterval)
        }
    }

    override fun onCreate() {
        super.onCreate()
        startForeground(WIDGETS_PRO_NOTIFICATION_ID, createNotification())
        prefs = getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
        prefs.registerOnSharedPreferenceChangeListener(prefListener)
        val interval = prefs.getInt(intervalKey, 60).coerceAtLeast(1)
        Log.d(TAG, "Service created for $intervalKey with interval $interval minutes")
        startMonitoring(interval)
    }

    override fun onDestroy() {
        prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
        scheduledFuture?.cancel(false)
        executorService.shutdown()
        Log.d(TAG, "Service destroyed for $intervalKey")
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    protected fun startMonitoring(intervalMinutes: Int) {
        scheduledFuture?.cancel(false)
        Log.d(TAG, "Starting monitoring for $intervalKey with interval $intervalMinutes minutes")
        scheduledFuture = executorService.scheduleAtFixedRate({
            handler.post {
                Log.d(TAG, "Executing update for $intervalKey at ${System.currentTimeMillis()}")
                updateWidgets()
            }
        }, 0, intervalMinutes.toLong(), TimeUnit.MINUTES)
    }

    protected abstract fun updateWidgets()

    private fun createNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Widgets Pro Channel",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Channel for keeping Widgets Pro services running"
            }
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, widgetProviderClass),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.widgets_pro_running))
            .setContentText(getString(R.string.widgets_pro_active_text))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }
}