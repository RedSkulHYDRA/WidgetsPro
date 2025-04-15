package com.tpk.widgetspro.widgets.analogclock

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import com.tpk.widgetspro.services.AnalogClockUpdateService_1

class AnalogClockWidgetProvider_1 : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        startService(context)
    }

    override fun onEnabled(context: Context) {
        startService(context)
    }

    override fun onDisabled(context: Context) {
        context.stopService(Intent(context, AnalogClockUpdateService_1::class.java))
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == AppWidgetManager.ACTION_APPWIDGET_UPDATE) {
            startService(context)
        }
    }

    private fun startService(context: Context) {
        val intent = Intent(context, AnalogClockUpdateService_1::class.java)
        context.startForegroundService(intent)
    }
}