package com.tpk.widgetspro.widgets.notes

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.tpk.widgetspro.R
import com.tpk.widgetspro.utils.CommonUtils

class NoteWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId)
        }
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

            val prefs = context.getSharedPreferences("notes", Context.MODE_PRIVATE)
            val noteText = prefs.getString("note_$appWidgetId", "")
            views.setImageViewBitmap(
                R.id.note_text,
                CommonUtils.createTextNotesWidgetBitmap(context,
                    (if (noteText?.isEmpty() == true) "Tap to add note" else noteText).toString(), 20f, CommonUtils.getTypeface(context))
            )

            val intent = Intent(context, NoteInputActivity::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                appWidgetId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.notes_widget_layout, pendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}