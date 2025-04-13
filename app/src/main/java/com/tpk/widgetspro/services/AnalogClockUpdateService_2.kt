package com.tpk.widgetspro.services

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.tpk.widgetspro.widgets.analogclock.AnalogClockWidgetProvider_2
import java.util.Calendar

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
                if (shouldUpdate()) {
                    animateWidgets()
                }
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

    private fun animateWidgets() {
        val appWidgetManager = AppWidgetManager.getInstance(this)
        val componentName = ComponentName(this, AnalogClockWidgetProvider_2::class.java)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)

        for (appWidgetId in appWidgetIds) {
            AnalogClockWidgetProvider_2.animateWidgetUpdate(this, appWidgetManager, appWidgetId)
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

    private fun getLastPositions(appWidgetId: Int): Triple<Int, Int, Int> {
        val prefs = getSharedPreferences("ClockPrefs_$appWidgetId", Context.MODE_PRIVATE)
        return Triple(
            prefs.getInt("lastSeconds", 0),
            prefs.getInt("lastMinutes", 0),
            prefs.getInt("lastHours", 0)
        )
    }

    private fun saveLastPositions(appWidgetId: Int, seconds: Int, minutes: Int, hours: Int) {
        val prefs = getSharedPreferences("ClockPrefs_$appWidgetId", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putInt("lastSeconds", seconds)
            putInt("lastMinutes", minutes)
            putInt("lastHours", hours)
            apply()
        }
    }
}