package com.tpk.widgetspro.services

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.util.Log
import com.tpk.widgetspro.widgets.datausage.DataUsageWidgetProvider

class DataUsageWidgetService : WidgetUpdateService() {
    override val intervalKey = "data_interval"
    override val notificationId = 6
    override val notificationChannelId = "DATA_USAGE_CHANNEL"
    override val notificationTitle = "Data Usage Updates"
    override val notificationText = "Monitoring data usage"
    override val widgetProviderClass = DataUsageWidgetProvider::class.java
    private val TAG = "DataUsageService"

    override fun updateWidgets() {
        val appWidgetManager = AppWidgetManager.getInstance(applicationContext)
        val thisWidget = ComponentName(applicationContext, widgetProviderClass)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
        Log.d(TAG, "Updating ${appWidgetIds.size} WiFi data widgets")
        appWidgetIds.forEach { appWidgetId ->
            DataUsageWidgetProvider.updateAppWidget(applicationContext, appWidgetManager, appWidgetId)
        }
    }
}