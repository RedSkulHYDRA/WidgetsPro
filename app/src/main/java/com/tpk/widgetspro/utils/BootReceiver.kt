package com.tpk.widgetspro.utils

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.tpk.widgetspro.services.*
import com.tpk.widgetspro.widgets.analogclock.AnalogClockWidgetProvider_1
import com.tpk.widgetspro.widgets.analogclock.AnalogClockWidgetProvider_2
import com.tpk.widgetspro.widgets.battery.BatteryWidgetProvider
import com.tpk.widgetspro.widgets.cpu.CpuWidgetProvider
import com.tpk.widgetspro.widgets.photo.GifWidgetProvider
import com.tpk.widgetspro.widgets.sun.SunTrackerWidget

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val prefs = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)

            val cpuWidgetIds = appWidgetManager.getAppWidgetIds(ComponentName(context, CpuWidgetProvider::class.java))
            if (cpuWidgetIds.isNotEmpty()) {
                context.startForegroundService(Intent(context, CpuMonitorService::class.java))
            }

            val batteryWidgetIds = appWidgetManager.getAppWidgetIds(ComponentName(context, BatteryWidgetProvider::class.java))
            if (batteryWidgetIds.isNotEmpty()) {
                context.startForegroundService(Intent(context, BatteryMonitorService::class.java))
            }

            val activeNetworkSpeedProviders = prefs.getStringSet("active_network_speed_providers", mutableSetOf())
            if (!activeNetworkSpeedProviders.isNullOrEmpty()) {
                context.startForegroundService(Intent(context, BaseNetworkSpeedWidgetService::class.java))
            }

            val activeSimDataUsageProviders = prefs.getStringSet("active_sim_data_usage_providers", mutableSetOf())
            if (!activeSimDataUsageProviders.isNullOrEmpty()) {
                context.startForegroundService(Intent(context, BaseSimDataUsageWidgetService::class.java))
            }

            val activeWifiDataUsageProviders = prefs.getStringSet("active_wifi_data_usage_providers", mutableSetOf())
            if (!activeWifiDataUsageProviders.isNullOrEmpty()) {
                context.startForegroundService(Intent(context, BaseWifiDataUsageWidgetService::class.java))
            }

            val analogClock1WidgetIds = appWidgetManager.getAppWidgetIds(ComponentName(context, AnalogClockWidgetProvider_1::class.java))
            if (analogClock1WidgetIds.isNotEmpty()) {
                context.startForegroundService(Intent(context, AnalogClockUpdateService_1::class.java))
            }

            val analogClock2WidgetIds = appWidgetManager.getAppWidgetIds(ComponentName(context, AnalogClockWidgetProvider_2::class.java))
            if (analogClock2WidgetIds.isNotEmpty()) {
                context.startForegroundService(Intent(context, AnalogClockUpdateService_2::class.java))
            }

            val sunTrackerWidgetIds = appWidgetManager.getAppWidgetIds(ComponentName(context, SunTrackerWidget::class.java))
            if (sunTrackerWidgetIds.isNotEmpty()) {
                context.startForegroundService(Intent(context, SunSyncService::class.java))
            }

            val gifWidgetIds = appWidgetManager.getAppWidgetIds(ComponentName(context, GifWidgetProvider::class.java))
            if (gifWidgetIds.isNotEmpty()) {
                context.startForegroundService(Intent(context, AnimationService::class.java))
            }
        }
    }
}