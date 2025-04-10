package com.tpk.widgetspro.widgets.photo

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import androidx.preference.PreferenceManager

class GifAppWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val uriString = prefs.getString("selected_file_uri", null)
        appWidgetIds.forEach { appWidgetId ->
            val intent = Intent(context, AnimationService::class.java).apply {
                putExtra("action", "ADD_WIDGET")
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                if (uriString != null) {
                    putExtra("file_uri", uriString)
                }
            }
            context.startService(intent)
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        appWidgetIds.forEach { appWidgetId ->
            val intent = Intent(context, AnimationService::class.java).apply {
                putExtra("action", "REMOVE_WIDGET")
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            context.startService(intent)
        }
    }
}