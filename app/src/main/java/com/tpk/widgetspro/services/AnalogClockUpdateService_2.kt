package com.tpk.widgetspro.services

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.widget.RemoteViews
import com.tpk.widgetspro.R
import com.tpk.widgetspro.widgets.analogclock.AnalogClockWidgetProvider_2
import java.util.Calendar

class AnalogClockUpdateService_2 : BaseMonitorService() {

    private lateinit var handler: Handler
    private lateinit var updateRunnable: Runnable
    private var isRunning = false
    private val updateInterval = 16L

    override fun onCreate() {
        super.onCreate()
        handler = Handler(Looper.getMainLooper())
        updateRunnable = object : Runnable {
            override fun run() {
                if (shouldUpdate()) {
                    updateHandPositions()
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

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!isRunning) startMonitoring()
        return super.onStartCommand(intent, flags, startId)
    }

    private fun updateHandPositions() {
        val appWidgetManager = AppWidgetManager.getInstance(this)
        val componentName = ComponentName(this, AnalogClockWidgetProvider_2::class.java)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
        val calendar = Calendar.getInstance()
        val hours = calendar.get(Calendar.HOUR)
        val minutes = calendar.get(Calendar.MINUTE)
        val seconds = calendar.get(Calendar.SECOND)
        val milliseconds = calendar.get(Calendar.MILLISECOND)
        val hourAngle = (hours % 12 * 30.0 + minutes / 2.0 + seconds / 120.0 + milliseconds / 120000.0).toFloat()
        val minuteAngle = (minutes * 6.0 + seconds / 10.0 + milliseconds / 10000.0).toFloat()
        val secondAngle = (seconds * 6.0 + milliseconds / 1000.0 * 6.0).toFloat()

        for (appWidgetId in appWidgetIds) {
            val views = RemoteViews(packageName, R.layout.analog_2_widget)
            views.setFloat(R.id.analog_2_hour, "setRotation", hourAngle)
            views.setFloat(R.id.analog_2_min, "setRotation", minuteAngle)
            views.setFloat(R.id.analog_2_secs, "setRotation", secondAngle)
            appWidgetManager.partiallyUpdateAppWidget(appWidgetId, views)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopMonitoring()
        super.onDestroy()
    }
}