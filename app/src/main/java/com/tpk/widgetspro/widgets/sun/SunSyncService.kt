package com.tpk.widgetspro.widgets.sun

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import com.tpk.widgetspro.services.BaseMonitorService
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
            if (lastFetchDate != today) {
                fetchSunriseSunsetData()
            }
            fetchWeatherData() // Fetch weather data every run
            updateWidgets()
            handler.postDelayed(this, 60 * 1000L) // Adjust interval as needed
        }
    }

    private fun fetchWeatherData() {
        scope.launch {
            val prefs = getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
            val latitude = prefs.getString("latitude", null)?.toDoubleOrNull() ?: return@launch
            val longitude = prefs.getString("longitude", null)?.toDoubleOrNull() ?: return@launch
            val weatherUrl = "https://api.met.no/weatherapi/locationforecast/2.0/compact?lat=$latitude&lon=$longitude"
            val weatherJson = fetchData(weatherUrl)
            weatherJson?.let { parseAndSaveWeather(it) }
        }
    }

    private fun fetchSunriseSunsetData() {
        scope.launch {
            val prefs = getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
            val latitude = prefs.getString("latitude", null)?.toDoubleOrNull() ?: return@launch
            val longitude = prefs.getString("longitude", null)?.toDoubleOrNull() ?: return@launch
            val today = LocalDate.now()
            val tomorrow = today.plusDays(1)

            // Removed the weather API call from here

            // Fetch sunrise/sunset for today and tomorrow
            val urlToday = "https://api.met.no/weatherapi/sunrise/2.0/.json?lat=$latitude&lon=$longitude&date=$today"
            val jsonToday = fetchData(urlToday)
            jsonToday?.let { parseAndSaveSunriseSunset(it, today.toString(), "today") }

            val urlTomorrow = "https://api.met.no/weatherapi/sunrise/2.0/.json?lat=$latitude&lon=$longitude&date=$tomorrow"
            val jsonTomorrow = fetchData(urlTomorrow)
            jsonTomorrow?.let { parseAndSaveSunriseSunset(it, tomorrow.toString(), "tomorrow") }
        }
    }

    private suspend fun fetchData(url: String): String? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "MyWeatherApp/1.0 (john.doe@gmail.com)")
                .build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) response.body?.string() else null
        } catch (e: Exception) {
            null
        }
    }

    private fun parseAndSaveSunriseSunset(json: String, date: String, keyPrefix: String) {
        try {
            val jsonObject = JSONObject(json)
            val timeArray = jsonObject.getJSONObject("location").getJSONArray("time")
            val timeObject = timeArray.getJSONObject(0)
            val sunrise = timeObject.getString("sunrise")
            val sunset = timeObject.getString("sunset")
            val sunriseTime = OffsetDateTime.parse(sunrise).toLocalTime()
            val sunsetTime = OffsetDateTime.parse(sunset).toLocalTime()
            val offset = OffsetDateTime.parse(sunrise).offset

            val prefs = getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
            with(prefs.edit()) {
                if (keyPrefix == "today") {
                    putString("sunrise_time_today", sunriseTime.toString())
                    putString("sunset_time_today", sunsetTime.toString())
                    putString("location_offset", offset.toString())
                    putString("last_fetch_date", date)
                } else if (keyPrefix == "tomorrow") {
                    putString("sunrise_time_tomorrow", sunriseTime.toString())
                }
                apply()
            }
        } catch (e: Exception) {
            // Log error if needed
        }
    }

    private fun parseAndSaveWeather(json: String) {
        try {
            val jsonObject = JSONObject(json)
            val properties = jsonObject.getJSONObject("properties")
            val timeseries = properties.getJSONArray("timeseries")
            val current = timeseries.getJSONObject(0)
                .getJSONObject("data")
                .getJSONObject("instant")
                .getJSONObject("details")
            val temperature = current.getDouble("air_temperature")
            val prefs = getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
            with(prefs.edit()) {
                putFloat("current_temperature", temperature.toFloat())
                apply()
            }
        } catch (e: Exception) {
            // Log error for debugging
            e.printStackTrace()
        }
    }

    private fun updateWidgets() {
        val appWidgetManager = AppWidgetManager.getInstance(this)
        val componentName = ComponentName(this, SunTrackerWidget::class.java)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
        if (appWidgetIds.isEmpty()) {
            stopSelf()
            return
        }
        val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
        intent.component = componentName
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds)
        sendBroadcast(intent)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        val appWidgetManager = AppWidgetManager.getInstance(this)
        val componentName = ComponentName(this, SunTrackerWidget::class.java)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
        if (appWidgetIds.isEmpty()) {
            stopSelf()
        } else {
            handler.removeCallbacks(updateRunnable)
            handler.post(updateRunnable)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(updateRunnable)
    }

    companion object {
        fun start(context: Context) {
            context.startService(Intent(context, SunSyncService::class.java))
        }
    }
}