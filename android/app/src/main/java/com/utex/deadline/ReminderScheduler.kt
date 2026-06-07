package com.utex.deadline

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

object ReminderScheduler {
    private val localZone: ZoneId = ZoneId.of("Asia/Ho_Chi_Minh")

    private val reminderOffsetsMinutes = listOf(
        7L * 24L * 60L,
        3L * 24L * 60L,
        24L * 60L,
        2L * 60L
    )

    fun schedulePeriodicSync(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .addTag("ute-deadline-sync")
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "ute-deadline-periodic-sync",
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun scheduleImmediateSync(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .addTag("ute-deadline-sync")
            .build()
        WorkManager.getInstance(context).enqueue(request)
    }

    fun scheduleDailySummary(context: Context) {
        val manager = WorkManager.getInstance(context)
        if (!EventStore.isDailySummaryEnabled(context)) {
            manager.cancelUniqueWork("ute-deadline-daily-summary")
            return
        }

        val delayMillis = millisUntilNextDailySummary(
            hour = EventStore.getDailySummaryHour(context),
            minute = EventStore.getDailySummaryMinute(context),
            daysMask = EventStore.getDailySummaryDaysMask(context)
        )
        val request = OneTimeWorkRequestBuilder<DailySummaryWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .addTag("ute-deadline-daily-summary")
            .build()

        manager.enqueueUniqueWork(
            "ute-deadline-daily-summary",
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun scheduleAll(context: Context, events: List<DeadlineEvent>) {
        val now = System.currentTimeMillis()
        val manager = WorkManager.getInstance(context)
        events.forEach { event ->
            reminderOffsetsMinutes.forEach { offsetMinutes ->
                val triggerAt = event.startAtMillis - offsetMinutes * 60_000L
                val delay = triggerAt - now
                if (delay > 60_000L) {
                    val data = Data.Builder()
                        .putString("title", event.title)
                        .putLong("startAtMillis", event.startAtMillis)
                        .build()
                    val request = OneTimeWorkRequestBuilder<ReminderWorker>()
                        .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                        .setInputData(data)
                        .addTag("ute-deadline-reminder")
                        .build()
                    manager.enqueueUniqueWork(
                        "reminder-${event.id}-$offsetMinutes",
                        ExistingWorkPolicy.REPLACE,
                        request
                    )
                }
            }
        }
    }

    private fun millisUntilNextDailySummary(hour: Int, minute: Int, daysMask: Int): Long {
        val now = ZonedDateTime.now(localZone)
        val safeHour = hour.coerceIn(0, 23)
        val safeMinute = minute.coerceIn(0, 59)
        val safeDays = daysMask and EventStore.ALL_DAYS_MASK

        for (offset in 0..7) {
            val candidate = now.plusDays(offset.toLong())
                .withHour(safeHour)
                .withMinute(safeMinute)
                .withSecond(0)
                .withNano(0)
            val dayBit = 1 shl (candidate.dayOfWeek.value - 1)
            if (safeDays and dayBit != 0 && candidate.isAfter(now)) {
                return Duration.between(now, candidate).toMillis().coerceAtLeast(60_000L)
            }
        }
        return TimeUnit.DAYS.toMillis(1)
    }
}
