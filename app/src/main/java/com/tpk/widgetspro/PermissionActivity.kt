package com.tpk.widgetspro

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class PermissionActivity : AppCompatActivity() {
    private val REQUEST_IGNORE_BATTERY_OPTIMIZATIONS = 1
    private val REQUEST_CODE_NOTIFICATION_PERMISSION = 2

    private lateinit var btnBatteryOptimizations: Button
    private lateinit var btnNotificationPermission: Button
    private lateinit var btnUsageAccess: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_permission)

        btnBatteryOptimizations = findViewById(R.id.btn_battery_optimizations)
        btnNotificationPermission = findViewById(R.id.btn_notification_permission)
        btnUsageAccess = findViewById(R.id.btn_usage_access)

        btnBatteryOptimizations.setOnClickListener { checkBatteryOptimizations() }
        btnNotificationPermission.setOnClickListener { requestNotificationPermission() }
        btnUsageAccess.setOnClickListener { requestUsageAccessPermission() }
    }

    override fun onResume() {
        super.onResume()
        if (areAllPermissionsGranted()) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        } else {
            updateButtonStates()
        }
    }

    private fun checkBatteryOptimizations() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivityForResult(intent, REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
        }
    }

    private fun requestNotificationPermission() {
        if (ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                REQUEST_CODE_NOTIFICATION_PERMISSION
            )
        }
    }

    private fun requestUsageAccessPermission() {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            packageName
        )
        if (mode != AppOpsManager.MODE_ALLOWED) {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
    }

    private fun areAllPermissionsGranted(): Boolean {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        val isIgnoringBatteryOptimizations = powerManager.isIgnoringBatteryOptimizations(packageName)
        val hasNotificationPermission = ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            packageName
        )
        val hasUsageAccessPermission = mode == AppOpsManager.MODE_ALLOWED
        return isIgnoringBatteryOptimizations && hasNotificationPermission && hasUsageAccessPermission
    }

    private fun updateButtonStates() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        btnBatteryOptimizations.isEnabled = !powerManager.isIgnoringBatteryOptimizations(packageName)
        btnNotificationPermission.isEnabled = ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.POST_NOTIFICATIONS
        ) != PackageManager.PERMISSION_GRANTED
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            packageName
        )
        btnUsageAccess.isEnabled = mode != AppOpsManager.MODE_ALLOWED
    }
}