package com.utex.deadline

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.security.MessageDigest
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

object ReminderScheduler {
    const val INPUT_AUTO_SYNC = "auto_sync"

    private const val AUTO_SYNC_WORK_NAME = "ute-deadline-auto-sync"
    private const val AUTO_SYNC_INTERVAL_MINUTES = 5L
    private const val MAX_REMINDER_ALARMS = 250
    private const val DAILY_SUMMARY_ALARM_KEY = "daily-summary"

    // Nếu người dùng thêm mốc đúng sát giờ báo, cho phép bắn ngay thay vì bỏ qua âm thầm.
    private const val MISSED_REMINDER_GRACE_MILLIS = 5L * 60L * 1000L
    private const val MIN_INITIAL_DELAY_MILLIS = 1_000L

    private val localZone: ZoneId = ZoneId.of("Asia/Ho_Chi_Minh")

    fun schedulePeriodicSync(context: Context) {
        scheduleAutoSync(context, ExistingWorkPolicy.KEEP)
    }

    fun scheduleNextPeriodicSync(context: Context) {
        scheduleAutoSync(context, ExistingWorkPolicy.APPEND_OR_REPLACE)
    }

    fun reschedulePeriodicSync(context: Context) {
        scheduleAutoSync(context, ExistingWorkPolicy.REPLACE)
    }

    private fun scheduleAutoSync(context: Context, policy: ExistingWorkPolicy) {
        if (EventStore.getIcalUrl(context).isBlank()) return
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setInitialDelay(AUTO_SYNC_INTERVAL_MINUTES, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .setInputData(Data.Builder().putBoolean(INPUT_AUTO_SYNC, true).build())
            .addTag("ute-deadline-sync")
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            AUTO_SYNC_WORK_NAME,
            policy,
            request
        )
    }

