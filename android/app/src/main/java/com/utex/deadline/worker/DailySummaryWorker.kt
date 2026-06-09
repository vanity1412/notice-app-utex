package com.utex.deadline

import android.content.Context
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters

class DailySummaryWorker(appContext: Context, params: WorkerParameters) : Worker(appContext, params) {
    override fun doWork(): Result {
        if (EventStore.isDailySummaryEnabled(applicationContext) &&
            EventStore.isDailySummaryAllowedToday(applicationContext)) {
            val events = EventStore.loadEvents(applicationContext)
            if (NotificationHelper.hasUpcomingDailySummary(applicationContext, events)) {
                val deliveryKey = dailySummaryDeliveryKey()

                if (EventStore.tryMarkNotificationDelivery(applicationContext, deliveryKey)) {
                    val sent = NotificationHelper.notifyDailySummary(applicationContext, events)

                    // Nếu không gửi được (chưa cấp quyền), lưu vào pending
                    if (!sent && !NotificationHelper.canPostNotifications(applicationContext)) {
                        val now = System.currentTimeMillis()
                        val pending = PendingDeadlineNotification(
                            key = deliveryKey,
                            type = "daily-summary",
                            event = DeadlineEvent(
                                id = "daily-summary",
                                title = "Daily Summary",
                                startAtMillis = now
                            ),
                            timestamp = now
                        )
                        EventStore.upsertPendingDeadlineNotifications(applicationContext, listOf(pending))
                    }
                }

                sendDailySummaryEmailOnce(events, deliveryKey)
            }
        }
        ReminderScheduler.scheduleDailySummary(applicationContext)
        return Result.success()
    }

    private fun sendDailySummaryEmailOnce(events: List<DeadlineEvent>, deliveryKey: String) {
        if (!EventStore.isEmailNotificationEnabled(applicationContext)) return

        val emailKey = "email-$deliveryKey"
        if (!EventStore.tryMarkNotificationDelivery(applicationContext, emailKey)) return

        EmailNotificationService.sendDailySummaryEmail(applicationContext, events) { success, message ->
            Log.i(TAG, "Daily summary email result: success=$success, message=$message")
        }
    }

    private fun dailySummaryDeliveryKey(): String {
        val date = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh"))
        val hour = EventStore.getDailySummaryHour(applicationContext)
        val minute = EventStore.getDailySummaryMinute(applicationContext)
        return "daily-summary-$date-$hour-$minute"
    }

    companion object {
        private const val TAG = "DailySummaryWorker"
    }
}
