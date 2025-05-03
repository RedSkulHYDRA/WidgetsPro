package com.tpk.widgetspro.widgets.caffeine

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.tpk.widgetspro.MainActivity
import com.tpk.widgetspro.R
import com.tpk.widgetspro.base.BaseWidgetProvider
import com.tpk.widgetspro.services.caffeine.CaffeineService
import com.tpk.widgetspro.utils.CommonUtils

class CaffeineWidget : BaseWidgetProvider() {
    override val layoutId = R.layout.caffeine_widget_layout
    override val setupText = "Tap to setup Caffeine"
    override val setupDestination = MainActivity::class.java

    override fun updateNormalWidgetView(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        val caffeinePrefs = context.getSharedPreferences("caffeine", Context.MODE_PRIVATE)
        val isActive = caffeinePrefs.getBoolean("active", false)
        val views = RemoteViews(context.packageName, layoutId).apply {
            val imageRes = if (isActive) R.drawable.ic_coffee_active else R.drawable.ic_coffee_inactive
            setImageViewResource(R.id.widget_toggle, imageRes)
            setOnClickPendingIntent(R.id.widget_toggle, getToggleIntent(context))
            setInt(R.id.widget_toggle, "setColorFilter", if (isActive) CommonUtils.getAccentColor(context) else ContextCompat.getColor(context, R.color.text_color))
        }
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val caffeinePrefs = context.getSharedPreferences("caffeine", Context.MODE_PRIVATE)
        val isActive = caffeinePrefs.getBoolean("active", false)
        val views = RemoteViews(context.packageName, layoutId).apply {
            val imageRes = if (isActive) R.drawable.ic_coffee_active else R.drawable.ic_coffee_inactive
            setImageViewResource(R.id.widget_toggle, imageRes)
            setOnClickPendingIntent(R.id.widget_toggle, getToggleIntent(context))
            setInt(R.id.widget_toggle, "setColorFilter", if (isActive) CommonUtils.getAccentColor(context) else ContextCompat.getColor(context, R.color.text_color))
        }
        appWidgetManager.updateAppWidget(appWidgetIds, views)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        val caffeinePrefs = context.getSharedPreferences("caffeine", Context.MODE_PRIVATE)
        if (caffeinePrefs.getBoolean("active", false)) {
            context.stopService(Intent(context, CaffeineService::class.java))
            caffeinePrefs.edit().putBoolean("active", false).apply()
        }
    }

    companion object {
        fun getToggleIntent(context: Context) = PendingIntent.getBroadcast(
            context, 0,
            Intent(context, CaffeineToggleReceiver::class.java).apply { action = "TOGGLE_CAFFEINE" },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        fun updateAllWidgets(context: Context) {
            CommonUtils.updateAllWidgets(context, CaffeineWidget::class.java)
        }
    }
}
