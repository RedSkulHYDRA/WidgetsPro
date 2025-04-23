package com.tpk.widgetspro.services

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.content.pm.PackageManager
import androidx.localbroadcastmanager.content.LocalBroadcastManager

class LauncherStateAccessibilityService : AccessibilityService() {
    companion object {
        const val ACTION_LAUNCHER_STATE_CHANGED = "com.tpk.widgetspro.LAUNCHER_STATE_CHANGED"
        const val EXTRA_IS_ACTIVE = "is_active"
    }

    private var defaultLauncherPackage: String? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.DEFAULT
        }
        this.serviceInfo = info

        // Get default launcher package
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
        }
        defaultLauncherPackage = packageManager.resolveActivity(
            intent,
            PackageManager.MATCH_DEFAULT_ONLY
        )?.activityInfo?.packageName

        // Perform initial state check
        checkCurrentWindowState()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event?.let {
            if (it.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                checkCurrentWindowState()
            }
        }
    }

    private fun checkCurrentWindowState() {
        val rootNode = rootInActiveWindow ?: return
        val currentPackage = rootNode.packageName?.toString()
        val isLauncherActive = currentPackage == defaultLauncherPackage
        sendLauncherStateUpdate(isLauncherActive)
    }

    private fun sendLauncherStateUpdate(isActive: Boolean) {
        LocalBroadcastManager.getInstance(this).sendBroadcast(
            Intent(ACTION_LAUNCHER_STATE_CHANGED).apply {
                putExtra(EXTRA_IS_ACTIVE, isActive)
            }
        )
    }

    override fun onInterrupt() {}
}