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
import android.net.TrafficStats
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.tpk.widgetspro.R
import com.tpk.widgetspro.utils.WidgetUtils
import com.tpk.widgetspro.widgets.speedtest.SpeedWidgetProvider

class SpeedUpdateService : Service() {
    private var previousBytes: Long = 0
    private val handler = Handler(Looper.getMainLooper())
    private val UPDATE_INTERVAL_MS = 1000L

    private val updateRunnable = object : Runnable {
        override fun run() {
            updateSpeed()
            handler.postDelayed(this, UPDATE_INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        handler.post(updateRunnable)
    }

    override fun onDestroy() {
        handler.removeCallbacks(updateRunnable)
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startMyForeground()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun updateSpeed() {
        val currentBytes = TrafficStats.getTotalRxBytes()
        val appWidgetManager = AppWidgetManager.getInstance(applicationContext)
        val thisWidget = ComponentName(applicationContext, SpeedWidgetProvider::class.java)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)

        if (currentBytes != TrafficStats.UNSUPPORTED.toLong()) {
            if (previousBytes != 0L) {
                val bytesInLastInterval = currentBytes - previousBytes
                val speedMBps = (bytesInLastInterval / 1024.0 / 1024.0).toFloat()

                appWidgetIds.forEach { appWidgetId ->
                    val views = RemoteViews(packageName, R.layout.speed_widget_layout)
                    val typeface = ResourcesCompat.getFont(applicationContext, R.font.my_custom_font)!!
                    val speedText = "↓  " + String.format("%.2f MB/s", speedMBps)
                    val setupBitmap = WidgetUtils.createTextBitmap(
                        applicationContext,
                        speedText,
                        20f,
                        ContextCompat.getColor(applicationContext, R.color.text_color),
                        typeface
                    )
                    views.setImageViewBitmap(R.id.speed_text, setupBitmap)
                    appWidgetManager.updateAppWidget(appWidgetId, views)
                }
            }
            previousBytes = currentBytes
        } else {
            appWidgetIds.forEach { appWidgetId ->
                val views = RemoteViews(packageName, R.layout.speed_widget_layout)
                val typeface = ResourcesCompat.getFont(applicationContext, R.font.my_custom_font)!!
                val setupBitmap = WidgetUtils.createTextBitmap(
                    applicationContext,
                    "N/A",
                    20f,
                    androidx.core.content.ContextCompat.getColor(applicationContext, R.color.text_color),
                    typeface
                )
                views.setImageViewBitmap(R.id.speed_text, setupBitmap)
                appWidgetManager.updateAppWidget(appWidgetId, views)
            }
        }
    }

    private fun startMyForeground() {
        val notificationChannelId = "SPEED_WIDGET_CHANNEL"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(notificationChannelId, "Speed Widget Updates", NotificationManager.IMPORTANCE_LOW)
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }

        val notificationIntent = Intent(this, SpeedWidgetProvider::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )

        val notification = NotificationCompat.Builder(this, notificationChannelId)
            .setContentTitle("Speed Widget Running")
            .setContentText("Monitoring network speed")
            .setContentIntent(pendingIntent)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .build()

        startForeground(4, notification)
    }
}