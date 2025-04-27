package com.tpk.widgetspro.services.battery

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.View
import android.widget.RemoteViews
import com.tpk.widgetspro.R
import com.tpk.widgetspro.base.BaseDottedGraphView
import com.tpk.widgetspro.services.BaseMonitorService
import com.tpk.widgetspro.utils.CommonUtils
import com.tpk.widgetspro.widgets.battery.BatteryDottedView
import com.tpk.widgetspro.widgets.battery.BatteryMonitor
import com.tpk.widgetspro.widgets.battery.BatteryWidgetProvider
import kotlin.reflect.KClass
import android.os.BatteryManager
import android.content.res.Configuration

class BatteryMonitorService : BaseMonitorService() {
    private lateinit var batteryMonitor: BatteryMonitor
    private var prefs: SharedPreferences? = null
    private var initialized = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val commandResult = super.onStartCommand(intent, flags, startId)

        val appWidgetManager = AppWidgetManager.getInstance(this)
        val widgetIds = appWidgetManager.getAppWidgetIds(ComponentName(this, BatteryWidgetProvider::class.java))
        if (widgetIds.isEmpty()) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (!initialized) {
            initializeMonitoring()
            initialized = true
        }

        val bm = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val currentPercentage = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val currentHealth = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
        if (shouldUpdate()) {
            updateWidgetViews(currentPercentage, currentHealth)
        }

        return commandResult
    }

    private fun initializeMonitoring() {
        batteryMonitor = BatteryMonitor(this) { percentage, health ->
            if (shouldUpdate()) {
                updateWidgetViews(percentage, health)
            }
        }
        prefs = getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
        prefs?.registerOnSharedPreferenceChangeListener(preferenceListener)
        val interval = prefs?.getInt("battery_interval", 60)?.coerceAtLeast(1) ?: 60
        batteryMonitor.startMonitoring(interval)
    }

    private fun updateWidgetViews(percentage: Int, health: Int) {
        val appWidgetManager = AppWidgetManager.getInstance(this)
        val widgetIds = appWidgetManager.getAppWidgetIds(ComponentName(this, BatteryWidgetProvider::class.java))

        if (widgetIds.isEmpty()) {
            return
        }

        val themePrefs = getSharedPreferences("theme_prefs", MODE_PRIVATE)
        val isDarkTheme = themePrefs.getBoolean("dark_theme", isSystemInDarkTheme(baseContext))
        val isRedAccent = themePrefs.getBoolean("red_accent", false)
        val themeResId = when {
            isDarkTheme && isRedAccent -> R.style.Theme_WidgetsPro_Red_Dark
            isDarkTheme -> R.style.Theme_WidgetsPro
            isRedAccent -> R.style.Theme_WidgetsPro_Red_Light
            else -> R.style.Theme_WidgetsPro
        }
        val themedContext = ContextThemeWrapper(applicationContext, themeResId)

        val typeface = CommonUtils.getTypeface(themedContext)
        val percentageBitmap = CommonUtils.createTextBitmap(themedContext, "$percentage%", 20f, typeface)
        val batteryBitmap = CommonUtils.createTextBitmap(themedContext, "BAT", 20f, typeface)
        val graphBitmap = createGraphBitmap(themedContext, percentage, BatteryDottedView::class)
        val healthString = health.toString()

        widgetIds.forEach { appWidgetId ->
            val views = RemoteViews(packageName, R.layout.battery_widget_layout).apply {
                setImageViewBitmap(R.id.batteryImageView, batteryBitmap)
                setImageViewBitmap(R.id.batteryPercentageImageView, percentageBitmap)
                setTextViewText(R.id.batteryModelWidgetTextView, healthString)
                setImageViewBitmap(R.id.graphWidgetImageView, graphBitmap)
            }
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }


    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "battery_interval") {
            val newInterval = prefs?.getInt(key, 60)?.coerceAtLeast(1) ?: 60
            if (::batteryMonitor.isInitialized) {
                batteryMonitor.updateInterval(newInterval)
            }
        }
    }

    private fun <T : BaseDottedGraphView> createGraphBitmap(context: Context, data: Any, viewClass: KClass<T>): Bitmap {
        val graphView = viewClass.java.getConstructor(Context::class.java).newInstance(context)
        (graphView as? BatteryDottedView)?.updatePercentage(data as Int)
        val widthPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 200f, context.resources.displayMetrics).toInt()
        val heightPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 80f, context.resources.displayMetrics).toInt()
        graphView.measure(View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY))
        graphView.layout(0, 0, widthPx, heightPx)
        return Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888).apply { graphView.draw(Canvas(this)) }
    }

    private fun isSystemInDarkTheme(context: Context): Boolean {
        val currentNightMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return currentNightMode == Configuration.UI_MODE_NIGHT_YES
    }


    override fun onDestroy() {
        prefs?.unregisterOnSharedPreferenceChangeListener(preferenceListener)
        if (::batteryMonitor.isInitialized) batteryMonitor.stopMonitoring()
        super.onDestroy()
    }
}