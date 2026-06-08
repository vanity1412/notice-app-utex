package com.utex.deadline

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters

class DailySummaryWorker(appContext: Context, params: WorkerParameters) : Worker(appContext, params) {
    override fun doWork(): Result {
        if (EventStore.isDailySummaryEnabled(applicationContext) && 
            EventStore.isDailySummaryAllowedToday(applicationContext)) {
            val events = EventStore.loadEvents(applicationContext)
            val sent = NotificationHelper.notifyDailySummary(applicationContext, events)
            
            // Nếu không gửi được (chưa cấp quyền), lưu vào pending
            if (!sent && !NotificationHelper.canPostNotifications(applicationContext) &&
                NotificationHelper.hasUpcomingDailySummary(applicationContext, events)) {
                val now = System.currentTimeMillis()
                val pending = PendingDeadlineNotification(
                    key = "daily-summary-$now",
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
        ReminderScheduler.scheduleDailySummary(applicationContext)
        return Result.success()
    }
}
