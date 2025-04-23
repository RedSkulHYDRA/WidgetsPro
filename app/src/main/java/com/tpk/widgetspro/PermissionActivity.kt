package com.tpk.widgetspro

import android.app.AppOpsManager
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.tpk.widgetspro.services.LauncherStateAccessibilityService

class PermissionActivity : AppCompatActivity() {
    private val REQUEST_IGNORE_BATTERY_OPTIMIZATIONS = 1
    private val REQUEST_CODE_NOTIFICATION_PERMISSION = 2

    private lateinit var btnBatteryOptimizations: Button
    private lateinit var btnNotificationPermission: Button
    private lateinit var btnUsageAccess: Button
    private lateinit var btnAccessibility: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_permission)

        btnBatteryOptimizations = findViewById(R.id.btn_battery_optimizations)
        btnNotificationPermission = findViewById(R.id.btn_notification_permission)
        btnUsageAccess = findViewById(R.id.btn_usage_access)
        btnAccessibility = findViewById(R.id.btn_accessibility)

        btnBatteryOptimizations.setOnClickListener { checkBatteryOptimizations() }
        btnNotificationPermission.setOnClickListener { requestNotificationPermission() }
        btnUsageAccess.setOnClickListener { requestUsageAccessPermission() }
        btnAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            Toast.makeText(this, "Please enable Widgets Pro in Accessibility Services", Toast.LENGTH_LONG).show()
        }
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
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
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
        val appOps = getSystemService(APP_OPS_SERVICE) as AppOpsManager
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
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        val isIgnoringBatteryOptimizations = powerManager.isIgnoringBatteryOptimizations(packageName)
        val hasNotificationPermission = ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        val appOps = getSystemService(APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            packageName
        )
        val hasUsageAccessPermission = mode == AppOpsManager.MODE_ALLOWED
        return isIgnoringBatteryOptimizations && hasNotificationPermission && hasUsageAccessPermission
    }

    private fun updateButtonStates() {
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        btnBatteryOptimizations.isEnabled = !powerManager.isIgnoringBatteryOptimizations(packageName)
        btnNotificationPermission.isEnabled = ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.POST_NOTIFICATIONS
        ) != PackageManager.PERMISSION_GRANTED
        val appOps = getSystemService(APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            packageName
        )
        btnUsageAccess.isEnabled = mode != AppOpsManager.MODE_ALLOWED
        btnAccessibility.isEnabled = !isAccessibilityServiceEnabled()
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val serviceName = ComponentName(this, LauncherStateAccessibilityService::class.java)
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabledServices.contains(serviceName.flattenToString())
    }
}