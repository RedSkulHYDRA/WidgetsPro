package com.tpk.widgetspro.widgets.sun

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class SunSyncService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        setupPeriodicSync()
    }

    private fun setupPeriodicSync() {
        val syncRequest = PeriodicWorkRequest.Builder(
            SunUpdateWorker::class.java,
            15, TimeUnit.MINUTES
        ).addTag("SUN_SYNC").build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "SUN_TRACKER_SYNC",
            ExistingPeriodicWorkPolicy.KEEP,
            syncRequest
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    companion object {
        fun start(context: Context) {
            context.startService(Intent(context, SunSyncService::class.java))
        }
    }
}