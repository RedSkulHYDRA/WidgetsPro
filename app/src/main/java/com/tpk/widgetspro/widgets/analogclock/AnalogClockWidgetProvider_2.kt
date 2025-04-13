package com.tpk.widgetspro.widgets.analogclock

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.view.ContextThemeWrapper
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.tpk.widgetspro.R
import com.tpk.widgetspro.services.AnalogClockUpdateService_2
import android.animation.ValueAnimator
import java.util.Calendar

class AnalogClockWidgetProvider_2 : AppWidgetProvider() {

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
        context.stopService(Intent(context, AnalogClockUpdateService_2::class.java))
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == AppWidgetManager.ACTION_APPWIDGET_UPDATE) {
            startService(context)
        }
    }

    private fun startService(context: Context) {
        val intent = Intent(context, AnalogClockUpdateService_2::class.java)
        context.startForegroundService(intent)
    }

    companion object {
        fun animateWidgetUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.analog_2_widget)


            views.setInt(R.id.analog_2_container, "setBackgroundResource", R.drawable.analog_2_bg)

            val dialResource = if (isSystemInDarkTheme(context)) R.drawable.analog_2_dial_dark else R.drawable.analog_2_dial_light
            views.setImageViewResource(R.id.analog_2_dial, dialResource)

            val prefs: SharedPreferences = context.getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)
            val isDarkTheme = prefs.getBoolean("dark_theme", isSystemInDarkTheme(context))
            val isRedAccent = prefs.getBoolean("red_accent", false)
            val themeResId = when {
                isDarkTheme && isRedAccent -> R.style.Theme_WidgetsPro_Red_Dark
                isDarkTheme -> R.style.Theme_WidgetsPro
                isRedAccent -> R.style.Theme_WidgetsPro_Red_Light
                else -> R.style.Theme_WidgetsPro
            }
            val themedContext = ContextThemeWrapper(context, themeResId)


            views.setImageViewResource(R.id.analog_2_hour, R.drawable.analog_2_hour)
            views.setImageViewResource(R.id.analog_2_min, R.drawable.analog_2_min)
            views.setImageViewResource(R.id.analog_2_secs, R.drawable.analog_2_secs)

            val accentColor = ContextCompat.getColor(themedContext, android.R.color.holo_blue_light)
            val typedValue = android.util.TypedValue()
            themedContext.theme.resolveAttribute(android.R.attr.colorAccent, typedValue, true)
            val resolvedAccentColor = typedValue.data ?: accentColor

            views.setInt(R.id.analog_2_hour, "setColorFilter", resolvedAccentColor)
            views.setInt(R.id.analog_2_min, "setColorFilter", resolvedAccentColor)
            views.setInt(R.id.analog_2_secs, "setColorFilter", resolvedAccentColor)


            val calendar = Calendar.getInstance()
            val newHour = calendar.get(Calendar.HOUR)
            val newMinute = calendar.get(Calendar.MINUTE)
            val newSecond = calendar.get(Calendar.SECOND)

            val lastPositions = getLastPositions(context, appWidgetId)
            val lastHour = lastPositions.first
            val lastMinute = lastPositions.second
            val lastSecond = lastPositions.third


            animateHand(lastHour, newHour, "hour", appWidgetId, context, appWidgetManager, views)
            animateHand(lastMinute, newMinute, "minute", appWidgetId, context, appWidgetManager, views)
            animateHand(lastSecond, newSecond, "second", appWidgetId, context, appWidgetManager, views)


            saveLastPositions(context, appWidgetId, newHour, newMinute, newSecond)


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
        }

        private fun animateHand(from: Int, to: Int, handType: String, appWidgetId: Int, context: Context, appWidgetManager: AppWidgetManager, views: RemoteViews) {
            val animator = ValueAnimator.ofFloat(from.toFloat(), to.toFloat()).apply {
                duration = 1000
                addUpdateListener { animation ->
                    val animatedValue = animation.animatedValue as Float
                    updateHandPosition(handType, animatedValue, appWidgetId, context, appWidgetManager, views)
                }
            }
            animator.start()
        }

        private fun updateHandPosition(handType: String, position: Float, appWidgetId: Int, context: Context, appWidgetManager: AppWidgetManager, views: RemoteViews) {
            when (handType) {
                "hour" -> views.setFloat(R.id.analog_2_hour, "setRotation", position * 30f + (position / 60f) * 30f)
                "minute" -> views.setFloat(R.id.analog_2_min, "setRotation", position * 6f + (position / 60f) * 6f)
                "second" -> views.setFloat(R.id.analog_2_secs, "setRotation", position * 6f)
            }
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        private fun getLastPositions(context: Context, appWidgetId: Int): Triple<Int, Int, Int> {
            val prefs = context.getSharedPreferences("ClockPrefs_$appWidgetId", Context.MODE_PRIVATE)
            return Triple(
                prefs.getInt("lastHour", 0),
                prefs.getInt("lastMinute", 0),
                prefs.getInt("lastSecond", 0)
            )
        }

        private fun saveLastPositions(context: Context, appWidgetId: Int, hour: Int, minute: Int, second: Int) {
            val prefs = context.getSharedPreferences("ClockPrefs_$appWidgetId", Context.MODE_PRIVATE)
            prefs.edit().apply {
                putInt("lastHour", hour)
                putInt("lastMinute", minute)
                putInt("lastSecond", second)
                apply()
            }
        }

        private fun isSystemInDarkTheme(context: Context): Boolean {
            val currentNightMode = context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
            return currentNightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES
        }
    }
}