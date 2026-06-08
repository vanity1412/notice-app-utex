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
        
        // Kiểm tra nếu đã tới hạn deadline
        if (startAt <= 0L || now >= startAt) {
            return Result.success()
        }
        
        // Kiểm tra nếu quá thời điểm nhắc (tolerance 5 phút)
        val expectedReminderTime = startAt - (leadMinutes * 60_000L)
        val tolerance = 5L * 60L * 1000L // 5 phút
        if (now > expectedReminderTime + tolerance) {
            // Quá thời điểm nhắc, bỏ qua thông báo này
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
        
        // Kiểm tra nếu deadline đã được đánh dấu done
        if (EventStore.isDone(applicationContext, event.id)) {
            return Result.success()
        }
        
        val leadText = inputData.getString("leadText") ?: EventStore.reminderLeadLabel(leadMinutes)
        NotificationHelper.notifyReminder(applicationContext, event, leadText, leadMinutes)
        return Result.success()
    }
}
