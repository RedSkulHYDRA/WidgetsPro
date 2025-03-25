package com.tpk.widgetspro.utils

import java.io.File
import rikka.shizuku.Shizuku

object PermissionUtils {
    private var hasRoot: Boolean? = null

    fun hasRootAccess(): Boolean {
        if (hasRoot == null) {
            hasRoot = checkSuBinary()
        }
        return hasRoot!!
    }

    private fun checkSuBinary(): Boolean {
        val paths = arrayOf(
            "/system/bin/su",
            "/system/xbin/su",
            "/sbin/su",
            "/vendor/bin/su"
        )
        return paths.any { File(it).exists() }
    }

    fun hasShizukuPermission(): Boolean {
        return try {
            Shizuku.pingBinder() &&
                    Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            false
        }
    }
}