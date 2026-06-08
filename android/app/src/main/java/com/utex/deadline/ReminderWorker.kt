package com.utex.deadline

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters

class ReminderWorker(appContext: Context, params: WorkerParameters) : Worker(appContext, params) {
    override fun doWork(): Result {
        val title = inputData.getString("title") ?: return Result.success()
        val startAt = inputData.getLong("startAtMillis", 0L)
        if (startAt <= 0L || System.currentTimeMillis() >= startAt) {
            return Result.success()
        }
        val event = DeadlineEvent(
            id = inputData.getString("id") ?: "$title-$startAt",
            title = title,
            startAtMillis = startAt,
            sourceUrl = inputData.getString("sourceUrl")?.takeIf { it.isNotBlank() },
            rawType = inputData.getString("rawType")?.takeIf { it.isNotBlank() },
            description = inputData.getString("description")?.takeIf { it.isNotBlank() }
        )
        val leadText = inputData.getString("leadText") ?: "gần tới hạn"
        if (!EventStore.isDone(applicationContext, event.id)) {
            NotificationHelper.notifyReminder(applicationContext, event, leadText)
        }
        return Result.success()
    }
}
