package com.tpk.widgetspro.widgets.cpu

import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

class CpuMonitor(private val useRoot: Boolean, private val callback: (Double, Double) -> Unit) {
    private var prevIdleTime: Long = 0
    private var prevNonIdleTime: Long = 0
    private var prevTotalTime: Long = 0
    private var isFirstReading = true
    private var executorService: ScheduledExecutorService? = null
    private var scheduledFuture: ScheduledFuture<*>? = null
    private var currentInterval = 60
    private var cpuThermalZone: String? = null

    fun startMonitoring(initialInterval: Int) {
        currentInterval = initialInterval
        executorService = Executors.newSingleThreadScheduledExecutor()
        executorService?.execute {
            performMonitoring()
            scheduleNextRun()
        }
    }

    private fun performMonitoring() {
        val cpuUsage = calculateCpuUsage()
        val cpuTemperature = readCpuTemperature()
        callback(cpuUsage, cpuTemperature)
    }

    private fun scheduleNextRun() {
        scheduledFuture = executorService?.schedule({
            performMonitoring()
            scheduleNextRun()
        }, currentInterval.toLong(), TimeUnit.SECONDS)
    }

    fun updateInterval(newInterval: Int) {
        currentInterval = newInterval.coerceAtLeast(1)
        scheduledFuture?.cancel(false)
        scheduleNextRun()
    }

    fun stopMonitoring() {
        scheduledFuture?.cancel(false)
        executorService?.shutdown()
        executorService = null
        scheduledFuture = null
    }

    private fun calculateCpuUsage(): Double {
        val stat = readProcStat() ?: return 0.0
        val cpuLine = stat.lines().firstOrNull { it.startsWith("cpu ") } ?: return 0.0
        val tokens = cpuLine.split("\\s+".toRegex()).drop(1).mapNotNull { it.toLongOrNull() }

        if (tokens.size >= 10) {
            val idleTime = tokens[3] + tokens[4]
            val nonIdleTime = tokens[0] + tokens[1] + tokens[2] + tokens[5] + tokens[6] + tokens[7]
            val totalTime = idleTime + nonIdleTime

            if (isFirstReading) {
                prevIdleTime = idleTime
                prevNonIdleTime = nonIdleTime
                prevTotalTime = totalTime
                isFirstReading = false
                return 0.0
            }

            val totalDelta = totalTime - prevTotalTime
            val nonIdleDelta = nonIdleTime - prevNonIdleTime

            prevIdleTime = idleTime
            prevNonIdleTime = nonIdleTime
            prevTotalTime = totalTime

            return if (totalDelta > 0) (nonIdleDelta.toDouble() / totalDelta) * 100.0 else 0.0
        }
        return 0.0
    }

    private fun executeCommand(command: Array<String>): Process? = try {
        if (useRoot) Runtime.getRuntime().exec(arrayOf("su", "-c", command.joinToString(" ")))
        else rikka.shizuku.Shizuku.newProcess(command, null, null)
    } catch (e: Exception) {
        null
    }

    private fun readProcStat(): String? {
        val process = executeCommand(arrayOf("cat", "/proc/stat")) ?: return null
        return BufferedReader(InputStreamReader(process.inputStream)).use { it.readText() }.also { process.destroy() }
    }

    private fun readCpuTemperature(): Double {
        if (cpuThermalZone == null) cpuThermalZone = findCpuThermalZone()
        return cpuThermalZone?.let { readThermalZoneTemp(it)?.div(1000.0) } ?: 0.0
    }

    private fun findCpuThermalZone(): String? {
        val zones = getThermalZones()
        val preferredTypes = listOf("cpu", "tsens", "processor")
        return zones.firstOrNull { zone ->
            readThermalZoneType(zone)?.let { type -> preferredTypes.any { type.contains(it, ignoreCase = true) } } == true
        } ?: zones.firstOrNull()
    }

    private fun getThermalZones(): List<String> {
        val process = executeCommand(arrayOf("ls", "/sys/class/thermal/")) ?: return emptyList()
        return BufferedReader(InputStreamReader(process.inputStream)).use { it.readText().lines().filter { line -> line.startsWith("thermal_zone") } }.also { process.destroy() }
    }

    private fun readThermalZoneType(zone: String): String? {
        val process = executeCommand(arrayOf("cat", "/sys/class/thermal/$zone/type")) ?: return null
        return BufferedReader(InputStreamReader(process.inputStream)).use { it.readLine() }.also { process.destroy() }
    }

    private fun readThermalZoneTemp(zone: String): Double? {
        val process = executeCommand(arrayOf("cat", "/sys/class/thermal/$zone/temp")) ?: return null
        return BufferedReader(InputStreamReader(process.inputStream)).use { it.readLine()?.toDoubleOrNull() }.also { process.destroy() }
    }
}