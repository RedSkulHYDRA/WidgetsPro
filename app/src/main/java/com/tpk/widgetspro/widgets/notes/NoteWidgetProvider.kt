package com.tpk.widgetspro.widgets.notes

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.TextPaint
import android.util.TypedValue
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.tpk.widgetspro.R
import com.tpk.widgetspro.utils.CommonUtils

class NoteWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle
    ) {
        updateWidget(context, appWidgetManager, appWidgetId)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val prefs = context.getSharedPreferences("notes", Context.MODE_PRIVATE)
        val editor = prefs.edit()
        for (appWidgetId in appWidgetIds) {
            editor.remove("note_$appWidgetId")
        }
        editor.apply()
    }

    companion object {
        fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.notes_widget_layout)

            val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
            val minWidthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
            val minHeightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
            val density = context.resources.displayMetrics.density
            val minWidthPx = (minWidthDp * density).toInt()
            val minHeightPx = (minHeightDp * density).toInt()

            val paint = TextPaint()
            paint.typeface = CommonUtils.getTypeface(context)
            val contentTextSizeSp = 18f
            val headingTextSizeSp = 20f
            paint.textSize = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP, maxOf(contentTextSizeSp, headingTextSizeSp), context.resources.displayMetrics
            )
            val fontMetrics = paint.fontMetrics
            val lineHeight = fontMetrics.bottom - fontMetrics.top + fontMetrics.leading
            val maxLines = if (lineHeight > 0) (minHeightPx / lineHeight).toInt() else 1

            val prefs = context.getSharedPreferences("notes", Context.MODE_PRIVATE)
            val noteText = prefs.getString("note_$appWidgetId", "") ?: ""
            val displayText = if (noteText.isNotEmpty()) {
                "${context.getString(R.string.notes_label)}\n$noteText"
            } else {
                context.getString(R.string.tap_to_add_notes)
            }

            val bitmap = CommonUtils.createTextNotesWidgetBitmap(
                context,
                displayText,
                headingTextSizeSp,
                contentTextSizeSp,
                CommonUtils.getTypeface(context),
                CommonUtils.getAccentColor(context),
                ContextCompat.getColor(context, R.color.text_color),
                minWidthPx,
                minHeightPx,
                maxLines
            )

            views.setImageViewBitmap(R.id.note_text, bitmap)

            val intent = Intent(context, NoteInputActivity::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            val pendingIntent = PendingIntent.getActivity(
                context, appWidgetId, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.notes_widget_layout, pendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

    }
}