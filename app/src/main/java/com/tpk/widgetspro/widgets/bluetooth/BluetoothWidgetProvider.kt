package com.tpk.widgetspro.widgets.bluetooth

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.RemoteViews
import com.tpk.widgetspro.R
import com.tpk.widgetspro.base.BaseWidgetProvider
import com.tpk.widgetspro.utils.CommonUtils
import com.tpk.widgetspro.utils.ImageLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BluetoothWidgetProvider : BaseWidgetProvider() {
    override val layoutId = R.layout.bluetooth_widget_layout
    override val setupText = "Tap to setup Bluetooth"
    override val setupDestination = BluetoothWidgetConfigActivity::class.java

    private val scope = CoroutineScope(Dispatchers.Main)

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        appWidgetIds.forEach { appWidgetId ->
            scope.launch {
                val views = RemoteViews(context.packageName, layoutId)
                setupCommonComponents(context, appWidgetId, views)
                appWidgetManager.updateAppWidget(appWidgetId, views)
                updateNormalWidgetView(context, appWidgetManager, appWidgetId)
            }
        }
    }

    override fun updateNormalWidgetView(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        scope.launch {
            val views = RemoteViews(context.packageName, layoutId)
            setupCommonComponents(context, appWidgetId, views)
            val deviceState = getDeviceConnectionState(context, appWidgetId)
            updateViewsBasedOnState(context, appWidgetId, views, deviceState, appWidgetManager)
            withContext(Dispatchers.Main) {
                appWidgetManager.updateAppWidget(appWidgetId, views)
            }
        }
    }

    private fun setupCommonComponents(context: Context, appWidgetId: Int, views: RemoteViews) {
        val configPI = createConfigPendingIntent(context, appWidgetId)
        val refreshPI = createRefreshPendingIntent(context, appWidgetId)
        val bluetoothPI = createBluetoothPendingIntent(context)
        views.apply {
            setOnClickPendingIntent(R.id.battery_percentage, refreshPI)
            setOnClickPendingIntent(R.id.device_image, bluetoothPI)
            setOnClickPendingIntent(R.id.device_name, configPI)
        }
    }

    private suspend fun getDeviceConnectionState(context: Context, appWidgetId: Int): Pair<BluetoothDevice?, Boolean> {
        return withContext(Dispatchers.IO) {
            val address = getSelectedDeviceAddress(context, appWidgetId)
            val device = address?.let { getBluetoothDeviceByAddress(it) }
            val isConnected = device?.let { isDeviceConnected(it) } ?: false
            Pair(device, isConnected)
        }
    }

    private suspend fun updateViewsBasedOnState(
        context: Context,
        appWidgetId: Int,
        views: RemoteViews,
        deviceState: Pair<BluetoothDevice?, Boolean>,
        appWidgetManager: AppWidgetManager
    ) {
        val (device, isConnected) = deviceState
        if (device != null && isConnected) {
            updateConnectedDeviceViews(context, appWidgetId, device, views, appWidgetManager)
        } else {
            updateDisconnectedDeviceViews(context, views)
        }
    }

    private suspend fun updateConnectedDeviceViews(
        context: Context,
        appWidgetId: Int,
        device: BluetoothDevice,
        views: RemoteViews,
        appWidgetManager: AppWidgetManager
    ) {
        views.setTextViewText(R.id.device_name, device.name ?: "Unknown Device")
        val batteryLevel = withContext(Dispatchers.IO) { getBatteryLevel(context, device) }
        views.setTextViewText(R.id.battery_percentage, if (batteryLevel in 0..100) "$batteryLevel%" else "--%")
        views.setViewVisibility(R.id.device_image1, View.GONE)
        ImageLoader(context, appWidgetManager, appWidgetId, views).loadImageAsync(device)
    }

    private fun updateDisconnectedDeviceViews(context: Context,views: RemoteViews) {
        views.apply {
            setTextViewText(R.id.device_name, "Device not connected")
            setTextViewText(R.id.battery_percentage, "--%")
            setImageViewResource(R.id.device_image1, R.drawable.ic_bluetooth_placeholder)
            setInt(R.id.device_image1, "setColorFilter", CommonUtils.getAccentColor(context))
        }
    }

    override fun onAppWidgetOptionsChanged(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        updateNormalWidgetView(context, appWidgetManager, appWidgetId)
    }

    companion object {
        fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, views: RemoteViews) {
            val deviceState = getDeviceState(context, appWidgetId)
            if (deviceState.first != null && deviceState.second) {
                updateConnectedViews(context, appWidgetId, deviceState.first!!, views)
            } else {
                views.apply {
                    setTextViewText(R.id.device_name, "No device selected")
                    setImageViewResource(R.id.device_image, R.drawable.ic_bluetooth_placeholder)
                    setTextViewText(R.id.battery_percentage, "--%")
                }
            }
            setupCommonComponents(context, appWidgetId, views)
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        @SuppressLint("MissingPermission")
        fun getBatteryLevel(context: Context, device: BluetoothDevice): Int {
            return readReflectiveBattery(device)
        }

        private fun readReflectiveBattery(device: BluetoothDevice): Int {
            val methods = arrayOf("getBatteryLevel", "getBattery", "getBatteryInfo", "getHeadsetBattery")
            for (methodName in methods) {
                try {
                    val method = device.javaClass.getMethod(methodName)
                    return (method.invoke(device) as? Int)?.takeIf { it in 0..100 } ?: -1
                } catch (e: Exception) {
                    continue
                }
            }
            return -1
        }

        @SuppressLint("MissingPermission")
        fun getSelectedDeviceAddress(context: Context, appWidgetId: Int): String? {
            return context.getSharedPreferences("BluetoothWidgetPrefs", Context.MODE_PRIVATE)
                .getString("device_address_$appWidgetId", null)
        }

        @SuppressLint("MissingPermission")
        fun getBluetoothDeviceByAddress(address: String): BluetoothDevice? {
            return BluetoothAdapter.getDefaultAdapter()?.getRemoteDevice(address)
        }

        @SuppressLint("MissingPermission")
        fun isDeviceConnected(device: BluetoothDevice): Boolean {
            return try {
                val method = device.javaClass.getMethod("isConnected")
                method.invoke(device) as? Boolean ?: false
            } catch (e: Exception) {
                false
            }
        }

        private fun getDeviceState(context: Context, appWidgetId: Int): Pair<BluetoothDevice?, Boolean> {
            val address = getSelectedDeviceAddress(context, appWidgetId)
            val device = address?.let { getBluetoothDeviceByAddress(it) }
            return Pair(device, device?.let { isDeviceConnected(it) } ?: false)
        }

        private fun updateConnectedViews(context: Context, appWidgetId: Int, device: BluetoothDevice, views: RemoteViews) {
            views.setTextViewText(R.id.device_name, device.name ?: "Unknown Device")
            val batteryLevel = getBatteryLevel(context, device)
            views.setTextViewText(R.id.battery_percentage, if (batteryLevel in 0..100) "$batteryLevel%" else "--%")
            views.setViewVisibility(R.id.device_image1, View.GONE)
            ImageLoader(context, AppWidgetManager.getInstance(context), appWidgetId, views).loadImageAsync(device)
        }

        private fun setupCommonComponents(context: Context, appWidgetId: Int, views: RemoteViews) {
            val configPI = createConfigPendingIntent(context, appWidgetId)
            val refreshPI = createRefreshPendingIntent(context, appWidgetId)
            val bluetoothPI = createBluetoothPendingIntent(context)
            views.apply {
                setOnClickPendingIntent(R.id.battery_percentage, refreshPI)
                setOnClickPendingIntent(R.id.device_image, bluetoothPI)
                setOnClickPendingIntent(R.id.device_name, configPI)
                setInt(R.id.device_image, "setColorFilter", CommonUtils.getAccentColor(context))
            }
        }

        private fun createConfigPendingIntent(context: Context, appWidgetId: Int): PendingIntent {
            return PendingIntent.getActivity(
                context,
                appWidgetId,
                Intent(context, BluetoothWidgetConfigActivity::class.java).apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        private fun createRefreshPendingIntent(context: Context, appWidgetId: Int): PendingIntent {
            return PendingIntent.getBroadcast(
                context,
                appWidgetId,
                Intent(context, BluetoothWidgetProvider::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(appWidgetId))
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        private fun createBluetoothPendingIntent(context: Context): PendingIntent {
            return PendingIntent.getActivity(
                context,
                0,
                Intent(Settings.ACTION_BLUETOOTH_SETTINGS),
                PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}