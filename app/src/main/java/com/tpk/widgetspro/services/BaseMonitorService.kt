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
import java.util.concurrent.atomic.AtomicBoolean

abstract class BaseMonitorService : Service() {
    companion object {
        const val WIDGETS_PRO_NOTIFICATION_ID = 100
        private const val CHANNEL_ID = "widgets_pro_channel"
        const val ACTION_VISIBILITY_RESUMED = "com.tpk.widgetspro.VISIBILITY_RESUMED"
        private const val NORMAL_QUERY_INTERVAL = 5000L
        private const val IDLE_QUERY_INTERVAL = 15000L
        private const val MIN_UPDATE_INTERVAL = 30000L
        private const val ACTION_WALLPAPER_CHANGED_STRING = "android.intent.action.WALLPAPER_CHANGED"
    }

    private lateinit var powerManager: PowerManager
    private lateinit var keyguardManager: KeyguardManager
    private lateinit var usageStatsManager: UsageStatsManager
    private var cachedLauncherPackage: String? = null
    private val isMonitoring = AtomicBoolean(false)
    private var lastUpdateTime = 0L
    private val handler = Handler(Looper.getMainLooper())

    private val systemReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> stopMonitoring()
                Intent.ACTION_SCREEN_ON -> startDelayedMonitoring()
                Intent.ACTION_USER_PRESENT -> handleUserPresent()
                Intent.ACTION_CLOSE_SYSTEM_DIALOGS -> handleSystemDialogClose()
                Intent.ACTION_CONFIGURATION_CHANGED -> updateAllWidgets()
                ACTION_WALLPAPER_CHANGED_STRING -> updateAllWidgets()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        initServices()
        setupReceivers()
        createNotificationChannel()
    }

    private fun initServices() {
        powerManager = getSystemService(POWER_SERVICE) as PowerManager
        keyguardManager = getSystemService(KEYGUARD_SERVICE) as KeyguardManager
        usageStatsManager = getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager
        updateCachedLauncherPackage()
    }

    private fun setupReceivers() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)
            addAction(Intent.ACTION_CONFIGURATION_CHANGED)
            addAction(ACTION_WALLPAPER_CHANGED_STRING)
        }
        registerReceiver(systemReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
    }

    private fun handleUserPresent() {
        if (shouldUpdate()) {
            notifyVisibilityResumed()
            startImmediateMonitoring()
        }
    }

    private fun handleSystemDialogClose() {
        updateCachedLauncherPackage()
        if (shouldUpdate()) {
            notifyVisibilityResumed()
            startDelayedMonitoring()
        }
    }

    private fun startImmediateMonitoring() {
        if (isMonitoring.compareAndSet(false, true)) {
            handler.post(monitoringRunnable)
        }
    }

    private fun startDelayedMonitoring() {
        if (isMonitoring.compareAndSet(false, true)) {
            handler.postDelayed(monitoringRunnable, 1000)
        }
    }

    private fun stopMonitoring() {
        isMonitoring.set(false)
        handler.removeCallbacks(monitoringRunnable)
    }

    private val monitoringRunnable = object : Runnable {
        override fun run() {
            if (!isMonitoring.get()) return

            val currentTime = System.currentTimeMillis()
            if (currentTime - lastUpdateTime > MIN_UPDATE_INTERVAL) {
                if (shouldUpdate()) {
                    notifyVisibilityResumed()
                    lastUpdateTime = currentTime
                }
            }

            val interval = when {
                powerManager.isInteractive -> NORMAL_QUERY_INTERVAL
                else -> IDLE_QUERY_INTERVAL
            }

            handler.postDelayed(this, interval)
        }
    }

    protected fun shouldUpdate(): Boolean {
        return powerManager.isInteractive &&
                !keyguardManager.isKeyguardLocked &&
                isLauncherForeground()
    }

    private fun isLauncherForeground(): Boolean {
        try {
            if (!hasUsageStatsPermission()) return lastWasLauncher

            val recentPackage = getRecentPackageName()
            if (recentPackage != null) {
                return checkAgainstLauncherPackage(recentPackage).also { lastWasLauncher = it }
            }

            val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val runningProcesses = am.runningAppProcesses ?: return lastWasLauncher

            runningProcesses.firstOrNull {
                it.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
            }?.pkgList?.firstOrNull()?.let {
                return checkAgainstLauncherPackage(it).also { lastWasLauncher = it }
            }

            return lastWasLauncher
        } catch (e: Exception) {
            return lastWasLauncher
        }
    }

    private var lastWasLauncher = true

    private fun getRecentPackageName(): String? {
        val endTime = System.currentTimeMillis()
        val startTime = endTime - NORMAL_QUERY_INTERVAL
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
        cachedLauncherPackage = packageManager.resolveActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
            0
        )?.activityInfo?.packageName
    }

    private fun hasUsageStatsPermission(): Boolean {
        val appOpsManager = getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
        return appOpsManager.checkOpNoThrow(
            android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            packageName
        ) == android.app.AppOpsManager.MODE_ALLOWED
    }

    private fun notifyVisibilityResumed() {
        LocalBroadcastManager.getInstance(this).sendBroadcast(Intent(ACTION_VISIBILITY_RESUMED))
    }

    private fun updateAllWidgets() {
        val appWidgetManager = AppWidgetManager.getInstance(this)
        val providers = arrayOf(
            AnalogClockWidgetProvider_1::class.java,
            AnalogClockWidgetProvider_2::class.java,
            BatteryWidgetProvider::class.java,
            BluetoothWidgetProvider::class.java,
            CaffeineWidget::class.java,
            CpuWidgetProvider::class.java,
            NetworkSpeedWidgetProviderCircle::class.java,
            NetworkSpeedWidgetProviderPill::class.java,
            WifiDataUsageWidgetProviderCircle::class.java,
            WifiDataUsageWidgetProviderPill::class.java,
            SimDataUsageWidgetProviderCircle::class.java,
            SimDataUsageWidgetProviderPill::class.java,
            NoteWidgetProvider::class.java,
            SunTrackerWidget::class.java
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

    private fun createNotificationChannel() {
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Widgets Pro Channel",
                NotificationManager.IMPORTANCE_MIN
            ).apply { description = "Channel for Widgets Pro services" }
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(WIDGETS_PRO_NOTIFICATION_ID, createNotification())
        return START_STICKY
    }

    fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.widgets_pro_running))
            .setContentText(getString(R.string.widgets_pro_active_text))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(PendingIntent.getActivity(
                this, 0, Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            ))
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopMonitoring()
        unregisterReceiver(systemReceiver)
        super.onDestroy()
    }
}