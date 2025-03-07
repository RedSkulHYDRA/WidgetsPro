package com.tpk.widgetspro.widgets.bluetooth

import android.appwidget.AppWidgetManager
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.tpk.widgetspro.R

class BluetoothReceiver : BroadcastReceiver() {
    private val ACTION_BATTERY_LEVEL_CHANGED = "android.bluetooth.device.action.BATTERY_LEVEL_CHANGED"
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == BluetoothDevice.ACTION_ACL_CONNECTED || action == BluetoothDevice.ACTION_ACL_DISCONNECTED || action == ACTION_BATTERY_LEVEL_CHANGED) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(ComponentName(context, BluetoothWidgetProvider::class.java))
            for (appWidgetId in appWidgetIds) {
                val views = RemoteViews(context.packageName, R.layout.bluetooth_widget_layout)
                BluetoothWidgetProvider.updateAppWidget(context, appWidgetManager, appWidgetId, views)
                appWidgetManager.updateAppWidget(appWidgetId, views)
            }
        }
    }
}