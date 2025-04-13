package com.tpk.widgetspro.services

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.tpk.widgetspro.MainActivity
import com.tpk.widgetspro.R
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.KeyguardManager
import android.app.usage.UsageStatsManager
import android.os.Handler
import android.os.Looper
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import java.util.concurrent.TimeUnit

abstract class BaseMonitorService : Service() {
    companion object {
        private const val WIDGETS_PRO_NOTIFICATION_ID = 100
        private const val CHANNEL_ID = "widgets_pro_channel"
        const val ACTION_VISIBILITY_RESUMED = "com.tpk.widgetspro.VISIBILITY_RESUMED"
        private const val FORCE_LAUNCHER_TIMEOUT_MS = 15000L
    }

    private var powerManager: PowerManager? = null
    private var keyguardManager: KeyguardManager? = null
    private var usageStatsManager: UsageStatsManager? = null
    private var handler: Handler? = null
    private var cachedLauncherPackage: String? = null
    private var forceLauncherForeground = false
    private var lastWasLauncher = true

    private val forceLauncherRunnable = Runnable {
        forceLauncherForeground = false
        android.util.Log.d("BaseMonitorService", "Force launcher foreground timeout")
    }

    private val systemReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> {
                    android.util.Log.d("BaseMonitorService", "Received ${intent.action}, checking visibility")
                    if (shouldUpdate()) {
                        notifyVisibilityResumed()
                    }
                }
                Intent.ACTION_CLOSE_SYSTEM_DIALOGS -> {
                    android.util.Log.d("BaseMonitorService", "Received ACTION_CLOSE_SYSTEM_DIALOGS, forcing launcher foreground")
                    forceLauncherForeground = true
                    lastWasLauncher = true
                    handler?.removeCallbacks(forceLauncherRunnable)
                    handler?.postDelayed(forceLauncherRunnable, FORCE_LAUNCHER_TIMEOUT_MS)
                    if (shouldUpdate()) {
                        notifyVisibilityResumed()
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        powerManager = getSystemService(POWER_SERVICE) as PowerManager
        keyguardManager = getSystemService(KEYGUARD_SERVICE) as KeyguardManager
        usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        handler = Handler(Looper.getMainLooper())

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)
        }
        registerReceiver(systemReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        android.util.Log.d("BaseMonitorService", "Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(WIDGETS_PRO_NOTIFICATION_ID, createNotification())
        return START_STICKY
    }

    private fun createNotification(): Notification {
        val channelName = "Widgets Pro Channel"
        val channel = NotificationChannel(CHANNEL_ID, channelName, NotificationManager.IMPORTANCE_MIN).apply {
            description = "Channel for keeping Widgets Pro services running"
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)

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

    override fun onBind(intent: Intent?): IBinder? = null

    protected fun shouldUpdate(): Boolean {
        val isScreenOn = powerManager?.isInteractive ?: false
        val isKeyguardLocked = keyguardManager?.isKeyguardLocked ?: false
        val isLauncherInForeground = isLauncherForeground()
        val shouldUpdate = isScreenOn && !isKeyguardLocked && isLauncherInForeground
        android.util.Log.d(
            "BaseMonitorService",
            "shouldUpdate: screenOn=$isScreenOn, keyguardLocked=$isKeyguardLocked, launcherForeground=$isLauncherInForeground, result=$shouldUpdate"
        )
        return shouldUpdate
    }

    private fun isLauncherForeground(): Boolean {
        if (forceLauncherForeground) {
            android.util.Log.d("BaseMonitorService", "Force launcher foreground active")
            return true
        }
        try {
            if (!hasUsageStatsPermission()) {
                android.util.Log.d("BaseMonitorService", "No usage stats permission, using last known state: $lastWasLauncher")
                return lastWasLauncher
            }
            val endTime = System.currentTimeMillis()
            val startTime = endTime - TimeUnit.SECONDS.toMillis(20)
            val usageStats = usageStatsManager?.queryUsageStats(
                UsageStatsManager.INTERVAL_BEST,
                startTime,
                endTime
            )

            if (usageStats == null || usageStats.isEmpty()) {
                android.util.Log.w("BaseMonitorService", "Usage stats empty, using last known state: $lastWasLauncher")
                return lastWasLauncher
            }

            if (cachedLauncherPackage == null) {
                val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                }
                val resolveInfo = packageManager.resolveActivity(launcherIntent, 0)
                cachedLauncherPackage = resolveInfo?.activityInfo?.packageName
                android.util.Log.d("BaseMonitorService", "Cached launcher package: $cachedLauncherPackage")
            }

            val recentStat = usageStats.maxByOrNull { it.lastTimeUsed }
            val isSystemUi = recentStat?.packageName == "com.android.systemui"
            val isLauncher = recentStat?.packageName == cachedLauncherPackage || isSystemUi
            if (isSystemUi) {
                android.util.Log.d("BaseMonitorService", "System UI detected, treating as launcher")
            }
            lastWasLauncher = isLauncher
            android.util.Log.d(
                "BaseMonitorService",
                "Launcher check: recent=${recentStat?.packageName}, launcher=$cachedLauncherPackage, isSystemUi=$isSystemUi, result=$isLauncher"
            )
            return isLauncher
        } catch (e: Exception) {
            android.util.Log.e("BaseMonitorService", "Error checking launcher: $e")
            return lastWasLauncher
        }
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
        android.util.Log.d("BaseMonitorService", "Notifying visibility resumed")
        val intent = Intent(ACTION_VISIBILITY_RESUMED)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    override fun onDestroy() {
        unregisterReceiver(systemReceiver)
        handler?.removeCallbacks(forceLauncherRunnable)
        android.util.Log.d("BaseMonitorService", "Service destroyed")
        super.onDestroy()
    }
}