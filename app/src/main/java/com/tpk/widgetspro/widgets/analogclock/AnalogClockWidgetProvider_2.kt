package com.tpk.widgetspro.widgets.analogclock

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.widget.RemoteViews
import com.tpk.widgetspro.R
import com.tpk.widgetspro.services.AnalogClockUpdateService_2
import java.util.Calendar

class AnalogClockWidgetProvider_2 : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
        startService(context) // Ensure service is running
    }

    override fun onEnabled(context: Context) {
        startService(context)
        Log.d("AnalogClockProvider_2", "Widget enabled at ${System.currentTimeMillis()}")
    }

    override fun onDisabled(context: Context) {
        context.stopService(Intent(context, AnalogClockUpdateService_2::class.java))
        Log.d("AnalogClockProvider_2", "Widget disabled at ${System.currentTimeMillis()}")
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        Log.d("AnalogClockProvider_2", "Received intent: ${intent.action} at ${System.currentTimeMillis()}")
        if (intent.action == AppWidgetManager.ACTION_APPWIDGET_UPDATE) {
            startService(context) // Ensure service is running on manual update
        }
    }

    private fun startService(context: Context) {
        val intent = Intent(context, AnalogClockUpdateService_2::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    companion object {
        fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.analog_2_widget)

            views.setInt(R.id.analog_2_container, "setBackgroundResource", R.drawable.analog_2_bg)

            val isDarkTheme = isSystemInDarkTheme(context)
            val dialResource = if (isDarkTheme) R.drawable.analog_2_dial_dark else R.drawable.analog_2_dial_light
            val hourResource = R.drawable.analog_2_hour
            val minuteResource = R.drawable.analog_2_min
            val secsResource = R.drawable.analog_2_secs

            views.setImageViewResource(R.id.analog_2_dial, dialResource)
            views.setImageViewResource(R.id.analog_2_hour, hourResource)
            views.setImageViewResource(R.id.analog_2_min, minuteResource)
            views.setImageViewResource(R.id.analog_2_secs, secsResource)

            updateClockHands(views)

            // Intent to open the Google clock app (com.google.android.deskclock)
            val clockIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                setComponent(ComponentName("com.google.android.deskclock", "com.android.deskclock.DeskClock"))
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            // Set the PendingIntent to launch the clock app directly
            val pendingIntent = android.app.PendingIntent.getActivity(
                context, appWidgetId, clockIntent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )

            views.setOnClickPendingIntent(R.id.analog_2_container, pendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
            Log.d("AnalogClockProvider_2", "Widget updated at ${System.currentTimeMillis()}")
        }

        private fun updateClockHands(views: RemoteViews) {
            val calendar = Calendar.getInstance()
            val hour = calendar.get(Calendar.HOUR) // 12-hour format
            val minute = calendar.get(Calendar.MINUTE)
            val second = calendar.get(Calendar.SECOND)

            val hourRotation = (hour * 30 + minute / 2).toFloat() // 30° per hour + 0.5° per minute
            val minuteRotation = (minute * 6 + second / 10).toFloat() // 6° per minute + 0.1° per second
            val secondRotation = (second * 6).toFloat() // 6° per second

            views.setFloat(R.id.analog_2_hour, "setRotation", hourRotation)
            views.setFloat(R.id.analog_2_min, "setRotation", minuteRotation)
            views.setFloat(R.id.analog_2_secs, "setRotation", secondRotation)
            Log.d("AnalogClockProvider_2", "Hands updated: Hour=$hourRotation, Minute=$minuteRotation, Second=$secondRotation at ${System.currentTimeMillis()}")
        }

        private fun isSystemInDarkTheme(context: Context): Boolean {
            val currentNightMode = context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
            return currentNightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES
        }
    }
}