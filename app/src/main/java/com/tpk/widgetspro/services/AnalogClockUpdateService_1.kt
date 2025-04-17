package com.tpk.widgetspro.services

import android.appwidget.AppWidgetManager
import android.content.*
import android.os.*
import android.util.Log
import android.view.ContextThemeWrapper
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.tpk.widgetspro.R
import com.tpk.widgetspro.widgets.analogclock.AnalogClockWidgetProvider_1
import java.util.*
import kotlin.math.min

class AnalogClockUpdateService_1 : BaseMonitorService() {

    private lateinit var handler: Handler
    private lateinit var updateRunnable: Runnable
    private var isRunning = false
    private val updateInterval = 16L
    private var wasUpdating = false
    private var isCatchingUp = false
    private var catchUpStartTime = 0L
    private var catchUpDelta = 0L
    private var lastUpdateTime = 0L

    private val clockIntent = Intent(Intent.ACTION_MAIN).apply {
        addCategory(Intent.CATEGORY_LAUNCHER)
        setComponent(ComponentName("com.google.android.deskclock", "com.android.deskclock.DeskClock"))
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    private val visibilityResumedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == ACTION_VISIBILITY_RESUMED) {
                Log.d("AnalogClockUpdate", "Visibility resumed broadcast received")
                wasUpdating = false
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        handler = Handler(Looper.getMainLooper())
        lastUpdateTime = System.currentTimeMillis()

        LocalBroadcastManager.getInstance(this).registerReceiver(
            visibilityResumedReceiver,
            IntentFilter(ACTION_VISIBILITY_RESUMED)
        )

        updateRunnable = object : Runnable {
            override fun run() {
                val shouldUpdateNow = shouldUpdate()
                if (shouldUpdateNow) {
                    if (!wasUpdating) {
                        val now = System.currentTimeMillis()
                        catchUpDelta = now - lastUpdateTime
                        catchUpStartTime = now
                        isCatchingUp = true
                        Log.d("AnalogClockUpdate", "Starting catch-up with delta: $catchUpDelta ms")
                    }
                    updateHandPositions()
                    wasUpdating = true
                } else {
                    wasUpdating = false
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

        if (isCatchingUp) {
            val currentTime = System.currentTimeMillis()
            val timeSinceCatchUpStart = currentTime - catchUpStartTime
            val progress = if (catchUpDelta > 0) {
                min(1f, timeSinceCatchUpStart.toFloat() / 1000f)
            } else {
                1f
            }
            val displayedTime = currentTime - (catchUpDelta * (1 - progress)).toLong()
            calendar.timeInMillis = displayedTime

            if (progress >= 1f) {
                isCatchingUp = false
                lastUpdateTime = currentTime
            }
        } else {
            lastUpdateTime = System.currentTimeMillis()
            calendar.timeInMillis = lastUpdateTime
        }

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

    override fun onDestroy() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(visibilityResumedReceiver)
        stopMonitoring()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}