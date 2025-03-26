package com.tpk.widgetspro.services

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.util.Log
import com.tpk.widgetspro.widgets.networkusage.SimDataUsageWidgetProvider

class SimDataUsageWidgetService : WidgetUpdateService() {
    override val intervalKey = "sim_data_usage_interval"
    override val notificationId = 5
    override val notificationChannelId = "SIM_DATA_USAGE_CHANNEL"
    override val notificationTitle = "SIM Data Usage Updates"
    override val notificationText = "Monitoring SIM data usage"
    override val widgetProviderClass = SimDataUsageWidgetProvider::class.java
    private val TAG = "SimDataUsageService"

    override fun updateWidgets() {
        val appWidgetManager = AppWidgetManager.getInstance(applicationContext)
        val thisWidget = ComponentName(applicationContext, widgetProviderClass)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
        Log.d(TAG, "Updating ${appWidgetIds.size} SIM data usage widget")
        appWidgetIds.forEach { appWidgetId ->
            SimDataUsageWidgetProvider.updateAppWidget(applicationContext, appWidgetManager, appWidgetId)
        }
    }
}