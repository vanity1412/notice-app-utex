package com.utex.deadline

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters

class DailySummaryWorker(appContext: Context, params: WorkerParameters) : Worker(appContext, params) {
    override fun doWork(): Result {
        if (EventStore.isDailySummaryEnabled(applicationContext) && 
            EventStore.isDailySummaryAllowedToday(applicationContext)) {
            val sent = NotificationHelper.notifyDailySummary(applicationContext, EventStore.loadEvents(applicationContext))
            
            // Nếu không gửi được (chưa cấp quyền), lưu vào pending
            if (!sent) {
                val pending = PendingDeadlineNotification(
                    key = "daily-summary-${System.currentTimeMillis()}",
                    type = "daily-summary",
                    event = DeadlineEvent(
                        id = "daily-summary",
                        title = "Daily Summary",
                        startAtMillis = System.currentTimeMillis()
                    ),
                    timestamp = System.currentTimeMillis()
                )
                EventStore.upsertPendingDeadlineNotifications(applicationContext, listOf(pending))
            }
        }
        ReminderScheduler.scheduleDailySummary(applicationContext)
        return Result.success()
    }
}
