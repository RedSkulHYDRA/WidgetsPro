package com.tpk.widgetspro.widgets.sun

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class SunUpdateWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        SunTrackerWidget.triggerUpdate(applicationContext)
        return Result.success()
    }
}