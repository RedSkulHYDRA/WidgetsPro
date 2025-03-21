package com.tpk.widgetspro.widgets.speedtest

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import com.tpk.widgetspro.services.SpeedUpdateService

class SpeedWidgetProvider : AppWidgetProvider() {
    override fun onEnabled(context: Context) {
        context.startForegroundService(Intent(context, SpeedUpdateService::class.java))
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        context.startForegroundService(Intent(context, SpeedUpdateService::class.java))
    }

    override fun onDisabled(context: Context) {
        context.stopService(Intent(context, SpeedUpdateService::class.java))
    }
}