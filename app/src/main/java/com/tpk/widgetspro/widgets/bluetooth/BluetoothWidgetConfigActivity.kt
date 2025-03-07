package com.tpk.widgetspro.widgets.bluetooth

import android.appwidget.AppWidgetManager
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.TypedValue
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.RemoteViews
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.tpk.widgetspro.R

class BluetoothWidgetConfigActivity : AppCompatActivity() {
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private val REQUEST_BLUETOOTH_PERMISSIONS = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.BLUETOOTH_CONNECT), REQUEST_BLUETOOTH_PERMISSIONS)
        } else {
            setupDeviceSelection()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_BLUETOOTH_PERMISSIONS) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                setupDeviceSelection()
            } else {
                setResult(RESULT_CANCELED)
                finish()
            }
        }
    }

    private fun setupDeviceSelection() {
        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        val pairedDevices = bluetoothAdapter?.bondedDevices ?: emptySet()
        if (pairedDevices.isEmpty()) {
            setResult(RESULT_CANCELED)
            finish()
            return
        }

        val window = window
        val displayMetrics = resources.displayMetrics
        val width = (displayMetrics.widthPixels * 0.8).toInt()
        window.setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT)
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window.attributes.dimAmount = 0.5f
        setFinishOnTouchOutside(true)

        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setBackgroundResource(R.drawable.dialog_background)
        layout.setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16))

        val titleTextView = TextView(this)
        titleTextView.text = "Select a Bluetooth device"
        layout.addView(titleTextView)

        val listView = ListView(this)
        val deviceNames = pairedDevices.map { it.name }
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, deviceNames)
        listView.adapter = adapter

        listView.setOnItemClickListener { _, _, position, _ ->
            val selectedDevice = pairedDevices.elementAt(position)
            saveSelectedDevice(appWidgetId, selectedDevice.address)
            updateWidget(appWidgetId)
            val resultValue = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            setResult(RESULT_OK, resultValue)
            finish()
        }

        layout.addView(listView)
        setContentView(layout)
    }

    private fun saveSelectedDevice(appWidgetId: Int, deviceAddress: String) {
        val prefs = getSharedPreferences("BluetoothWidgetPrefs", Context.MODE_PRIVATE)
        prefs.edit().putString("device_address_$appWidgetId", deviceAddress).apply()
    }

    private fun updateWidget(appWidgetId: Int) {
        val appWidgetManager = AppWidgetManager.getInstance(this)
        val views = RemoteViews(packageName, R.layout.bluetooth_widget_layout)
        BluetoothWidgetProvider.updateAppWidget(this, appWidgetManager, appWidgetId, views)
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            resources.displayMetrics
        ).toInt()
    }
}