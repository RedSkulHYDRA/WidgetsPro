package com.tpk.widgetspro.widgets.gif

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import com.tpk.widgetspro.services.gif.AnimationService

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
                    .mapNotNull { key ->
                        try { prefs.getInt(key, 0) } catch (e: ClassCastException) { null }
                    }
                val newIndex = (currentIndices.maxOrNull() ?: 0) + 1
                prefs.edit().putInt("widget_index_$appWidgetId", newIndex).apply()
            }

            val intent = Intent(context, AnimationService::class.java).apply {
                action = "ADD_WIDGET"
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                uriString?.let { putExtra("file_uri", it) }
            }
            try {
                context.startForegroundService(intent)
            } catch (e: Exception) {
                context.startService(intent)
            }
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        appWidgetIds.forEach { appWidgetId ->
            val prefs = context.getSharedPreferences("gif_widget_prefs", Context.MODE_PRIVATE)
            prefs.edit()
                .remove("file_uri_$appWidgetId")
                .remove("widget_index_$appWidgetId")
                .remove("sync_group_$appWidgetId")
                .apply()

            val intent = Intent(context, AnimationService::class.java).apply {
                action = "REMOVE_WIDGET"
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            try {
                context.startForegroundService(intent)
            } catch (e: Exception) {
                context.startService(intent)
            }
        }
    }
}