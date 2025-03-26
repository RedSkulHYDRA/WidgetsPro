package com.tpk.widgetspro.utils


import android.content.Context
import android.net.ConnectivityManager
import android.os.RemoteException
import android.app.usage.NetworkStatsManager
import java.util.Calendar

object NetworkStatsHelper {
    const val SESSION_TODAY = 0
    const val SESSION_MONTHLY = 1

    @Throws(RemoteException::class)
    fun getSimDataUsage(context: Context, session: Int): LongArray {
        val (startTime, endTime) = getTimeRange(context, session, -1)
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
    fun getWifiDataUsage(context: Context, session: Int): LongArray {
        val (startTime, endTime) = getTimeRange(context, session, -1)
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

    private fun getTimeRange(context: Context, session: Int, date: Int): Pair<Long, Long> {
        val calendar = Calendar.getInstance()
        val endTime = calendar.timeInMillis
        val startTime: Long

        when (session) {
            SESSION_TODAY -> {
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                startTime = calendar.timeInMillis
            }
            SESSION_MONTHLY -> {
                startTime = getLastResetTime(context, date)
            }
            else -> {
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                startTime = calendar.timeInMillis
            }
        }
        return Pair(startTime, endTime)
    }
    private fun getLastResetTime(context: Context, resetDate: Int): Long {
        val calendar = Calendar.getInstance()
        val currentDay = calendar.get(Calendar.DAY_OF_MONTH)
        if (currentDay >= resetDate) {
            calendar.set(Calendar.DAY_OF_MONTH, resetDate)
        } else {
            calendar.add(Calendar.MONTH, -1)
            calendar.set(Calendar.DAY_OF_MONTH, resetDate)
        }
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }
}