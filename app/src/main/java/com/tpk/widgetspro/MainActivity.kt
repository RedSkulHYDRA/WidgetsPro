package com.tpk.widgetspro

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.ComponentName
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.RemoteViews
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputLayout
import com.tpk.widgetspro.api.ImageApiClient
import com.tpk.widgetspro.services.CpuMonitorService
import com.tpk.widgetspro.utils.BitmapCacheManager
import com.tpk.widgetspro.utils.ImageLoader
import com.tpk.widgetspro.utils.NotificationUtils
import com.tpk.widgetspro.utils.PermissionUtils
import com.tpk.widgetspro.widgets.battery.BatteryWidgetProvider
import com.tpk.widgetspro.widgets.bluetooth.BluetoothWidgetProvider
import com.tpk.widgetspro.widgets.caffeine.CaffeineWidget
import com.tpk.widgetspro.widgets.cpu.CpuWidgetProvider
import com.tpk.widgetspro.widgets.sun.SunTrackerWidget
import rikka.shizuku.Shizuku

class MainActivity : AppCompatActivity() {
    private val SHIZUKU_REQUEST_CODE = 1001
    private val REQUEST_BLUETOOTH_PERMISSIONS = 100
    private lateinit var seekBarCpu: SeekBar
    private lateinit var seekBarBattery: SeekBar
    private lateinit var tvCpuValue: TextView
    private lateinit var tvBatteryValue: TextView
    private lateinit var enumInputLayout: TextInputLayout
    private lateinit var chipGroup: ChipGroup
    private val enumOptions = arrayOf(
        "black", "blue", "white", "silver", "transparent", "case",
        "fullproduct", "product", "withcase", "headphones", "headset"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        NotificationUtils.createAppWidgetChannel(this)
        setupUI()
        BitmapCacheManager.clearExpiredCache(this)
    }

