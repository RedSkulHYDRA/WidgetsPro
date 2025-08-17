package com.tpk.widgetspro

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.tpk.widgetspro.services.cpu.CpuMonitorService
import com.tpk.widgetspro.utils.BitmapCacheManager
import com.tpk.widgetspro.widgets.battery.BatteryWidgetProvider
import com.tpk.widgetspro.widgets.bluetooth.BluetoothWidgetProvider
import com.tpk.widgetspro.widgets.caffeine.CaffeineWidget
import com.tpk.widgetspro.widgets.cpu.CpuWidgetProvider
import com.tpk.widgetspro.widgets.networkusage.NetworkSpeedWidgetProviderCircle
import com.tpk.widgetspro.widgets.networkusage.NetworkSpeedWidgetProviderPill
import com.tpk.widgetspro.widgets.networkusage.SimDataUsageWidgetProviderCircle
import com.tpk.widgetspro.widgets.networkusage.SimDataUsageWidgetProviderPill
import com.tpk.widgetspro.widgets.networkusage.WifiDataUsageWidgetProviderCircle
import com.tpk.widgetspro.widgets.networkusage.WifiDataUsageWidgetProviderPill
import com.tpk.widgetspro.widgets.notes.NoteWidgetProvider
import com.tpk.widgetspro.widgets.sun.SunTrackerWidget
import com.tpk.widgetspro.widgets.analogclock.AnalogClockWidgetProvider_1
import com.tpk.widgetspro.widgets.analogclock.AnalogClockWidgetProvider_2
import androidx.navigation.findNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.tpk.widgetspro.services.BaseMonitorService
import com.tpk.widgetspro.widgets.music.MusicSimpleWidgetProvider
import com.tpk.widgetspro.widgets.sports.SportsWidgetProvider

class MainActivity : AppCompatActivity() {
    internal val SHIZUKU_REQUEST_CODE = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        applyTheme()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val navView: BottomNavigationView = findViewById(R.id.nav_view)
        val navController = findNavController(R.id.nav_host_fragment_activity_main)

        navView.setupWithNavController(navController)
        BitmapCacheManager.clearExpiredCache(this)
    }

    private fun applyTheme() {
        val prefs = getSharedPreferences("theme_prefs", MODE_PRIVATE)
        val isDarkTheme = prefs.getBoolean("dark_theme", false)
        val isRedAccent = prefs.getBoolean("red_accent", false)
        setTheme(
            when {
                isDarkTheme && isRedAccent -> R.style.Theme_WidgetsPro_Red_Dark
                isDarkTheme -> R.style.Theme_WidgetsPro
                isRedAccent -> R.style.Theme_WidgetsPro_Red_Light
                else -> R.style.Theme_WidgetsPro
            }
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            SHIZUKU_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)
                    startServiceAndFinish(useRoot = false)
                else Toast.makeText(this, R.string.shizuku_toast_fail, Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun startServiceAndFinish(useRoot: Boolean) {
        startForegroundService(
            Intent(this, CpuMonitorService::class.java).putExtra("use_root", useRoot)
        )
    }

    fun switchTheme() {
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
            NetworkSpeedWidgetProviderCircle::class.java,
            NetworkSpeedWidgetProviderPill::class.java,
            WifiDataUsageWidgetProviderCircle::class.java,
            WifiDataUsageWidgetProviderPill::class.java,
            SimDataUsageWidgetProviderCircle::class.java,
            SimDataUsageWidgetProviderPill::class.java,
            NoteWidgetProvider::class.java,
            AnalogClockWidgetProvider_1::class.java,
            AnalogClockWidgetProvider_2::class.java,
            MusicSimpleWidgetProvider::class.java,
            SportsWidgetProvider::class.java
        )
        val intent = Intent(BaseMonitorService.ACTION_THEME_CHANGED)
        sendBroadcast(intent)
        providers.forEach { provider ->
            val componentName = ComponentName(this, provider)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            if (appWidgetIds.isNotEmpty()) {
                val updateIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE).apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds)
                    component = componentName
                }
                sendBroadcast(updateIntent)
            }
        }
        recreate()
    }
}
