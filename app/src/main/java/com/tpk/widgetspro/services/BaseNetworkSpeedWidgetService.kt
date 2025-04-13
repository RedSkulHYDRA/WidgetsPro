package com.tpk.widgetspro.services

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.net.TrafficStats
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.widget.RemoteViews
import com.tpk.widgetspro.R
import com.tpk.widgetspro.utils.CommonUtils
import com.tpk.widgetspro.widgets.networkusage.NetworkSpeedWidgetProviderCircle
import com.tpk.widgetspro.widgets.networkusage.NetworkSpeedWidgetProviderPill

class BaseNetworkSpeedWidgetService : BaseMonitorService() {
    private var previousBytes: Long = 0
    private val handler = Handler(Looper.getMainLooper())
    private val UPDATE_INTERVAL_MS = 1000L

    private val updateRunnable = object : Runnable {
        override fun run() {
            if (shouldUpdate()) {
                updateSpeed()
            }
            handler.postDelayed(this, UPDATE_INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        handler.post(updateRunnable)
    }

    override fun onDestroy() {
        handler.removeCallbacks(updateRunnable)
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun updateSpeed() {
        val currentBytes = TrafficStats.getTotalRxBytes()
        val appWidgetManager = AppWidgetManager.getInstance(applicationContext)

        val circleWidgetIds = appWidgetManager.getAppWidgetIds(ComponentName(applicationContext, NetworkSpeedWidgetProviderCircle::class.java))
        updateWidgets(appWidgetManager, circleWidgetIds, R.layout.network_speed_widget_circle, currentBytes)

        val pillWidgetIds = appWidgetManager.getAppWidgetIds(ComponentName(applicationContext, NetworkSpeedWidgetProviderPill::class.java))
        updateWidgets(appWidgetManager, pillWidgetIds, R.layout.network_speed_widget_pill, currentBytes)

        previousBytes = currentBytes
    }

    private fun updateWidgets(
        manager: AppWidgetManager,
        appWidgetIds: IntArray,
        layoutResId: Int,
        currentBytes: Long
    ) {
        val typeface = CommonUtils.getTypeface(applicationContext)
        val speedText = if (currentBytes != TrafficStats.UNSUPPORTED.toLong() && previousBytes != 0L) {
            val bytesInLastInterval = currentBytes - previousBytes
            formatSpeed(bytesInLastInterval.toLong())
        } else {
            "N/A"
        }

        appWidgetIds.forEach { appWidgetId ->
            val views = RemoteViews(packageName, layoutResId).apply {
                if (layoutResId == R.layout.network_speed_widget_circle) {
                    val iconDrawable = applicationContext.getDrawable(R.drawable.network_speed_widget_icon)
                    val scaledIcon = scaleDrawable(iconDrawable, 0.9f)
                    setImageViewBitmap(R.id.network_speed_widget_image, scaledIcon)
                    setInt(R.id.network_speed_widget_image, "setColorFilter", CommonUtils.getAccentColor(applicationContext))
                    setImageViewBitmap(
                        R.id.network_speed_widget_text,
                        CommonUtils.createTextAlternateBitmap(applicationContext, speedText, 14f, typeface)
                    )
                } else {
                    setImageViewBitmap(
                        R.id.network_speed_widget_text,
                        CommonUtils.createTextAlternateBitmap(applicationContext, speedText, 20f, typeface)
                    )
                    setInt(R.id.network_speed_widget_image, "setColorFilter", CommonUtils.getAccentColor(applicationContext))
                }
            }
            manager.updateAppWidget(appWidgetId, views)
        }
    }

    private fun scaleDrawable(drawable: Drawable?, scaleFactor: Float): Bitmap? {
        if (drawable == null) return null
        val width = (drawable.intrinsicWidth * scaleFactor).toInt()
        val height = (drawable.intrinsicHeight * scaleFactor).toInt()
        drawable.setBounds(0, 0, width, height)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.draw(canvas)
        return bitmap
    }

    private fun formatSpeed(bytesPerSecond: Long): String {
        val unit = 1024
        if (bytesPerSecond < unit) return "$bytesPerSecond B/s"
        val exp = minOf((Math.log(bytesPerSecond.toDouble()) / Math.log(unit.toDouble())).toInt(), 5)
        val pre = "KMGTPE"[exp - 1]
        val value = bytesPerSecond / Math.pow(unit.toDouble(), exp.toDouble())

        return if (exp <= 2) {
            String.format("%.0f %sB/s", value, pre)
        } else {
            String.format("%.1f %sB/s", value, pre)
        }
    }
}