package com.tpk.widgetspro.services

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.tpk.widgetspro.R
import com.tpk.widgetspro.base.BaseMonitorService
import com.tpk.widgetspro.utils.WidgetUtils
import com.tpk.widgetspro.widgets.battery.BatteryDottedView
import com.tpk.widgetspro.widgets.battery.BatteryMonitor
import com.tpk.widgetspro.widgets.battery.BatteryWidgetProvider

class BatteryMonitorService : BaseMonitorService() {
    override val notificationId = 2
    override val notificationTitle = "Battery Monitor"
    override val notificationText = "Monitoring battery status"

    private lateinit var batteryMonitor: BatteryMonitor

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!::batteryMonitor.isInitialized) initializeMonitoring()
        return super.onStartCommand(intent, flags, startId)
    }

    private fun initializeMonitoring() {
        batteryMonitor = BatteryMonitor(this) { percentage, health ->
            val appWidgetManager = AppWidgetManager.getInstance(this)
            val componentName = ComponentName(this, BatteryWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            val typeface = ResourcesCompat.getFont(this, R.font.my_custom_font)!!
            val percentageBitmap = WidgetUtils.createTextBitmap(this, "$percentage%", 20f, ContextCompat.getColor(applicationContext, R.color.accent_color), typeface)
            val batteryBitmap = WidgetUtils.createTextBitmap(this, "BAT", 20f, ContextCompat.getColor(applicationContext, R.color.accent_color), typeface)
            val graphBitmap = WidgetUtils.createGraphBitmap(this, percentage, BatteryDottedView::class)

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
        val prefs = getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
        val interval = prefs.getInt("battery_interval", 60).coerceAtLeast(1)
        batteryMonitor.startMonitoring(interval)
    }

    override fun onDestroy() {
        if (::batteryMonitor.isInitialized) batteryMonitor.stopMonitoring()
        super.onDestroy()
    }
}