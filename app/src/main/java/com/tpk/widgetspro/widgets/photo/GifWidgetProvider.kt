package com.tpk.widgetspro.widgets.photo

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import com.tpk.widgetspro.services.AnimationService

class GifWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { appWidgetId ->
            val prefs = context.getSharedPreferences("gif_widget_prefs", Context.MODE_PRIVATE)
            val uriString = prefs.getString("file_uri_$appWidgetId", null)
            if (!prefs.contains("widget_index_$appWidgetId")) {
                val currentIndices = prefs.all.keys
                    .filter { it.startsWith("widget_index_") }
                    .mapNotNull { prefs.getInt(it, 0) }
                val newIndex = (currentIndices.maxOrNull() ?: 0) + 1
                prefs.edit().putInt("widget_index_$appWidgetId", newIndex).apply()
            }
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
        super.onDeleted(context, appWidgetIds)
        appWidgetIds.forEach { appWidgetId ->
            val prefs = context.getSharedPreferences("gif_widget_prefs", Context.MODE_PRIVATE)
            prefs.edit()
                .remove("file_uri_$appWidgetId")
                .remove("widget_index_$appWidgetId")
                .apply()

            val intent = Intent(context, AnimationService::class.java).apply {
                putExtra("action", "REMOVE_WIDGET")
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            context.startService(intent)
        }
    }
}