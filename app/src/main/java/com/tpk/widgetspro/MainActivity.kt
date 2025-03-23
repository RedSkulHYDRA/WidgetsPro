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
import android.location.Geocoder
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
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
import com.tpk.widgetspro.services.SunSyncService
import com.tpk.widgetspro.utils.BitmapCacheManager
import com.tpk.widgetspro.utils.ImageLoader
import com.tpk.widgetspro.utils.NotificationUtils
import com.tpk.widgetspro.utils.PermissionUtils
import com.tpk.widgetspro.widgets.battery.BatteryWidgetProvider
import com.tpk.widgetspro.widgets.bluetooth.BluetoothWidgetProvider
import com.tpk.widgetspro.widgets.caffeine.CaffeineWidget
import com.tpk.widgetspro.widgets.cpu.CpuWidgetProvider
import com.tpk.widgetspro.widgets.datausage.DataUsageWidgetProvider
import com.tpk.widgetspro.widgets.datausage.SimDataUsageWidgetProvider
import com.tpk.widgetspro.widgets.speedtest.SpeedWidgetProvider
import com.tpk.widgetspro.widgets.sun.SunTrackerWidget
import rikka.shizuku.Shizuku
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private val SHIZUKU_REQUEST_CODE = 1001
    private val REQUEST_BLUETOOTH_PERMISSIONS = 100
    private val REQUEST_IGNORE_BATTERY_OPTIMIZATIONS = 1
    private lateinit var seekBarCpu: SeekBar
    private lateinit var seekBarBattery: SeekBar
    private lateinit var tvCpuValue: TextView
    private lateinit var tvBatteryValue: TextView
    private lateinit var enumInputLayout: TextInputLayout
    private lateinit var chipGroup: ChipGroup
    private lateinit var locationAutoComplete: AutoCompleteTextView
    private lateinit var setLocationButton: Button
    private lateinit var suggestionsAdapter: ArrayAdapter<String>
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
        locationAutoComplete = findViewById(R.id.location_auto_complete)
        setLocationButton = findViewById(R.id.set_location_button)
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
        findViewById<Button>(R.id.button6).setOnClickListener {
            requestWidgetInstallation(SpeedWidgetProvider::class.java)
        }
        findViewById<Button>(R.id.button7).setOnClickListener {
            requestWidgetInstallation(DataUsageWidgetProvider::class.java)
        }
        findViewById<Button>(R.id.button8).setOnClickListener {
            requestWidgetInstallation(SimDataUsageWidgetProvider::class.java)
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
                        setCustomQueryForDevice(this, it.name, getSelectedItemsAsString(), appWidgetId)
                    }
                }
            }
        }
        suggestionsAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line)
        locationAutoComplete.setAdapter(suggestionsAdapter)
        locationAutoComplete.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                // No action needed
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (s != null && s.length > 2) { // Fetch suggestions after 3 characters
                    fetchLocationSuggestions(s.toString())
                }
            }

            override fun afterTextChanged(s: Editable?) {
                // No action needed
            }
        })

        // Handle button click
        setLocationButton.setOnClickListener {
            val location = locationAutoComplete.text.toString().trim()
            if (location.isNotEmpty()) {
                getCoordinatesFromLocation(location)
            } else {
                Toast.makeText(this, "Please enter a location", Toast.LENGTH_SHORT).show()
            }
        }
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {requestIgnoreBatteryOptimizations()}
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
            .setPositiveButton("Open Shizuku") { _, _ ->
                if (isShizukuInstalled()) {
                    checkPermissions()
                } else {
                    startActivity(Intent(Intent.ACTION_VIEW).apply {
                        data = Uri.parse("https://shizuku.rikka.app/")
                    })
                }
                finish()
            }
            .setNegativeButton("Exit") { _, _ -> null }
            .setCancelable(false)
            .show().applyDialogTheme()
    }

    private fun isShizukuInstalled() = try {
        packageManager.getPackageInfo("moe.shizuku.privileged.api", 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == SHIZUKU_REQUEST_CODE && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startServiceAndFinish(false)
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

    private fun showEnumSelectionDialog() {
        val builder = AlertDialog.Builder(this, R.style.CustomDialogTheme)
        builder.setTitle("Select options")
        builder.setMultiChoiceItems(enumOptions, null) { _, _, _ -> }
        builder.setPositiveButton("OK") { dialog: DialogInterface, _: Int ->
            val checkedItems = (dialog as AlertDialog).listView.checkedItemPositions
            chipGroup.removeAllViews()
            for (i in 0 until enumOptions.size) {
                if (checkedItems[i]) {
                    addChipToGroup(enumOptions[i])
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
        chip.setOnCloseIconClickListener { chipGroup.removeView(chip) }
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

    private fun getCoordinatesFromLocation(location: String) {
        val geocoder = Geocoder(this, Locale.getDefault())
        try {
            val addresses = geocoder.getFromLocationName(location, 1)
            if (!addresses.isNullOrEmpty()) {
                val address = addresses[0]
                val latitude = address.latitude
                val longitude = address.longitude
                saveLocationToPreferences(latitude, longitude)
                Toast.makeText(this, "Location set to $location", Toast.LENGTH_SHORT).show()
                // Trigger data refresh
                SunSyncService.start(this)
            } else {
                Toast.makeText(this, "Location not found", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error finding location: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveLocationToPreferences(latitude: Double, longitude: Double) {
        val prefs = getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
        with(prefs.edit()) {
            putString("latitude", latitude.toString())
            putString("longitude", longitude.toString())
            apply()
        }
    }

    private fun fetchLocationSuggestions(query: String) {
        val geocoder = Geocoder(this, Locale.getDefault())
        try {
            val addresses = geocoder.getFromLocationName(query, 5) // Limit to 5 suggestions
            val suggestions = addresses?.map { it.getAddressLine(0) } ?: emptyList()
            suggestionsAdapter.clear()
            suggestionsAdapter.addAll(suggestions)
            suggestionsAdapter.notifyDataSetChanged()
            locationAutoComplete.showDropDown() // Ensure dropdown is shown
        } catch (e: Exception) {
            Toast.makeText(this, "Error fetching suggestions", Toast.LENGTH_SHORT).show()
        }
    }
    private fun requestIgnoreBatteryOptimizations() {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:$packageName")
        }
        startActivityForResult(intent, REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
    }
}