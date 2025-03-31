package com.tpk.widgetspro.widgets.networkusage

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import com.tpk.widgetspro.services.NetworkSpeedService

class NetworkSpeedProvider : AppWidgetProvider() {
    override fun onEnabled(context: Context) {
        context.startForegroundService(Intent(context, NetworkSpeedService::class.java))
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        context.startForegroundService(Intent(context, NetworkSpeedService::class.java))
    }

    override fun onDisabled(context: Context) {
        context.stopService(Intent(context, NetworkSpeedService::class.java))
    }
}