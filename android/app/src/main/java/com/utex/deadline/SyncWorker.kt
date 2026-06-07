package com.utex.deadline

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters

class SyncWorker(appContext: Context, params: WorkerParameters) : Worker(appContext, params) {
    override fun doWork(): Result {
        val result = DeadlineSync.sync(applicationContext, notifyNew = true)
        return if (result.ok) Result.success() else Result.retry()
    }
}
