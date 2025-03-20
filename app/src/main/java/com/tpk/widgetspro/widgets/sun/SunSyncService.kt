package com.tpk.widgetspro.widgets.sun

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import com.tpk.widgetspro.services.BaseMonitorService

class SunSyncService : BaseMonitorService() {

    override val notificationId = 3
    override val notificationTitle = "Sun Tracker Widget"
    override val notificationText = "Monitoring sun position"

    private val handler = Handler(Looper.getMainLooper())
    private val updateRunnable = object : Runnable {
        override fun run() {
            updateWidgets()
            handler.postDelayed(this, 60 * 1000L)
        }
    }

    private fun updateWidgets() {
        val appWidgetManager = AppWidgetManager.getInstance(this)
        val componentName = ComponentName(this, SunTrackerWidget::class.java)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
        if (appWidgetIds.isEmpty()) {
            stopSelf()
            return
        }
        val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
        intent.component = componentName
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds)
        sendBroadcast(intent)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        val appWidgetManager = AppWidgetManager.getInstance(this)
        val componentName = ComponentName(this, SunTrackerWidget::class.java)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
        if (appWidgetIds.isEmpty()) {
            stopSelf()
        } else {
            handler.removeCallbacks(updateRunnable)
            handler.post(updateRunnable)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(updateRunnable)
    }

    companion object {
        fun start(context: Context) {
            context.startService(Intent(context, SunSyncService::class.java))
        }
    }
}