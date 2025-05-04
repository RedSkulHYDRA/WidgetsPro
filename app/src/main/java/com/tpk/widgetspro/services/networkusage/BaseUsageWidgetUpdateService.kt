package com.tpk.widgetspro.services.networkusage

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.tpk.widgetspro.services.BaseMonitorService
import kotlinx.coroutines.*
import java.util.concurrent.TimeUnit

abstract class BaseUsageWidgetUpdateService : BaseMonitorService(), CoroutineScope {

    private val supervisorJob = SupervisorJob()
    override val coroutineContext = Dispatchers.Main + supervisorJob

    private var updateJob: Job? = null
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
        updateJob?.cancel()
        supervisorJob.cancel()
        prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
        LocalBroadcastManager.getInstance(this).unregisterReceiver(visibilityResumedReceiver)
        super.onDestroy()
    }

    private fun updateIntervals() {
        val intervalMinutes = prefs.getInt(intervalKey, 60).coerceAtLeast(1)
        userIntervalMs = TimeUnit.MINUTES.toMillis(intervalMinutes.toLong())
    }

    private fun restartMonitoring() {
        updateJob?.cancel()
        updateJob = launch {
            while (isActive) {
                if (shouldUpdate()) {
                    updateWidgets()
                    delay(userIntervalMs)
                } else {
                    delay(idleIntervalMs)
                }
            }
        }
    }

    protected abstract fun updateWidgets()
}