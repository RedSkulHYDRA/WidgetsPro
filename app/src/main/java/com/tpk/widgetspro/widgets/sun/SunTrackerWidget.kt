package com.tpk.widgetspro.widgets.sun

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import com.tpk.widgetspro.R
import com.tpk.widgetspro.utils.NotificationUtils
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.math.pow

class SunTrackerWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
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
        NotificationUtils.createAppWidgetChannel(context)
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

        if (animator.isDaytime) {
            views.setViewVisibility(R.id.sun_orb, View.VISIBLE)
            views.setViewVisibility(R.id.moon_orb, View.GONE)
            val (sunX, sunY) = animator.sunTranslation
            views.setFloat(R.id.sun_orb, "setTranslationX", sunX)
            views.setFloat(R.id.sun_orb, "setTranslationY", sunY)
        } else {
            views.setViewVisibility(R.id.sun_orb, View.GONE)
            views.setViewVisibility(R.id.moon_orb, View.VISIBLE)
            val (moonX, moonY) = animator.moonTranslation
            views.setFloat(R.id.moon_orb, "setTranslationX", moonX)
            views.setFloat(R.id.moon_orb, "setTranslationY", moonY)
        }

        val currentDateTime = LocalDateTime.now()
        val sunrise = LocalDateTime.of(currentDateTime.toLocalDate(), CelestialAnimator.DAY_START)
        val sunset = LocalDateTime.of(currentDateTime.toLocalDate(), CelestialAnimator.DAY_END)
        val timeText = if (animator.isDaytime) {
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

        val isDaytime get() = currentTime.isAfter(DAY_START) && currentTime.isBefore(DAY_END)

        private val sunNormalizedTime: Float
            get() {
                val dayStartSeconds = DAY_START.toSecondOfDay().toFloat()
                val dayEndSeconds = DAY_END.toSecondOfDay().toFloat()
                val currentSeconds = currentTime.toSecondOfDay().toFloat()
                return if (currentSeconds in dayStartSeconds..dayEndSeconds) {
                    (currentSeconds - dayStartSeconds) / (dayEndSeconds - dayStartSeconds)
                } else {
                    if (currentSeconds < dayStartSeconds) 0f else 1f
                }
            }

        private val moonNormalizedTime: Float
            get() {
                val secondsInDay = 86400f
                val dayEndSeconds = DAY_END.toSecondOfDay().toFloat()
                val currentSeconds = currentTime.toSecondOfDay().toFloat()
                val timeSinceSunset = if (currentSeconds >= dayEndSeconds) {
                    currentSeconds - dayEndSeconds
                } else if (currentTime.isBefore(DAY_START)) {
                    currentSeconds + (secondsInDay - dayEndSeconds)
                } else {
                    0f
                }
                return (timeSinceSunset / (NIGHT_DURATION * 3600f)).coerceIn(0f, 1f)
            }

        private fun bezierPosition(t: Float): Pair<Float, Float> {
            val x = (1 - t).pow(2) * 40f + 2 * (1 - t) * t * 150f + t.pow(2) * 260f
            val y = (1 - t).pow(2) * 82f + 2 * (1 - t) * t * (-20f) + t.pow(2) * 82f
            return Pair(x, y)
        }

        private fun getTranslation(t: Float): Pair<Float, Float> {
            val (xVec, yVec) = bezierPosition(t)
            val xPixel = (xVec / 300f) * widgetWidth
            val yPixel = (yVec / 150f) * widgetHeight
            return Pair(xPixel - widgetWidth / 2f, yPixel - widgetHeight / 2f)
        }

        val sunTranslation get() = getTranslation(sunNormalizedTime)
        val moonTranslation get() = getTranslation(moonNormalizedTime)

        companion object {
            val DAY_START = LocalTime.of(6, 0)  // Fixed sunrise
            val DAY_END = LocalTime.of(18, 0)   // Fixed sunset
            const val NIGHT_DURATION = 12       // Hours

            fun Int.dpToPx(metrics: android.util.DisplayMetrics) = (this * metrics.density).toInt()
        }
    }
}