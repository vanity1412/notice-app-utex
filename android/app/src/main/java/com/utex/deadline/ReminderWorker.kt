package com.utex.deadline

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters

class ReminderWorker(appContext: Context, params: WorkerParameters) : Worker(appContext, params) {
    override fun doWork(): Result {
        val title = inputData.getString("title") ?: return Result.success()
        val startAt = inputData.getLong("startAtMillis", 0L)
        val leadMinutes = inputData.getLong("leadMinutes", 0L)
        val now = System.currentTimeMillis()

        if (startAt <= 0L) {
            return Result.success()
        }

        // Cho phép thông báo ngay cả khi đã quá hạn (nhưng không quá 1 giờ)
        if (now > startAt + 60 * 60_000L) {
            return Result.success() // Quá hạn hơn 1 giờ thì không thông báo nữa
        }

        val expectedReminderTime = startAt - (leadMinutes * 60_000L)
        if (now < expectedReminderTime - 5 * 60_000L) { // Cho phép sai số 5 phút
            return Result.retry()
        }

        val event = DeadlineEvent(
            id = inputData.getString("id") ?: "$title-$startAt",
            title = title,
            startAtMillis = startAt,
            sourceUrl = inputData.getString("sourceUrl")?.takeIf { it.isNotBlank() },
            rawType = inputData.getString("rawType")?.takeIf { it.isNotBlank() },
            description = inputData.getString("description")?.takeIf { it.isNotBlank() }
        )

        if (EventStore.isDone(applicationContext, event.id)) {
            return Result.success()
        }

        val leadText = inputData.getString("leadText") ?: EventStore.reminderLeadLabel(leadMinutes)
        val sent = NotificationHelper.notifyReminder(applicationContext, event, leadText, leadMinutes)
        if (!sent && !NotificationHelper.canPostNotifications(applicationContext)) {
            EventStore.upsertPendingDeadlineNotifications(
                applicationContext,
                listOf(
                    PendingDeadlineNotification(
                        key = "reminder-${event.id}-$leadMinutes",
                        type = "reminder",
                        event = event,
                        timestamp = now,
                        leadText = leadText,
                        leadMinutes = leadMinutes
                    )
                )
            )
        }
        return Result.success()
    }
}
