package com.tpk.widgetspro.ui.permission

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.tpk.widgetspro.PermissionActivity
import com.tpk.widgetspro.R

class PermissionPage1Fragment : Fragment() {

    private lateinit var btnBatteryOptimizations: Button
    private lateinit var btnNotificationPermission: Button

    private val batteryOptimizationsLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        updateButtonStates()
        (activity as? PermissionActivity)?.checkCurrentPermissionStateAndSetPage()
    }

    private val notificationPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        updateButtonStates()
        (activity as? PermissionActivity)?.checkCurrentPermissionStateAndSetPage()
    }


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_permission_page1, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        btnBatteryOptimizations = view.findViewById(R.id.btn_battery_optimizations)
        btnNotificationPermission = view.findViewById(R.id.btn_notification_permission)

        btnBatteryOptimizations.setOnClickListener { checkBatteryOptimizations() }
        btnNotificationPermission.setOnClickListener { requestNotificationPermission() }

        updateButtonStates()
    }

    override fun onResume() {
        super.onResume()
        updateButtonStates()
        (activity as? PermissionActivity)?.checkCurrentPermissionStateAndSetPage()
    }

    private fun checkBatteryOptimizations() {
        if (!isAdded) return
        val powerManager = requireContext().getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!powerManager.isIgnoringBatteryOptimizations(requireContext().packageName)) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${requireContext().packageName}")
            }
            try {
                batteryOptimizationsLauncher.launch(intent)
            } catch (e: Exception) {
            }
        } else {
            updateButtonStates()
        }
    }

    private fun requestNotificationPermission() {
        if (!isAdded) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    requireContext(),
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            } else {
                updateButtonStates()
            }
        } else {
            updateButtonStates()
        }
    }

    private fun updateButtonStates() {
        if (!isAdded) return

        val powerManager = requireContext().getSystemService(Context.POWER_SERVICE) as PowerManager
        val batteryOptGranted = powerManager.isIgnoringBatteryOptimizations(requireContext().packageName)
        btnBatteryOptimizations.isEnabled = !batteryOptGranted

        val notificationGranted: Boolean
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationGranted = ContextCompat.checkSelfPermission(
                requireContext(),
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            btnNotificationPermission.isEnabled = !notificationGranted
            btnNotificationPermission.visibility = View.VISIBLE
        } else {
            notificationGranted = true
            btnNotificationPermission.isEnabled = false
            btnNotificationPermission.visibility = View.GONE
        }

        if (batteryOptGranted && notificationGranted) {
            (activity as? PermissionActivity)?.checkCurrentPermissionStateAndSetPage()
        }
    }
}
