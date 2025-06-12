package com.tpk.widgetspro.widgets.gif

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.tpk.widgetspro.services.gif.AnimationService
import java.io.File

class GifWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val deviceContext = context.createDeviceProtectedStorageContext()
        val prefs = deviceContext.getSharedPreferences("gif_widget_prefs", Context.MODE_PRIVATE)

        appWidgetIds.forEach { appWidgetId ->
            if (!prefs.contains("widget_index_$appWidgetId")) {
                val currentIndices = prefs.all.keys
                    .filter { it.startsWith("widget_index_") }
                    .mapNotNull { key -> prefs.getInt(key, 0) }
                val newIndex = (currentIndices.maxOrNull() ?: 0) + 1
                prefs.edit().putInt("widget_index_$appWidgetId", newIndex).apply()
            }

            val filePath = prefs.getString("file_path_$appWidgetId", null)
            if (filePath != null && File(filePath).exists()) {
                val intent = Intent(context, AnimationService::class.java).apply {
                    action = "ADD_WIDGET"
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                    putExtra("file_path", filePath)
                }
                context.startForegroundService(intent)
            }
        }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        val intent = Intent(context, AnimationService::class.java)
        context.startService(intent)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        val deviceContext = context.createDeviceProtectedStorageContext()
        val prefs = deviceContext.getSharedPreferences("gif_widget_prefs", Context.MODE_PRIVATE)

        appWidgetIds.forEach { appWidgetId ->
            prefs.edit()
                .remove("file_path_$appWidgetId")
                .remove("widget_index_$appWidgetId")
                .remove("sync_group_$appWidgetId")
                .apply()

            val intent = Intent(context, AnimationService::class.java).apply {
                action = "REMOVE_WIDGET"
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            context.startService(intent)
        }
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val componentName = ComponentName(context, GifWidgetProvider::class.java)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
        if (appWidgetIds.isEmpty()) {
            context.stopService(Intent(context, AnimationService::class.java))
        }
    }
}