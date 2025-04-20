package com.tpk.widgetspro.services

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.tpk.widgetspro.widgets.networkusage.BaseSimDataUsageWidgetProvider
import com.tpk.widgetspro.widgets.networkusage.SimDataUsageWidgetProviderCircle
import com.tpk.widgetspro.widgets.networkusage.SimDataUsageWidgetProviderPill

class BaseSimDataUsageWidgetService : BaseUsageWidgetUpdateService() {
    override val intervalKey = "sim_data_usage_interval"
    override val widgetProviderClass = BaseSimDataUsageWidgetProvider::class.java

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Check for active widgets and stop if none are present
        val appWidgetManager = AppWidgetManager.getInstance(applicationContext)
        val circleWidgetIds = appWidgetManager.getAppWidgetIds(ComponentName(applicationContext, SimDataUsageWidgetProviderCircle::class.java))
        val pillWidgetIds = appWidgetManager.getAppWidgetIds(ComponentName(applicationContext, SimDataUsageWidgetProviderPill::class.java))
        if (circleWidgetIds.isEmpty() && pillWidgetIds.isEmpty()) {
            stopSelf()
            return START_NOT_STICKY
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun updateWidgets() {
        val appWidgetManager = AppWidgetManager.getInstance(applicationContext)
        val circleWidgetIds = appWidgetManager.getAppWidgetIds(ComponentName(applicationContext, SimDataUsageWidgetProviderCircle::class.java))
        val pillWidgetIds = appWidgetManager.getAppWidgetIds(ComponentName(applicationContext, SimDataUsageWidgetProviderPill::class.java))
        // Stop service if no widgets are present
        if (circleWidgetIds.isEmpty() && pillWidgetIds.isEmpty()) {
            stopSelf()
            return
        }
        BaseSimDataUsageWidgetProvider.updateAllWidgets(applicationContext, SimDataUsageWidgetProviderCircle::class.java)
        BaseSimDataUsageWidgetProvider.updateAllWidgets(applicationContext, SimDataUsageWidgetProviderPill::class.java)
    }
}