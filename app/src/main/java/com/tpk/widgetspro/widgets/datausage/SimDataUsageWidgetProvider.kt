package com.tpk.widgetspro.widgets.datausage

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
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
        val intent = Intent(context, SimDataUsageWidgetProvider::class.java).apply { action = AppWidgetManager.ACTION_APPWIDGET_UPDATE }
        val pendingIntent = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
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
        private const val KEY_INITIAL_BASELINE = "initial_baseline"
        private const val KEY_LAST_BASELINE = "last_baseline"
        private const val KEY_ACCUMULATED = "accumulated"
        private const val KEY_DATE = "baseline_date"

        fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val currentDate = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
            var initialBaseline = prefs.getLong(KEY_INITIAL_BASELINE, -1)
            var lastBaseline = prefs.getLong(KEY_LAST_BASELINE, -1)
            var accumulated = prefs.getLong(KEY_ACCUMULATED, 0)
            val savedDate = prefs.getString(KEY_DATE, null)
            val currentRxBytes = TrafficStats.getMobileRxBytes()
            val currentTxBytes = TrafficStats.getMobileTxBytes()
            val totalBytes = currentRxBytes+currentTxBytes

            if (savedDate != currentDate) {
                initialBaseline = totalBytes
                lastBaseline = totalBytes
                accumulated = 0
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
                apply()
            }

            val totalUsage = accumulated + (totalBytes - initialBaseline)
            val views = RemoteViews(context.packageName, R.layout.sim_data_usage_widget).apply {
                setImageViewBitmap(R.id.sim_data_text, CommonUtils.createTextAlternateBitmap(context, formatBytes(totalUsage), 20f, CommonUtils.getTypeface(context)))
                setInt(R.id.imageData, "setColorFilter", CommonUtils.getAccentColor(context))
            }
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        fun updateAllWidgets(context: Context) {
            CommonUtils.updateAllWidgets(context, SimDataUsageWidgetProvider::class.java)
        }

        private fun formatBytes(bytes: Long): String {
            val unit = 1024
            if (bytes < unit) return "$bytes B"
            val exp = (Math.log(bytes.toDouble()) / Math.log(unit.toDouble())).toInt()
            val pre = "KMGTPE"[exp - 1]
            val value = bytes / Math.pow(unit.toDouble(), exp.toDouble())
            return String.format("%.1f %sB", value, pre)
        }
    }
}