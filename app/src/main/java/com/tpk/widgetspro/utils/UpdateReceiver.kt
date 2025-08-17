package com.tpk.widgetspro.utils

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.tpk.widgetspro.services.analogclock.AnalogClockUpdateService_1
import com.tpk.widgetspro.services.analogclock.AnalogClockUpdateService_2
import com.tpk.widgetspro.services.battery.BatteryMonitorService
import com.tpk.widgetspro.services.caffeine.CaffeineService
import com.tpk.widgetspro.services.cpu.CpuMonitorService
import com.tpk.widgetspro.services.gif.AnimationService
import com.tpk.widgetspro.services.music.MediaMonitorService
import com.tpk.widgetspro.services.networkusage.BaseNetworkSpeedWidgetService
import com.tpk.widgetspro.services.networkusage.BaseSimDataUsageWidgetService
import com.tpk.widgetspro.services.networkusage.BaseWifiDataUsageWidgetService
import com.tpk.widgetspro.services.sports.SportsWidgetService
import com.tpk.widgetspro.services.sun.SunSyncService
import com.tpk.widgetspro.widgets.analogclock.AnalogClockWidgetProvider_1
import com.tpk.widgetspro.widgets.analogclock.AnalogClockWidgetProvider_2
import com.tpk.widgetspro.widgets.battery.BatteryWidgetProvider
import com.tpk.widgetspro.widgets.bluetooth.BluetoothWidgetProvider
import com.tpk.widgetspro.widgets.caffeine.CaffeineWidget
import com.tpk.widgetspro.widgets.cpu.CpuWidgetProvider
import com.tpk.widgetspro.widgets.gif.GifWidgetProvider
import com.tpk.widgetspro.widgets.music.MusicSimpleWidgetProvider
import com.tpk.widgetspro.widgets.networkusage.*
import com.tpk.widgetspro.widgets.notes.NoteWidgetProvider
import com.tpk.widgetspro.widgets.sports.SportsWidgetProvider
import com.tpk.widgetspro.widgets.sun.SunTrackerWidget

class UpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            stopAllServices(context)
            startRelevantServices(context)
            updateAllWidgetProviders(context)
        }
    }

    private fun stopAllServices(context: Context) {
        val servicesToStop = listOf(
            CpuMonitorService::class.java,
            BatteryMonitorService::class.java,
            AnalogClockUpdateService_1::class.java,
            AnalogClockUpdateService_2::class.java,
            AnimationService::class.java,
            BaseNetworkSpeedWidgetService::class.java,
            BaseWifiDataUsageWidgetService::class.java,
            BaseSimDataUsageWidgetService::class.java,
            SunSyncService::class.java,
            CaffeineService::class.java,
            MediaMonitorService::class.java,
            SportsWidgetService::class.java
        )

        servicesToStop.forEach { serviceClass ->
            try {
                context.stopService(Intent(context, serviceClass))
            } catch (e: Exception) {
            }
        }
    }

    private fun startRelevantServices(context: Context) {
        val appWidgetManager = AppWidgetManager.getInstance(context)

        try {
            if (appWidgetManager.getAppWidgetIds(ComponentName(context, CpuWidgetProvider::class.java)).isNotEmpty()) {
                context.startForegroundService(Intent(context, CpuMonitorService::class.java))
            }
            if (appWidgetManager.getAppWidgetIds(ComponentName(context, BatteryWidgetProvider::class.java)).isNotEmpty()) {
                context.startForegroundService(Intent(context, BatteryMonitorService::class.java))
            }
            if (appWidgetManager.getAppWidgetIds(ComponentName(context, AnalogClockWidgetProvider_1::class.java)).isNotEmpty()) {
                context.startForegroundService(Intent(context, AnalogClockUpdateService_1::class.java))
            }
            if (appWidgetManager.getAppWidgetIds(ComponentName(context, AnalogClockWidgetProvider_2::class.java)).isNotEmpty()) {
                context.startForegroundService(Intent(context, AnalogClockUpdateService_2::class.java))
            }
            if (appWidgetManager.getAppWidgetIds(ComponentName(context, GifWidgetProvider::class.java)).isNotEmpty()) {
                context.startForegroundService(Intent(context, AnimationService::class.java))
            }
            val networkSpeedCircleIds = appWidgetManager.getAppWidgetIds(ComponentName(context, NetworkSpeedWidgetProviderCircle::class.java))
            val networkSpeedPillIds = appWidgetManager.getAppWidgetIds(ComponentName(context, NetworkSpeedWidgetProviderPill::class.java))
            if (networkSpeedCircleIds.isNotEmpty() || networkSpeedPillIds.isNotEmpty()) {
                context.startForegroundService(Intent(context, BaseNetworkSpeedWidgetService::class.java))
            }
            val wifiCircleIds = appWidgetManager.getAppWidgetIds(ComponentName(context, WifiDataUsageWidgetProviderCircle::class.java))
            val wifiPillIds = appWidgetManager.getAppWidgetIds(ComponentName(context, WifiDataUsageWidgetProviderPill::class.java))
            if (wifiCircleIds.isNotEmpty() || wifiPillIds.isNotEmpty()) {
                context.startForegroundService(Intent(context, BaseWifiDataUsageWidgetService::class.java))
            }
            val simCircleIds = appWidgetManager.getAppWidgetIds(ComponentName(context, SimDataUsageWidgetProviderCircle::class.java))
            val simPillIds = appWidgetManager.getAppWidgetIds(ComponentName(context, SimDataUsageWidgetProviderPill::class.java))
            if (simCircleIds.isNotEmpty() || simPillIds.isNotEmpty()) {
                context.startForegroundService(Intent(context, BaseSimDataUsageWidgetService::class.java))
            }
            if (appWidgetManager.getAppWidgetIds(ComponentName(context, SunTrackerWidget::class.java)).isNotEmpty()) {
                context.startForegroundService(Intent(context, SunSyncService::class.java))
            }

            val caffeinePrefs = context.getSharedPreferences("caffeine", Context.MODE_PRIVATE)
            if (caffeinePrefs.getBoolean("active", false)) {
                context.startForegroundService(Intent(context, CaffeineService::class.java))
            }

            if (MusicSimpleWidgetProvider.getWidgetIds(context).isNotEmpty()) {
                context.startForegroundService(Intent(context, MediaMonitorService::class.java))
            }
            if (appWidgetManager.getAppWidgetIds(ComponentName(context, SportsWidgetProvider::class.java)).isNotEmpty()) {
                context.startForegroundService(Intent(context, SportsWidgetService::class.java))
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
            GifWidgetProvider::class.java,
            MusicSimpleWidgetProvider::class.java,
            SportsWidgetProvider::class.java
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
        }
    }
}
