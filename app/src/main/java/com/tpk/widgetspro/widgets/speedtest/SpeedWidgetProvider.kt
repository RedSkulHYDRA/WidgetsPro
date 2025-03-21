package com.tpk.widgetspro.widgets.speedtest

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent

class SpeedWidgetProvider : AppWidgetProvider() {

    override fun onEnabled(context: Context) {
        // Start the background update service when the first widget is placed.
        val intent = Intent(context, SpeedUpdateService::class.java)
        context.startForegroundService(intent)
    }

    override fun onDisabled(context: Context) {
        // Stop the service when the widget is removed.
        val intent = Intent(context, SpeedUpdateService::class.java)
        context.stopService(intent)
    }
}
