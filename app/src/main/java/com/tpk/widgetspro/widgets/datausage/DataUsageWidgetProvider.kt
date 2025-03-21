package com.tpk.widgetspro.widgets.datausage

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.net.TrafficStats
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.tpk.widgetspro.R
import com.tpk.widgetspro.services.DataUsageWidgetService
import com.tpk.widgetspro.utils.WidgetUtils
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class DataUsageWidgetProvider : AppWidgetProvider() {
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
            action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
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
        private const val KEY_BASELINE = "baseline"
        private const val KEY_DATE = "baseline_date"

        fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val currentDate = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
            var baseline = prefs.getLong(KEY_BASELINE, -1)
            val savedDate = prefs.getString(KEY_DATE, null)

            if (baseline == -1L || savedDate != currentDate) {
                baseline = TrafficStats.getTotalRxBytes() + TrafficStats.getTotalTxBytes()
                prefs.edit().apply {
                    putLong(KEY_BASELINE, baseline)
                    putString(KEY_DATE, currentDate)
                    apply()
                }
            }

            val currentBytes = TrafficStats.getTotalRxBytes() + TrafficStats.getTotalTxBytes()
            val usedBytesToday = currentBytes - baseline
            val usageText = formatBytes(usedBytesToday)

            val views = RemoteViews(context.packageName, R.layout.data_usage_widget)
            val typeface = ResourcesCompat.getFont(context, R.font.my_custom_font)!!
            val setupBitmap = WidgetUtils.createTextBitmap(
                context,
                usageText,
                20f,
                ContextCompat.getColor(context, R.color.text_color),
                typeface
            )
            views.setImageViewBitmap(R.id.data_text, setupBitmap)
            appWidgetManager.updateAppWidget(appWidgetId, views)
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