    fun scheduleImmediateSync(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .setInputData(Data.Builder().putBoolean(INPUT_AUTO_SYNC, false).build())
            .addTag("ute-deadline-sync")
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "ute-deadline-immediate-sync",
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    fun cancelAll(context: Context) {
        val appContext = context.applicationContext
        val manager = WorkManager.getInstance(appContext)
        manager.cancelUniqueWork(AUTO_SYNC_WORK_NAME)
        manager.cancelUniqueWork("ute-deadline-daily-summary")
        manager.cancelAllWorkByTag("ute-deadline-sync")
        manager.cancelAllWorkByTag("ute-deadline-reminder")
        cancelDailySummaryAlarm(appContext)
        cancelScheduledReminderAlarms(appContext)
    }

    fun scheduleDailySummary(context: Context) {
        val appContext = context.applicationContext
        val manager = WorkManager.getInstance(appContext)
        manager.cancelUniqueWork("ute-deadline-daily-summary")
        cancelDailySummaryAlarm(appContext)

        if (!EventStore.isDailySummaryEnabled(appContext)) {
            return
        }

        val times = EventStore.getDailySummaryTimes(appContext)
        if (times.isEmpty()) {
            return
        }

        val next = nextDailySummaryAtMillis(
            timesOfDay = times,
            daysMask = EventStore.getDailySummaryDaysMask(appContext)
        )
        dailySummaryPendingIntent(
            appContext,
            PendingIntent.FLAG_UPDATE_CURRENT,
            next.minutesOfDay
        )?.let {
            setAlarm(appContext, next.triggerAtMillis, it)
        }
    }

    fun scheduleAll(context: Context, events: List<DeadlineEvent>) {
        val appContext = context.applicationContext
        val now = System.currentTimeMillis()
        val workManager = WorkManager.getInstance(appContext)
        workManager.cancelAllWorkByTag("ute-deadline-reminder")
        cancelScheduledReminderAlarms(appContext)

        val reminderOffsets = EventStore.getReminderOffsetsMinutes(appContext).map { minutes ->
            ReminderOffset(EventStore.reminderLeadLabel(minutes), minutes)
        }
        val plans = events
            .filterNot { EventStore.isDone(appContext, it.id) }
            .filter { it.startAtMillis > now }
            .flatMap { event ->
                reminderOffsets.mapNotNull { offset ->
                    val rawTriggerAt = event.startAtMillis - offset.minutes * 60_000L
                    when {
                        // Mốc đã qua quá lâu thì bỏ qua, tránh spam khi người dùng bật lại mốc cũ.
                        rawTriggerAt < now - MISSED_REMINDER_GRACE_MILLIS -> null
                        else -> ReminderPlan(
                            key = "reminder-${event.id}-${event.startAtMillis}-${offset.minutes}",
                            event = event,
                            offset = offset,
                            triggerAt = rawTriggerAt.coerceAtLeast(now + MIN_INITIAL_DELAY_MILLIS)
                        )
                    }
                }
            }
            .sortedBy { it.triggerAt }
            .take(MAX_REMINDER_ALARMS)

        val scheduledKeys = mutableSetOf<String>()
        plans.forEach { plan ->
            val pendingIntent = reminderPendingIntent(appContext, plan, PendingIntent.FLAG_UPDATE_CURRENT)
            setAlarm(appContext, plan.triggerAt, pendingIntent)

            // Backup bằng WorkManager: nếu AlarmManager bị máy chặn/delay vì pin hoặc quyền exact alarm,
            // worker vẫn có cơ hội bắn thông báo gần thời điểm đã chọn.
            scheduleReminderWork(appContext, plan)

            scheduledKeys += plan.key
        }
        EventStore.saveScheduledReminderAlarmKeys(appContext, scheduledKeys)
    }

    fun canScheduleExactAlarms(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return alarmManager.canScheduleExactAlarms()
    }

    private fun setAlarm(context: Context, triggerAtMillis: Long, pendingIntent: PendingIntent) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        try {
            if (canScheduleExactAlarms(context)) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                } else {
                    alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                }
            } else {
                setInexactAllowWhileIdle(alarmManager, triggerAtMillis, pendingIntent)
            }
        } catch (_: SecurityException) {
            setInexactAllowWhileIdle(alarmManager, triggerAtMillis, pendingIntent)
        }
    }

    private fun setInexactAllowWhileIdle(
        alarmManager: AlarmManager,
        triggerAtMillis: Long,
        pendingIntent: PendingIntent
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        } else {
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }
    }

    private fun scheduleReminderWork(context: Context, plan: ReminderPlan) {
        val delayMillis = (plan.triggerAt - System.currentTimeMillis()).coerceAtLeast(MIN_INITIAL_DELAY_MILLIS)
        val data = Data.Builder()
            .putString("id", plan.event.id)
            .putString("title", plan.event.title)
            .putLong("startAtMillis", plan.event.startAtMillis)
            .putString("sourceUrl", plan.event.sourceUrl.orEmpty())
            .putString("rawType", plan.event.rawType.orEmpty())
            .putString("description", plan.event.description.orEmpty())
            .putString("source", plan.event.source.name)
            .putString("leadText", plan.offset.label)
            .putLong("leadMinutes", plan.offset.minutes)
            .putString("reminderKey", plan.key)
            .build()

        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .setInputData(data)
            .addTag("ute-deadline-reminder")
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "ute-deadline-${plan.key}",
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    private fun cancelScheduledReminderAlarms(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        EventStore.getScheduledReminderAlarmKeys(context).forEach { key ->
            reminderCancelPendingIntent(context, key)?.let { alarmManager.cancel(it) }
        }
        EventStore.clearScheduledReminderAlarmKeys(context)
    }

    private fun cancelDailySummaryAlarm(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        dailySummaryPendingIntent(context, PendingIntent.FLAG_NO_CREATE)?.let { alarmManager.cancel(it) }
    }

    private fun reminderPendingIntent(context: Context, plan: ReminderPlan, flags: Int): PendingIntent {
        val intent = Intent(context, DeadlineAlarmReceiver::class.java)
            .setAction(DeadlineAlarmReceiver.ACTION_REMINDER)
            .putExtra(DeadlineAlarmReceiver.EXTRA_ID, plan.event.id)
            .putExtra(DeadlineAlarmReceiver.EXTRA_TITLE, plan.event.title)
            .putExtra(DeadlineAlarmReceiver.EXTRA_START_AT, plan.event.startAtMillis)
            .putExtra(DeadlineAlarmReceiver.EXTRA_SOURCE_URL, plan.event.sourceUrl.orEmpty())
            .putExtra(DeadlineAlarmReceiver.EXTRA_RAW_TYPE, plan.event.rawType.orEmpty())
            .putExtra(DeadlineAlarmReceiver.EXTRA_DESCRIPTION, plan.event.description.orEmpty())
            .putExtra(DeadlineAlarmReceiver.EXTRA_SOURCE, plan.event.source.name)
            .putExtra(DeadlineAlarmReceiver.EXTRA_LEAD_TEXT, plan.offset.label)
            .putExtra(DeadlineAlarmReceiver.EXTRA_LEAD_MINUTES, plan.offset.minutes)
            .putExtra(DeadlineAlarmReceiver.EXTRA_REMINDER_KEY, plan.key)
        return PendingIntent.getBroadcast(
            context,
            stableAlarmRequestCode(plan.key),
            intent,
            flags or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun reminderCancelPendingIntent(context: Context, key: String): PendingIntent? {
        val intent = Intent(context, DeadlineAlarmReceiver::class.java)
            .setAction(DeadlineAlarmReceiver.ACTION_REMINDER)
        return PendingIntent.getBroadcast(
            context,
            stableAlarmRequestCode(key),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun dailySummaryPendingIntent(
        context: Context,
        flags: Int,
        minutesOfDay: Int = -1
    ): PendingIntent? {
        val intent = Intent(context, DeadlineAlarmReceiver::class.java)
            .setAction(DeadlineAlarmReceiver.ACTION_DAILY_SUMMARY)
            .putExtra(DeadlineAlarmReceiver.EXTRA_DAILY_SUMMARY_MINUTES_OF_DAY, minutesOfDay)
        return PendingIntent.getBroadcast(
            context,
            stableAlarmRequestCode(DAILY_SUMMARY_ALARM_KEY),
            intent,
            flags or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun nextDailySummaryAtMillis(timesOfDay: List<Int>, daysMask: Int): DailySummaryPlan {
        val now = ZonedDateTime.now(localZone)
        val safeDays = daysMask and EventStore.ALL_DAYS_MASK
        val safeTimes = timesOfDay
            .filter { it in 0 until 24 * 60 }
            .distinct()
            .sorted()
            .ifEmpty { listOf(6 * 60) }

        for (dayOffset in 0..7) {
            val date = now.plusDays(dayOffset.toLong()).toLocalDate()
            val dayBit = 1 shl (date.dayOfWeek.value - 1)
            if (safeDays and dayBit == 0) continue

            safeTimes.forEach { minutes ->
                val candidate = date
                    .atTime(minutes / 60, minutes % 60)
                    .atZone(localZone)
                if (candidate.isAfter(now)) {
                    return DailySummaryPlan(
                        triggerAtMillis = candidate.toInstant().toEpochMilli(),
                        minutesOfDay = minutes
                    )
                }
            }
        }

        val fallback = now.plusDays(1).withHour(6).withMinute(0).withSecond(0).withNano(0)
        return DailySummaryPlan(fallback.toInstant().toEpochMilli(), 6 * 60)
    }

    private fun stableAlarmRequestCode(text: String): Int {
        val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
        return ((digest[0].toInt() and 0x7F) shl 24) or
            ((digest[1].toInt() and 0xFF) shl 16) or
            ((digest[2].toInt() and 0xFF) shl 8) or
            (digest[3].toInt() and 0xFF)
    }

    private data class DailySummaryPlan(
        val triggerAtMillis: Long,
        val minutesOfDay: Int
    )

    private data class ReminderOffset(
        val label: String,
        val minutes: Long
    )

    private data class ReminderPlan(
        val key: String,
        val event: DeadlineEvent,
        val offset: ReminderOffset,
        val triggerAt: Long
    )
}
