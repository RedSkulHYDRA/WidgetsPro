package com.tpk.widgetspro.widgets.sun

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.tpk.widgetspro.R
import com.tpk.widgetspro.utils.NotificationUtils
import com.tpk.widgetspro.widgets.sun.SunTrackerWidget.CelestialAnimator.Companion.dpToPx
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
        val animator = CelestialAnimator(options, context)

        val widgetWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
            .dpToPx(context.resources.displayMetrics)
        val widgetHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
            .dpToPx(context.resources.displayMetrics)
        val pathBitmap = generatePathBitmap(context, widgetWidth, widgetHeight)
        views.setImageViewBitmap(R.id.path_view, pathBitmap)

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

        // Fetch data from shared preferences (assuming times are in local time)
        val prefs = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
        val sunriseTimeStr = prefs.getString("sunrise_time_today", "06:00") ?: "06:00"
        val sunsetTimeStr = prefs.getString("sunset_time_today", "18:00") ?: "18:00"
        val sunriseTomorrowTimeStr = prefs.getString("sunrise_time_tomorrow", "06:00") ?: "06:00"

        // Use device's local time zone for all calculations
        val currentDateTime = LocalDateTime.now()
        val currentDate = currentDateTime.toLocalDate()

        // Parse sunrise and sunset times (assuming they are in local time)
        val sunriseTime = LocalTime.parse(sunriseTimeStr)
        val sunsetTime = LocalTime.parse(sunsetTimeStr)
        val sunriseTomorrowTime = LocalTime.parse(sunriseTomorrowTimeStr)

        // Construct LocalDateTime objects using the device's local date
        val sunriseToday = LocalDateTime.of(currentDate, sunriseTime)
        val sunsetToday = LocalDateTime.of(currentDate, sunsetTime)
        val sunriseTomorrow = LocalDateTime.of(currentDate.plusDays(1), sunriseTomorrowTime)

        // Fetch temperature (assuming it's already handled correctly)
        val temperature = prefs.getFloat("current_temperature", 0f)

        // Calculate time until next event
        val timeText = if (animator.isDaytime) {
            val duration = Duration.between(currentDateTime, sunsetToday)
            formatDuration(duration, "sunset")
        } else {
            val nextSunrise = if (currentDateTime.isBefore(sunriseToday)) sunriseToday else sunriseTomorrow
            val duration = Duration.between(currentDateTime, nextSunrise)
            formatDuration(duration, "sunrise")
        }

        // Update widget views
        views.setTextViewText(R.id.time_until_text, timeText)
        views.setTextViewText(R.id.temp, "$temperature°C")
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    private fun generatePathBitmap(context: Context, width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint().apply {
            color = ContextCompat.getColor(context, R.color.text_color)
            strokeWidth = 2f
            style = Paint.Style.STROKE
        }
        val path = Path()
        val p0 = Pair(40f / 300f * width, 82f / 150f * height)
        val p1 = Pair(150f / 300f * width, (-20f) / 150f * height)
        val p2 = Pair(260f / 300f * width, 82f / 150f * height)
        path.moveTo(p0.first, p0.second)
        path.quadTo(p1.first, p1.second, p2.first, p2.second)
        canvas.drawPath(path, paint)
        return bitmap
    }

    private fun formatDuration(duration: Duration, event: String): String {
        val hours = duration.toHours()
        val minutes = duration.toMinutes() % 60
        return if (duration.isNegative || duration.isZero) {
            "Now"
        } else {
            "${hours}h ${minutes}m until $event"
        }
    }

    private class CelestialAnimator(
        private val widgetOptions: Bundle,
        private val context: Context
    ) {
        private val displayMetrics = context.resources.displayMetrics
        private val widgetWidth = widgetOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
            .dpToPx(displayMetrics)
        private val widgetHeight = widgetOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
            .dpToPx(displayMetrics)

        private val prefs = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
        private val sunriseTime = LocalTime.parse(prefs.getString("sunrise_time_today", "06:00"))
        private val sunsetTime = LocalTime.parse(prefs.getString("sunset_time_today", "18:00"))
        private val sunriseTomorrowTime = LocalTime.parse(prefs.getString("sunrise_time_tomorrow", "06:00"))

        // Use device's local time
        private val currentTime = LocalTime.now()

        val isDaytime get() = currentTime.isAfter(sunriseTime) && currentTime.isBefore(sunsetTime)

        private val sunNormalizedTime: Float
            get() {
                val dayStartSeconds = sunriseTime.toSecondOfDay().toFloat()
                val dayEndSeconds = sunsetTime.toSecondOfDay().toFloat()
                val currentSeconds = currentTime.toSecondOfDay().toFloat()
                return if (currentSeconds in dayStartSeconds..dayEndSeconds) {
                    (currentSeconds - dayStartSeconds) / (dayEndSeconds - dayStartSeconds)
                } else {
                    if (currentSeconds < dayStartSeconds) 0f else 1f
                }
            }

        private val totalNightDuration: Float
            get() {
                val sunsetSeconds = sunsetTime.toSecondOfDay().toFloat()
                val sunriseTomorrowSeconds = sunriseTomorrowTime.toSecondOfDay().toFloat()
                return (86400f - sunsetSeconds) + sunriseTomorrowSeconds
            }

        private val moonNormalizedTime: Float
            get() {
                val currentSeconds = currentTime.toSecondOfDay().toFloat()
                val sunsetSeconds = sunsetTime.toSecondOfDay().toFloat()
                val sunriseSeconds = sunriseTime.toSecondOfDay().toFloat()
                val timeSinceSunset: Float = if (currentSeconds >= sunsetSeconds) {
                    currentSeconds - sunsetSeconds
                } else if (currentSeconds <= sunriseSeconds) {
                    (86400f - sunsetSeconds) + currentSeconds
                } else {
                    0f
                }
                return (timeSinceSunset / totalNightDuration).coerceIn(0f, 1f)
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
            fun Int.dpToPx(metrics: android.util.DisplayMetrics) = (this * metrics.density).toInt()
        }
    }
}