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
import android.util.Log
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
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
    override val setupDestination = PermissionRequestActivity::class.java

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            val views = RemoteViews(context.packageName, layoutId)
            if (ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.BLUETOOTH_CONNECT
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                val intent = Intent(context, PermissionRequestActivity::class.java).apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                }
                val pendingIntent = PendingIntent.getActivity(
                    context,
                    appWidgetId,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.device_image, pendingIntent)
            } else {
                updateNormalWidgetView(context, appWidgetManager, appWidgetId)
            }
            val refreshIntent = Intent(context, BluetoothWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds)
            }
            val refreshPendingIntent = PendingIntent.getBroadcast(
                context,
                0,
                refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.battery_percentage, refreshPendingIntent)
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    override fun updateNormalWidgetView(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        val views = RemoteViews(context.packageName, layoutId)
        scope.launch {
            val device = withContext(Dispatchers.IO) { getConnectedBluetoothDevice(context) }
            if (device != null) {
                views.setTextViewText(R.id.device_name, device.name)
                val retrievedLevel = withContext(Dispatchers.IO) { getBatteryLevel(context,device) }
                views.setTextViewText(
                    R.id.battery_percentage,
                    if (retrievedLevel in 0..100) "$retrievedLevel%" else "--%"
                )
                val bluetoothIntent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                val pendingIntent = PendingIntent.getActivity(
                    context,
                    0,
                    bluetoothIntent,
                    PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.device_name, pendingIntent)
                ImageLoader(context, appWidgetManager, appWidgetId, views).loadImageAsync(device)
            } else {
                views.setTextViewText(R.id.device_name, "")
                views.setImageViewResource(R.id.device_image, R.drawable.ic_bluetooth_placeholder)
                views.setTextViewText(R.id.battery_percentage, "")
            }
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    companion object {
        private val scope = CoroutineScope(Dispatchers.Main)
        private val BATTERY_SERVICE_UUID = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb")
        private val BATTERY_LEVEL_UUID = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")

        @SuppressLint("MissingPermission")
        private fun getConnectedBluetoothDevice(context: Context): BluetoothDevice? {
            val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter() ?: return null
            val pairedDevices = bluetoothAdapter.bondedDevices ?: return null
            for (device in pairedDevices) {
                try {
                    val method = device.javaClass.getMethod("isConnected")
                    val isConnected = method.invoke(device) as Boolean
                    if (isConnected) {
                        return device
                    }
                } catch (e: Exception) {
                    Log.e("BluetoothWidget", "Error checking connection status", e)
                }
            }
            return null
        }

        private fun getBatteryLevel(context: Context, device: BluetoothDevice): Int {
            if (device.name.lowercase().contains("watch")) {

                var batteryLevel = -1
                val latch = CountDownLatch(1)
                val gattCallback = object : BluetoothGattCallback() {
                    override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                        if (newState == BluetoothProfile.STATE_CONNECTED) {
                            gatt.discoverServices()
                        } else {
                            gatt.close()
                            latch.countDown()
                        }
                    }

                    override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                        if (status == BluetoothGatt.GATT_SUCCESS) {
                            gatt.getService(BATTERY_SERVICE_UUID)?.let { service ->
                                service.getCharacteristic(BATTERY_LEVEL_UUID)?.let { characteristic ->
                                    gatt.readCharacteristic(characteristic)
                                    return
                                }
                            }
                        }
                        gatt.close()
                        latch.countDown()
                    }

                    override fun onCharacteristicRead(
                        gatt: BluetoothGatt,
                        characteristic: BluetoothGattCharacteristic,
                        status: Int
                    ) {
                        batteryLevel = if (status == BluetoothGatt.GATT_SUCCESS) {
                            characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0)
                        } else -1
                        gatt.disconnect()
                        gatt.close()
                        latch.countDown()
                    }
                }

                try {
                    val gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
                    } else {
                        device.connectGatt(context, false, gattCallback)
                    }
                    latch.await(5, TimeUnit.SECONDS)
                } catch (e: Exception) {
                    Log.e("BluetoothWidget", "BLE battery read error", e)
                }
                return batteryLevel.coerceIn(-1..100)


            } else {
                val methods =
                    arrayOf("getBatteryLevel", "getBattery", "getBatteryInfo", "getHeadsetBattery")
                for (methodName in methods) {
                    try {
                        Thread.sleep(2000)
                        val method = device.javaClass.getMethod(methodName)
                        val level = method.invoke(device) as? Int ?: -1
                        if (level in 0..100) return level
                    } catch (e: Exception) {
                        continue
                    }
                }
            }
            return -1
        }

        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            views: RemoteViews
        ) {
            val device = getConnectedBluetoothDevice(context) // Hypothetical method
            if (device != null) {
                views.setTextViewText(R.id.device_name, device.name)
                val batteryLevel = getBatteryLevel(context, device) // Your custom method
                val batteryText = if (batteryLevel != -1) "$batteryLevel%" else "--%"
                val loader = ImageLoader(context, appWidgetManager, appWidgetId, views)
                loader.loadImageAsync(device)
                views.setTextViewText(R.id.battery_percentage, batteryText)
            } else {
                views.setTextViewText(R.id.device_name, "No device connected")
                views.setImageViewResource(R.id.device_image, R.drawable.ic_bluetooth_placeholder)
                views.setTextViewText(R.id.battery_percentage, "--%")
            }
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}