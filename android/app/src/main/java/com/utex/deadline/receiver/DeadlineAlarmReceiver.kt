package com.utex.deadline

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class DeadlineAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_REMINDER -> handleReminder(context.applicationContext, intent)
            ACTION_DAILY_SUMMARY -> handleDailySummary(context.applicationContext, intent)
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

        val eventId = intent.getStringExtra(EXTRA_ID) ?: "$title-$startAt"
        val event = DeadlineEvent(
            id = eventId,
            title = title,
            startAtMillis = startAt,
            sourceUrl = intent.getStringExtra(EXTRA_SOURCE_URL)?.takeIf { it.isNotBlank() },
            rawType = intent.getStringExtra(EXTRA_RAW_TYPE)?.takeIf { it.isNotBlank() },
            description = intent.getStringExtra(EXTRA_DESCRIPTION)?.takeIf { it.isNotBlank() },
            source = DeadlineSource.fromStored(intent.getStringExtra(EXTRA_SOURCE))
        )

        if (EventStore.isDone(context, event.id)) {
            return
        }

        val reminderKey = intent.getStringExtra(EXTRA_REMINDER_KEY)
            ?: "reminder-${event.id}-${event.startAtMillis}-$leadMinutes"
        val leadText = intent.getStringExtra(EXTRA_LEAD_TEXT) ?: EventStore.reminderLeadLabel(leadMinutes)

        // Notification và email dùng key riêng.
        // Không return khi notification key đã tồn tại, vì WorkManager backup có thể đã bắn notification
        // nhưng chưa gửi email. Email vẫn cần được xét bằng emailKey riêng để gửi đúng 1 lần.
        if (EventStore.tryMarkNotificationDelivery(context, reminderKey)) {
            val sent = NotificationHelper.notifyReminder(context, event, leadText, leadMinutes)
            if (!sent && !NotificationHelper.canPostNotifications(context)) {
                EventStore.upsertPendingDeadlineNotifications(
                    context,
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

        sendReminderEmailOnce(context, event, leadText, reminderKey)
    }

    private fun handleDailySummary(context: Context, intent: Intent) {
        if (EventStore.isDailySummaryEnabled(context) && EventStore.isDailySummaryAllowedToday(context)) {
            val events = EventStore.loadEvents(context)
            if (NotificationHelper.hasUpcomingDailySummary(context, events)) {
                val minutesOfDay = intent.getIntExtra(EXTRA_DAILY_SUMMARY_MINUTES_OF_DAY, -1)
                val deliveryKey = dailySummaryDeliveryKey(context, minutesOfDay)

                if (EventStore.tryMarkNotificationDelivery(context, deliveryKey)) {
                    val sent = NotificationHelper.notifyDailySummary(context, events)
                    if (!sent && !NotificationHelper.canPostNotifications(context)) {
                        val now = System.currentTimeMillis()
                        EventStore.upsertPendingDeadlineNotifications(
                            context,
                            listOf(
                                PendingDeadlineNotification(
                                    key = deliveryKey,
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

                sendDailySummaryEmailOnce(context, events, deliveryKey)
            }
        }
        ReminderScheduler.scheduleDailySummary(context)
    }

    private fun sendReminderEmailOnce(
        context: Context,
        event: DeadlineEvent,
        leadText: String,
        reminderKey: String
    ) {
        if (!EventStore.isEmailNotificationEnabled(context)) return

        val emailKey = "email-$reminderKey"
        if (!EventStore.tryMarkNotificationDelivery(context, emailKey)) return

        EmailNotificationService.sendReminderEmail(context, event, leadText) { success, message ->
            Log.i(TAG, "Reminder email result: success=$success, message=$message")
        }
    }

    private fun sendDailySummaryEmailOnce(context: Context, events: List<DeadlineEvent>, deliveryKey: String) {
        if (!EventStore.isEmailNotificationEnabled(context)) return

        val emailKey = "email-$deliveryKey"
        if (!EventStore.tryMarkNotificationDelivery(context, emailKey)) return

        EmailNotificationService.sendDailySummaryEmail(context, events) { success, message ->
            Log.i(TAG, "Daily summary email result: success=$success, message=$message")
        }
    }

    private fun dailySummaryDeliveryKey(context: Context, minutesOfDay: Int): String {
        val date = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh"))
        val safeMinutes = if (minutesOfDay in 0 until 24 * 60) {
            minutesOfDay
        } else {
            EventStore.getDailySummaryTimes(context).firstOrNull()
                ?: (EventStore.getDailySummaryHour(context) * 60 + EventStore.getDailySummaryMinute(context))
        }
        val hour = safeMinutes / 60
        val minute = safeMinutes % 60
        return "daily-summary-$date-$hour-$minute"
    }

    companion object {
        private const val TAG = "DeadlineAlarmReceiver"

        const val ACTION_REMINDER = "com.utex.deadline.action.REMINDER"
        const val ACTION_DAILY_SUMMARY = "com.utex.deadline.action.DAILY_SUMMARY"

        const val EXTRA_ID = "id"
        const val EXTRA_TITLE = "title"
        const val EXTRA_START_AT = "startAtMillis"
        const val EXTRA_SOURCE_URL = "sourceUrl"
        const val EXTRA_RAW_TYPE = "rawType"
        const val EXTRA_DESCRIPTION = "description"
        const val EXTRA_SOURCE = "source"
        const val EXTRA_LEAD_TEXT = "leadText"
        const val EXTRA_LEAD_MINUTES = "leadMinutes"
        const val EXTRA_REMINDER_KEY = "reminderKey"
        const val EXTRA_DAILY_SUMMARY_MINUTES_OF_DAY = "dailySummaryMinutesOfDay"
    }
}
