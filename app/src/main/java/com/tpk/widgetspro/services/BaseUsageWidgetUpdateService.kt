package com.tpk.widgetspro.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.localbroadcastmanager.content.LocalBroadcastManager
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

    private val idleIntervalMs = CHECK_INTERVAL_INACTIVE_MS
    private var userIntervalMs = TimeUnit.MINUTES.toMillis(1)

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == intervalKey) {
            updateIntervals()
            restartMonitoring()
        }
    }

    private val visibilityResumedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == ACTION_VISIBILITY_RESUMED) {
                restartMonitoring()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
        prefs.registerOnSharedPreferenceChangeListener(prefListener)
        LocalBroadcastManager.getInstance(this).registerReceiver(
            visibilityResumedReceiver,
            IntentFilter(ACTION_VISIBILITY_RESUMED)
        )
        updateIntervals()
        restartMonitoring()
    }

    override fun onDestroy() {
        prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
        LocalBroadcastManager.getInstance(this).unregisterReceiver(visibilityResumedReceiver)
        scheduledFuture?.cancel(true)
        executorService.shutdown()
        super.onDestroy()
    }

    private fun updateIntervals() {
        val intervalMinutes = prefs.getInt(intervalKey, 60).coerceAtLeast(1)
        userIntervalMs = TimeUnit.MINUTES.toMillis(intervalMinutes.toLong())
    }

    private fun restartMonitoring() {
        scheduledFuture?.cancel(true)
        scheduledFuture = executorService.schedule(object : Runnable {
            override fun run() {
                if (shouldUpdate()) {
                    handler.post { updateWidgets() }
                    scheduledFuture = executorService.schedule(this, userIntervalMs, TimeUnit.MILLISECONDS)
                } else {
                    scheduledFuture = executorService.schedule(this, idleIntervalMs, TimeUnit.MILLISECONDS)
                }
            }
        }, 0, TimeUnit.MILLISECONDS)
    }

    protected abstract fun updateWidgets()
}