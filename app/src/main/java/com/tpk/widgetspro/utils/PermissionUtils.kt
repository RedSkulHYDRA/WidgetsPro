package com.tpk.widgetspro.utils

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader

object PermissionUtils {
    fun hasRootAccess(): Boolean = try {
        val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "cat /proc/version"))
        val output = BufferedReader(InputStreamReader(process.inputStream)).use { it.readLine() }
        process.destroy()
        output != null
    } catch (e: Exception) {
        false
    }

    fun hasShizukuAccess(): Boolean = try {
        Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (e: Exception) {
        false
    }
}
