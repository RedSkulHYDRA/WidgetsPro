package com.tpk.widgetspro.ui.permission

import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.tpk.widgetspro.MainActivity
import com.tpk.widgetspro.PermissionActivity
import com.tpk.widgetspro.R
import com.tpk.widgetspro.services.LauncherStateAccessibilityService

class PermissionPage2Fragment : Fragment() {

    private lateinit var btnUsageAccess: Button
    private lateinit var switchAccessibility: Button
    private lateinit var btnNextToMain: Button
    private lateinit var prefs: SharedPreferences

    private val accessibilitySettingsLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        updateButtonStates()
        (activity as? PermissionActivity)?.areAllPermissionsGrantedInPage2()
    }

    private val usageAccessSettingsLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        updateButtonStates()
        (activity as? PermissionActivity)?.areAllPermissionsGrantedInPage2()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        prefs = requireActivity().getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        return inflater.inflate(R.layout.fragment_permission_page2, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        btnUsageAccess = view.findViewById(R.id.btn_usage_access)
        switchAccessibility = view.findViewById(R.id.switch_accessibility)
        btnNextToMain = view.findViewById(R.id.btn_next_to_main)

        btnUsageAccess.setOnClickListener { requestUsageAccessPermission() }
        switchAccessibility.setOnClickListener {
            if (!isAccessibilityServiceEnabled()) {
                showAccessibilityDisclosureDialog()
            } else {
                openAccessibilitySettings()
            }
        }

        btnNextToMain.setOnClickListener {
            prefs.edit().putBoolean("optional_permissions_skipped_via_next", true).apply()
            val intent = Intent(requireContext(), MainActivity::class.java)
            startActivity(intent)
            activity?.finish()
        }

        updateButtonStates()
    }

    override fun onResume() {
        super.onResume()
        updateButtonStates()
        (activity as? PermissionActivity)?.areAllPermissionsGrantedInPage2()
    }

    private fun requestUsageAccessPermission() {
        if (!isUsageAccessGranted()) {
            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            usageAccessSettingsLauncher.launch(intent)
        }
    }

    private fun showAccessibilityDisclosureDialog() {
        if (!isAdded) return

        val themePrefs = requireContext().getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)
        val isDarkTheme = themePrefs.getBoolean("dark_theme", false)
        val isRedAccent = themePrefs.getBoolean("red_accent", false)
        val dialogThemeStyle = when {
            isDarkTheme && isRedAccent -> R.style.CustomDialogTheme1
            isDarkTheme && !isRedAccent -> R.style.CustomDialogTheme
            !isDarkTheme && isRedAccent -> R.style.CustomDialogTheme1
            else -> R.style.CustomDialogTheme
        }

        val builder = AlertDialog.Builder(requireContext(), dialogThemeStyle)
        builder.setTitle(R.string.accessibility_disclosure_title)
            .setMessage(R.string.accessibility_disclosure_message)
            .setCancelable(false)
            .setPositiveButton(R.string.accessibility_disclosure_agree) { dialog, _ ->
                dialog.dismiss()
                openAccessibilitySettings()
            }
            .setNegativeButton(R.string.accessibility_disclosure_decline) { dialog, _ ->
                dialog.dismiss()
            }
        val dialog = builder.create()
        dialog.show()
        dialog.window?.setBackgroundDrawableResource(R.drawable.rounded_layout_bg_alt)
        dialog.applyDialogTheme()

        try {
            val textColor = ContextCompat.getColor(requireContext(), R.color.text_color)
            dialog.findViewById<TextView>(android.R.id.message)?.setTextColor(textColor)
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(textColor)
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(textColor)
        } catch (e: Exception) {

        }
    }

    private fun AlertDialog.applyDialogTheme() {
        val textColor = ContextCompat.getColor(requireContext(), R.color.text_color)
        findViewById<TextView>(android.R.id.title)?.setTextColor(textColor)
        findViewById<TextView>(android.R.id.message)?.setTextColor(textColor)
        getButton(DialogInterface.BUTTON_POSITIVE)?.setTextColor(textColor)
        getButton(DialogInterface.BUTTON_NEGATIVE)?.setTextColor(textColor)
    }

    private fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        try {
            accessibilitySettingsLauncher.launch(intent)
        } catch (e: Exception) {
        }
    }

    private fun isUsageAccessGranted(): Boolean {
        if (!isAdded) return false
        val appOps = requireContext().getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = try {
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                requireContext().packageName
            )
        } catch (e: Exception) {
            AppOpsManager.MODE_DEFAULT
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        if (!isAdded) return false
        val context = requireContext()
        val serviceName = ComponentName(context, LauncherStateAccessibilityService::class.java)
        val enabledServices = try {
            Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
        } catch (e: Exception) {
            null
        }
        return enabledServices?.contains(serviceName.flattenToString()) ?: false
    }

    private fun updateButtonStates() {
        if (!isAdded) return

        val usageGranted = isUsageAccessGranted()
        val accessibilityGranted = isAccessibilityServiceEnabled()
        val basicPermissionsGranted = (activity as? PermissionActivity)?.areBasicPermissionsGrantedInPage1() ?: false

        btnUsageAccess.isEnabled = !usageGranted
        switchAccessibility.isEnabled = true

        switchAccessibility.text = if (accessibilityGranted) {
            getString(R.string.manage_accessibility_switch)
        } else {
            getString(R.string.enable_accessibility_switch)
        }


        btnNextToMain.isEnabled = basicPermissionsGranted
        btnNextToMain.visibility = if (basicPermissionsGranted) View.VISIBLE else View.GONE

        if (usageGranted && accessibilityGranted) {
            if (!prefs.getBoolean("optional_permissions_interacted", false)) {
                prefs.edit().putBoolean("optional_permissions_interacted", true).apply()
            }
        } else {
            if (prefs.getBoolean("optional_permissions_interacted", false)) {
            }
        }
    }
}