package com.tpk.widgetspro.widgets.caffeine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.tpk.widgetspro.services.caffeine.CaffeineService

class CaffeineToggleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val prefs = context.getSharedPreferences("caffeine", Context.MODE_PRIVATE)
        val isActive = !prefs.getBoolean("active", false)
        if (toggleCaffeine(context, isActive)) {
            prefs.edit().putBoolean("active", isActive).apply()
            CaffeineWidget.updateAllWidgets(context)
        }
    }

    private fun toggleCaffeine(context: Context, enable: Boolean): Boolean {
        val serviceIntent = Intent(context, CaffeineService::class.java)
        return try {
            if (enable) {
                context.startForegroundService(serviceIntent)
            } else {
                context.stopService(serviceIntent)
            }
            true
        } catch (e: SecurityException) {
            false
        }
    }
}