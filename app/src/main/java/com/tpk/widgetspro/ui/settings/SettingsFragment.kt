package com.tpk.widgetspro.ui.settings

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.location.Geocoder
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputLayout
import com.tpk.widgetspro.MainActivity
import com.tpk.widgetspro.R
import com.tpk.widgetspro.api.ImageApiClient
import com.tpk.widgetspro.services.gif.AnimationService
import com.tpk.widgetspro.services.sun.SunSyncService
import com.tpk.widgetspro.utils.BitmapCacheManager
import com.tpk.widgetspro.utils.CommonUtils
import com.tpk.widgetspro.utils.ImageLoader
import com.tpk.widgetspro.widgets.bluetooth.BluetoothWidgetProvider
import com.tpk.widgetspro.widgets.networkusage.BaseSimDataUsageWidgetProvider
import com.tpk.widgetspro.widgets.networkusage.SimDataUsageWidgetProviderCircle
import com.tpk.widgetspro.widgets.networkusage.SimDataUsageWidgetProviderPill
import com.tpk.widgetspro.widgets.networkusage.BaseWifiDataUsageWidgetProvider
import com.tpk.widgetspro.widgets.networkusage.WifiDataUsageWidgetProviderCircle
import com.tpk.widgetspro.widgets.networkusage.WifiDataUsageWidgetProviderPill
import java.text.SimpleDateFormat
import java.util.*

class SettingsFragment : Fragment() {

    private lateinit var seekBarCpu: SeekBar
    private lateinit var seekBarBattery: SeekBar
    private lateinit var seekBarWifi: SeekBar
    private lateinit var seekBarSim: SeekBar
    private lateinit var seekBarNetworkSpeed: SeekBar
    private lateinit var tvCpuValue: TextView
    private lateinit var tvBatteryValue: TextView
    private lateinit var tvWifiValue: TextView
    private lateinit var tvSimValue: TextView
    private lateinit var tvNetworkSpeedValue: TextView
    private lateinit var enumInputLayout: TextInputLayout
    private lateinit var chipGroup: ChipGroup
    private lateinit var locationAutoComplete: AutoCompleteTextView
    private lateinit var setLocationButton: Button
    private lateinit var suggestionsAdapter: ArrayAdapter<String>
    private lateinit var radioGroupResetMode: RadioGroup
    private lateinit var btnResetNow: Button
    private lateinit var tvNextReset: TextView
    private var pendingAppWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID
    private val enumOptions = arrayOf(
        "black", "blue", "white", "silver", "transparent",
        "case", "fullproduct", "product", "withcase",
        "headphones", "headset"
    )

