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
import android.content.pm.PackageManager
import android.location.Geocoder
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
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
import com.tpk.widgetspro.utils.CommonUtils
import com.tpk.widgetspro.utils.ImageLoader
import com.tpk.widgetspro.utils.NotificationUtils
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

    private val enumOptions = arrayOf("black", "blue", "white", "silver", "transparent", "case", "fullproduct", "product", "withcase", "headphones", "headset")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyTheme()
        setContentView(R.layout.activity_main)
        NotificationUtils.createChannel(this)
        setupUI()
        checkBatteryOptimizations()
        BitmapCacheManager.clearExpiredCache(this)
    }

    private fun applyTheme() {
        val prefs = getSharedPreferences("theme_prefs", MODE_PRIVATE)
        val isDarkTheme = prefs.getBoolean("dark_theme", false)
        val isRedAccent = prefs.getBoolean("red_accent", false)
        setTheme(when {
            isDarkTheme && isRedAccent -> R.style.Theme_WidgetsPro_Red_Dark
            isDarkTheme -> R.style.Theme_WidgetsPro
            isRedAccent -> R.style.Theme_WidgetsPro_Red_Light
            else -> R.style.Theme_WidgetsPro
        })
    }

    private fun setupUI() {
        seekBarCpu = findViewById(R.id.seekBarCpu)
        seekBarBattery = findViewById(R.id.seekBarBattery)
        tvCpuValue = findViewById(R.id.tvCpuValue)
        tvBatteryValue = findViewById(R.id.tvBatteryValue)
        enumInputLayout = findViewById(R.id.enum_input_layout)
        chipGroup = findViewById(R.id.chip_group)
        locationAutoComplete = findViewById(R.id.location_auto_complete)
        setLocationButton = findViewById(R.id.set_location_button)

        val prefs = getSharedPreferences("widget_prefs", MODE_PRIVATE)
        seekBarCpu.progress = prefs.getInt("cpu_interval", 60)
        seekBarBattery.progress = prefs.getInt("battery_interval", 60)
        tvCpuValue.text = seekBarCpu.progress.toString()
        tvBatteryValue.text = seekBarBattery.progress.toString()

        setupSeekBarListeners(prefs)
        enumInputLayout.setOnClickListener { showEnumSelectionDialog() }

        findViewById<TextView>(R.id.title_main).setTextColor(CommonUtils.getAccentColor(this))
        findViewById<Button>(R.id.button1).setOnClickListener {
            if (hasCpuPermissions()) requestWidgetInstallation(CpuWidgetProvider::class.java) else Toast.makeText(this, "Provide Root/Shizuku access", Toast.LENGTH_SHORT).show()
        }
        findViewById<Button>(R.id.button2).setOnClickListener { requestWidgetInstallation(BatteryWidgetProvider::class.java) }
        findViewById<ImageView>(R.id.imageViewButton).setOnClickListener { checkPermissions() }
        findViewById<Button>(R.id.button3).setOnClickListener { requestWidgetInstallation(CaffeineWidget::class.java) }
        findViewById<Button>(R.id.button4).setOnClickListener {
            if (hasBluetoothPermission()) requestWidgetInstallation(BluetoothWidgetProvider::class.java)
            else ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.BLUETOOTH_CONNECT), REQUEST_BLUETOOTH_PERMISSIONS)
        }
        findViewById<Button>(R.id.button5).setOnClickListener { requestWidgetInstallation(SunTrackerWidget::class.java) }
        findViewById<Button>(R.id.button6).setOnClickListener { requestWidgetInstallation(SpeedWidgetProvider::class.java) }
        findViewById<Button>(R.id.button7).setOnClickListener { requestWidgetInstallation(DataUsageWidgetProvider::class.java) }
        findViewById<Button>(R.id.button8).setOnClickListener { requestWidgetInstallation(SimDataUsageWidgetProvider::class.java) }
        findViewById<Button>(R.id.button9).setOnClickListener { switchTheme() }
        findViewById<Button>(R.id.reset_image_button).setOnClickListener {resetBluetoothImage()}
        suggestionsAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line)
        locationAutoComplete.setAdapter(suggestionsAdapter)
        locationAutoComplete.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (s != null && s.length > 2) {
                    fetchLocationSuggestions(s.toString())
                }
            }

            override fun afterTextChanged(s: Editable?) {
            }
        })
        setLocationButton.setOnClickListener {
            val location = locationAutoComplete.text.toString().trim()
            if (location.isNotEmpty()) {
                getCoordinatesFromLocation(location)
            } else {
                Toast.makeText(this, "Please enter a location", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun resetBluetoothImage() {
        val appWidgetIds = getBluetoothWidgetIds(this)
        appWidgetIds.forEach { appWidgetId ->
            val deviceAddress = getSelectedDeviceAddress(this, appWidgetId)
            deviceAddress?.let {
                val device = getBluetoothDeviceByAddress(it)
                device?.let {
                    resetImageForDevice(this, it.name, appWidgetId)
                    clearCustomQueryForDevice(this, it.name, appWidgetId)
                    setCustomQueryForDevice(this, it.name, getSelectedItemsAsString(), appWidgetId)
                    Toast.makeText(this, "Reset image and query for ${it.name}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun requestWidgetInstallation(providerClass: Class<*>) {
        val appWidgetManager = AppWidgetManager.getInstance(this)
        val provider = ComponentName(this, providerClass)
        if (appWidgetManager.isRequestPinAppWidgetSupported) {
            val requestCode = System.currentTimeMillis().toInt()
            val successCallback = PendingIntent.getBroadcast(
                this, requestCode,
                Intent().setComponent(provider).setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            if (!appWidgetManager.requestPinAppWidget(provider, null, successCallback)) {
                Toast.makeText(this, R.string.widget_pin_failed, Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, R.string.widget_pin_unsupported, Toast.LENGTH_SHORT).show()
        }
    }

    private fun hasCpuPermissions(): Boolean = try {
        Runtime.getRuntime().exec(arrayOf("su", "-c", "cat /proc/version")).inputStream.bufferedReader().use { it.readLine() } != null
    } catch (e: Exception) {
        Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }

    private fun hasBluetoothPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    private fun checkPermissions() {
        when {
            hasCpuPermissions() -> startServiceAndFinish(true)
            Shizuku.pingBinder() -> if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) startServiceAndFinish(false) else Shizuku.requestPermission(SHIZUKU_REQUEST_CODE)
            else -> showPermissionDialog()
        }
    }

    private fun startServiceAndFinish(useRoot: Boolean) {
        startForegroundService(Intent(this, CpuMonitorService::class.java).putExtra("use_root", useRoot))
    }

    private fun showPermissionDialog() {
        AlertDialog.Builder(this, R.style.CustomDialogTheme)
            .setTitle(R.string.permission_required_title)
            .setMessage(R.string.permission_required_message)
            .setPositiveButton("Open Shizuku") { _, _ ->
                if (isShizukuInstalled()) checkPermissions() else startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/")))
                finish()
            }
            .setNegativeButton("Exit") { _, _ -> finish() }
            .setCancelable(false)
            .create()
            .show()
    }

    private fun isShizukuInstalled(): Boolean = try {
        packageManager.getPackageInfo("rikka.shizuku", 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            SHIZUKU_REQUEST_CODE -> if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) startServiceAndFinish(false) else Toast.makeText(this, "Shizuku permission denied", Toast.LENGTH_SHORT).show()
            REQUEST_BLUETOOTH_PERMISSIONS -> if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) requestWidgetInstallation(BluetoothWidgetProvider::class.java) else Toast.makeText(this, "Bluetooth permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupSeekBarListeners(prefs: android.content.SharedPreferences) {
        seekBarCpu.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                tvCpuValue.text = progress.toString()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                prefs.edit().putInt("cpu_interval", seekBar?.progress ?: 60).apply()
            }
        })
        seekBarBattery.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                tvBatteryValue.text = progress.toString()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                prefs.edit().putInt("battery_interval", seekBar?.progress ?: 60).apply()
            }
        })
    }

    private fun switchTheme() {
        val prefs = getSharedPreferences("theme_prefs", MODE_PRIVATE)
        val isDarkTheme = prefs.getBoolean("dark_theme", false)
        val isRedAccent = prefs.getBoolean("red_accent", false)
        prefs.edit().apply {
            putBoolean("dark_theme", !isDarkTheme)
            putBoolean("red_accent", !isRedAccent)
            apply()
        }
        val appWidgetManager = AppWidgetManager.getInstance(this)
        val providers = arrayOf(
            CpuWidgetProvider::class.java,
            BatteryWidgetProvider::class.java,
            BluetoothWidgetProvider::class.java,
            CaffeineWidget::class.java,
            SunTrackerWidget::class.java,
            SpeedWidgetProvider::class.java,
            DataUsageWidgetProvider::class.java,
            SimDataUsageWidgetProvider::class.java
        )

        providers.forEach { provider ->
            val componentName = ComponentName(this, provider)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            if (appWidgetIds.isNotEmpty()) {
                val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE).apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds)
                    component = componentName
                }
                sendBroadcast(intent)
            }
        }
        recreate()
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
            val addresses = geocoder.getFromLocationName(query, 5)
            val suggestions = addresses?.map { it.getAddressLine(0) } ?: emptyList()
            suggestionsAdapter.clear()
            suggestionsAdapter.addAll(suggestions)
            suggestionsAdapter.notifyDataSetChanged()
            locationAutoComplete.showDropDown()
        } catch (e: Exception) {
            Toast.makeText(this, "Error fetching suggestions", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkBatteryOptimizations() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivityForResult(intent, REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_IGNORE_BATTERY_OPTIMIZATIONS) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (powerManager.isIgnoringBatteryOptimizations(packageName)) {
                Toast.makeText(this, "Battery optimizations disabled", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Please disable battery optimizations for better performance", Toast.LENGTH_LONG).show()
            }
        }
    }
}