package com.tpk.widgetspro.services

import android.app.*
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.appwidget.AppWidgetManager
import android.content.*
import android.os.*
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.tpk.widgetspro.MainActivity
import com.tpk.widgetspro.R
import com.tpk.widgetspro.widgets.analogclock.AnalogClockWidgetProvider_1
import com.tpk.widgetspro.widgets.analogclock.AnalogClockWidgetProvider_2
import com.tpk.widgetspro.widgets.battery.BatteryWidgetProvider
import com.tpk.widgetspro.widgets.bluetooth.BluetoothWidgetProvider
import com.tpk.widgetspro.widgets.caffeine.CaffeineWidget
import com.tpk.widgetspro.widgets.cpu.CpuWidgetProvider
import com.tpk.widgetspro.widgets.networkusage.*
import com.tpk.widgetspro.widgets.notes.NoteWidgetProvider
import com.tpk.widgetspro.widgets.sun.SunTrackerWidget
import java.util.concurrent.TimeUnit

abstract class BaseMonitorService : Service() {
    companion object {
        private const val WIDGETS_PRO_NOTIFICATION_ID = 100
        private const val CHANNEL_ID = "widgets_pro_channel"
        const val ACTION_VISIBILITY_RESUMED = "com.tpk.widgetspro.VISIBILITY_RESUMED"
        private val EVENT_QUERY_INTERVAL_MS = TimeUnit.SECONDS.toMillis(3)
        private const val ACTION_WALLPAPER_CHANGED_STRING = "android.intent.action.WALLPAPER_CHANGED"
        private val CHECK_INTERVAL_INACTIVE_MS = TimeUnit.MINUTES.toMillis(5)
    }

    private lateinit var powerManager: PowerManager
    private lateinit var keyguardManager: KeyguardManager
    private lateinit var usageStatsManager: UsageStatsManager
    private var cachedLauncherPackage: String? = null
    private var lastWasLauncher = true
    private var isInActiveState = false

    private val handler = Handler(Looper.getMainLooper())
    private val updateChecker = object : Runnable {
        override fun run() {
            if (!shouldUpdate()) {
                updateAllWidgets()
                handler.postDelayed(this, CHECK_INTERVAL_INACTIVE_MS)
            }
        }
    }

    private val systemReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> {
                    if (shouldUpdate()) {
                        notifyVisibilityResumed()
                        cancelInactiveUpdates()
                    }
                }
                Intent.ACTION_CLOSE_SYSTEM_DIALOGS -> {
                    updateCachedLauncherPackage()
                    if (shouldUpdate()) {
                        notifyVisibilityResumed()
                        cancelInactiveUpdates()
                    }
                }
                Intent.ACTION_CONFIGURATION_CHANGED -> updateAllWidgets()
                ACTION_WALLPAPER_CHANGED_STRING -> updateAllWidgets()
            }

            val currentActiveState = shouldUpdate()
            if (currentActiveState != isInActiveState) {
                isInActiveState = currentActiveState
                if (!currentActiveState) {
                    handler.removeCallbacks(updateChecker)
                    handler.post(updateChecker)
                } else {
                    cancelInactiveUpdates()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        powerManager = getSystemService(POWER_SERVICE) as PowerManager
        keyguardManager = getSystemService(KEYGUARD_SERVICE) as KeyguardManager
        usageStatsManager = getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager
        updateCachedLauncherPackage()
        isInActiveState = shouldUpdate()

        createNotificationChannel()

        registerReceiver(systemReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)
            addAction(Intent.ACTION_CONFIGURATION_CHANGED)
            addAction(ACTION_WALLPAPER_CHANGED_STRING)
        }, Context.RECEIVER_NOT_EXPORTED)

        if (!isInActiveState) {
            handler.post(updateChecker)
        }
    }

    private fun cancelInactiveUpdates() {
        handler.removeCallbacks(updateChecker)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Widgets Pro Channel",
            NotificationManager.IMPORTANCE_MIN
        ).apply {
            description = "Channel for keeping Widgets Pro services running"
        }
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(WIDGETS_PRO_NOTIFICATION_ID, createNotification())
        return START_STICKY
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.widgets_pro_running))
            .setContentText(getString(R.string.widgets_pro_active_text))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }

    protected fun shouldUpdate(): Boolean {
        val isScreenOn = powerManager.isInteractive
        val isKeyguardLocked = keyguardManager.isKeyguardLocked
        val isLauncherInForeground = isLauncherForeground()
        return isScreenOn && !isKeyguardLocked && isLauncherInForeground
    }

    private fun isLauncherForeground(): Boolean {
        try {
            if (!hasUsageStatsPermission()) return lastWasLauncher

            getRecentPackageName()?.let {
                return checkAgainstLauncherPackage(it).also { lastWasLauncher = it }
            }

            (getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).runningAppProcesses?.forEach { process ->
                if (process.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
                    return checkAgainstLauncherPackage(process.pkgList?.get(0) ?: "").also { lastWasLauncher = it }
                }
            }

            return lastWasLauncher
        } catch (e: Exception) {
            return lastWasLauncher
        }
    }

    private fun getRecentPackageName(): String? {
        val endTime = System.currentTimeMillis()
        val startTime = endTime - EVENT_QUERY_INTERVAL_MS
        val events = usageStatsManager.queryEvents(startTime, endTime)
        var recentPackage: String? = null
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                recentPackage = event.packageName
            }
        }
        return recentPackage
    }

    private fun checkAgainstLauncherPackage(packageName: String): Boolean {
        if (cachedLauncherPackage == null) updateCachedLauncherPackage()
        return packageName == cachedLauncherPackage
    }

    private fun updateCachedLauncherPackage() {
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        cachedLauncherPackage = packageManager.resolveActivity(launcherIntent, 0)?.activityInfo?.packageName
    }

    private fun hasUsageStatsPermission(): Boolean {
        val appOpsManager = getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
        val mode = appOpsManager.checkOpNoThrow(
            android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            packageName
        )
        return mode == android.app.AppOpsManager.MODE_ALLOWED
    }

    private fun notifyVisibilityResumed() {
        LocalBroadcastManager.getInstance(this).sendBroadcast(Intent(ACTION_VISIBILITY_RESUMED))
    }

    private fun updateAllWidgets() {
        val appWidgetManager = AppWidgetManager.getInstance(this)
        val providers = arrayOf(
            CpuWidgetProvider::class.java,
            BatteryWidgetProvider::class.java,
            BluetoothWidgetProvider::class.java,
            CaffeineWidget::class.java,
            SunTrackerWidget::class.java,
            NetworkSpeedWidgetProviderCircle::class.java,
            NetworkSpeedWidgetProviderPill::class.java,
            WifiDataUsageWidgetProviderCircle::class.java,
            WifiDataUsageWidgetProviderPill::class.java,
            SimDataUsageWidgetProviderCircle::class.java,
            SimDataUsageWidgetProviderPill::class.java,
            NoteWidgetProvider::class.java,
            AnalogClockWidgetProvider_1::class.java,
            AnalogClockWidgetProvider_2::class.java
        )

        providers.forEach { provider ->
            val componentName = ComponentName(this, provider)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            if (appWidgetIds.isNotEmpty()) {
                sendBroadcast(Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE).apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds)
                    component = componentName
                })
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        cancelInactiveUpdates()
        unregisterReceiver(systemReceiver)
        super.onDestroy()
    }
}