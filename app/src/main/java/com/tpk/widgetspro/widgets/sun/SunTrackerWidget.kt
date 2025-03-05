package com.tpk.widgetspro.widgets.sun

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import com.tpk.widgetspro.R
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.math.PI
import kotlin.math.sin

class SunTrackerWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // Start the sync service to schedule updates
        SunSyncService.start(context)
        appWidgetIds.forEach { updateWidget(context, appWidgetManager, it) }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle?
    ) {
        updateWidget(context, appWidgetManager, appWidgetId)
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        SunSyncService.start(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        context.stopService(Intent(context, SunSyncService::class.java))
    }

    private fun updateWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_sun_tracker)
        val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
        val now = LocalTime.now()
        val animator = CelestialAnimator(options, context, now)

        // Update Sun/Moon visibility and position
        if (animator.isDaytime) {
            views.setViewVisibility(R.id.sun_orb, View.VISIBLE)
            views.setViewVisibility(R.id.moon_orb, View.GONE)
            views.setFloat(R.id.sun_orb, "setTranslationX", animator.sunX)
            views.setFloat(R.id.sun_orb, "setTranslationY", animator.sunY)
        } else {
            views.setViewVisibility(R.id.sun_orb, View.GONE)
            views.setViewVisibility(R.id.moon_orb, View.VISIBLE)
            views.setFloat(R.id.moon_orb, "setTranslationX", animator.moonX)
            views.setFloat(R.id.moon_orb, "setTranslationY", animator.moonY)
        }

        // Calculate time until sunset or sunrise using fixed times
        val currentDateTime = LocalDateTime.now()
        val sunrise = LocalDateTime.of(currentDateTime.toLocalDate(), CelestialAnimator.DAY_START)
        val sunset = LocalDateTime.of(currentDateTime.toLocalDate(), CelestialAnimator.DAY_END)
        val isDaytime = now.isAfter(CelestialAnimator.DAY_START) && now.isBefore(CelestialAnimator.DAY_END)

        val timeText = if (isDaytime) {
            val duration = Duration.between(currentDateTime, sunset)
            formatDuration(duration, "sunset")
        } else {
            val nextSunrise = if (currentDateTime.isBefore(sunrise)) sunrise else sunrise.plusDays(1)
            val duration = Duration.between(currentDateTime, nextSunrise)
            formatDuration(duration, "sunrise")
        }
        views.setTextViewText(R.id.time_until_text, timeText)

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    private fun formatDuration(duration: Duration, event: String): String {
        val hours = duration.toHours()
        val minutes = duration.toMinutes() % 60
        return "${hours}h ${minutes}m until $event"
    }

    private class CelestialAnimator(
        private val widgetOptions: Bundle,
        private val context: Context,
        private val currentTime: LocalTime
    ) {
        private val displayMetrics = context.resources.displayMetrics
        private val widgetWidth = widgetOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
            .dpToPx(displayMetrics)
        private val widgetHeight = widgetOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
            .dpToPx(displayMetrics)

        val isDaytime get() = !currentTime.isBefore(DAY_START) && currentTime.isBefore(DAY_END)

        private val sunNormalizedTime
            get() = currentTime.let {
                ((it.toSecondOfDay() - DAY_START.toSecondOfDay()).toFloat() /
                        (DAY_END.toSecondOfDay() - DAY_START.toSecondOfDay())).coerceIn(0f..1f)
            }

        private val moonNormalizedTime
            get() = currentTime.let {
                val base = if (it.isBefore(DAY_END)) it.plusHours((24 - DAY_END.hour).toLong()) else it
                ((base.toSecondOfDay() - DAY_END.toSecondOfDay()).toFloat() /
                        (NIGHT_DURATION * 3600)).coerceIn(0f..1f)
            }

        private val offsetPx = (20f * displayMetrics.density).toInt()

        val sunX get() = (widgetWidth * 0.9f * sunNormalizedTime) - (widgetWidth * 0.45f)
        val sunY get() = -(widgetHeight * 0.4f) * sin(sunNormalizedTime * PI).toFloat()+offsetPx

        val moonX get() = (widgetWidth * 0.9f * moonNormalizedTime) - (widgetWidth * 0.45f)
        val moonY get() = -(widgetHeight * 0.4f) * sin(moonNormalizedTime * PI).toFloat()+offsetPx

        companion object {
            val DAY_START = LocalTime.of(6, 0)  // Fixed sunrise at 6 AM
            val DAY_END = LocalTime.of(18, 0)   // Fixed sunset at 6 PM
            const val NIGHT_DURATION = 12

            fun Int.dpToPx(metrics: android.util.DisplayMetrics) = (this * metrics.density).toInt()
        }
    }

    companion object {
        fun triggerUpdate(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, SunTrackerWidget::class.java)
            val ids = manager.getAppWidgetIds(component)
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, SunTrackerWidget::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + 15 * 60 * 1000, // Update every 15 minutes
                pendingIntent
            )
        }
    }
}