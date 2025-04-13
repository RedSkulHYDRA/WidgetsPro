package com.tpk.widgetspro.services

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

abstract class BaseUsageWidgetUpdateService : BaseMonitorService() {
    private val executorService: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private var scheduledFuture: ScheduledFuture<*>? = null
    private lateinit var prefs: SharedPreferences
    protected abstract val intervalKey: String
    protected abstract val widgetProviderClass: Class<*>

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == intervalKey) {
            val newInterval = prefs.getInt(intervalKey, 60).coerceAtLeast(1)
            startMonitoring(newInterval)
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
        prefs.registerOnSharedPreferenceChangeListener(prefListener)
        val interval = prefs.getInt(intervalKey, 60).coerceAtLeast(1)
        startMonitoring(interval)
    }

    override fun onDestroy() {
        prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
        scheduledFuture?.cancel(false)
        executorService.shutdown()
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    protected fun startMonitoring(intervalMinutes: Int) {
        scheduledFuture?.cancel(false)
        scheduledFuture = executorService.scheduleAtFixedRate({
            handler.post {
                if (shouldUpdate()) {
                    updateWidgets()
                }
            }
        }, 0, intervalMinutes.toLong(), TimeUnit.MINUTES)
    }

    protected abstract fun updateWidgets()
}