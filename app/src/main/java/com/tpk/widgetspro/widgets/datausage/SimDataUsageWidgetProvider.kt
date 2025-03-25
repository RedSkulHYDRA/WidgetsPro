package com.tpk.widgetspro.widgets.datausage

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.TrafficStats
import android.widget.RemoteViews
import com.tpk.widgetspro.R
import com.tpk.widgetspro.services.SimDataUsageWidgetService
import com.tpk.widgetspro.utils.CommonUtils
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class SimDataUsageWidgetProvider : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_MIDNIGHT_RESET) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(
                ComponentName(context, SimDataUsageWidgetProvider::class.java)
            )
            onUpdate(context, appWidgetManager, appWidgetIds)
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { updateAppWidget(context, appWidgetManager, it) }
        scheduleMidnightReset(context)
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        context.startForegroundService(Intent(context, SimDataUsageWidgetService::class.java))
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        context.stopService(Intent(context, SimDataUsageWidgetService::class.java))
    }

    private fun scheduleMidnightReset(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, SimDataUsageWidgetProvider::class.java).apply {
            action = ACTION_MIDNIGHT_RESET
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            SIM_DATA_USAGE_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val calendar = Calendar.getInstance().apply {
            timeInMillis = System.currentTimeMillis()
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        alarmManager.setExact(AlarmManager.RTC, calendar.timeInMillis, pendingIntent)
    }

    companion object {
        private const val PREFS_NAME = "SimDataUsagePrefs"
        private const val KEY_INITIAL_BASELINE = "sim_initial_baseline"
        private const val KEY_LAST_BASELINE = "sim_last_baseline"
        private const val KEY_ACCUMULATED = "sim_accumulated"
        private const val KEY_DATE = "sim_baseline_date"
        private const val KEY_LAST_UPDATE_TIME = "sim_last_update_time"
        private const val ACTION_MIDNIGHT_RESET = "com.tpk.widgetspro.ACTION_SIM_DATA_USAGE_RESET"
        private const val SIM_DATA_USAGE_REQUEST_CODE = 1002

        private val dateFormat = SimpleDateFormat("yyyyMMdd", Locale.getDefault())

        fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val currentDate = synchronized(dateFormat) { dateFormat.format(Date()) }
            var initialBaseline = prefs.getLong(KEY_INITIAL_BASELINE, -1L)
            var lastBaseline = prefs.getLong(KEY_LAST_BASELINE, -1L)
            var accumulated = prefs.getLong(KEY_ACCUMULATED, 0L)
            val savedDate = prefs.getString(KEY_DATE, null)
            val lastUpdateTime = prefs.getLong(KEY_LAST_UPDATE_TIME, 0L)
            val currentTime = System.currentTimeMillis()

            val mobileRx = TrafficStats.getMobileRxBytes()
            val mobileTx = TrafficStats.getMobileTxBytes()
            val totalBytes = mobileRx + mobileTx

            val views = RemoteViews(context.packageName, R.layout.sim_data_usage_widget)

            if (mobileRx == TrafficStats.UNSUPPORTED.toLong() || mobileTx == TrafficStats.UNSUPPORTED.toLong()) {
                views.setImageViewBitmap(
                    R.id.sim_data_text,
                    CommonUtils.createTextAlternateBitmap(context, "Unsupported", 20f, CommonUtils.getTypeface(context))
                )
            } else {
                val timeSinceLastUpdate = currentTime - lastUpdateTime
                val possibleReboot = timeSinceLastUpdate > 5 * 60 * 1000 && lastBaseline > totalBytes

                if (savedDate != currentDate || possibleReboot) {
                    initialBaseline = totalBytes
                    lastBaseline = totalBytes
                    accumulated = 0L
                } else if (initialBaseline == -1L || lastBaseline == -1L) {
                    initialBaseline = totalBytes
                    lastBaseline = totalBytes
                } else if (totalBytes < lastBaseline) {
                    accumulated += lastBaseline - initialBaseline
                    initialBaseline = totalBytes
                    lastBaseline = totalBytes
                } else {
                    lastBaseline = totalBytes
                }

                prefs.edit().apply {
                    putLong(KEY_INITIAL_BASELINE, initialBaseline)
                    putLong(KEY_LAST_BASELINE, lastBaseline)
                    putLong(KEY_ACCUMULATED, accumulated)
                    putString(KEY_DATE, currentDate)
                    putLong(KEY_LAST_UPDATE_TIME, currentTime)
                    apply()
                }

                val totalUsage = accumulated + (totalBytes - initialBaseline)
                views.setImageViewBitmap(
                    R.id.sim_data_text,
                    CommonUtils.createTextAlternateBitmap(context, formatBytes(totalUsage), 20f, CommonUtils.getTypeface(context))
                )
            }

            views.setInt(R.id.simImageData, "setColorFilter", CommonUtils.getAccentColor(context))
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        fun updateAllWidgets(context: Context) {
            CommonUtils.updateAllWidgets(context, SimDataUsageWidgetProvider::class.java)
        }

        private fun formatBytes(bytes: Long): String {
            val unit = 1024
            if (bytes < unit) return "$bytes B"
            val exp = minOf((Math.log(bytes.toDouble()) / Math.log(unit.toDouble())).toInt(), 5)
            val pre = "KMGTPE"[exp - 1]
            val value = bytes / Math.pow(unit.toDouble(), exp.toDouble())
            return String.format("%.1f %sB", value, pre)
        }
    }
}