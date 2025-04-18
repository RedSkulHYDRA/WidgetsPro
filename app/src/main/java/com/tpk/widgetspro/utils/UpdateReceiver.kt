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
import com.tpk.widgetspro.widgets.bluetooth.BluetoothWidgetProvider
import com.tpk.widgetspro.widgets.caffeine.CaffeineWidget
import com.tpk.widgetspro.widgets.cpu.CpuWidgetProvider
import com.tpk.widgetspro.widgets.networkusage.*
import com.tpk.widgetspro.widgets.notes.NoteWidgetProvider
import com.tpk.widgetspro.widgets.photo.GifWidgetProvider
import com.tpk.widgetspro.widgets.sun.SunTrackerWidget

class UpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            startRelevantServices(context)
            updateAllWidgetProviders(context)
        }
    }

    private fun startRelevantServices(context: Context) {
        try {
            context.startForegroundService(Intent(context, CpuMonitorService::class.java))
            context.startForegroundService(Intent(context, BatteryMonitorService::class.java))
            context.startForegroundService(Intent(context, AnalogClockUpdateService_1::class.java))
            context.startForegroundService(Intent(context, AnalogClockUpdateService_2::class.java))
            context.startForegroundService(Intent(context, AnimationService::class.java))
            context.startForegroundService(Intent(context, BaseNetworkSpeedWidgetService::class.java))
            context.startForegroundService(Intent(context, BaseWifiDataUsageWidgetService::class.java))
            context.startForegroundService(Intent(context, BaseSimDataUsageWidgetService::class.java))
            context.startForegroundService(Intent(context, SunSyncService::class.java))

            val caffeinePrefs = context.getSharedPreferences("caffeine", Context.MODE_PRIVATE)
            if (caffeinePrefs.getBoolean("active", false)) {
                context.startForegroundService(Intent(context, CaffeineService::class.java))
            }
        } catch (e: Exception) {
        }
    }

    private fun updateAllWidgetProviders(context: Context) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val providers = arrayOf(
            CpuWidgetProvider::class.java,
            BatteryWidgetProvider::class.java,
            BluetoothWidgetProvider::class.java,
            CaffeineWidget::class.java,
            SunTrackerWidget::class.java,
            NetworkSpeedWidgetProviderCircle::class.java,
            NetworkSpeedWidgetProviderPill::class.java,
            WifiDataUsageWidgetProviderCircle::class.java,
            WifiDataUsageWidgetProviderPill::class.java,
            SimDataUsageWidgetProviderCircle::class.java,
            SimDataUsageWidgetProviderPill::class.java,
            NoteWidgetProvider::class.java,
            AnalogClockWidgetProvider_1::class.java,
            AnalogClockWidgetProvider_2::class.java,
            GifWidgetProvider::class.java
        )

        providers.forEach { provider ->
            updateWidgets(context, appWidgetManager, provider)
        }
    }

    private fun updateWidgets(context: Context, manager: AppWidgetManager, providerClass: Class<*>) {
        val provider = ComponentName(context, providerClass)
        val widgetIds = manager.getAppWidgetIds(provider)
        if (widgetIds.isNotEmpty()) {
            val updateIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, widgetIds)
                setPackage(context.packageName)
                component = provider
            }

            try {
                context.sendBroadcast(updateIntent)
            } catch (e: Exception) {
            }
        } else {
        }
    }
}