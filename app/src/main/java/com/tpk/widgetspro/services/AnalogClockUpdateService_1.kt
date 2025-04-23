package com.tpk.widgetspro.services

import android.appwidget.AppWidgetManager
import android.content.*
import android.os.*
import android.view.ContextThemeWrapper
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.tpk.widgetspro.R
import com.tpk.widgetspro.widgets.analogclock.AnalogClockWidgetProvider_1
import java.util.*
import java.util.concurrent.TimeUnit

class AnalogClockUpdateService_1 : BaseMonitorService() {

    private lateinit var handler: Handler
    private lateinit var updateRunnable: Runnable
    private var isRunning = false
    private val normalUpdateInterval = TimeUnit.SECONDS.toMillis(1)
    private val idleUpdateInterval = TimeUnit.MINUTES.toMillis(10)
    private var cachedThemeResId: Int = R.style.Theme_WidgetsPro
    private var cachedAccentColor: Int = 0
    private var lastRedAccent: Boolean = false

    private val clockIntent = Intent(Intent.ACTION_MAIN).apply {
        addCategory(Intent.CATEGORY_LAUNCHER)
        setComponent(ComponentName("com.google.android.deskclock", "com.android.deskclock.DeskClock"))
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    private val visibilityResumedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == ACTION_VISIBILITY_RESUMED) {
                handler.removeCallbacks(updateRunnable)
                handler.post(updateRunnable)
            }
        }
    }

    private val userPresentReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_USER_PRESENT) {
                handler.removeCallbacks(updateRunnable)
                handler.post(updateRunnable)
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
        handler = Handler(Looper.getMainLooper())
        updateThemeCache()

        // Register all receivers
        LocalBroadcastManager.getInstance(this).registerReceiver(
            visibilityResumedReceiver,
            IntentFilter(ACTION_VISIBILITY_RESUMED)
        )
        registerReceiver(configChangeReceiver, IntentFilter(Intent.ACTION_CONFIGURATION_CHANGED))
        registerReceiver(userPresentReceiver, IntentFilter(Intent.ACTION_USER_PRESENT))

        updateRunnable = object : Runnable {
            override fun run() {
                val shouldUpdateNow = shouldUpdate()
                if (shouldUpdateNow) {
                    updateHandPositions()
                }
                val nextDelay = if (shouldUpdateNow) normalUpdateInterval else idleUpdateInterval
                handler.postDelayed(this, nextDelay)
            }
        }
        startMonitoring()
    }

    private fun updateThemeCache() {
        val prefs = getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)
        val isDarkTheme = prefs.getBoolean("dark_theme", isSystemInDarkTheme(baseContext))
        val isRedAccent = prefs.getBoolean("red_accent", false)
        lastRedAccent = isRedAccent

        cachedThemeResId = when {
            isDarkTheme && isRedAccent -> R.style.Theme_WidgetsPro_Red_Dark
            isDarkTheme -> R.style.Theme_WidgetsPro
            isRedAccent -> R.style.Theme_WidgetsPro_Red_Light
            else -> R.style.Theme_WidgetsPro
        }

        val themedContext = ContextThemeWrapper(baseContext, cachedThemeResId)
        cachedAccentColor = ContextCompat.getColor(themedContext, android.R.color.holo_blue_light)
        val typedValue = android.util.TypedValue()
        themedContext.theme.resolveAttribute(android.R.attr.colorAccent, typedValue, true)
        if (typedValue.data != 0) {
            cachedAccentColor = typedValue.data
        }
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
        val appWidgetManager = AppWidgetManager.getInstance(this)
        val widgetIds = appWidgetManager.getAppWidgetIds(ComponentName(this, AnalogClockWidgetProvider_1::class.java))
        if (widgetIds.isEmpty()) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!isRunning) startMonitoring()
        return super.onStartCommand(intent, flags, startId)
    }

    private fun updateHandPositions() {
        val prefs = getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)
        val currentRedAccent = prefs.getBoolean("red_accent", false)
        if (currentRedAccent != lastRedAccent) {
            updateThemeCache()
        }

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

        val hourAngle = (hours % 12 * 30.0 + minutes / 2.0 + seconds / 120.0).toFloat()
        val minuteAngle = (minutes * 6.0 + seconds / 10.0).toFloat()
        val secondAngle = (seconds * 6.0).toFloat()

        for (appWidgetId in appWidgetIds) {
            val views = RemoteViews(packageName, R.layout.analog_1_widget)
            views.setInt(R.id.analog_1_container, "setBackgroundResource", R.drawable.analog_1_bg)
            val dialResource = if (isSystemInDarkTheme(baseContext)) R.drawable.analog_1_dial_dark else R.drawable.analog_1_dial_light
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
        LocalBroadcastManager.getInstance(this).unregisterReceiver(visibilityResumedReceiver)
        unregisterReceiver(configChangeReceiver)
        unregisterReceiver(userPresentReceiver)
        stopMonitoring()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}