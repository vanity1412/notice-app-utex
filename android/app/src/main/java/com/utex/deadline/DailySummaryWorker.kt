package com.utex.deadline

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters

class DailySummaryWorker(appContext: Context, params: WorkerParameters) : Worker(appContext, params) {
    override fun doWork(): Result {
        if (EventStore.isDailySummaryEnabled(applicationContext)) {
            NotificationHelper.notifyDailySummary(applicationContext, EventStore.loadEvents(applicationContext))
        }
        return Result.success()
    }
}
