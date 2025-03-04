package com.tpk.widgetspro.services

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.res.ResourcesCompat
import com.tpk.widgetspro.R
import com.tpk.widgetspro.utils.WidgetUtils
import com.tpk.widgetspro.widgets.cpu.CpuMonitor
import com.tpk.widgetspro.widgets.cpu.CpuWidgetProvider
import com.tpk.widgetspro.widgets.cpu.DottedGraphView
import rikka.shizuku.Shizuku
import java.util.LinkedList

class CpuMonitorService : BaseMonitorService() {
    override val notificationId = 1
    override val notificationTitle = "CPU Monitor"
    override val notificationText = "Monitoring CPU usage"

    private lateinit var cpuMonitor: CpuMonitor
    private val dataPoints = LinkedList<Double>()
    private val MAX_DATA_POINTS = 50
    private var useRoot = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let { useRoot = it.getBooleanExtra("use_root", false) }
        if (!useRoot && (!Shizuku.pingBinder() || Shizuku.checkSelfPermission() != android.content.pm.PackageManager.PERMISSION_GRANTED)) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!::cpuMonitor.isInitialized) initializeMonitoring()
        return super.onStartCommand(intent, flags, startId)
    }

    private fun initializeMonitoring() {
        repeat(MAX_DATA_POINTS) { dataPoints.add(0.0) }
        cpuMonitor = CpuMonitor(useRoot) { cpuUsage, cpuTemperature ->
            val appWidgetManager = AppWidgetManager.getInstance(this)
            val componentName = ComponentName(this, CpuWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            val typeface = ResourcesCompat.getFont(this, R.font.my_custom_font)!!
            val usageBitmap = WidgetUtils.createTextBitmap(this, "%.0f%%".format(cpuUsage), 20f, Color.RED, typeface)
            val cpuBitmap = WidgetUtils.createTextBitmap(this, "CPU", 20f, Color.RED, typeface)

            dataPoints.addLast(cpuUsage)
            if (dataPoints.size > MAX_DATA_POINTS) dataPoints.removeFirst()

            appWidgetIds.forEach { appWidgetId ->
                val views = RemoteViews(packageName, R.layout.cpu_widget_layout).apply {
                    setImageViewBitmap(R.id.cpuUsageImageView, usageBitmap)
                    setImageViewBitmap(R.id.cpuImageView, cpuBitmap)
                    setViewVisibility(R.id.setupView, View.GONE)
                    setTextViewText(R.id.cpuTempWidgetTextView, "%.1f°C".format(cpuTemperature))
                    setTextViewText(R.id.cpuModelWidgetTextView, getDeviceProcessorModel())
                    setImageViewBitmap(R.id.graphWidgetImageView, WidgetUtils.createGraphBitmap(this@CpuMonitorService, dataPoints, DottedGraphView::class))
                }
                appWidgetManager.updateAppWidget(appWidgetId, views)
            }
        }
        val prefs = getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
        val cpuInterval = prefs.getInt("cpu_interval", 60).coerceAtLeast(1)
        cpuMonitor.startMonitoring(cpuInterval)
    }

    private fun getDeviceProcessorModel(): String = when (android.os.Build.SOC_MODEL) {
        "SM8475" -> "8+ Gen 1"
        else -> android.os.Build.SOC_MODEL
    }

    override fun onDestroy() {
        cpuMonitor.stopMonitoring()
        super.onDestroy()
    }
}