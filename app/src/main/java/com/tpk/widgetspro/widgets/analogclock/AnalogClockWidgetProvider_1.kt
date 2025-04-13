package com.tpk.widgetspro.widgets.analogclock

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.ContextThemeWrapper
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.tpk.widgetspro.R
import com.tpk.widgetspro.services.AnalogClockUpdateService_1
import java.util.Calendar

class AnalogClockWidgetProvider_1 : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            animateWidgetUpdate(context, appWidgetManager, appWidgetId)
        }
        startService(context)
    }

    override fun onEnabled(context: Context) {
        startService(context)
    }

    override fun onDisabled(context: Context) {
        context.stopService(Intent(context, AnalogClockUpdateService_1::class.java))
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == AppWidgetManager.ACTION_APPWIDGET_UPDATE) {
            startService(context)
        }
    }

    private fun startService(context: Context) {
        val intent = Intent(context, AnalogClockUpdateService_1::class.java)
        context.startForegroundService(intent)
    }

    companion object {
        fun animateWidgetUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.analog_1_widget)
            views.setInt(R.id.analog_1_container, "setBackgroundResource", R.drawable.analog_1_bg)
            val dialResource = if (isSystemInDarkTheme(context)) R.drawable.analog_1_dial_dark else R.drawable.analog_1_dial_light
            views.setImageViewResource(R.id.analog_1_dial, dialResource)
            val prefs = context.getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)
            val isDarkTheme = prefs.getBoolean("dark_theme", isSystemInDarkTheme(context))
            val isRedAccent = prefs.getBoolean("red_accent", false)
            val themeResId = when {
                isDarkTheme && isRedAccent -> R.style.Theme_WidgetsPro_Red_Dark
                isDarkTheme -> R.style.Theme_WidgetsPro
                isRedAccent -> R.style.Theme_WidgetsPro_Red_Light
                else -> R.style.Theme_WidgetsPro
            }
            val themedContext = ContextThemeWrapper(context, themeResId)
            val accentColor = ContextCompat.getColor(themedContext, android.R.color.holo_blue_light)
            val typedValue = android.util.TypedValue()
            themedContext.theme.resolveAttribute(android.R.attr.colorAccent, typedValue, true)
            val resolvedAccentColor = typedValue.data ?: accentColor
            views.setImageViewResource(R.id.analog_1_hour, R.drawable.analog_1_hour)
            views.setImageViewResource(R.id.analog_1_min, R.drawable.analog_1_min)
            views.setImageViewResource(R.id.analog_1_secs, R.drawable.analog_1_secs)
            views.setInt(R.id.analog_1_hour, "setColorFilter", resolvedAccentColor)
            views.setInt(R.id.analog_1_min, "setColorFilter", resolvedAccentColor)
            views.setInt(R.id.analog_1_secs, "setColorFilter", resolvedAccentColor)
            val calendar = Calendar.getInstance()
            val hours = calendar.get(Calendar.HOUR)
            val minutes = calendar.get(Calendar.MINUTE)
            val seconds = calendar.get(Calendar.SECOND)
            val milliseconds = calendar.get(Calendar.MILLISECOND)
            val hourAngle = (hours % 12 * 30.0 + minutes / 2.0 + seconds / 120.0 + milliseconds / 120000.0).toFloat()
            val minuteAngle = (minutes * 6.0 + seconds / 10.0 + milliseconds / 10000.0).toFloat()
            val secondAngle = (seconds * 6.0 + milliseconds / 1000.0 * 6.0).toFloat()
            views.setFloat(R.id.analog_1_hour, "setRotation", hourAngle)
            views.setFloat(R.id.analog_1_min, "setRotation", minuteAngle)
            views.setFloat(R.id.analog_1_secs, "setRotation", secondAngle)
            val clockIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                setComponent(ComponentName("com.google.android.deskclock", "com.android.deskclock.DeskClock"))
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val pendingIntent = android.app.PendingIntent.getActivity(
                context, appWidgetId, clockIntent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.analog_1_container, pendingIntent)
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        private fun isSystemInDarkTheme(context: Context): Boolean {
            val currentNightMode = context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
            return currentNightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES
        }
    }
}