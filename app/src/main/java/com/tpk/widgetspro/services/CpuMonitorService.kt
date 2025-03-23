package com.tpk.widgetspro.services

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import com.tpk.widgetspro.R
import com.tpk.widgetspro.base.BaseDottedGraphView
import com.tpk.widgetspro.base.BaseMonitorService
import com.tpk.widgetspro.utils.CommonUtils
import com.tpk.widgetspro.widgets.cpu.CpuMonitor
import com.tpk.widgetspro.widgets.cpu.CpuWidgetProvider
import com.tpk.widgetspro.widgets.cpu.DottedGraphView
import rikka.shizuku.Shizuku
import java.util.LinkedList
import kotlin.reflect.KClass

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
            val typeface = CommonUtils.getTypeface(this)
            val usageBitmap = CommonUtils.createTextBitmap(this, "%.0f%%".format(cpuUsage), 20f, typeface)
            val cpuBitmap = CommonUtils.createTextBitmap(this, "CPU", 20f, typeface)

            dataPoints.addLast(cpuUsage)
            if (dataPoints.size > MAX_DATA_POINTS) dataPoints.removeFirst()

            appWidgetIds.forEach { appWidgetId ->
                val views = RemoteViews(packageName, R.layout.cpu_widget_layout).apply {
                    setImageViewBitmap(R.id.cpuUsageImageView, usageBitmap)
                    setImageViewBitmap(R.id.cpuImageView, cpuBitmap)
                    setViewVisibility(R.id.setupView, View.GONE)
                    setTextViewText(R.id.cpuTempWidgetTextView, "%.1f°C".format(cpuTemperature))
                    setTextViewText(R.id.cpuModelWidgetTextView, getDeviceProcessorModel())
                    setImageViewBitmap(R.id.graphWidgetImageView, createGraphBitmap(this@CpuMonitorService, dataPoints, DottedGraphView::class))
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

    private fun <T : BaseDottedGraphView> createGraphBitmap(context: Context, data: Any, viewClass: KClass<T>): Bitmap {
        val graphView = viewClass.java.getConstructor(Context::class.java).newInstance(context)
        when (data) {
            is List<*> -> (graphView as? DottedGraphView)?.setDataPoints(data.filterIsInstance<Double>())
        }
        val widthPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 200f, context.resources.displayMetrics).toInt()
        val heightPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 80f, context.resources.displayMetrics).toInt()
        graphView.measure(View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY))
        graphView.layout(0, 0, widthPx, heightPx)
        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        graphView.draw(Canvas(bitmap))
        return bitmap
    }

    override fun onDestroy() {
        if (::cpuMonitor.isInitialized) cpuMonitor.stopMonitoring()
        super.onDestroy()
    }
}