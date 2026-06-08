package com.utex.deadline

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters

class SyncWorker(appContext: Context, params: WorkerParameters) : Worker(appContext, params) {
    override fun doWork(): Result {
        if (EventStore.getIcalUrl(applicationContext).isBlank()) {
            return Result.success()
        }
        val isAutoSync = inputData.getBoolean(ReminderScheduler.INPUT_AUTO_SYNC, false)
        val result = DeadlineSync.sync(applicationContext, notifyNew = true)
        return if (result.ok) {
            if (isAutoSync) {
                ReminderScheduler.scheduleNextPeriodicSync(applicationContext)
            } else {
                ReminderScheduler.reschedulePeriodicSync(applicationContext)
            }
            Result.success()
        } else {
            Result.retry()
        }
    }
}
