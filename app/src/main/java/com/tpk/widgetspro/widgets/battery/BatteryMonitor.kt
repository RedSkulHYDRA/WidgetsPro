package com.tpk.widgetspro.widgets.battery

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

class BatteryMonitor(
    private val context: Context,
    private val callback: (Int, Int) -> Unit
) {
    private var executorService: ScheduledExecutorService? = null
    private var scheduledFuture: ScheduledFuture<*>? = null
    private var currentInterval = 60

    fun startMonitoring(initialInterval: Int) {
        currentInterval = initialInterval
        executorService = Executors.newSingleThreadScheduledExecutor()
        executorService?.execute {
            performMonitoring()
            scheduleNextRun()
        }
    }

    private fun performMonitoring() {
        val percentage = batteryPercentage()
        val health = batteryCycleCount()
        callback(percentage, health)
    }

    private fun scheduleNextRun() {
        scheduledFuture = executorService?.schedule({
            performMonitoring()
            scheduleNextRun()
        }, currentInterval.toLong(), TimeUnit.SECONDS)
    }

    fun updateInterval(newInterval: Int) {
        currentInterval = newInterval.coerceAtLeast(1)
        scheduledFuture?.cancel(false)
        scheduleNextRun()
    }

    fun stopMonitoring() {
        scheduledFuture?.cancel(false)
        executorService?.shutdown()
        executorService = null
        scheduledFuture = null
    }

    private fun batteryPercentage(): Int {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }

    private fun batteryCycleCount(): Int {
        val batteryStatus = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        return batteryStatus?.getIntExtra(BatteryManager.EXTRA_CYCLE_COUNT, -1) ?: -1
    }
}