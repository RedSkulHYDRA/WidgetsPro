package com.tpk.widgetspro.services

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.tpk.widgetspro.R
import com.tpk.widgetspro.widgets.analogclock.AnalogClockWidgetProvider
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import java.util.Calendar

class AnalogClockUpdateService : Service() {

    private val updateInterval = 60000L // Update every minute
    private val WIDGETS_PRO_NOTIFICATION_ID = 100
    private val CHANNEL_ID = "widgets_pro_channel"
    private lateinit var alarmManager: AlarmManager
    private lateinit var alarmIntent: PendingIntent
    private lateinit var notificationBuilder: NotificationCompat.Builder

    override fun onCreate() {
        super.onCreate()
        startForegroundService()
        setupAlarm()
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

        notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.widgets_pro_running))
            .setContentText(getString(R.string.widgets_pro_active_text))
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)

        val notification = notificationBuilder.build()
        startForeground(WIDGETS_PRO_NOTIFICATION_ID, notification)
    }

    private fun setupAlarm() {
        alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, AnalogClockWidgetProvider::class.java).apply {
            action = "UPDATE_ANALOG_CLOCK"
        }
        alarmIntent = PendingIntent.getBroadcast(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Align to the next minute boundary
        val calendar = Calendar.getInstance().apply {
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.MINUTE, 1)
        }
        val nextMinuteTime = calendar.timeInMillis

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            nextMinuteTime,
            alarmIntent
        )
        Log.d("AnalogClockService", "Alarm scheduled for next minute at $nextMinuteTime (${calendar.get(Calendar.HOUR_OF_DAY)}:${calendar.get(Calendar.MINUTE)})")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        updateWidgets() // Update immediately on start
        return START_STICKY
    }

    private fun updateWidgets() {
        Log.d("AnalogClockService", "Updating widget at ${System.currentTimeMillis()}")
        val appWidgetManager = AppWidgetManager.getInstance(this)
        val componentName = ComponentName(this, AnalogClockWidgetProvider::class.java)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)

        for (appWidgetId in appWidgetIds) {
            AnalogClockWidgetProvider.updateAppWidget(this, appWidgetManager, appWidgetId)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        alarmManager.cancel(alarmIntent)
        stopForeground(STOP_FOREGROUND_REMOVE)
        Log.d("AnalogClockService", "Service destroyed at ${System.currentTimeMillis()}")
        super.onDestroy()
    }
}