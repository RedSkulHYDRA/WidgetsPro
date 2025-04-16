package com.tpk.widgetspro.services

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.ContextThemeWrapper
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.tpk.widgetspro.R
import com.tpk.widgetspro.widgets.analogclock.AnalogClockWidgetProvider_1
import java.util.Calendar

class AnalogClockUpdateService_1 : BaseMonitorService() {

    private lateinit var handler: Handler
    private lateinit var updateRunnable: Runnable
    private var isRunning = false
    private val updateInterval = 16L
    private val clockIntent = Intent(Intent.ACTION_MAIN).apply {
        addCategory(Intent.CATEGORY_LAUNCHER)
        setComponent(ComponentName("com.google.android.deskclock", "com.android.deskclock.DeskClock"))
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    override fun onCreate() {
        super.onCreate()
        handler = Handler(Looper.getMainLooper())
        updateRunnable = object : Runnable {
            override fun run() {
                if (shouldUpdate()) {
                    updateHandPositions()
                }
                handler.postDelayed(this, updateInterval)
            }
        }
        startMonitoring()
    }

    private fun startMonitoring() {
        if (!isRunning) {
            isRunning = true
            handler.post(updateRunnable)
        }
    }

    private fun stopMonitoring() {
        if (isRunning) {
            isRunning = false
            handler.removeCallbacks(updateRunnable)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!isRunning) startMonitoring()
        return super.onStartCommand(intent, flags, startId)
    }

    private fun updateHandPositions() {
        val appWidgetManager = AppWidgetManager.getInstance(this)
        val componentName = ComponentName(this, AnalogClockWidgetProvider_1::class.java)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
        val calendar = Calendar.getInstance()
        val hours = calendar.get(Calendar.HOUR)
        val minutes = calendar.get(Calendar.MINUTE)
        val seconds = calendar.get(Calendar.SECOND)
        val milliseconds = calendar.get(Calendar.MILLISECOND)
        val hourAngle = (hours % 12 * 30.0 + minutes / 2.0 + seconds / 120.0 + milliseconds / 120000.0).toFloat()
        val minuteAngle = (minutes * 6.0 + seconds / 10.0 + milliseconds / 10000.0).toFloat()
        val secondAngle = (seconds * 6.0 + milliseconds / 1000.0 * 6.0).toFloat()

        for (appWidgetId in appWidgetIds) {
            val views = RemoteViews(packageName, R.layout.analog_1_widget)
            views.setInt(R.id.analog_1_container, "setBackgroundResource", R.drawable.analog_1_bg)
            val dialResource = if (isSystemInDarkTheme(baseContext)) R.drawable.analog_1_dial_dark else R.drawable.analog_1_dial_light
            views.setImageViewResource(R.id.analog_1_dial, dialResource)
            val prefs = baseContext.getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)
            val isDarkTheme = prefs.getBoolean("dark_theme", isSystemInDarkTheme(baseContext))
            val isRedAccent = prefs.getBoolean("red_accent", false)
            val themeResId = when {
                isDarkTheme && isRedAccent -> R.style.Theme_WidgetsPro_Red_Dark
                isDarkTheme -> R.style.Theme_WidgetsPro
                isRedAccent -> R.style.Theme_WidgetsPro_Red_Light
                else -> R.style.Theme_WidgetsPro
            }
            val themedContext = ContextThemeWrapper(baseContext, themeResId)
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
            views.setFloat(R.id.analog_1_hour, "setRotation", hourAngle)
            views.setFloat(R.id.analog_1_min, "setRotation", minuteAngle)
            views.setFloat(R.id.analog_1_secs, "setRotation", secondAngle)
            val pendingIntent = android.app.PendingIntent.getActivity(
                baseContext, appWidgetId, clockIntent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.analog_1_container, pendingIntent)
            appWidgetManager.partiallyUpdateAppWidget(appWidgetId, views)
        }
    }

    private fun isSystemInDarkTheme(context: Context): Boolean {
        val currentNightMode = context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
        return currentNightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopMonitoring()
        super.onDestroy()
    }
}