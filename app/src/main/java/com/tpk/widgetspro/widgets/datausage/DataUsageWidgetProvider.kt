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
import com.tpk.widgetspro.services.DataUsageWidgetService
import com.tpk.widgetspro.utils.CommonUtils
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class DataUsageWidgetProvider : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_MIDNIGHT_RESET) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(
                ComponentName(context, DataUsageWidgetProvider::class.java)
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
        context.startForegroundService(Intent(context, DataUsageWidgetService::class.java))
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        context.stopService(Intent(context, DataUsageWidgetService::class.java))
    }

    private fun scheduleMidnightReset(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, DataUsageWidgetProvider::class.java).apply {
            action = ACTION_MIDNIGHT_RESET
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            DATA_USAGE_REQUEST_CODE,
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
        private const val PREFS_NAME = "DataUsagePrefs"
        private const val KEY_INITIAL_BASELINE = "wifi_initial_baseline"
        private const val KEY_LAST_BASELINE = "wifi_last_baseline"
        private const val KEY_ACCUMULATED = "wifi_accumulated"
        private const val KEY_DATE = "wifi_baseline_date"
        private const val KEY_LAST_UPDATE_TIME = "wifi_last_update_time"
        private const val ACTION_MIDNIGHT_RESET = "com.tpk.widgetspro.ACTION_DATA_USAGE_RESET"
        private const val DATA_USAGE_REQUEST_CODE = 1001

        fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val currentDate = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
            var initialBaseline = prefs.getLong(KEY_INITIAL_BASELINE, -1L)
            var lastBaseline = prefs.getLong(KEY_LAST_BASELINE, -1L)
            var accumulated = prefs.getLong(KEY_ACCUMULATED, 0L)
            val savedDate = prefs.getString(KEY_DATE, null)
            val lastUpdateTime = prefs.getLong(KEY_LAST_UPDATE_TIME, 0L)
            val currentTime = System.currentTimeMillis()

            val totalRx = TrafficStats.getTotalRxBytes()
            val totalTx = TrafficStats.getTotalTxBytes()
            val mobileRx = TrafficStats.getMobileRxBytes()
            val mobileTx = TrafficStats.getMobileTxBytes()
            val wifiBytes = if (totalRx == TrafficStats.UNSUPPORTED.toLong() ||
                totalTx == TrafficStats.UNSUPPORTED.toLong() ||
                mobileRx == TrafficStats.UNSUPPORTED.toLong() ||
                mobileTx == TrafficStats.UNSUPPORTED.toLong()) {
                0L
            } else {
                val wifiRx = if (totalRx >= mobileRx) totalRx - mobileRx else 0L
                val wifiTx = if (totalTx >= mobileTx) totalTx - mobileTx else 0L
                wifiRx + wifiTx
            }

            val timeSinceLastUpdate = currentTime - lastUpdateTime
            val possibleReboot = timeSinceLastUpdate > 5 * 60 * 1000 && lastBaseline > wifiBytes

            if (savedDate != currentDate || possibleReboot) {
                initialBaseline = wifiBytes
                lastBaseline = wifiBytes
                accumulated = 0L
            } else if (initialBaseline == -1L || lastBaseline == -1L) {
                initialBaseline = wifiBytes
                lastBaseline = wifiBytes
            } else if (wifiBytes < lastBaseline) {
                accumulated += lastBaseline - initialBaseline
                initialBaseline = wifiBytes
                lastBaseline = wifiBytes
            } else {
                lastBaseline = wifiBytes
            }

            prefs.edit().apply {
                putLong(KEY_INITIAL_BASELINE, initialBaseline)
                putLong(KEY_LAST_BASELINE, lastBaseline)
                putLong(KEY_ACCUMULATED, accumulated)
                putString(KEY_DATE, currentDate)
                putLong(KEY_LAST_UPDATE_TIME, currentTime)
                apply()
            }

            val totalUsage = accumulated + (wifiBytes - initialBaseline)

            val views = RemoteViews(context.packageName, R.layout.data_usage_widget).apply {
                setImageViewBitmap(
                    R.id.wifi_data_text,
                    CommonUtils.createTextAlternateBitmap(
                        context, formatBytes(totalUsage), 20f, CommonUtils.getTypeface(context)
                    )
                )
                setInt(R.id.wifiImageData, "setColorFilter", CommonUtils.getAccentColor(context))
            }
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        fun updateAllWidgets(context: Context) {
            CommonUtils.updateAllWidgets(context, DataUsageWidgetProvider::class.java)
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