package com.tpk.widgetspro.widgets.analogclock

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import android.view.ContextThemeWrapper
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
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

            // Determine dial based solely on system theme
            val dialResource = if (isSystemInDarkTheme(context)) R.drawable.analog_2_dial_dark else R.drawable.analog_2_dial_light
            views.setImageViewResource(R.id.analog_2_dial, dialResource)

            // Apply theme for hands based on app preferences (including red accent)
            val prefs: SharedPreferences = context.getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)
            val isDarkTheme = prefs.getBoolean("dark_theme", isSystemInDarkTheme(context)) // Fallback to system theme
            val isRedAccent = prefs.getBoolean("red_accent", false)
            val themeResId = when {
                isDarkTheme && isRedAccent -> R.style.Theme_WidgetsPro_Red_Dark
                isDarkTheme -> R.style.Theme_WidgetsPro
                isRedAccent -> R.style.Theme_WidgetsPro_Red_Light
                else -> R.style.Theme_WidgetsPro
            }
            val themedContext = ContextThemeWrapper(context, themeResId)

            // Load PNGs for hands
            views.setImageViewResource(R.id.analog_2_hour, R.drawable.analog_2_hour)
            views.setImageViewResource(R.id.analog_2_min, R.drawable.analog_2_min)
            views.setImageViewResource(R.id.analog_2_secs, R.drawable.analog_2_secs)

            // Apply Monet/accent color to hands
            val accentColor = ContextCompat.getColor(themedContext, android.R.color.holo_blue_light) // Default fallback
            val typedValue = android.util.TypedValue()
            themedContext.theme.resolveAttribute(android.R.attr.colorAccent, typedValue, true)
            val resolvedAccentColor = typedValue.data ?: accentColor // Use Monet accent color if available

            views.setInt(R.id.analog_2_hour, "setColorFilter", resolvedAccentColor)
            views.setInt(R.id.analog_2_min, "setColorFilter", resolvedAccentColor)
            views.setInt(R.id.analog_2_secs, "setColorFilter", resolvedAccentColor)

            updateClockHands(views)

            // Intent to open the Google clock app (com.google.android.deskclock)
            val clockIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                setComponent(ComponentName("com.google.android.deskclock", "com.android.deskclock.DeskClock"))
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

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