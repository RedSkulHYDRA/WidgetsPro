package com.tpk.widgetspro.services

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.tpk.widgetspro.widgets.networkusage.BaseWifiDataUsageWidgetProvider
import com.tpk.widgetspro.widgets.networkusage.WifiDataUsageWidgetProviderCircle
import com.tpk.widgetspro.widgets.networkusage.WifiDataUsageWidgetProviderPill

class BaseWifiDataUsageWidgetService : BaseUsageWidgetUpdateService() {
    override val intervalKey = "wifi_data_usage_interval"
    override val widgetProviderClass = BaseWifiDataUsageWidgetProvider::class.java

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Check for active widgets and stop if none are present
        val appWidgetManager = AppWidgetManager.getInstance(applicationContext)
        val circleWidgetIds = appWidgetManager.getAppWidgetIds(ComponentName(applicationContext, WifiDataUsageWidgetProviderCircle::class.java))
        val pillWidgetIds = appWidgetManager.getAppWidgetIds(ComponentName(applicationContext, WifiDataUsageWidgetProviderPill::class.java))
        if (circleWidgetIds.isEmpty() && pillWidgetIds.isEmpty()) {
            stopSelf()
            return START_NOT_STICKY
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun updateWidgets() {
        val appWidgetManager = AppWidgetManager.getInstance(applicationContext)
        val circleWidgetIds = appWidgetManager.getAppWidgetIds(ComponentName(applicationContext, WifiDataUsageWidgetProviderCircle::class.java))
        val pillWidgetIds = appWidgetManager.getAppWidgetIds(ComponentName(applicationContext, WifiDataUsageWidgetProviderPill::class.java))
        // Stop service if no widgets are present
        if (circleWidgetIds.isEmpty() && pillWidgetIds.isEmpty()) {
            stopSelf()
            return
        }
        BaseWifiDataUsageWidgetProvider.updateAllWidgets(applicationContext, WifiDataUsageWidgetProviderCircle::class.java)
        BaseWifiDataUsageWidgetProvider.updateAllWidgets(applicationContext, WifiDataUsageWidgetProviderPill::class.java)
    }
}