package com.tpk.widgetspro.widgets.cpu

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import com.tpk.widgetspro.MainActivity
import com.tpk.widgetspro.R
import com.tpk.widgetspro.base.BaseWidgetProvider
import com.tpk.widgetspro.services.cpu.CpuMonitorService
import com.tpk.widgetspro.utils.PermissionUtils

class CpuWidgetProvider : BaseWidgetProvider() {
    override val layoutId = R.layout.cpu_widget_layout
    override val setupText = "Tap to setup CPU"
    override val setupDestination = MainActivity::class.java

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        if (hasRequiredPermissions(context)) startService(context)
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        if (hasRequiredPermissions(context)) startService(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        context.stopService(Intent(context, CpuMonitorService::class.java))
    }

    override fun updateNormalWidgetView(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        startService(context)
    }

    override fun hasRequiredPermissions(context: Context): Boolean {
        return PermissionUtils.hasRootAccess() || PermissionUtils.hasShizukuPermission()
    }

    private fun startService(context: Context) {
        context.startService(Intent(context, CpuMonitorService::class.java))
    }
}