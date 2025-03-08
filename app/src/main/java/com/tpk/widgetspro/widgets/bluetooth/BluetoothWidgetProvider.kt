package com.tpk.widgetspro.widgets.bluetooth

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.RemoteViews
import com.tpk.widgetspro.R
import com.tpk.widgetspro.base.BaseWidgetProvider
import com.tpk.widgetspro.utils.ImageLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class BluetoothWidgetProvider : BaseWidgetProvider() {
    override val layoutId = R.layout.bluetooth_widget_layout
    override val setupText = "Tap to setup Bluetooth"
    override val setupDestination = BluetoothWidgetConfigActivity::class.java

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            val views = RemoteViews(context.packageName, layoutId)
            setupCommonComponents(context, appWidgetId, views)
            appWidgetManager.updateAppWidget(appWidgetId, views)
            updateNormalWidgetView(context, appWidgetManager, appWidgetId)
        }
    }

    override fun updateNormalWidgetView(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
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

    private fun setupCommonComponents(
        context: Context,
        appWidgetId: Int,
        views: RemoteViews
    ) {
        // Common click listeners setup
        val configPI = createConfigPendingIntent(context, appWidgetId)
        val refreshPI = createRefreshPendingIntent(context, appWidgetId)
        val bluetoothPI = createBluetoothPendingIntent(context)

        views.apply {
            setOnClickPendingIntent(R.id.battery_percentage, refreshPI)
            setOnClickPendingIntent(R.id.device_image, bluetoothPI)
            setOnClickPendingIntent(R.id.device_name, configPI)
        }
    }

    private suspend fun getDeviceConnectionState(
        context: Context,
        appWidgetId: Int
    ): Pair<BluetoothDevice?, Boolean> {
        return withContext(Dispatchers.IO) {
            val address = Companion.getSelectedDeviceAddress(context, appWidgetId)
            val device = address?.let { Companion.getBluetoothDeviceByAddress(it) }
            val isConnected = device?.let { Companion.isDeviceConnected(it) } ?: false
            Pair(device, isConnected)
        }
    }

    private suspend fun updateViewsBasedOnState(
        context: Context,
        appWidgetId: Int,
        views: RemoteViews,
        deviceState: Pair<BluetoothDevice?, Boolean>,
        appWidgetManager: AppWidgetManager,
    ) {
        val (device, isConnected) = deviceState
        if (device != null && isConnected) {
            updateConnectedDeviceViews(context, appWidgetId, device, views, appWidgetManager)
        } else {
            updateDisconnectedDeviceViews(views)
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

        val batteryLevel = withContext(Dispatchers.IO) {
            Companion.getBatteryLevel(context, device)
        }
        views.setTextViewText(
            R.id.battery_percentage,
            if (batteryLevel in 0..100) "$batteryLevel%" else "--%"
        )
        setupCommonComponents(context, appWidgetId, views)
        ImageLoader(context, appWidgetManager, appWidgetId, views).loadImageAsync(device)
    }

    private fun updateDisconnectedDeviceViews(views: RemoteViews) {
        views.apply {
            setTextViewText(R.id.device_name, "Device not connected")
            setTextViewText(R.id.battery_percentage, "--%")
            setImageViewResource(R.id.device_image, R.drawable.ic_bluetooth_placeholder)
        }
    }

    companion object {
        private val scope = CoroutineScope(Dispatchers.Main)
        private val BATTERY_SERVICE_UUID = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb")
        private val BATTERY_LEVEL_UUID = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")

        // Original methods preserved exactly as per signature requirements
        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            views: RemoteViews
        ) {
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
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        @SuppressLint("MissingPermission")
        fun getBatteryLevel(context: Context, device: BluetoothDevice): Int {
//            return if (device.name?.lowercase()?.contains("watch") == true) {
//                readBleBattery(context, device)
//            } else {
//                readReflectiveBattery(device)
//            }
            return readReflectiveBattery(device)
        }

//        @SuppressLint("MissingPermission")
//        private fun readBleBattery(context: Context, device: BluetoothDevice): Int {
//            var batteryLevel = -1
//            val latch = CountDownLatch(1)
//
//            val gattCallback = object : BluetoothGattCallback() {
//                override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
//                    if (newState == BluetoothProfile.STATE_CONNECTED) {
//                        gatt.discoverServices()
//                    } else {
//                        gatt.close()
//                        latch.countDown()
//                    }
//                }
//
//                override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
//                    if (status == BluetoothGatt.GATT_SUCCESS) {
//                        gatt.getService(BATTERY_SERVICE_UUID)
//                            ?.getCharacteristic(BATTERY_LEVEL_UUID)
//                            ?.let { gatt.readCharacteristic(it) }
//                            ?: run { latch.countDown() }
//                    }
//                }
//
//                override fun onCharacteristicRead(
//                    gatt: BluetoothGatt,
//                    characteristic: BluetoothGattCharacteristic,
//                    status: Int
//                ) {
//                    batteryLevel = if (status == BluetoothGatt.GATT_SUCCESS) {
//                        characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0)
//                    } else -1
//                    gatt.disconnect()
//                    gatt.close()
//                    latch.countDown()
//                }
//            }
//
//            try {
//                val gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
//                    device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
//                } else {
//                    device.connectGatt(context, false, gattCallback)
//                }
//                latch.await(5, TimeUnit.SECONDS)
//            } catch (e: Exception) {
//                // Handle exception
//            }
//            return batteryLevel.coerceIn(-1..100)
//        }

        private fun readReflectiveBattery(device: BluetoothDevice): Int {
            val methods = arrayOf(
                "getBatteryLevel", "getBattery",
                "getBatteryInfo", "getHeadsetBattery"
            )
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
            val prefs = context.getSharedPreferences("BluetoothWidgetPrefs", Context.MODE_PRIVATE)
            return prefs.getString("device_address_$appWidgetId", null)
        }

        @SuppressLint("MissingPermission")
        fun getBluetoothDeviceByAddress(address: String): BluetoothDevice? {
            return try {
                BluetoothAdapter.getDefaultAdapter()?.getRemoteDevice(address)
            } catch (e: IllegalArgumentException) {
                null
            }
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

        // New helper methods for companion
        private fun getDeviceState(context: Context, appWidgetId: Int): Pair<BluetoothDevice?, Boolean> {
            val address = getSelectedDeviceAddress(context, appWidgetId)
            val device = address?.let { getBluetoothDeviceByAddress(it) }
            return Pair(device, device?.let { isDeviceConnected(it) } ?: false)
        }

        private fun updateConnectedViews(
            context: Context,
            appWidgetId: Int,
            device: BluetoothDevice,
            views: RemoteViews
        ) {
            views.setTextViewText(R.id.device_name, device.name ?: "Unknown Device")
            val batteryLevel = getBatteryLevel(context, device)
            views.setTextViewText(
                R.id.battery_percentage,
                if (batteryLevel in 0..100) "$batteryLevel%" else "--%"
            )
            ImageLoader(context, AppWidgetManager.getInstance(context), appWidgetId, views)
                .loadImageAsync(device)
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
                0,
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