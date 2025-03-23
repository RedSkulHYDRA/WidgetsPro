package com.tpk.widgetspro.services

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import com.tpk.widgetspro.base.BaseMonitorService
import com.tpk.widgetspro.utils.CommonUtils
import com.tpk.widgetspro.widgets.sun.SunTrackerWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId

class SunSyncService : BaseMonitorService() {
    override val notificationId = 3
    override val notificationTitle = "Sun Tracker Widget"
    override val notificationText = "Monitoring sun position"

    private val handler = Handler(Looper.getMainLooper())
    private val client = OkHttpClient()
    private val scope = CoroutineScope(Dispatchers.IO)

    private val updateRunnable = object : Runnable {
        override fun run() {
            val prefs = getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
            val lastFetchDate = prefs.getString("last_fetch_date", null)
            val today = LocalDate.now().toString()
            if (lastFetchDate != today) fetchSunriseSunsetData()
            fetchWeatherData()
            updateWidgets()
            handler.postDelayed(this, 60 * 1000L)
        }
    }

    private fun fetchWeatherData() {
        scope.launch {
            val prefs = getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
            val latitude = prefs.getString("latitude", null)?.toDoubleOrNull() ?: return@launch
            val longitude = prefs.getString("longitude", null)?.toDoubleOrNull() ?: return@launch
            val weatherUrl = "https://api.met.no/weatherapi/locationforecast/2.0/compact?lat=$latitude&lon=$longitude"
            fetchData(weatherUrl)?.let { parseAndSaveWeather(it) }
        }
    }

    private fun fetchSunriseSunsetData() {
        scope.launch {
            val prefs = getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
            val latitude = prefs.getString("latitude", null)?.toDoubleOrNull() ?: return@launch
            val longitude = prefs.getString("longitude", null)?.toDoubleOrNull() ?: return@launch
            val today = LocalDate.now()
            val tomorrow = today.plusDays(1)

            fetchData("https://api.met.no/weatherapi/sunrise/2.0/.json?lat=$latitude&lon=$longitude&date=$today")?.let {
                parseAndSaveSunriseSunset(it, today.toString(), "today")
            }
            fetchData("https://api.met.no/weatherapi/sunrise/2.0/.json?lat=$latitude&lon=$longitude&date=$tomorrow")?.let {
                parseAndSaveSunriseSunset(it, tomorrow.toString(), "tomorrow")
            }
        }
    }

    private suspend fun fetchData(url: String): String? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(url).header("User-Agent", "MyWeatherApp/1.0").build()
            client.newCall(request).execute().takeIf { it.isSuccessful }?.body?.string()
        } catch (e: Exception) {
            null
        }
    }

    private fun parseAndSaveSunriseSunset(json: String, date: String, keyPrefix: String) {
        try {
            val jsonObject = JSONObject(json)
            val timeObject = jsonObject.getJSONObject("location").getJSONArray("time").getJSONObject(0)
            val sunriseStr = timeObject.getString("sunrise")
            val sunsetStr = timeObject.getString("sunset")

            val sunriseLocal = OffsetDateTime.parse(sunriseStr).atZoneSameInstant(ZoneId.systemDefault()).toLocalTime()
            val sunsetLocal = OffsetDateTime.parse(sunsetStr).atZoneSameInstant(ZoneId.systemDefault()).toLocalTime()

            getSharedPreferences("widget_prefs", Context.MODE_PRIVATE).edit().apply {
                if (keyPrefix == "today") {
                    putString("sunrise_time_today", sunriseLocal.toString())
                    putString("sunset_time_today", sunsetLocal.toString())
                    putString("last_fetch_date", date)
                } else if (keyPrefix == "tomorrow") {
                    putString("sunrise_time_tomorrow", sunriseLocal.toString())
                }
                apply()
            }
        } catch (e: Exception) {
        }
    }

    private fun parseAndSaveWeather(json: String) {
        try {
            val current = JSONObject(json).getJSONObject("properties").getJSONArray("timeseries")
                .getJSONObject(0).getJSONObject("data").getJSONObject("instant").getJSONObject("details")
            val temperature = current.getDouble("air_temperature")
            getSharedPreferences("widget_prefs", Context.MODE_PRIVATE).edit()
                .putFloat("current_temperature", temperature.toFloat()).apply()
        } catch (e: Exception) {
        }
    }

    private fun updateWidgets() {
        val appWidgetManager = AppWidgetManager.getInstance(this)
        val componentName = ComponentName(this, SunTrackerWidget::class.java)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
        if (appWidgetIds.isEmpty()) stopSelf() else CommonUtils.updateAllWidgets(this, SunTrackerWidget::class.java)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        val appWidgetManager = AppWidgetManager.getInstance(this)
        val componentName = ComponentName(this, SunTrackerWidget::class.java)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
        if (appWidgetIds.isEmpty()) stopSelf() else {
            handler.removeCallbacks(updateRunnable)
            handler.post(updateRunnable)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(updateRunnable)
        super.onDestroy()
    }

    companion object {
        fun start(context: Context) {
            context.startService(Intent(context, SunSyncService::class.java))
        }
    }
}