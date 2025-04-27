package com.tpk.widgetspro.services.cpu

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
import com.tpk.widgetspro.utils.PermissionUtils
import com.tpk.widgetspro.widgets.cpu.CpuMonitor
import com.tpk.widgetspro.widgets.cpu.CpuWidgetProvider
import com.tpk.widgetspro.widgets.cpu.DottedGraphView
import java.util.LinkedList
import kotlin.reflect.KClass

class CpuMonitorService : BaseMonitorService() {
    private lateinit var cpuMonitor: CpuMonitor
    private val dataPoints = LinkedList<Double>()
    private val MAX_DATA_POINTS = 50
    private var useRoot = false
    private var prefs: SharedPreferences? = null
    private var initialized = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val commandResult = super.onStartCommand(intent, flags, startId)

        val appWidgetManager = AppWidgetManager.getInstance(this)
        val widgetIds = appWidgetManager.getAppWidgetIds(ComponentName(this, CpuWidgetProvider::class.java))
        if (widgetIds.isEmpty()) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (!initialized) {
            if (PermissionUtils.hasRootAccess()) {
                useRoot = true
                initializeMonitoring()
                initialized = true
            } else if (PermissionUtils.hasShizukuPermission()) {
                useRoot = false
                initializeMonitoring()
                initialized = true
            } else {
                stopSelf()
                return START_NOT_STICKY
            }
        }

        return commandResult
    }

    private fun initializeMonitoring() {
        repeat(MAX_DATA_POINTS) { dataPoints.add(0.0) }
        cpuMonitor = CpuMonitor(useRoot) { cpuUsage, cpuTemperature ->
            if (shouldUpdate()) {
                val appWidgetManager = AppWidgetManager.getInstance(this)
                val widgetIds = appWidgetManager.getAppWidgetIds(ComponentName(this, CpuWidgetProvider::class.java))

                if (widgetIds.isEmpty()) {
                    stopSelf()
                    return@CpuMonitor
                }

                val themePrefs = getSharedPreferences("theme_prefs", MODE_PRIVATE)
                val isDarkTheme = themePrefs.getBoolean("dark_theme", false)
                val isRedAccent = themePrefs.getBoolean("red_accent", false)
                val themeResId = when {
                    isDarkTheme && isRedAccent -> R.style.Theme_WidgetsPro_Red_Dark
                    isDarkTheme -> R.style.Theme_WidgetsPro
                    isRedAccent -> R.style.Theme_WidgetsPro_Red_Light
                    else -> R.style.Theme_WidgetsPro
                }
                val themedContext = ContextThemeWrapper(applicationContext, themeResId)

                val typeface = CommonUtils.getTypeface(themedContext)
                val usageBitmap = CommonUtils.createTextBitmap(themedContext, "%.0f%%".format(cpuUsage), 20f, typeface)
                val cpuBitmap = CommonUtils.createTextBitmap(themedContext, "CPU", 20f, typeface)

                dataPoints.addLast(cpuUsage)
                if (dataPoints.size > MAX_DATA_POINTS) dataPoints.removeFirst()

                widgetIds.forEach { appWidgetId ->
                    val views = RemoteViews(packageName, R.layout.cpu_widget_layout).apply {
                        setImageViewBitmap(R.id.cpuUsageImageView, usageBitmap)
                        setImageViewBitmap(R.id.cpuImageView, cpuBitmap)
                        setImageViewBitmap(R.id.graphWidgetImageView, createGraphBitmap(themedContext, dataPoints, DottedGraphView::class))
                        setTextViewText(R.id.cpuTempWidgetTextView, "%.1f°C".format(cpuTemperature))
                        setTextViewText(R.id.cpuModelWidgetTextView, getDeviceProcessorModel())
                    }
                    appWidgetManager.updateAppWidget(appWidgetId, views)
                }
            }
        }
        prefs = getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
        prefs?.registerOnSharedPreferenceChangeListener(preferenceListener)
        val cpuInterval = prefs?.getInt("cpu_interval", 60)?.coerceAtLeast(1) ?: 60
        cpuMonitor.startMonitoring(cpuInterval)
    }

    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "cpu_interval") {
            val newInterval = prefs?.getInt(key, 60)?.coerceAtLeast(1) ?: 60
            if (::cpuMonitor.isInitialized) {
                cpuMonitor.updateInterval(newInterval)
            }
        }
    }

    private fun getDeviceProcessorModel(): String = when (android.os.Build.SOC_MODEL) {
        "SM8475" -> "8+ Gen 1"
        else -> android.os.Build.SOC_MODEL
    }

    private fun <T : BaseDottedGraphView> createGraphBitmap(context: Context, data: Any, viewClass: KClass<T>): Bitmap {
        val graphView = viewClass.java.getConstructor(Context::class.java).newInstance(context)
        (graphView as? DottedGraphView)?.setDataPoints((data as List<*>).filterIsInstance<Double>())
        val widthPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 200f, context.resources.displayMetrics).toInt()
        val heightPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 80f, context.resources.displayMetrics).toInt()
        graphView.measure(View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY))
        graphView.layout(0, 0, widthPx, heightPx)
        return Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888).apply { graphView.draw(Canvas(this)) }
    }

    override fun onDestroy() {
        prefs?.unregisterOnSharedPreferenceChangeListener(preferenceListener)
        if (::cpuMonitor.isInitialized) cpuMonitor.stopMonitoring()
        super.onDestroy()
    }
}