package com.tpk.widgetspro.utils

import android.content.Context
import android.net.ConnectivityManager
import android.os.RemoteException
import android.app.usage.NetworkStatsManager
import java.util.Calendar

object NetworkStatsHelper {

    @Throws(RemoteException::class)
    fun getSimDataUsage(context: Context): LongArray {
        val (startTime, endTime) = getTimeRange(context)
        val networkStatsManager = context.getSystemService(Context.NETWORK_STATS_SERVICE) as NetworkStatsManager
        val bucket = networkStatsManager.querySummaryForDevice(
            ConnectivityManager.TYPE_MOBILE,
            null,
            startTime,
            endTime
        )
        val txBytes = bucket.txBytes
        val rxBytes = bucket.rxBytes
        val totalBytes = txBytes + rxBytes
        return longArrayOf(txBytes, rxBytes, totalBytes)
    }

    @Throws(RemoteException::class)
    fun getWifiDataUsage(context: Context): LongArray {
        val (startTime, endTime) = getTimeRange(context)
        val networkStatsManager = context.getSystemService(Context.NETWORK_STATS_SERVICE) as NetworkStatsManager
        val bucket = networkStatsManager.querySummaryForDevice(
            ConnectivityManager.TYPE_WIFI,
            null,
            startTime,
            endTime
        )
        val txBytes = bucket.txBytes
        val rxBytes = bucket.rxBytes
        val totalBytes = txBytes + rxBytes
        return longArrayOf(txBytes, rxBytes, totalBytes)
    }

    private fun getTimeRange(context: Context): Pair<Long, Long> {
        val prefs = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
        val resetMode = prefs.getString("data_usage_reset_mode", "daily") ?: "daily"

        return if (resetMode == "daily") {
            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
            }
            val startTime = calendar.timeInMillis
            val endTime = startTime + 24 * 60 * 60 * 1000
            Pair(startTime, endTime)
        } else {
            val manualResetTime = prefs.getLong("manual_reset_time", System.currentTimeMillis())
            val endTime = System.currentTimeMillis()
            Pair(manualResetTime, endTime)
        }
    }
}