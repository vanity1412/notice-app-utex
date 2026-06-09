package com.utex.deadline

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class DeadlineAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_REMINDER -> handleReminder(context.applicationContext, intent)
            ACTION_DAILY_SUMMARY -> handleDailySummary(context.applicationContext)
        }
    }

    private fun handleReminder(context: Context, intent: Intent) {
        val title = intent.getStringExtra(EXTRA_TITLE) ?: return
        val startAt = intent.getLongExtra(EXTRA_START_AT, 0L)
        val leadMinutes = intent.getLongExtra(EXTRA_LEAD_MINUTES, 0L)
        val now = System.currentTimeMillis()

        if (startAt <= 0L) {
            return
        }

        // Cho phép thông báo ngay cả khi đã quá hạn (nhưng không quá 1 giờ)
        if (now > startAt + 60 * 60_000L) {
            return // Quá hạn hơn 1 giờ thì không thông báo nữa
        }

        val event = DeadlineEvent(
            id = intent.getStringExtra(EXTRA_ID) ?: "$title-$startAt",
            title = title,
            startAtMillis = startAt,
            sourceUrl = intent.getStringExtra(EXTRA_SOURCE_URL)?.takeIf { it.isNotBlank() },
            rawType = intent.getStringExtra(EXTRA_RAW_TYPE)?.takeIf { it.isNotBlank() },
            description = intent.getStringExtra(EXTRA_DESCRIPTION)?.takeIf { it.isNotBlank() }
        )

        if (EventStore.isDone(context, event.id)) {
            return
        }

        val leadText = intent.getStringExtra(EXTRA_LEAD_TEXT) ?: EventStore.reminderLeadLabel(leadMinutes)
        val sent = NotificationHelper.notifyReminder(context, event, leadText, leadMinutes)
        if (!sent && !NotificationHelper.canPostNotifications(context)) {
            EventStore.upsertPendingDeadlineNotifications(
                context,
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
    }

    private fun handleDailySummary(context: Context) {
        if (EventStore.isDailySummaryEnabled(context) && EventStore.isDailySummaryAllowedToday(context)) {
            val events = EventStore.loadEvents(context)
            val sent = NotificationHelper.notifyDailySummary(context, events)
            if (!sent && !NotificationHelper.canPostNotifications(context) &&
                NotificationHelper.hasUpcomingDailySummary(context, events)) {
                val now = System.currentTimeMillis()
                EventStore.upsertPendingDeadlineNotifications(
                    context,
                    listOf(
                        PendingDeadlineNotification(
                            key = "daily-summary-$now",
                            type = "daily-summary",
                            event = DeadlineEvent(
                                id = "daily-summary",
                                title = "Daily Summary",
                                startAtMillis = now
                            ),
                            timestamp = now
                        )
                    )
                )
            }
        }
        ReminderScheduler.scheduleDailySummary(context)
    }

    companion object {
        const val ACTION_REMINDER = "com.utex.deadline.action.REMINDER"
        const val ACTION_DAILY_SUMMARY = "com.utex.deadline.action.DAILY_SUMMARY"

        const val EXTRA_ID = "id"
        const val EXTRA_TITLE = "title"
        const val EXTRA_START_AT = "startAtMillis"
        const val EXTRA_SOURCE_URL = "sourceUrl"
        const val EXTRA_RAW_TYPE = "rawType"
        const val EXTRA_DESCRIPTION = "description"
        const val EXTRA_LEAD_TEXT = "leadText"
        const val EXTRA_LEAD_MINUTES = "leadMinutes"
    }
}
