package com.tpk.widgetspro.widgets.speedtest

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
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

class SpeedUpdateService : Service() {

    companion object {
        // Adjust this value as needed. (Update every second here)
        private const val UPDATE_INTERVAL_MS = 1000L
        private var previousBytes: Long = 0
    }

    private val handler = Handler(Looper.getMainLooper())
    private val updateRunnable = object : Runnable {
        override fun run() {
            updateSpeed()
            handler.postDelayed(this, UPDATE_INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        // Start the update loop.
        handler.post(updateRunnable)
    }

    override fun onDestroy() {
        // Remove any pending updates.
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

        // Different update behaviors based on support.
        if (currentBytes != TrafficStats.UNSUPPORTED.toLong()) {
            if (previousBytes != 0L) {
                val bytesInLastInterval = currentBytes - previousBytes
                val speedMBps = (bytesInLastInterval / 1024.0 / 1024.0).toFloat()

                for (appWidgetId in appWidgetIds) {
                    val views = RemoteViews(packageName, R.layout.speed_widget_layout)
                    val typeface = ResourcesCompat.getFont(applicationContext, R.font.my_custom_font)!!
                    val speedText = "↓  "+String.format("%.2f MB/s", speedMBps)
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
            // Case for unsupported TrafficStats.
            for (appWidgetId in appWidgetIds) {
                val views = RemoteViews(packageName, R.layout.speed_widget_layout)
                val typeface = ResourcesCompat.getFont(applicationContext, R.font.my_custom_font)!!
                val setupBitmap = WidgetUtils.createTextBitmap(
                    applicationContext,
                    "N/A",
                    20f,
                    Color.WHITE,
                    typeface
                )
                views.setImageViewBitmap(R.id.speed_text, setupBitmap)
                appWidgetManager.updateAppWidget(appWidgetId, views)
            }
        }
    }

    /**
     * Create a minimal foreground notification to keep the service alive.
     */
    private fun startMyForeground() {
        val notificationChannelId = "SPEED_WIDGET_CHANNEL"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                notificationChannelId,
                "Speed Widget Updates",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        // Create an intent that launches your app when the notification is selected.
        val notificationIntent = Intent(this, SpeedWidgetProvider::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                PendingIntent.FLAG_IMMUTABLE else 0
        )

        val notification = NotificationCompat.Builder(this, notificationChannelId)
            .setContentTitle("Speed Widget Running")
            .setContentText("Monitoring network speed")
            .setContentIntent(pendingIntent)
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Make sure your drawable exists.
            .build()

        startForeground(4, notification)
    }
}
