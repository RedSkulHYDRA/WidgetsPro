package com.tpk.widgetspro.widgets.speedtest


import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.graphics.Color
import android.net.TrafficStats
import android.os.Handler
import android.os.Looper
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.tpk.widgetspro.R
import com.tpk.widgetspro.utils.WidgetUtils


class SpeedWidgetProvider : AppWidgetProvider() {

    private var handler: Handler? = null
    private var updateRunnable: Runnable? = null

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        if (handler == null) {
            handler = Handler(Looper.getMainLooper())
            updateRunnable = object : Runnable {
                override fun run() {
                    updateSpeed(context)
                    handler?.postDelayed(this, UPDATE_INTERVAL)
                }
            }
            handler?.post(updateRunnable!!)
        }
    }

    override fun onDisabled(context: Context) {
        handler?.removeCallbacks(updateRunnable!!)
        handler = null
        updateRunnable = null
    }

    companion object {
        private const val UPDATE_INTERVAL = 1000L
        private var previousBytes: Long = 0

        private fun updateSpeed(context: Context) {
            val currentBytes = TrafficStats.getTotalRxBytes()
            if (currentBytes != TrafficStats.UNSUPPORTED.toLong()) {
                if (previousBytes != 0L) {
                    val bytesInLastSecond = currentBytes - previousBytes
                    val speedMBps = (bytesInLastSecond / 1024.0 / 1024.0).toFloat()
                    val appWidgetManager = AppWidgetManager.getInstance(context)
                    val thisWidget = ComponentName(context, SpeedWidgetProvider::class.java)
                    val appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
                    for (appWidgetId in appWidgetIds) {
                        val views = RemoteViews(context.packageName, R.layout.speed_widget_layout)
                        val typeface = ResourcesCompat.getFont(context, R.font.my_custom_font)!!
                        val setupBitmap = WidgetUtils.createTextBitmap(context, String.format("%.2f MB/s", speedMBps), 20f, ContextCompat.getColor(context, R.color.text_color), typeface)
                        views.setImageViewBitmap(R.id.speed_text, setupBitmap)
                        appWidgetManager.updateAppWidget(appWidgetId, views)
                    }
                }
                previousBytes = currentBytes
            } else {
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val thisWidget = ComponentName(context, SpeedWidgetProvider::class.java)
                val appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
                for (appWidgetId in appWidgetIds) {
                    val views = RemoteViews(context.packageName, R.layout.speed_widget_layout)
                    val typeface = ResourcesCompat.getFont(context, R.font.my_custom_font)!!
                    val setupBitmap = WidgetUtils.createTextBitmap(context, "N/A", 20f, Color.WHITE, typeface)
                    views.setImageViewBitmap(R.id.speed_text, setupBitmap)
                    appWidgetManager.updateAppWidget(appWidgetId, views)
                }
            }
        }
    }
}