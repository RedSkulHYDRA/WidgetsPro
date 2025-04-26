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
import com.tpk.widgetspro.services.notes.NoteWidgetInputService
import com.tpk.widgetspro.utils.CommonUtils

class NoteWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            appWidgetManager.updateAppWidget(appWidgetId, buildRemoteViews(context, appWidgetId))
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        appWidgetManager.updateAppWidget(appWidgetId, buildRemoteViews(context, appWidgetId))
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val prefs = context.getSharedPreferences("notes", Context.MODE_PRIVATE)
        val editor = prefs.edit()
        for (appWidgetId in appWidgetIds) {
            editor.remove("note_$appWidgetId")
            editor.remove("bullets_enabled_$appWidgetId")
        }
        editor.apply()
    }

    companion object {
        fun buildRemoteViews(context: Context, appWidgetId: Int): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.notes_widget_layout)
            val prefs = context.getSharedPreferences("notes", Context.MODE_PRIVATE)
            val noteText = prefs.getString("note_$appWidgetId", "") ?: ""
            val bulletsEnabled = prefs.getBoolean("bullets_enabled_$appWidgetId", true)

            val (displayText, headingSize, contentSize) = prepareDisplayText(context, noteText, bulletsEnabled)
            val bitmap = createWidgetBitmap(context, displayText, headingSize, contentSize, appWidgetId)

            views.setImageViewBitmap(R.id.note_text, bitmap)
            setClickIntent(context, views, appWidgetId)

            return views
        }

        private fun prepareDisplayText(
            context: Context,
            noteText: String,
            bulletsEnabled: Boolean
        ): Triple<String, Float, Float> {
            val headingSize = 20f
            val contentSize = 18f

            val displayText = when {
                noteText.isEmpty() -> context.getString(R.string.tap_to_add_notes)
                bulletsEnabled -> {
                    val lines = noteText.split("\n")
                    "${context.getString(R.string.notes_label)}\n" +
                            lines.joinToString("\n") {
                                if (it.trim().isEmpty()) "" else "• ${it.trimStart()}"
                            }
                }
                else -> "${context.getString(R.string.notes_label)}\n${noteText.trimStart()}"
            }

            return Triple(displayText, headingSize, contentSize)
        }

        private fun createWidgetBitmap(
            context: Context,
            text: String,
            headingSize: Float,
            contentSize: Float,
            appWidgetId: Int
        ) = CommonUtils.createTextNotesWidgetBitmap(
            context,
            text,
            headingSize,
            contentSize,
            CommonUtils.getTypeface(context),
            CommonUtils.getAccentColor(context),
            ContextCompat.getColor(context, R.color.text_color),
            getMinWidthPx(context, appWidgetId),
            getMinHeightPx(context, appWidgetId),
            calculateMaxLines(context, appWidgetId)
        )

        private fun getMinWidthPx(context: Context, appWidgetId: Int): Int {
            val options = AppWidgetManager.getInstance(context).getAppWidgetOptions(appWidgetId)
            return (options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH) *
                    context.resources.displayMetrics.density).toInt()
        }

        private fun getMinHeightPx(context: Context, appWidgetId: Int): Int {
            val options = AppWidgetManager.getInstance(context).getAppWidgetOptions(appWidgetId)
            return (options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT) *
                    context.resources.displayMetrics.density).toInt()
        }

        private fun calculateMaxLines(context: Context, appWidgetId: Int): Int {
            val paint = TextPaint().apply {
                typeface = CommonUtils.getTypeface(context)
                textSize = TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_SP, 18f, context.resources.displayMetrics
                )
            }

            val fontMetrics = paint.fontMetrics
            val lineHeight = fontMetrics.bottom - fontMetrics.top + fontMetrics.leading
            return (getMinHeightPx(context, appWidgetId) / lineHeight).toInt().coerceAtLeast(1)
        }

        private fun setClickIntent(context: Context, views: RemoteViews, appWidgetId: Int) {
            val intent = Intent(context, NoteWidgetInputService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }

            val pendingIntent = PendingIntent.getActivity(
                context,
                appWidgetId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.notes_widget_layout, pendingIntent)
        }
    }
}