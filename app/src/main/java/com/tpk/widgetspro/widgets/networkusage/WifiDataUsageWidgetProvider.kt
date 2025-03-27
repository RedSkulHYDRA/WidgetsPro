package com.tpk.widgetspro.widgets.networkusage

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import android.widget.RemoteViews
import com.tpk.widgetspro.R
import com.tpk.widgetspro.services.WifiDataUsageWidgetService
import com.tpk.widgetspro.utils.CommonUtils
import com.tpk.widgetspro.utils.NetworkStatsHelper

class WifiDataUsageWidgetProvider : AppWidgetProvider() {
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_MIDNIGHT_RESET) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(ComponentName(context, WifiDataUsageWidgetProvider::class.java))
            onUpdate(context, appWidgetManager, appWidgetIds)
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { updateAppWidget(context, appWidgetManager, it) }
        scheduleMidnightReset(context)
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        context.startForegroundService(Intent(context, WifiDataUsageWidgetService::class.java))
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        context.stopService(Intent(context, WifiDataUsageWidgetService::class.java))
    }

    private fun scheduleMidnightReset(context: Context) {
        CommonUtils.scheduleMidnightReset(context, WIFI_DATA_USAGE_REQUEST_CODE, ACTION_MIDNIGHT_RESET, WifiDataUsageWidgetProvider::class.java)
    }

    companion object {
        private const val ACTION_MIDNIGHT_RESET = "com.tpk.widgetspro.ACTION_WIFI_DATA_USAGE_RESET"
        private const val WIFI_DATA_USAGE_REQUEST_CODE = 1001

        fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            try {
                val usage = NetworkStatsHelper.getWifiDataUsage(context, NetworkStatsHelper.SESSION_TODAY)
                val totalBytes = usage[2]
                val formattedUsage = formatBytes(totalBytes)
                val views = RemoteViews(context.packageName, R.layout.wifi_data_usage_widget).apply {
                    setImageViewBitmap(
                        R.id.wifi_data_usage_text,
                        CommonUtils.createTextAlternateBitmap(context, formattedUsage, 20f, CommonUtils.getTypeface(context))
                    )
                    setInt(R.id.wifi_data_usage_image, "setColorFilter", CommonUtils.getAccentColor(context))
                }
                val intent = Intent(Settings.ACTION_WIFI_SETTINGS)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                val pendingIntent = PendingIntent.getActivity(
                    context,
                    0,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.wifi_data_usage, pendingIntent)
                appWidgetManager.updateAppWidget(appWidgetId, views)
            } catch (e: Exception) {
                Log.e("DataUsageWidget", "Error getting WiFi data usage", e)
                val views = RemoteViews(context.packageName, R.layout.wifi_data_usage_widget).apply {
                    setImageViewBitmap(
                        R.id.wifi_data_usage_text,
                        CommonUtils.createTextAlternateBitmap(context, "Click here", 20f, CommonUtils.getTypeface(context))
                    )
                    setInt(R.id.wifi_data_usage_image, "setColorFilter", CommonUtils.getAccentColor(context))
                }
                val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                val pendingIntent = PendingIntent.getActivity(
                    context,
                    0,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(
                    R.id.wifi_data_usage,
                    pendingIntent
                )
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