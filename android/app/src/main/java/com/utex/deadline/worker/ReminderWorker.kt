package com.utex.deadline

import android.content.Context
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters

class ReminderWorker(appContext: Context, params: WorkerParameters) : Worker(appContext, params) {
    override fun doWork(): Result {
        val title = inputData.getString("title") ?: return Result.success()
        val startAt = inputData.getLong("startAtMillis", 0L)
        if (startAt <= 0L) return Result.success()

        val now = System.currentTimeMillis()
        if (now > startAt + 60L * 60_000L) {
            return Result.success()
        }

        val leadMinutes = inputData.getLong("leadMinutes", 0L)
        val event = DeadlineEvent(
            id = inputData.getString("id") ?: "$title-$startAt",
            title = title,
            startAtMillis = startAt,
            sourceUrl = inputData.getString("sourceUrl")?.takeIf { it.isNotBlank() },
            rawType = inputData.getString("rawType")?.takeIf { it.isNotBlank() },
            description = inputData.getString("description")?.takeIf { it.isNotBlank() },
            source = DeadlineSource.fromStored(inputData.getString("source"))
        )

        if (EventStore.isDone(applicationContext, event.id)) {
            return Result.success()
        }

        val reminderKey = inputData.getString("reminderKey")
            ?: "reminder-${event.id}-${event.startAtMillis}-$leadMinutes"
        val leadText = inputData.getString("leadText") ?: EventStore.reminderLeadLabel(leadMinutes)

        if (EventStore.tryMarkNotificationDelivery(applicationContext, reminderKey)) {
            val sent = NotificationHelper.notifyReminder(applicationContext, event, leadText, leadMinutes)
            if (!sent && !NotificationHelper.canPostNotifications(applicationContext)) {
                EventStore.upsertPendingDeadlineNotifications(
                    applicationContext,
                    listOf(
                        PendingDeadlineNotification(
                            key = reminderKey,
                            type = "reminder",
                            event = event,
                            timestamp = now,
                            leadText = leadText,
                            leadMinutes = leadMinutes
                        )
                    )
                )
            }
        }

        sendReminderEmailOnce(event, leadText, reminderKey)
        return Result.success()
    }

    private fun sendReminderEmailOnce(event: DeadlineEvent, leadText: String, reminderKey: String) {
        if (!EventStore.isEmailNotificationEnabled(applicationContext)) return

        val emailKey = "email-$reminderKey"
        if (!EventStore.tryMarkNotificationDelivery(applicationContext, emailKey)) return

        EmailNotificationService.sendReminderEmail(applicationContext, event, leadText) { success, message ->
            Log.i(TAG, "Reminder email worker result: success=$success, message=$message")
        }
    }

    companion object {
        private const val TAG = "ReminderWorker"
    }
}
