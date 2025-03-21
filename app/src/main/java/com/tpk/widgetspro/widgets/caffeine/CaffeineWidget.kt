package com.tpk.widgetspro.widgets.caffeine

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.tpk.widgetspro.MainActivity
import com.tpk.widgetspro.R
import com.tpk.widgetspro.base.BaseWidgetProvider

class CaffeineWidget : BaseWidgetProvider() {
    override val layoutId = R.layout.caffeine_widget_layout
    override val setupText = "Tap to setup Caffeine"
    override val setupDestination = MainActivity::class.java

    override fun updateNormalWidgetView(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        val prefs = context.getSharedPreferences("caffeine", Context.MODE_PRIVATE)
        val isActive = prefs.getBoolean("active", false)
        val views = RemoteViews(context.packageName, layoutId).apply {
            setImageViewResource(R.id.widget_toggle, if (isActive) R.drawable.ic_coffee_active else R.drawable.ic_coffee_inactive)
            setOnClickPendingIntent(R.id.widget_toggle, getToggleIntent(context))
        }
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val prefs = context.getSharedPreferences("caffeine", Context.MODE_PRIVATE)
        val isActive = prefs.getBoolean("active", false)
        val views = RemoteViews(context.packageName, layoutId).apply {
            setImageViewResource(R.id.widget_toggle, if (isActive) R.drawable.ic_coffee_active else R.drawable.ic_coffee_inactive)
            setOnClickPendingIntent(R.id.widget_toggle, getToggleIntent(context))
        }
        appWidgetManager.updateAppWidget(appWidgetIds, views)
    }

    companion object {
        fun getToggleIntent(context: Context) = PendingIntent.getBroadcast(context, 0, Intent(context, CaffeineToggleReceiver::class.java).apply {
            action = "TOGGLE_CAFFEINE"
        }, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        fun updateAllWidgets(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, CaffeineWidget::class.java)
            manager.getAppWidgetIds(component).forEach { CaffeineWidget().updateNormalWidgetView(context, manager, it) }
        }
    }
}