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
import java.time.LocalDate
import java.time.ZoneId
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

        val delayMillis = millisUntilNextDailySummary(EventStore.getDailySummaryHour(context))
        val request = PeriodicWorkRequestBuilder<DailySummaryWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .addTag("ute-deadline-daily-summary")
            .build()

        manager.enqueueUniquePeriodicWork(
            "ute-deadline-daily-summary",
            ExistingPeriodicWorkPolicy.UPDATE,
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

    private fun millisUntilNextDailySummary(hour: Int): Long {
        val now = java.time.ZonedDateTime.now(localZone)
        var next = LocalDate.now(localZone).atTime(hour.coerceIn(0, 23), 0).atZone(localZone)
        if (!next.isAfter(now)) {
            next = next.plusDays(1)
        }
        return Duration.between(now, next).toMillis().coerceAtLeast(60_000L)
    }
}
