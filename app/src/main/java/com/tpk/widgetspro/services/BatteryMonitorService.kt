package com.tpk.widgetspro.services

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import com.tpk.widgetspro.R
import com.tpk.widgetspro.base.BaseDottedGraphView
import com.tpk.widgetspro.base.BaseMonitorService
import com.tpk.widgetspro.utils.CommonUtils
import com.tpk.widgetspro.widgets.battery.BatteryDottedView
import com.tpk.widgetspro.widgets.battery.BatteryMonitor
import com.tpk.widgetspro.widgets.battery.BatteryWidgetProvider
import kotlin.reflect.KClass

class BatteryMonitorService : BaseMonitorService() {
    override val notificationId = 2
    override val notificationTitle = "Battery Monitor"
    override val notificationText = "Monitoring battery status"

    private lateinit var batteryMonitor: BatteryMonitor
    private var prefs: SharedPreferences? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!::batteryMonitor.isInitialized) initializeMonitoring()
        return super.onStartCommand(intent, flags, startId)
    }

    private fun initializeMonitoring() {
        batteryMonitor = BatteryMonitor(this) { percentage, health ->
            val appWidgetManager = AppWidgetManager.getInstance(this)
            val componentName = ComponentName(this, BatteryWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            val typeface = CommonUtils.getTypeface(this)
            val percentageBitmap = CommonUtils.createTextBitmap(this, "$percentage%", 20f, typeface)
            val batteryBitmap = CommonUtils.createTextBitmap(this, "BAT", 20f, typeface)
            val graphBitmap = createGraphBitmap(this, percentage, BatteryDottedView::class)

            appWidgetIds.forEach { appWidgetId ->
                val views = RemoteViews(packageName, R.layout.battery_widget_layout).apply {
                    setImageViewBitmap(R.id.batteryImageView, batteryBitmap)
                    setImageViewBitmap(R.id.batteryPercentageImageView, percentageBitmap)
                    setTextViewText(R.id.batteryModelWidgetTextView, health.toString())
                    setImageViewBitmap(R.id.graphWidgetImageView, graphBitmap)
                    if (percentage == 0) setTextViewText(R.id.batteryPercentageImageView, getString(R.string.loading))
                }
                appWidgetManager.updateAppWidget(appWidgetId, views)
            }
        }
        prefs = getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
        prefs?.registerOnSharedPreferenceChangeListener(preferenceListener)
        val interval = prefs?.getInt("battery_interval", 60)?.coerceAtLeast(1) ?: 60
        batteryMonitor.startMonitoring(interval)
    }

    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "battery_interval") {
            val newInterval = prefs?.getInt(key, 60)?.coerceAtLeast(1) ?: 60
            batteryMonitor.updateInterval(newInterval)
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

    override fun onDestroy() {
        prefs?.unregisterOnSharedPreferenceChangeListener(preferenceListener)
        if (::batteryMonitor.isInitialized) batteryMonitor.stopMonitoring()
        super.onDestroy()
    }
}