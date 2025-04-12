package com.tpk.widgetspro.utils

import android.app.AlarmManager
import android.app.AppOpsManager
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
import android.graphics.Rect
import android.graphics.Typeface
import android.os.Build
import android.text.Layout
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.tpk.widgetspro.R
import java.util.Calendar

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
        val textBounds = Rect()
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
        val textBounds = Rect()
        paint.getTextBounds(text, 0, text.length, textBounds)
        val width = paint.measureText(text).toInt()
        val height = textBounds.height()
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawText(text, 0f, height - textBounds.bottom.toFloat(), paint)
        return bitmap
    }

    internal fun createTextNotesWidgetBitmap(
        context: Context,
        text: String,
        headingTextSizeSp: Float,
        contentTextSizeSp: Float,
        typeface: Typeface,
        accentColor: Int,
        textColor: Int,
        widthPx: Int,
        heightPx: Int,
        maxLines: Int
    ): Bitmap {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            textSize = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP, maxOf(headingTextSizeSp, contentTextSizeSp), context.resources.displayMetrics
            )
            color = textColor
        }

        if (text.isEmpty()) {
            return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        }

        val spannable = SpannableStringBuilder(text)
        val firstNewlineIndex = text.indexOf('\n')
        val headingEnd = if (firstNewlineIndex != -1) firstNewlineIndex else text.length
        val contentStart = if (firstNewlineIndex != -1 && firstNewlineIndex + 1 < text.length && text[firstNewlineIndex + 1] == '\n') {
            firstNewlineIndex + 2
        } else if (firstNewlineIndex != -1) {
            firstNewlineIndex + 1
        } else {
            text.length
        }

        if (headingEnd > 0) {
            spannable.setSpan(
                AbsoluteSizeSpan(TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_SP, headingTextSizeSp, context.resources.displayMetrics).toInt()),
                0,
                headingEnd,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            spannable.setSpan(
                ForegroundColorSpan(accentColor),
                0,
                headingEnd,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        if (contentStart < text.length) {
            spannable.setSpan(
                AbsoluteSizeSpan(TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_SP, contentTextSizeSp, context.resources.displayMetrics).toInt()),
                contentStart,
                text.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            spannable.setSpan(
                ForegroundColorSpan(textColor),
                contentStart,
                text.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        val staticLayout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            StaticLayout.Builder.obtain(spannable, 0, spannable.length, paint, widthPx)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(4f, 1.2f)
                .setIncludePad(false)
                .setMaxLines(maxLines)
                .build()
        } else {
            @Suppress("DEPRECATION")
            val tempLayout = StaticLayout(spannable, paint, widthPx, Layout.Alignment.ALIGN_NORMAL, 1.2f, 4f, false)
            if (tempLayout.lineCount > maxLines) {
                val endIndex = tempLayout.getLineEnd(maxLines - 1)
                val truncatedSpannable = SpannableStringBuilder(spannable.subSequence(0, endIndex))
                StaticLayout(truncatedSpannable, paint, widthPx, Layout.Alignment.ALIGN_NORMAL, 1.2f, 4f, false)
            } else {
                tempLayout
            }
        }

        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        staticLayout.draw(canvas)
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

    fun hasUsageAccessPermission(context: Context): Boolean {
        val appOpsManager = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOpsManager.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun getTypeface(context: Context): Typeface = ResourcesCompat.getFont(context, R.font.ndot)!!
}