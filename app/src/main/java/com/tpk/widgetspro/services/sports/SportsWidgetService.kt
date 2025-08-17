package com.tpk.widgetspro.services.sports

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.IBinder
import android.view.View
import android.widget.RemoteViews
import com.tpk.widgetspro.R
import com.tpk.widgetspro.services.BaseMonitorService
import com.tpk.widgetspro.widgets.sports.SportsWidgetConfigActivity
import com.tpk.widgetspro.widgets.sports.SportsWidgetProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL

class SportsWidgetService : BaseMonitorService() {
    private var updateJob: Job? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startMonitoring()
        return super.onStartCommand(intent, flags, startId)
    }

    private fun startMonitoring() {
        updateJob?.cancel()
        updateJob = launch {
            while (isActive) {
                if (shouldUpdate()) {
                    val prefs = getSharedPreferences("SportsWidgetPrefs", MODE_PRIVATE)
                    val homeTeam = prefs.getString("home_team", "Arsenal")
                    val awayTeam = prefs.getString("away_team", "Chelsea")
                    fetchData(homeTeam, awayTeam)
                }
                delay(120000)
            }
        }
    }

    private fun fetchData(homeTeam: String?, awayTeam: String?) {
        launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    URL("https://www.thesportsdb.com/api/v1/json/123/searchevents.php?e=${homeTeam}_vs_${awayTeam}")
                        .readText()
                }
                val json = JSONObject(result)

                var fetchedHomeTeam = homeTeam ?: "N/A"
                var fetchedAwayTeam = awayTeam ?: "N/A"
                var homeScore = "-"
                var awayScore = "-"
                var homeBadge: String? = null
                var awayBadge: String? = null

                if (json.has("event") && !json.isNull("event")) {
                    val event = json.getJSONArray("event").getJSONObject(0)
                    fetchedHomeTeam = event.getString("strHomeTeam")
                    fetchedAwayTeam = event.getString("strAwayTeam")
                    homeScore = event.optString("intHomeScore", "-")
                    awayScore = event.optString("intAwayScore", "-")
                    homeBadge = event.optString("strHomeTeamBadge", null)
                    awayBadge = event.optString("strAwayTeamBadge", null)
                } else {
                    homeBadge = fetchBadge(homeTeam ?: "")
                    awayBadge = fetchBadge(awayTeam ?: "")
                }

                val homeBitmap = homeBadge?.let { loadBitmap(it) }
                val awayBitmap = awayBadge?.let { loadBitmap(it) }

                withContext(Dispatchers.Main) {
                    updateWidget(fetchedHomeTeam, fetchedAwayTeam, homeScore, awayScore, homeBitmap, awayBitmap)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    updateWidget(homeTeam ?: "Error", awayTeam ?: "Error", "-", "-", null, null)
                }
            }
        }
    }

    private suspend fun fetchBadge(team: String): String? {
        return try {
            val result = withContext(Dispatchers.IO) {
                URL("https://www.thesportsdb.com/api/v1/json/123/searchteams.php?t=${team.replace(" ", "%20")}").readText()
            }
            val json = JSONObject(result)
            if (json.has("teams") && !json.isNull("teams")) {
                json.getJSONArray("teams").getJSONObject(0).optString("strBadge", null)
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun loadBitmap(url: String): Bitmap? {
        return try {
            withContext(Dispatchers.IO) {
                val fullUrl = "$url/tiny"
                val input = URL(fullUrl).openStream()
                BitmapFactory.decodeStream(input)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun updateWidget(
        homeTeam: String,
        awayTeam: String,
        homeScore: String,
        awayScore: String,
        homeBitmap: Bitmap?,
        awayBitmap: Bitmap?
    ) {
        val appWidgetManager = AppWidgetManager.getInstance(this)
        val componentName = ComponentName(this, SportsWidgetProvider::class.java)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)

        for (appWidgetId in appWidgetIds) {
            val views = RemoteViews(packageName, R.layout.sports_widget_layout)
            views.setTextViewText(R.id.home_score, homeScore)
            views.setTextViewText(R.id.away_score, awayScore)

            if (homeBitmap != null) {
                views.setImageViewBitmap(R.id.home_team_image, homeBitmap)
                views.setViewVisibility(R.id.home_team_image, View.VISIBLE)
            } else {
                views.setViewVisibility(R.id.home_team_image, View.GONE)
            }

            if (awayBitmap != null) {
                views.setImageViewBitmap(R.id.away_team_image, awayBitmap)
                views.setViewVisibility(R.id.away_team_image, View.VISIBLE)
            } else {
                views.setViewVisibility(R.id.away_team_image, View.GONE)
            }

            val configIntent = Intent(this, SportsWidgetConfigActivity::class.java)
            configIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val configPendingIntent = PendingIntent.getActivity(this, appWidgetId, configIntent, flags)
            views.setOnClickPendingIntent(R.id.widget_container, configPendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    override fun onDestroy() {
        updateJob?.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}