    private fun setupUI() {
        seekBarCpu = findViewById(R.id.seekBarCpu)
        seekBarBattery = findViewById(R.id.seekBarBattery)
        enumInputLayout = findViewById(R.id.enum_input_layout)
        chipGroup = findViewById(R.id.chip_group)
        val prefs = getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
        seekBarCpu.progress = prefs.getInt("cpu_interval", 60)
        seekBarBattery.progress = prefs.getInt("battery_interval", 60)
        tvCpuValue = findViewById(R.id.tvCpuValue)
        tvBatteryValue = findViewById(R.id.tvBatteryValue)
        tvCpuValue.text = seekBarCpu.progress.toString()
        tvBatteryValue.text = seekBarBattery.progress.toString()
        setupSeekBarListeners(prefs)
        enumInputLayout.setOnClickListener { showEnumSelectionDialog() }

        findViewById<Button>(R.id.button1).setOnClickListener {
            if (PermissionUtils.hasShizukuAccess() || PermissionUtils.hasRootAccess()) {
                requestWidgetInstallation(CpuWidgetProvider::class.java)
            } else {
                Toast.makeText(this, "Provide Root/Shizuku access", Toast.LENGTH_SHORT).show()
            }
        }
        findViewById<Button>(R.id.button2).setOnClickListener {
            requestWidgetInstallation(BatteryWidgetProvider::class.java)
        }
        findViewById<ImageView>(R.id.imageViewButton).setOnClickListener { checkPermissions() }
        findViewById<Button>(R.id.button3).setOnClickListener {
            requestWidgetInstallation(CaffeineWidget::class.java)
        }
        findViewById<Button>(R.id.button4).setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                requestWidgetInstallation(BluetoothWidgetProvider::class.java)
            } else {
                ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.BLUETOOTH_CONNECT), REQUEST_BLUETOOTH_PERMISSIONS)
            }
        }
        findViewById<Button>(R.id.button5).setOnClickListener {
            requestWidgetInstallation(SunTrackerWidget::class.java)
        }
        findViewById<Button>(R.id.reset_image_button).setOnClickListener {
            val appWidgetIds = getBluetoothWidgetIds(this)
            appWidgetIds.forEach { appWidgetId ->
                val deviceAddress = getSelectedDeviceAddress(this, appWidgetId)
                deviceAddress?.let {
                    val device = getBluetoothDeviceByAddress(it)
                    device?.let {
                        resetImageForDevice(this, it.name, appWidgetId)
                        clearCustomQueryForDevice(this, it.name, appWidgetId)
                        Toast.makeText(this, "Reset image and query for ${it.name}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        findViewById<ImageView>(R.id.update_query_button).setOnClickListener {
            val appWidgetIds = getBluetoothWidgetIds(this)
            appWidgetIds.forEach { appWidgetId ->
                val deviceAddress = getSelectedDeviceAddress(this, appWidgetId)
                deviceAddress?.let {
                    val device = getBluetoothDeviceByAddress(it)
                    device?.let {
                        setCustomQueryForDevice(this, it.name, getSelectedItemsAsString(), appWidgetId)
                    }
                }
            }
        }
    }

    private fun requestWidgetInstallation(providerClass: Class<*>) {
        try {
            val appWidgetManager = AppWidgetManager.getInstance(this)
            val provider = ComponentName(this, providerClass)
            if (appWidgetManager.isRequestPinAppWidgetSupported) {
                val requestCode = System.currentTimeMillis().toInt()
                val intent = Intent().setComponent(provider).setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                val successCallback = PendingIntent.getBroadcast(
                    this,
                    requestCode,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                if (!appWidgetManager.requestPinAppWidget(provider, null, successCallback)) {
                    Toast.makeText(this, R.string.widget_pin_failed, Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, R.string.widget_pin_unsupported, Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to add widget: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkPermissions() {
        when {
            PermissionUtils.hasRootAccess() -> startServiceAndFinish(true)
            Shizuku.pingBinder() -> if (PermissionUtils.hasShizukuAccess()) startServiceAndFinish(false) else Shizuku.requestPermission(SHIZUKU_REQUEST_CODE)
            else -> showPermissionDialog()
        }
    }

    private fun startServiceAndFinish(useRoot: Boolean) {
        ContextCompat.startForegroundService(
            this,
            Intent(this, CpuMonitorService::class.java).apply { putExtra("use_root", useRoot) }
        )
    }

    private fun showPermissionDialog() {
        AlertDialog.Builder(this, R.style.CustomDialogTheme)
            .setTitle(R.string.permission_required_title)
            .setMessage(R.string.permission_required_message)
            .setPositiveButton(R.string.retry) { _, _ -> checkPermissions() }
            .setNegativeButton(R.string.cancel) { _, _ -> null }
            .show().applyDialogTheme()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == SHIZUKU_REQUEST_CODE && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startServiceAndFinish(false)
        } else {
            //Toast.makeText(this, "Shizuku permission denied", Toast.LENGTH_LONG).show()
        }
    }

    private fun setupSeekBarListeners(prefs: SharedPreferences) {
        seekBarCpu.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val adjustedProgress = progress.coerceAtLeast(1)
                    seekBar?.progress = adjustedProgress
                    tvCpuValue.text = adjustedProgress.toString()
                    prefs.edit().putInt("cpu_interval", adjustedProgress).apply()
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        seekBarBattery.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val adjustedProgress = progress.coerceAtLeast(1)
                    seekBar?.progress = adjustedProgress
                    tvBatteryValue.text = adjustedProgress.toString()
                    prefs.edit().putInt("battery_interval", adjustedProgress).apply()
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun resetImageForDevice(context: Context, deviceName: String, appWidgetId: Int) {
        ImageApiClient.clearUrlCache(context, deviceName)
        BitmapCacheManager.clearBitmapCache(context, deviceName)
        updateWidget(context, appWidgetId)
    }

    private fun setCustomQueryForDevice(context: Context, deviceName: String, query: String, appWidgetId: Int) {
        ImageApiClient.setCustomQuery(context, deviceName, query)
        resetImageForDevice(context, deviceName, appWidgetId)
        resetUpdateWidget(context, appWidgetId)
    }

    private fun clearCustomQueryForDevice(context: Context, deviceName: String, appWidgetId: Int) {
        ImageApiClient.clearCustomQuery(context, deviceName)
        resetImageForDevice(context, deviceName, appWidgetId)
    }

    private fun updateWidget(context: Context, appWidgetId: Int) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val views = RemoteViews(context.packageName, R.layout.bluetooth_widget_layout)
        views.setImageViewResource(R.id.device_image, R.drawable.ic_bluetooth_placeholder)
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    private fun resetUpdateWidget(context: Context, appWidgetId: Int) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val views = RemoteViews(context.packageName, R.layout.bluetooth_widget_layout)
        val deviceAddress = getSelectedDeviceAddress(context, appWidgetId)
        deviceAddress?.let {
            val device = getBluetoothDeviceByAddress(it)
            device?.let {
                ImageLoader(context, appWidgetManager, appWidgetId, views).loadImageAsync(it)
            }
        }
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    private fun getBluetoothWidgetIds(context: Context): IntArray {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        return appWidgetManager.getAppWidgetIds(ComponentName(context, BluetoothWidgetProvider::class.java))
    }

    private fun getSelectedDeviceAddress(context: Context, appWidgetId: Int): String? {
        val prefs = context.getSharedPreferences("BluetoothWidgetPrefs", Context.MODE_PRIVATE)
        return prefs.getString("device_address_$appWidgetId", null)
    }

    @SuppressLint("MissingPermission")
    private fun getBluetoothDeviceByAddress(address: String): BluetoothDevice? {
        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter() ?: return null
        return try {
            bluetoothAdapter.getRemoteDevice(address)
        } catch (e: Exception) {
            null
        }
    }

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

    private fun showEnumSelectionDialog() {
        val builder = AlertDialog.Builder(this, R.style.CustomDialogTheme)
        builder.setTitle("Select options")
        builder.setMultiChoiceItems(enumOptions, null) { dialog, which, isChecked -> }
        builder.setPositiveButton("OK") { dialog: DialogInterface, which: Int ->
            val checkedItems = (dialog as AlertDialog).listView.checkedItemPositions
            chipGroup.removeAllViews()
            for (i in 0 until enumOptions.size) {
                if (checkedItems[i]) {
                    addChipToGroup(enumOptions.get(i))
                }
            }
        }
        builder.setNegativeButton("Cancel", null)
        builder.show().applyDialogTheme()
    }

    private fun addChipToGroup(enumText: String) {
        val chip = Chip(this)
        chip.text = enumText
        chip.isCloseIconVisible = true
        chip.setChipBackgroundColorResource(R.color.text_color)
        chip.setTextColor(resources.getColor(R.color.shape_background_color))
        chip.setCloseIconTintResource(R.color.shape_background_color)
        chip.setOnCloseIconClickListener { v: View? -> chipGroup.removeView(chip) }
        chipGroup.addView(chip)
    }

    private fun getSelectedItemsAsString(): String {
        val selectedItems: MutableList<String> = ArrayList()
        for (i in 0 until chipGroup.childCount) {
            val chip = chipGroup.getChildAt(i) as Chip
            selectedItems.add(chip.text.toString())
        }
        return selectedItems.joinToString(" ")
    }

    private fun AlertDialog.applyDialogTheme() {
        val textColor = ContextCompat.getColor(context, R.color.text_color)
        findViewById<TextView>(android.R.id.title)?.setTextColor(textColor)
        findViewById<TextView>(android.R.id.message)?.setTextColor(textColor)
        getButton(DialogInterface.BUTTON_POSITIVE)?.setTextColor(textColor)
        getButton(DialogInterface.BUTTON_NEGATIVE)?.setTextColor(textColor)
    }
}