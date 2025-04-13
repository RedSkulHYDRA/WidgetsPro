package com.tpk.widgetspro.utils

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.tpk.widgetspro.widgets.battery.BatteryWidgetProvider
import com.tpk.widgetspro.widgets.bluetooth.BluetoothWidgetProvider
import com.tpk.widgetspro.widgets.caffeine.CaffeineWidget
import com.tpk.widgetspro.widgets.cpu.CpuWidgetProvider
import com.tpk.widgetspro.widgets.notes.NoteWidgetProvider
import com.tpk.widgetspro.widgets.networkusage.NetworkSpeedWidgetProviderCircle
import com.tpk.widgetspro.widgets.networkusage.NetworkSpeedWidgetProviderPill
import com.tpk.widgetspro.widgets.networkusage.SimDataUsageWidgetProviderCircle
import com.tpk.widgetspro.widgets.networkusage.SimDataUsageWidgetProviderPill
import com.tpk.widgetspro.widgets.networkusage.WifiDataUsageWidgetProviderCircle
import com.tpk.widgetspro.widgets.networkusage.WifiDataUsageWidgetProviderPill
import com.tpk.widgetspro.widgets.sun.SunTrackerWidget
import com.tpk.widgetspro.widgets.analogclock.AnalogClockWidgetProvider_1
import com.tpk.widgetspro.widgets.analogclock.AnalogClockWidgetProvider_2
import com.tpk.widgetspro.services.AnalogClockUpdateService_1
import com.tpk.widgetspro.services.AnalogClockUpdateService_2
import com.tpk.widgetspro.widgets.photo.GifWidgetProvider

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            updateWidgets(context, appWidgetManager, CpuWidgetProvider::class.java)
            updateWidgets(context, appWidgetManager, BatteryWidgetProvider::class.java)
            updateWidgets(context, appWidgetManager, CaffeineWidget::class.java)
            updateWidgets(context, appWidgetManager, BluetoothWidgetProvider::class.java)
            updateWidgets(context, appWidgetManager, SunTrackerWidget::class.java)
            updateWidgets(context, appWidgetManager, NetworkSpeedWidgetProviderCircle::class.java)
            updateWidgets(context, appWidgetManager, NetworkSpeedWidgetProviderPill::class.java)
            updateWidgets(context, appWidgetManager, WifiDataUsageWidgetProviderCircle::class.java)
            updateWidgets(context, appWidgetManager, WifiDataUsageWidgetProviderPill::class.java)
            updateWidgets(context, appWidgetManager, SimDataUsageWidgetProviderCircle::class.java)
            updateWidgets(context, appWidgetManager, SimDataUsageWidgetProviderPill::class.java)
            updateWidgets(context, appWidgetManager, NoteWidgetProvider::class.java)
            updateWidgets(context, appWidgetManager, AnalogClockWidgetProvider_1::class.java)
            updateWidgets(context, appWidgetManager, AnalogClockWidgetProvider_2::class.java)
            updateWidgets(context, appWidgetManager, GifWidgetProvider::class.java)

            val serviceIntent1 = Intent(context, AnalogClockUpdateService_1::class.java)
            val serviceIntent2 = Intent(context, AnalogClockUpdateService_2::class.java)

            context.startForegroundService(serviceIntent1)
            context.startForegroundService(serviceIntent2)

        }
    }

    private fun updateWidgets(context: Context, manager: AppWidgetManager, providerClass: Class<*>) {
        val provider = ComponentName(context, providerClass)
        val widgetIds = manager.getAppWidgetIds(provider)
        if (widgetIds.isNotEmpty()) {
            Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, widgetIds)
                component = provider
                context.sendBroadcast(this)
            }
        }
    }
}