    private val selectFileLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let {
            if (pendingAppWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                val prefs = requireContext().getSharedPreferences("gif_widget_prefs", Context.MODE_PRIVATE)
                prefs.edit().putString("file_uri_$pendingAppWidgetId", it.toString()).apply()
                requireContext().contentResolver.takePersistableUriPermission(
                    it, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                val intent = Intent(requireContext(), AnimationService::class.java).apply {
                    putExtra("action", "UPDATE_FILE")
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingAppWidgetId)
                    putExtra("file_uri", it.toString())
                }
                requireContext().startService(intent)
                Toast.makeText(requireContext(), R.string.gif_selected_message, Toast.LENGTH_SHORT).show()
                pendingAppWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    @SuppressLint("SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        seekBarCpu = view.findViewById(R.id.seekBarCpu)
        seekBarBattery = view.findViewById(R.id.seekBarBattery)
        seekBarWifi = view.findViewById(R.id.seekBarWifi)
        seekBarSim = view.findViewById(R.id.seekBarSim)
        seekBarNetworkSpeed = view.findViewById(R.id.seekBarNetworkSpeed)
        tvCpuValue = view.findViewById(R.id.tvCpuValue)
        tvBatteryValue = view.findViewById(R.id.tvBatteryValue)
        tvWifiValue = view.findViewById(R.id.tvWifiValue)
        tvSimValue = view.findViewById(R.id.tvSimValue)
        tvNetworkSpeedValue = view.findViewById(R.id.tvNetworkSpeedValue)
        enumInputLayout = view.findViewById(R.id.enum_input_layout)
        chipGroup = view.findViewById(R.id.chip_group)
        locationAutoComplete = view.findViewById(R.id.location_auto_complete)
        setLocationButton = view.findViewById(R.id.set_location_button)
        radioGroupResetMode = view.findViewById(R.id.radio_group_reset_mode)
        btnResetNow = view.findViewById(R.id.btn_reset_now)
        tvNextReset = view.findViewById(R.id.tv_next_reset)

        val prefs = requireContext().getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
        seekBarCpu.progress = prefs.getInt("cpu_interval", 60)
        seekBarBattery.progress = prefs.getInt("battery_interval", 60)
        seekBarWifi.progress = prefs.getInt("wifi_data_usage_interval", 60)
        seekBarSim.progress = prefs.getInt("sim_data_usage_interval", 60)
        seekBarNetworkSpeed.progress = prefs.getInt("network_speed_interval", 60)

        tvCpuValue.text = seekBarCpu.progress.toString()
        tvBatteryValue.text = seekBarBattery.progress.toString()
        tvWifiValue.text = seekBarWifi.progress.toString()
        tvSimValue.text = seekBarSim.progress.toString()
        tvNetworkSpeedValue.text = seekBarNetworkSpeed.progress.toString()

        val resetMode = prefs.getString("data_usage_reset_mode", "daily") ?: "daily"
        when (resetMode) {
            "daily" -> {
                radioGroupResetMode.check(R.id.radio_daily_reset)
                btnResetNow.visibility = View.GONE
                updateNextResetText("daily")
            }
            "manual" -> {
                radioGroupResetMode.check(R.id.radio_manual_reset)
                btnResetNow.visibility = View.VISIBLE
                updateNextResetText("manual")
            }
        }

        setupSeekBarListeners(prefs)
        enumInputLayout.setOnClickListener { showEnumSelectionDialog() }

        suggestionsAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line)
        locationAutoComplete.setAdapter(suggestionsAdapter)
        locationAutoComplete.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (s != null && s.length > 2) {
                    fetchLocationSuggestions(s.toString())
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })
        setLocationButton.setOnClickListener {
            val location = locationAutoComplete.text.toString().trim()
            if (location.isNotEmpty()) {
                getCoordinatesFromLocation(location)
            } else {
                Toast.makeText(requireContext(), R.string.enter_location_prompt, Toast.LENGTH_SHORT).show()
            }
        }

        view.findViewById<Button>(R.id.reset_image_button).setOnClickListener {
            resetBluetoothImage()
        }
        val switchThemes = view.findViewById<com.google.android.material.materialswitch.MaterialSwitch>(R.id.button9)
        switchThemes.isChecked = prefs.getBoolean("theme_enabled", false)
        switchThemes.setOnCheckedChangeListener {_, isChecked ->
            prefs.edit().putBoolean("theme_enabled", isChecked).apply()
            (activity as? MainActivity)?.switchTheme()
        }

        view.findViewById<TextView>(R.id.title_main)?.setTextColor(CommonUtils.getAccentColor(requireContext()))

        radioGroupResetMode.setOnCheckedChangeListener { _, checkedId ->
            with(prefs.edit()) {
                when (checkedId) {
                    R.id.radio_daily_reset -> {
                        putString("data_usage_reset_mode", "daily")
                        btnResetNow.visibility = View.GONE
                        updateNextResetText("daily")
                    }
                    R.id.radio_manual_reset -> {
                        putString("data_usage_reset_mode", "manual")
                        btnResetNow.visibility = View.VISIBLE
                        updateNextResetText("manual")
                    }
                }
                apply()
            }
        }

        btnResetNow.setOnClickListener {
            resetDataUsageNow(prefs)
            updateNextResetText("manual")
            Toast.makeText(requireContext(), R.string.data_usage_reset_message, Toast.LENGTH_SHORT).show()
        }

        view.findViewById<Button>(R.id.select_file_button).setOnClickListener {
            showWidgetSelectionDialog()
        }

        view.findViewById<Button>(R.id.sync_gif_widgets_button).setOnClickListener {
            showSyncWidgetSelectionDialog()
        }

        val switchClockFps = view.findViewById<com.google.android.material.materialswitch.MaterialSwitch>(R.id.switch_clock_fps)
        switchClockFps.isChecked = prefs.getBoolean("clock_60fps_enabled", false)
        switchClockFps.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("clock_60fps_enabled", isChecked).apply()
            if (isChecked) {
                Toast.makeText(requireContext(), R.string.warning_battery_drain, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showWidgetSelectionDialog() {
        val prefss = requireContext().getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)
        val isDarkTheme = prefss.getBoolean("dark_theme", false)
        val isRedAccent = prefss.getBoolean("red_accent", false)

        val dialogThemeStyle = when {
            isDarkTheme && isRedAccent -> R.style.CustomDialogTheme1
            isDarkTheme && !isRedAccent -> R.style.CustomDialogTheme
            !isDarkTheme && isRedAccent -> R.style.CustomDialogTheme1
            else -> R.style.CustomDialogTheme
        }
        val appWidgetManager = AppWidgetManager.getInstance(requireContext())
        val widgetIds = appWidgetManager.getAppWidgetIds(
            ComponentName(requireContext(), com.tpk.widgetspro.widgets.gif.GifWidgetProvider::class.java)
        )
        if (widgetIds.isEmpty()) {
            Toast.makeText(requireContext(), R.string.no_gif_widgets_found, Toast.LENGTH_SHORT).show()
            return
        }
        val prefs = requireContext().getSharedPreferences("gif_widget_prefs", Context.MODE_PRIVATE)
        val items = widgetIds.map { appWidgetId ->
            val index = prefs.getInt("widget_index_$appWidgetId", 0)
            getString(R.string.gif_widget_name, index)
        }.toTypedArray()
        var dialog: AlertDialog? = null

        dialog = AlertDialog.Builder(requireContext(), dialogThemeStyle)
            .setTitle(R.string.select_gif_widget)
            .setItems(items) { _, which ->
                pendingAppWidgetId = widgetIds[which]
                selectFileLauncher.launch(arrayOf("image/gif"))
            }
            .setNegativeButton("Cancel", null)
            .create()
            .apply {
                setOnShowListener {
                    window?.setBackgroundDrawableResource(R.drawable.rounded_layout_bg_alt)
                    findViewById<TextView>(android.R.id.title)?.setTextColor(
                        ContextCompat.getColor(requireContext(), R.color.text_color)
                    )
                    getButton(DialogInterface.BUTTON_NEGATIVE)?.setTextColor(
                        ContextCompat.getColor(requireContext(), R.color.text_color)
                    )
                }
            }
        dialog.show()
        dialog.window?.setBackgroundDrawableResource(R.drawable.rounded_layout_bg_alt)
        dialog.applyDialogTheme()
    }

    private fun showSyncWidgetSelectionDialog() {
        val prefss = requireContext().getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)
        val isDarkTheme = prefss.getBoolean("dark_theme", false)
        val isRedAccent = prefss.getBoolean("red_accent", false)

        val dialogThemeStyle = when {
            isDarkTheme && isRedAccent -> R.style.CustomDialogTheme1
            isDarkTheme && !isRedAccent -> R.style.CustomDialogTheme
            !isDarkTheme && isRedAccent -> R.style.CustomDialogTheme1
            else -> R.style.CustomDialogTheme
        }
        val appWidgetManager = AppWidgetManager.getInstance(requireContext())
        val widgetIds = appWidgetManager.getAppWidgetIds(
            ComponentName(requireContext(), com.tpk.widgetspro.widgets.gif.GifWidgetProvider::class.java)
        )
        if (widgetIds.size < 2) {
            Toast.makeText(requireContext(), R.string.insufficient_gif_widgets, Toast.LENGTH_SHORT).show()
            return
        }
        val prefs = requireContext().getSharedPreferences("gif_widget_prefs", Context.MODE_PRIVATE)
        val items = widgetIds.map { appWidgetId ->
            val index = prefs.getInt("widget_index_$appWidgetId", 0)
            getString(R.string.gif_widget_name, index)
        }.toTypedArray()
        val checkedItems = BooleanArray(widgetIds.size) { false }
        var selectedWidgetIds = mutableSetOf<Int>()

        var dialog: AlertDialog? = null

        dialog = AlertDialog.Builder(requireContext(), dialogThemeStyle)
            .setTitle(R.string.sync_widgets)
            .setMultiChoiceItems(items, checkedItems) { dialogInterface, which, isChecked ->
                if (isChecked) {
                    selectedWidgetIds.add(widgetIds[which])
                    if (selectedWidgetIds.size > 2) {
                        val oldest = selectedWidgetIds.first()
                        selectedWidgetIds.remove(oldest)
                        checkedItems[widgetIds.indexOf(oldest)] = false
                        dialog?.listView?.setItemChecked(widgetIds.indexOf(oldest), false)
                    }
                } else {
                    selectedWidgetIds.remove(widgetIds[which])
                }
            }
            .setPositiveButton("Sync") { _, _ ->
                if (selectedWidgetIds.size == 2) {
                    val syncGroupId = UUID.randomUUID().toString()
                    val intent = Intent(requireContext(), AnimationService::class.java).apply {
                        putExtra("action", "SYNC_WIDGETS")
                        putExtra("sync_group_id", syncGroupId)
                        putExtra("sync_widget_ids", selectedWidgetIds.toIntArray())
                    }
                    requireContext().startService(intent)
                    Toast.makeText(requireContext(), R.string.widgets_synced, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), R.string.select_two_widgets, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawableResource(R.drawable.rounded_layout_bg_alt)
            dialog.findViewById<TextView>(android.R.id.title)?.setTextColor(
                ContextCompat.getColor(requireContext(), R.color.text_color)
            )
            dialog.getButton(DialogInterface.BUTTON_POSITIVE)?.setTextColor(
                ContextCompat.getColor(requireContext(), R.color.text_color)
            )
            dialog.getButton(DialogInterface.BUTTON_NEGATIVE)?.setTextColor(
                ContextCompat.getColor(requireContext(), R.color.text_color)
            )
        }
        dialog.show()
        dialog.window?.setBackgroundDrawableResource(R.drawable.rounded_layout_bg_alt)
        dialog.applyDialogTheme()
    }

    private fun updateNextResetText(mode: String) {
        val nextResetLabel = getString(R.string.next_reset_label)
        if (mode == "daily") {
            val calendar = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
            }
            val dateFormat = SimpleDateFormat("EEE, dd MMM yyyy • hh:mm a", Locale.getDefault())
            tvNextReset.text = "$nextResetLabel ${dateFormat.format(calendar.time)}"
        } else {
            tvNextReset.text = "$nextResetLabel ${getString(R.string.until_reset_now_pressed)}"
        }
    }

    private fun resetDataUsageNow(prefs: android.content.SharedPreferences) {
        val currentTime = System.currentTimeMillis()
        with(prefs.edit()) {
            putLong("manual_reset_time", currentTime)
            apply()
        }
        BaseWifiDataUsageWidgetProvider.updateAllWidgets(
            requireContext(),
            WifiDataUsageWidgetProviderCircle::class.java
        )
        BaseWifiDataUsageWidgetProvider.updateAllWidgets(
            requireContext(),
            WifiDataUsageWidgetProviderPill::class.java
        )
        BaseSimDataUsageWidgetProvider.updateAllWidgets(
            requireContext(),
            SimDataUsageWidgetProviderCircle::class.java
        )
        BaseSimDataUsageWidgetProvider.updateAllWidgets(
            requireContext(),
            SimDataUsageWidgetProviderPill::class.java
        )
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
        seekBarWifi.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                tvWifiValue.text = progress.toString()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                prefs.edit().putInt("wifi_data_usage_interval", seekBar?.progress ?: 60).apply()
                BaseWifiDataUsageWidgetProvider.updateAllWidgets(
                    requireContext(),
                    WifiDataUsageWidgetProviderCircle::class.java
                )
                BaseWifiDataUsageWidgetProvider.updateAllWidgets(
                    requireContext(),
                    WifiDataUsageWidgetProviderPill::class.java
                )
            }
        })
        seekBarSim.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                tvSimValue.text = progress.toString()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                prefs.edit().putInt("sim_data_usage_interval", seekBar?.progress ?: 60).apply()
                BaseSimDataUsageWidgetProvider.updateAllWidgets(
                    requireContext(),
                    SimDataUsageWidgetProviderCircle::class.java
                )
                BaseSimDataUsageWidgetProvider.updateAllWidgets(
                    requireContext(),
                    SimDataUsageWidgetProviderPill::class.java
                )
            }
        })
        seekBarNetworkSpeed.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                tvNetworkSpeedValue.text = progress.toString()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                prefs.edit().putInt("network_speed_interval", seekBar?.progress ?: 60).apply()
            }
        })
    }

    private fun showEnumSelectionDialog() {
        val prefs = requireContext().getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)
        val isDarkTheme = prefs.getBoolean("dark_theme", false)
        val isRedAccent = prefs.getBoolean("red_accent", false)

        val dialogThemeStyle = when {
            isDarkTheme && isRedAccent -> R.style.CustomDialogTheme1
            isDarkTheme && !isRedAccent -> R.style.CustomDialogTheme
            !isDarkTheme && isRedAccent -> R.style.CustomDialogTheme1
            else -> R.style.CustomDialogTheme
        }

        val builder = AlertDialog.Builder(requireContext(), dialogThemeStyle)
        builder.setTitle("Select options")
        builder.setMultiChoiceItems(enumOptions, null) { _, _, _ -> }
        builder.setPositiveButton("OK") { dialog, _ ->
            val checkedItems = (dialog as AlertDialog).listView.checkedItemPositions
            chipGroup.removeAllViews()
            for (i in 0 until enumOptions.size) {
                if (checkedItems[i]) {
                    addChipToGroup(enumOptions[i])
                }
            }
        }
        builder.setNegativeButton("Cancel", null)
        val dialog = builder.create()
        dialog.show()
        dialog.window?.setBackgroundDrawableResource(R.drawable.rounded_layout_bg_alt)
        dialog.applyDialogTheme()
    }

    private fun addChipToGroup(enumText: String) {
        val chip = Chip(requireContext())
        chip.text = enumText
        chip.isCloseIconVisible = true
        chip.setChipBackgroundColorResource(R.color.text_color)
        chip.setTextColor(resources.getColor(R.color.shape_background_color))
        chip.setCloseIconTintResource(R.color.shape_background_color)
        chip.setOnCloseIconClickListener { chipGroup.removeView(chip) }
        chipGroup.addView(chip)
    }

    private fun getSelectedItemsAsString(): String {
        val selectedItems = mutableListOf<String>()
        for (i in 0 until chipGroup.childCount) {
            val chip = chipGroup.getChildAt(i) as Chip
            selectedItems.add(chip.text.toString())
        }
        return selectedItems.joinToString(" ")
    }

    private fun AlertDialog.applyDialogTheme() {
        val textColor = ContextCompat.getColor(requireContext(), R.color.text_color)
        findViewById<TextView>(android.R.id.title)?.setTextColor(textColor)
        findViewById<TextView>(android.R.id.message)?.setTextColor(textColor)
        getButton(DialogInterface.BUTTON_POSITIVE)?.setTextColor(textColor)
        getButton(DialogInterface.BUTTON_NEGATIVE)?.setTextColor(textColor)
    }

    private fun getCoordinatesFromLocation(location: String) {
        val geocoder = Geocoder(requireContext(), Locale.getDefault())
        try {
            val addresses = geocoder.getFromLocationName(location, 1)
            if (!addresses.isNullOrEmpty()) {
                val address = addresses[0]
                val latitude = address.latitude
                val longitude = address.longitude
                saveLocationToPreferences(latitude, longitude)
                Toast.makeText(requireContext(), getString(R.string.location_set_message, location), Toast.LENGTH_SHORT).show()
                SunSyncService.start(requireContext())
            } else {
                Toast.makeText(requireContext(), R.string.location_not_found_message, Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(requireContext(), getString(R.string.location_error_message, e.message), Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveLocationToPreferences(latitude: Double, longitude: Double) {
        val prefs = requireContext().getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
        with(prefs.edit()) {
            putString("latitude", latitude.toString())
            putString("longitude", longitude.toString())
            apply()
        }
    }

    private fun fetchLocationSuggestions(query: String) {
        val geocoder = Geocoder(requireContext(), Locale.getDefault())
        try {
            val addresses = geocoder.getFromLocationName(query, 5)
            val suggestions = addresses?.map { it.getAddressLine(0) } ?: emptyList()
            suggestionsAdapter.clear()
            suggestionsAdapter.addAll(suggestions)
            suggestionsAdapter.notifyDataSetChanged()
            locationAutoComplete.showDropDown()
        } catch (e: Exception) {
            Toast.makeText(requireContext(), R.string.suggestions_error_message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun resetBluetoothImage() {
        val appWidgetIds = getBluetoothWidgetIds(requireContext())
        appWidgetIds.forEach { appWidgetId ->
            val deviceAddress = getSelectedDeviceAddress(requireContext(), appWidgetId)
            deviceAddress?.let { address ->
                val device = getBluetoothDeviceByAddress(address)
                device?.let { btDevice ->
                    resetImageForDevice(requireContext(), btDevice.name, appWidgetId)
                    clearCustomQueryForDevice(requireContext(), btDevice.name, appWidgetId)
                    setCustomQueryForDevice(requireContext(), btDevice.name, getSelectedItemsAsString(), appWidgetId)
                    Toast.makeText(requireContext(), getString(R.string.bluetooth_reset_message, btDevice.name), Toast.LENGTH_SHORT).show()
                }
            }
        }
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
        val views = android.widget.RemoteViews(context.packageName, R.layout.bluetooth_widget_layout)
        views.setImageViewResource(R.id.device_image, R.drawable.ic_bluetooth_placeholder)
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    private fun resetUpdateWidget(context: Context, appWidgetId: Int) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val views = android.widget.RemoteViews(context.packageName, R.layout.bluetooth_widget_layout)
        val deviceAddress = getSelectedDeviceAddress(context, appWidgetId)
        deviceAddress?.let {
            val device = getBluetoothDeviceByAddress(it)
            device?.let { btDevice ->
                ImageLoader(context, appWidgetManager, appWidgetId, views).loadImageAsync(btDevice)
            }
        }
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    private fun getBluetoothWidgetIds(context: Context): IntArray {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        return appWidgetManager.getAppWidgetIds(
            ComponentName(context, BluetoothWidgetProvider::class.java)
        )
    }

    private fun getSelectedDeviceAddress(context: Context, appWidgetId: Int): String? {
        val prefs = context.getSharedPreferences("BluetoothWidgetPrefs", Context.MODE_PRIVATE)
        return prefs.getString("device_address_$appWidgetId", null)
    }

    @SuppressLint("MissingPermission")
    private fun getBluetoothDeviceByAddress(address: String): android.bluetooth.BluetoothDevice? {
        val bluetoothAdapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter() ?: return null
        return try {
            bluetoothAdapter.getRemoteDevice(address)
        } catch (e: Exception) {
            null
        }
    }
}