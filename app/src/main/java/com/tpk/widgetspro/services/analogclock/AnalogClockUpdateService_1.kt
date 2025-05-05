package com.tpk.widgetspro.services.analogclock

import android.appwidget.AppWidgetManager
import android.content.*
import android.os.IBinder
import android.util.Log
import android.view.ContextThemeWrapper
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.tpk.widgetspro.R
import com.tpk.widgetspro.services.BaseMonitorService
import com.tpk.widgetspro.widgets.analogclock.AnalogClockWidgetProvider_1
import kotlinx.coroutines.*
import java.util.*
import java.util.concurrent.TimeUnit

class AnalogClockUpdateService_1 : BaseMonitorService(), CoroutineScope {

    private val supervisorJob = SupervisorJob()
    override val coroutineContext = Dispatchers.Main + supervisorJob

    private var updateJob: Job? = null
    private val normalUpdateInterval = TimeUnit.SECONDS.toMillis(1)
    private val idleUpdateInterval = CHECK_INTERVAL_INACTIVE_MS
    private var cachedThemeResId: Int = R.style.Theme_WidgetsPro
    private var cachedAccentColor: Int = 0
    private var cachedShapeColor: Int = 0

    private val clockIntent = Intent(Intent.ACTION_MAIN).apply {
        addCategory(Intent.CATEGORY_LAUNCHER)
        setComponent(ComponentName("com.google.android.deskclock", "com.android.deskclock.DeskClock"))
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    private val visibilityResumedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == ACTION_VISIBILITY_RESUMED) {
                startMonitoring()
            }
        }
    }

    private val userPresentReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_USER_PRESENT) {
                startMonitoring()
            }
        }
    }

    private val configChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_CONFIGURATION_CHANGED) {
                updateThemeCache()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        updateThemeCache()

        LocalBroadcastManager.getInstance(this).registerReceiver(
            visibilityResumedReceiver,
            IntentFilter(ACTION_VISIBILITY_RESUMED)
        )
        registerReceiver(configChangeReceiver, IntentFilter(Intent.ACTION_CONFIGURATION_CHANGED))
        registerReceiver(userPresentReceiver, IntentFilter(Intent.ACTION_USER_PRESENT))

        startMonitoring()
    }

    private fun updateThemeCache() {
        val prefs = getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)
        val isDarkTheme = prefs.getBoolean("dark_theme", isSystemInDarkTheme(baseContext))
        val isRedAccent = prefs.getBoolean("red_accent", false)

        cachedThemeResId = when {
            isDarkTheme && isRedAccent -> R.style.Theme_WidgetsPro_Red_Dark
            isDarkTheme -> R.style.Theme_WidgetsPro
            isRedAccent -> R.style.Theme_WidgetsPro_Red_Light
            else -> R.style.Theme_WidgetsPro
        }

        val themedContext = ContextThemeWrapper(baseContext, cachedThemeResId)
        val typedValue = android.util.TypedValue()
        themedContext.theme.resolveAttribute(R.attr.colorAccent, typedValue, true)
        if (typedValue.resourceId != 0) {
            cachedAccentColor = ContextCompat.getColor(themedContext, typedValue.resourceId)
        } else {
            cachedAccentColor = ContextCompat.getColor(themedContext, R.color.accent_color)
        }
        cachedShapeColor = ContextCompat.getColor(themedContext, R.color.shape_background_color)
    }

    private fun startMonitoring() {
        updateJob?.cancel()
        updateJob = launch {
            while (isActive) {
                if (shouldUpdate()) {
                    updateHandPositions()
                    val prefs = getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
                    val is60FpsEnabled = prefs.getBoolean("clock_60fps_enabled", false)
                    val delayMs = if (is60FpsEnabled) 16L else normalUpdateInterval
                    delay(delayMs)
                } else {
                    delay(idleUpdateInterval)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val appWidgetManager = AppWidgetManager.getInstance(this)
        val widgetIds = appWidgetManager.getAppWidgetIds(ComponentName(this, AnalogClockWidgetProvider_1::class.java))
        if (widgetIds.isEmpty()) {
            stopSelf()
            return START_NOT_STICKY
        }
        updateThemeCache()
        startMonitoring()
        return super.onStartCommand(intent, flags, startId)
    }

    private fun updateHandPositions() {
        val appWidgetManager = AppWidgetManager.getInstance(this)
        val componentName = ComponentName(this, AnalogClockWidgetProvider_1::class.java)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)

        if (appWidgetIds.isEmpty()) {
            stopSelf()
            return
        }

        val calendar = Calendar.getInstance()
        calendar.timeInMillis = System.currentTimeMillis()

        val hours = calendar.get(Calendar.HOUR)
        val minutes = calendar.get(Calendar.MINUTE)
        val seconds = calendar.get(Calendar.SECOND)
        val milliseconds = calendar.get(Calendar.MILLISECOND)

        val totalSeconds = seconds + milliseconds / 1000.0
        val totalMinutes = minutes + totalSeconds / 60.0
        val totalHours = hours + totalMinutes / 60.0

        val hourAngle = (totalHours % 12 * 30.0).toFloat()
        val minuteAngle = (totalMinutes * 6.0).toFloat()
        val secondAngle = (totalSeconds * 6.0).toFloat()

        val isDark = isSystemInDarkTheme(baseContext)
        val dialResource = if (isDark) R.drawable.analog_1_dial_dark else R.drawable.analog_1_dial_light
        val dialBackground = if (isDark) R.drawable.analog_1_bg_dark else R.drawable.analog_1_bg_light
        for (appWidgetId in appWidgetIds) {
            val views = RemoteViews(packageName, R.layout.analog_1_widget)
            views.setImageViewResource(R.id.analog_1_bg, dialBackground)
            views.setInt(R.id.analog_1_bg, "setColorFilter", cachedShapeColor)
            views.setImageViewResource(R.id.analog_1_dial, dialResource)
            views.setImageViewResource(R.id.analog_1_hour, R.drawable.analog_1_hour)
            views.setImageViewResource(R.id.analog_1_min, R.drawable.analog_1_min)
            views.setImageViewResource(R.id.analog_1_secs, R.drawable.analog_1_secs)
            views.setInt(R.id.analog_1_hour, "setColorFilter", cachedAccentColor)
            views.setInt(R.id.analog_1_min, "setColorFilter", cachedAccentColor)
            views.setInt(R.id.analog_1_secs, "setColorFilter", cachedAccentColor)
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

    override fun onDestroy() {
        updateJob?.cancel()
        supervisorJob.cancel()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(visibilityResumedReceiver)
        unregisterReceiver(configChangeReceiver)
        unregisterReceiver(userPresentReceiver)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}