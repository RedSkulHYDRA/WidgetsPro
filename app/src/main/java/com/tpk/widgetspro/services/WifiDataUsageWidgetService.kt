package com.tpk.widgetspro.services

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.util.Log
import com.tpk.widgetspro.widgets.networkusage.WifiDataUsageWidgetProvider

class WifiDataUsageWidgetService : WidgetUpdateService() {
    override val intervalKey = "wifi_data_usage_interval"
    override val notificationId = 6
    override val notificationChannelId = "WIFI_DATA_USAGE_CHANNEL"
    override val notificationTitle = "Wifi Data Usage Updates"
    override val notificationText = "Monitoring Wifi data usage"
    override val widgetProviderClass = WifiDataUsageWidgetProvider::class.java
    private val TAG = "WifiDataUsageService"

    override fun updateWidgets() {
        val appWidgetManager = AppWidgetManager.getInstance(applicationContext)
        val thisWidget = ComponentName(applicationContext, widgetProviderClass)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
        Log.d(TAG, "Updating ${appWidgetIds.size} WiFi data usage widget")
        appWidgetIds.forEach { appWidgetId ->
            WifiDataUsageWidgetProvider.updateAppWidget(applicationContext, appWidgetManager, appWidgetId)
        }
    }
}