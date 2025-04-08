package com.tpk.widgetspro.widgets.analogclock

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.widget.RemoteViews
import com.tpk.widgetspro.R
import com.tpk.widgetspro.services.AnalogClockUpdateService
import java.util.Calendar

class AnalogClockWidgetProvider : AppWidgetProvider() {

    private val updateInterval = 60000L // Update every minute

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onEnabled(context: Context) {
        val intent = Intent(context, AnalogClockUpdateService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
        Log.d("AnalogClockProvider", "Widget enabled at ${System.currentTimeMillis()}")
    }

    override fun onDisabled(context: Context) {
        val intent = Intent(context, AnalogClockUpdateService::class.java)
        context.stopService(intent)
        Log.d("AnalogClockProvider", "Widget disabled at ${System.currentTimeMillis()}")
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        Log.d("AnalogClockProvider", "Received intent: ${intent.action} at ${System.currentTimeMillis()}")
        if (intent.action == AppWidgetManager.ACTION_APPWIDGET_UPDATE || intent.action == "UPDATE_ANALOG_CLOCK") {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, AnalogClockWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)

            for (appWidgetId in appWidgetIds) {
                updateAppWidget(context, appWidgetManager, appWidgetId)
            }

            // Reschedule the next update to align with the minute boundary
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val alarmIntent = PendingIntent.getBroadcast(
                context,
                0,
                Intent(context, AnalogClockWidgetProvider::class.java).apply { action = "UPDATE_ANALOG_CLOCK" },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val calendar = Calendar.getInstance().apply {
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                add(Calendar.MINUTE, 1)
            }
            val nextMinuteTime = calendar.timeInMillis

            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                nextMinuteTime,
                alarmIntent
            )
            Log.d("AnalogClockProvider", "Next alarm scheduled for $nextMinuteTime (${calendar.get(Calendar.HOUR_OF_DAY)}:${calendar.get(Calendar.MINUTE)})")
        }
    }

    companion object {
        fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.analog_1_widget)

            views.setInt(R.id.analog_1_container, "setBackgroundResource", R.drawable.analog_1_bg)

            val isDarkTheme = isSystemInDarkTheme(context)
            val dialResource = if (isDarkTheme) R.drawable.analog_1_dial_dark else R.drawable.analog_1_dial_light
            val hourResource = R.drawable.analog_1_hour
            val minuteResource = R.drawable.analog_1_min

            views.setImageViewResource(R.id.analog_1_dial, dialResource)
            views.setImageViewResource(R.id.analog_1_hour, hourResource)
            views.setImageViewResource(R.id.analog_1_minute, minuteResource)

            updateClockHands(views, context)

            // Intent to open the Google clock app (com.google.android.deskclock)
            val clockIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                setComponent(ComponentName("com.google.android.deskclock", "com.android.deskclock.DeskClock"))
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            // Set the PendingIntent to launch the clock app directly
            val pendingIntent = PendingIntent.getActivity(
                context, appWidgetId, clockIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            views.setOnClickPendingIntent(R.id.analog_1_container, pendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
            Log.d("AnalogClockProvider", "Widget updated at ${System.currentTimeMillis()}")
        }

        private fun updateClockHands(views: RemoteViews, context: Context) {
            val calendar = Calendar.getInstance()
            val hour = calendar.get(Calendar.HOUR) // 12-hour format
            val minute = calendar.get(Calendar.MINUTE)

            val hourRotation = (hour * 30 + minute / 2).toFloat() // 30° per hour + 0.5° per minute
            val minuteRotation = (minute * 6).toFloat() // 6° per minute

            views.setFloat(R.id.analog_1_hour, "setRotation", hourRotation)
            views.setFloat(R.id.analog_1_minute, "setRotation", minuteRotation)
            Log.d("AnalogClockProvider", "Hands updated: Hour=$hourRotation, Minute=$minuteRotation at ${System.currentTimeMillis()}")
        }

        private fun isSystemInDarkTheme(context: Context): Boolean {
            val currentNightMode = context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
            return currentNightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES
        }
    }
}