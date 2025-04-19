package com.tpk.widgetspro.services

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.*
import android.os.*
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.tpk.widgetspro.R
import com.tpk.widgetspro.widgets.analogclock.AnalogClockWidgetProvider_2
import java.util.*
import kotlin.math.min

class AnalogClockUpdateService_2 : BaseMonitorService() {
    private val handler = Handler(Looper.getMainLooper())
    private var isRunning = false
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
                wasUpdating = false
            }
        }
    }

    private val updateRunnable = object : Runnable {
        override fun run() {
            if (!isRunning) return

            val shouldUpdateNow = shouldUpdate()
            if (shouldUpdateNow) {
                if (!wasUpdating) {
                    startCatchUp()
                }
                updateHandPositions()
                wasUpdating = true
            } else {
                wasUpdating = false
            }

            handler.postDelayed(this, getUpdateInterval())
        }
    }

    override fun onCreate() {
        super.onCreate()
        LocalBroadcastManager.getInstance(this).registerReceiver(
            visibilityResumedReceiver,
            IntentFilter(ACTION_VISIBILITY_RESUMED)
        )
        lastUpdateTime = System.currentTimeMillis()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(WIDGETS_PRO_NOTIFICATION_ID, createNotification())
        startMonitoring()
        return START_STICKY
    }

    private fun startMonitoring() {
        if (!isRunning) {
            isRunning = true
            handler.post(updateRunnable)
        }
    }

    private fun stopMonitoring() {
        isRunning = false
        handler.removeCallbacks(updateRunnable)
    }

    private fun getUpdateInterval(): Long {
        val prefs = getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)
        return if (prefs.getBoolean("smooth_clock", false)) 16L else 1000L
    }

    private fun startCatchUp() {
        val prefs = getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("smooth_clock", false)) return

        val now = System.currentTimeMillis()
        catchUpDelta = now - lastUpdateTime
        catchUpStartTime = now
        isCatchingUp = true


        handler.postDelayed({
            isCatchingUp = false
            lastUpdateTime = System.currentTimeMillis()
        }, 1000L)
    }

    private fun updateHandPositions() {
        val prefs = getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)
        val isSmooth = prefs.getBoolean("smooth_clock", false)

        val calendar = Calendar.getInstance().apply {
            timeInMillis = if (isSmooth && isCatchingUp) {
                val currentTime = System.currentTimeMillis()
                val timeSinceCatchUpStart = currentTime - catchUpStartTime
                val progress = min(1f, timeSinceCatchUpStart.toFloat() / 1000f)
                currentTime - (catchUpDelta * (1 - progress)).toLong()
            } else {
                System.currentTimeMillis()
            }
        }

        val hours = calendar.get(Calendar.HOUR)
        val minutes = calendar.get(Calendar.MINUTE)
        val seconds = calendar.get(Calendar.SECOND)
        val milliseconds = calendar.get(Calendar.MILLISECOND)

        val hourAngle = (hours % 12 * 30.0 + minutes / 2.0 + seconds / 120.0 + milliseconds / 120000.0).toFloat()
        val minuteAngle = (minutes * 6.0 + seconds / 10.0 + milliseconds / 10000.0).toFloat()
        val secondAngle = (seconds * 6.0 + milliseconds / 1000.0 * 6.0).toFloat()

        val views = createClockViews(hourAngle, minuteAngle, secondAngle)
        val appWidgetManager = AppWidgetManager.getInstance(this)
        val componentName = ComponentName(this, AnalogClockWidgetProvider_2::class.java)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
        appWidgetIds.forEach { appWidgetManager.partiallyUpdateAppWidget(it, views) }
    }

    private fun createClockViews(hourAngle: Float, minuteAngle: Float, secondAngle: Float): RemoteViews {
        return RemoteViews(packageName, R.layout.analog_2_widget).apply {

            setInt(R.id.analog_2_container, "setBackgroundResource", R.drawable.analog_2_bg)
            setImageViewResource(R.id.analog_2_dial,
                if (isSystemInDarkTheme(this@AnalogClockUpdateService_2))
                    R.drawable.analog_2_dial_dark
                else
                    R.drawable.analog_2_dial_light)


            val prefs = getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)
            val isDarkTheme = prefs.getBoolean("dark_theme", isSystemInDarkTheme(this@AnalogClockUpdateService_2))
            val isRedAccent = prefs.getBoolean("red_accent", false)


            val themeResId = when {
                isDarkTheme && isRedAccent -> R.style.Theme_WidgetsPro_Red_Dark
                isDarkTheme -> R.style.Theme_WidgetsPro_Dark
                isRedAccent -> R.style.Theme_WidgetsPro_Red_Light
                else -> R.style.Theme_WidgetsPro_Light
            }
            val themedContext = ContextThemeWrapper(this@AnalogClockUpdateService_2, themeResId)


            val typedValue = TypedValue()
            themedContext.theme.resolveAttribute(android.R.attr.colorAccent, typedValue, true)
            val accentColor = typedValue.data


            setImageViewResource(R.id.analog_2_hour, R.drawable.analog_2_hour)
            setImageViewResource(R.id.analog_2_min, R.drawable.analog_2_min)
            setImageViewResource(R.id.analog_2_secs, R.drawable.analog_2_secs)


            setInt(R.id.analog_2_hour, "setColorFilter", accentColor)
            setInt(R.id.analog_2_min, "setColorFilter", accentColor)
            setInt(R.id.analog_2_secs, "setColorFilter", accentColor)


            setFloat(R.id.analog_2_hour, "setRotation", hourAngle)
            setFloat(R.id.analog_2_min, "setRotation", minuteAngle)
            setFloat(R.id.analog_2_secs, "setRotation", secondAngle)


            setOnClickPendingIntent(R.id.analog_2_container,
                PendingIntent.getActivity(
                    this@AnalogClockUpdateService_2,
                    System.currentTimeMillis().toInt(),
                    clockIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
        }
    }

    private fun isSystemInDarkTheme(context: Context): Boolean {
        return (context.resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES
    }

    override fun onDestroy() {
        stopMonitoring()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(visibilityResumedReceiver)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}