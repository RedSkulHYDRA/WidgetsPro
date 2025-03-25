package com.tpk.widgetspro.widgets.datausage

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.RemoteViews
import com.tpk.widgetspro.R
import com.tpk.widgetspro.services.DataUsageWidgetService
import com.tpk.widgetspro.utils.CommonUtils
import com.tpk.widgetspro.utils.NetworkStatsHelper

class DataUsageWidgetProvider : AppWidgetProvider() {
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_MIDNIGHT_RESET) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(ComponentName(context, DataUsageWidgetProvider::class.java))
            onUpdate(context, appWidgetManager, appWidgetIds)
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { updateAppWidget(context, appWidgetManager, it) }
        scheduleMidnightReset(context)
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        context.startForegroundService(Intent(context, DataUsageWidgetService::class.java))
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        context.stopService(Intent(context, DataUsageWidgetService::class.java))
    }

    private fun scheduleMidnightReset(context: Context) {
        CommonUtils.scheduleMidnightReset(context, DATA_USAGE_REQUEST_CODE, ACTION_MIDNIGHT_RESET, DataUsageWidgetProvider::class.java)
    }

    companion object {
        private const val ACTION_MIDNIGHT_RESET = "com.tpk.widgetspro.ACTION_DATA_USAGE_RESET"
        private const val DATA_USAGE_REQUEST_CODE = 1001

        fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            try {
                val usage = NetworkStatsHelper.getDeviceWifiDataUsage(context, NetworkStatsHelper.SESSION_TODAY)
                val totalBytes = usage[2]
                val formattedUsage = formatBytes(totalBytes)
                val views = RemoteViews(context.packageName, R.layout.data_usage_widget).apply {
                    setImageViewBitmap(
                        R.id.wifi_data_text,
                        CommonUtils.createTextAlternateBitmap(context, formattedUsage, 20f, CommonUtils.getTypeface(context))
                    )
                    setInt(R.id.wifiImageData, "setColorFilter", CommonUtils.getAccentColor(context))
                }
                appWidgetManager.updateAppWidget(appWidgetId, views)
            } catch (e: Exception) {
                Log.e("DataUsageWidget", "Error getting WiFi data usage", e)
                val views = RemoteViews(context.packageName, R.layout.data_usage_widget).apply {
                    setImageViewBitmap(
                        R.id.wifi_data_text,
                        CommonUtils.createTextAlternateBitmap(context, "Error", 20f, CommonUtils.getTypeface(context))
                    )
                    setInt(R.id.wifiImageData, "setColorFilter", CommonUtils.getAccentColor(context))
                }
                appWidgetManager.updateAppWidget(appWidgetId, views)
            }
        }

        private fun formatBytes(bytes: Long): String {
            val unit = 1024
            if (bytes < unit) return "$bytes B"
            val exp = minOf((Math.log(bytes.toDouble()) / Math.log(unit.toDouble())).toInt(), 5)
            val pre = "KMGTPE"[exp - 1]
            val value = bytes / Math.pow(unit.toDouble(), exp.toDouble())
            return String.format("%.1f %sB", value, pre)
        }
    }
}