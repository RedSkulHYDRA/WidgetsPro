package com.tpk.widgetspro.widgets.networkusage

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.provider.Settings
import android.util.Log
import android.widget.RemoteViews
import com.tpk.widgetspro.R
import com.tpk.widgetspro.services.WifiDataUsageWidgetService
import com.tpk.widgetspro.utils.CommonUtils
import com.tpk.widgetspro.utils.NetworkStatsHelper

abstract class BaseWifiDataUsageWidgetProvider : AppWidgetProvider() {
    abstract val layoutResId: Int

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_MIDNIGHT_RESET) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(ComponentName(context, this::class.java))
            onUpdate(context, appWidgetManager, appWidgetIds)
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { updateAppWidget(context, appWidgetManager, it, layoutResId) }
        scheduleMidnightReset(context)
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        val prefs = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
        val activeProviders = prefs.getStringSet("active_wifi_data_usage_providers", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        activeProviders.add(this::class.java.name)
        prefs.edit().putStringSet("active_wifi_data_usage_providers", activeProviders).apply()
        context.startForegroundService(Intent(context, WifiDataUsageWidgetService::class.java))
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        val prefs = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
        val activeProviders = prefs.getStringSet("active_wifi_data_usage_providers", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        activeProviders.remove(this::class.java.name)
        prefs.edit().putStringSet("active_wifi_data_usage_providers", activeProviders).apply()
        if (activeProviders.isEmpty()) {
            context.stopService(Intent(context, WifiDataUsageWidgetService::class.java))
        }
    }

    private fun scheduleMidnightReset(context: Context) {
        CommonUtils.scheduleMidnightReset(context, WIFI_DATA_USAGE_REQUEST_CODE, ACTION_MIDNIGHT_RESET, this::class.java)
    }

    companion object {
        private const val ACTION_MIDNIGHT_RESET = "com.tpk.widgetspro.ACTION_WIFI_DATA_USAGE_RESET"
        private const val WIFI_DATA_USAGE_REQUEST_CODE = 1001

        fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, layoutResId: Int) {
            try {
                val usage = NetworkStatsHelper.getWifiDataUsage(context, NetworkStatsHelper.SESSION_TODAY)
                val totalBytes = usage[2]
                val formattedUsage = formatBytes(totalBytes)
                val views = RemoteViews(context.packageName, layoutResId).apply {
                    // Apply scaling only if the circle layout is used
                    if (layoutResId == R.layout.wifi_data_usage_widget_circle) {
                        val iconDrawable = context.getDrawable(R.drawable.wifi_data_usage)
                        val scaledIcon = scaleDrawable(iconDrawable, 0.9f)
                        setImageViewBitmap(R.id.wifi_data_usage_image, scaledIcon)
                        setInt(R.id.wifi_data_usage_image, "setColorFilter", CommonUtils.getAccentColor(context))
                        setImageViewBitmap(
                            R.id.wifi_usage_text,
                            CommonUtils.createTextAlternateBitmap(context, formattedUsage, 14f, CommonUtils.getTypeface(context))
                        )

                    }
                    else{
                        setImageViewBitmap(
                            R.id.wifi_data_usage_text,
                            CommonUtils.createTextAlternateBitmap(context, formattedUsage, 20f, CommonUtils.getTypeface(context))
                        )
                        setInt(R.id.wifi_data_usage_image, "setColorFilter", CommonUtils.getAccentColor(context))

                    }
                }
                val intent = Intent(Settings.ACTION_WIFI_SETTINGS)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                val pendingIntent = PendingIntent.getActivity(
                    context,
                    0,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.wifi_data_usage, pendingIntent)
                appWidgetManager.updateAppWidget(appWidgetId, views)
            } catch (e: Exception) {
                Log.e("DataUsageWidget", "Error getting WiFi data usage", e)
                val usage = NetworkStatsHelper.getWifiDataUsage(context, NetworkStatsHelper.SESSION_TODAY)
                val totalBytes = usage[2]
                val formattedUsage = formatBytes(totalBytes)
                val views = RemoteViews(context.packageName, layoutResId).apply {
                    // Apply scaling only if the circle layout is used
                    if (layoutResId == R.layout.wifi_data_usage_widget_circle) {
                        val iconDrawable = context.getDrawable(R.drawable.wifi_data_usage)
                        val scaledIcon = scaleDrawable(iconDrawable, 0.9f)
                        setImageViewBitmap(R.id.wifi_data_usage_image, scaledIcon)
                        setInt(R.id.wifi_data_usage_image, "setColorFilter", CommonUtils.getAccentColor(context))
                        setImageViewBitmap(
                            R.id.wifi_usage_text,
                            CommonUtils.createTextAlternateBitmap(context, formattedUsage, 14f, CommonUtils.getTypeface(context))
                        )

                    }
                    else{
                        setImageViewBitmap(
                            R.id.wifi_data_usage_text,
                            CommonUtils.createTextAlternateBitmap(context, "Click here", 20f, CommonUtils.getTypeface(context))
                        )
                        setInt(R.id.wifi_data_usage_image, "setColorFilter", CommonUtils.getAccentColor(context))

                    }
                }
                val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                val pendingIntent = PendingIntent.getActivity(
                    context,
                    0,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.wifi_data_usage, pendingIntent)
                appWidgetManager.updateAppWidget(appWidgetId, views)
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

        fun updateAllWidgets(context: Context, providerClass: Class<*>) {
            val layoutResId = when (providerClass) {
                WifiDataUsageWidgetProviderCircle::class.java -> R.layout.wifi_data_usage_widget_circle
                WifiDataUsageWidgetProviderPill::class.java -> R.layout.wifi_data_usage_widget_pill
                else -> throw IllegalArgumentException("Unknown provider class: $providerClass")
            }
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(ComponentName(context, providerClass))
            appWidgetIds.forEach { updateAppWidget(context, appWidgetManager, it, layoutResId) }
        }

        private fun formatBytes(bytes: Long): String {
            val unit = 1024
            if (bytes < unit) return "$bytes B"
            val exp = minOf((Math.log(bytes.toDouble()) / Math.log(unit.toDouble())).toInt(), 5)
            val pre = "KMGTPE"[exp - 1]
            val value = bytes / Math.pow(unit.toDouble(), exp.toDouble())
            return String.format("%.1f %sB", value, pre)
        }
    }
}