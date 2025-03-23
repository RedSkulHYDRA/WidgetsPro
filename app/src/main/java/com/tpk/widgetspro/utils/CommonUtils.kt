package com.tpk.widgetspro.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.util.TypedValue
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.tpk.widgetspro.R

object CommonUtils {
    fun getAccentColor(context: Context): Int {
        val prefs = context.getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)
        val isDarkTheme = prefs.getBoolean("dark_theme", false)
        val isRedAccent = prefs.getBoolean("red_accent", false)
        return when {
            isDarkTheme && isRedAccent -> ContextCompat.getColor(context, R.color.accent_color1)
            isDarkTheme -> ContextCompat.getColor(context, R.color.accent_color)
            isRedAccent -> ContextCompat.getColor(context, R.color.accent_color1)
            else -> ContextCompat.getColor(context, R.color.accent_color)
        }
    }

    fun createTextBitmap(
        context: Context,
        text: String,
        textSizeSp: Float,
        typeface: Typeface
    ): Bitmap {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP,
                textSizeSp,
                context.resources.displayMetrics
            )
            color = getAccentColor(context)
            this.typeface = typeface
            textAlign = Paint.Align.LEFT
        }
        val textBounds = android.graphics.Rect()
        paint.getTextBounds(text, 0, text.length, textBounds)
        val width = paint.measureText(text).toInt()
        val height = textBounds.height()
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawText(text, 0f, height - textBounds.bottom.toFloat(), paint)
        return bitmap
    }

    fun createTextAlternateBitmap(
        context: Context,
        text: String,
        textSizeSp: Float,
        typeface: Typeface
    ): Bitmap {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP,
                textSizeSp,
                context.resources.displayMetrics
            )
            color = ContextCompat.getColor(context, R.color.text_color)
            this.typeface = typeface
            textAlign = Paint.Align.LEFT
        }
        val textBounds = android.graphics.Rect()
        paint.getTextBounds(text, 0, text.length, textBounds)
        val width = paint.measureText(text).toInt()
        val height = textBounds.height()
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawText(text, 0f, height - textBounds.bottom.toFloat(), paint)
        return bitmap
    }

    fun getPendingIntent(context: Context, appWidgetId: Int, destination: Class<*>): PendingIntent =
        PendingIntent.getActivity(
            context,
            appWidgetId,
            Intent(context, destination).putExtra("appWidgetId", appWidgetId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    fun updateAllWidgets(context: Context, providerClass: Class<*>) {
        val manager = AppWidgetManager.getInstance(context)
        val component = ComponentName(context, providerClass)
        val appWidgetIds = manager.getAppWidgetIds(component)
        if (appWidgetIds.isNotEmpty()) {
            val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds)
                setComponent(component)
            }
            context.sendBroadcast(intent)
        }
    }

    fun getTypeface(context: Context): Typeface = ResourcesCompat.getFont(context, R.font.ndot)!!
}

object NotificationUtils {
    const val CHANNEL_ID = "widget_monitor_channel"

    fun createChannel(context: Context) {

        val channel =
            NotificationChannel(CHANNEL_ID, "Widget Monitor", NotificationManager.IMPORTANCE_LOW)
                .apply { description = "System resource monitoring" }
        context.getSystemService(NotificationManager::class.java)
            ?.createNotificationChannel(channel)

    }
}