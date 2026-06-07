package com.utex.deadline

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object ReminderScheduler {
    private val reminderOffsetsMinutes = listOf(
        7L * 24L * 60L,
        3L * 24L * 60L,
        24L * 60L,
        2L * 60L
    )

    fun schedulePeriodicSync(context: Context) {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .addTag("ute-deadline-sync")
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "ute-deadline-periodic-sync",
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun scheduleImmediateSync(context: Context) {
        val request = OneTimeWorkRequestBuilder<SyncWorker>().build()
        WorkManager.getInstance(context).enqueue(request)
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
                        ExistingWorkPolicy.KEEP,
                        request
                    )
                }
            }
        }
    }
}
