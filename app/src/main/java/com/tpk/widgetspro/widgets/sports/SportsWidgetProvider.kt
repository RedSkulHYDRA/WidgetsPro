package com.tpk.widgetspro.widgets.sports

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import com.tpk.widgetspro.R
import com.tpk.widgetspro.services.sports.SportsWidgetService

class SportsWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onEnabled(context: Context) {
        val intent = Intent(context, SportsWidgetService::class.java)
        context.startService(intent)
    }

    override fun onDisabled(context: Context) {
        val intent = Intent(context, SportsWidgetService::class.java)
        context.stopService(intent)
    }
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == "REFRESH_WIDGET") {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(
                ComponentName(context, SportsWidgetProvider::class.java)
            )
            onUpdate(context, appWidgetManager, appWidgetIds)
        }
    }
}

internal fun updateAppWidget(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetId: Int
) {
    val views = RemoteViews(context.packageName, R.layout.sports_widget_layout)

    views.setTextViewText(R.id.home_score, "-")
    views.setTextViewText(R.id.away_score, "-")

    val configIntent = Intent(context, SportsWidgetConfigActivity::class.java)
    configIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
    val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    } else {
        PendingIntent.FLAG_UPDATE_CURRENT
    }
    val configPendingIntent = PendingIntent.getActivity(context, appWidgetId, configIntent, flags)
    views.setOnClickPendingIntent(R.id.widget_container, configPendingIntent)

    appWidgetManager.updateAppWidget(appWidgetId, views)

    val serviceIntent = Intent(context, SportsWidgetService::class.java)
    context.startService(serviceIntent)
}
