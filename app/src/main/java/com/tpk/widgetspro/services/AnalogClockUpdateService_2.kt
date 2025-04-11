package com.tpk.widgetspro.services

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.tpk.widgetspro.widgets.analogclock.AnalogClockWidgetProvider_2

class AnalogClockUpdateService_2 : BaseMonitorService() {


    private val updateInterval = 1000L
    private lateinit var handler: Handler
    private lateinit var updateRunnable: Runnable
    private var isRunning = false

    override fun onCreate() {
        super.onCreate()
        initializeMonitoring()
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
        }
    }

    private fun stopMonitoring() {
        if (isRunning) {
            isRunning = false
            handler.removeCallbacks(updateRunnable)
        }
    }

    private fun updateWidgets() {
        val appWidgetManager = AppWidgetManager.getInstance(this)
        val componentName = ComponentName(this, AnalogClockWidgetProvider_2::class.java)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)

        for (appWidgetId in appWidgetIds) {
            AnalogClockWidgetProvider_2.updateAppWidget(this, appWidgetManager, appWidgetId)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!isRunning) startMonitoring()
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopMonitoring()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
}