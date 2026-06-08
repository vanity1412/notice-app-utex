package com.utex.deadline

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object NotificationHelper {
    private const val ALERT_CHANNEL_ID = "ute_deadline_alerts_v2"
    private const val SUMMARY_CHANNEL_ID = "ute_deadline_summary_v1"
    private const val DAILY_SUMMARY_WINDOW_MILLIS = 3L * 24L * 60L * 60L * 1000L
    private val vibrationPattern = longArrayOf(0L, 280L, 160L, 280L)
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy", Locale.forLanguageTag("vi-VN"))
        .withZone(ZoneId.of("Asia/Ho_Chi_Minh"))

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val alertChannel = NotificationChannel(
                ALERT_CHANNEL_ID,
                "UTE Notice - Cảnh báo",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Báo deadline mới, deadline thay đổi và nhắc trước hạn"
                enableVibration(true)
                setVibrationPattern(this@NotificationHelper.vibrationPattern)
                enableLights(true)
                lightColor = android.graphics.Color.BLUE
                setSound(
                    android.provider.Settings.System.DEFAULT_NOTIFICATION_URI,
                    android.media.AudioAttributes.Builder()
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
                        .build()
                )
            }
            val summaryChannel = NotificationChannel(
                SUMMARY_CHANNEL_ID,
                "UTE Notice - Tổng hợp",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Thông báo tổng hợp hằng ngày, không âm thanh và không rung"
                setSound(null, null)
                enableVibration(false)
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(alertChannel)
            manager.createNotificationChannel(summaryChannel)
        }
    }

    fun notifyNewDeadline(context: Context, event: DeadlineEvent): Boolean {
        return show(
            context = context,
            title = "Lịch mới: ${EventLabels.kind(event)}",
            message = eventMessage(context, event),
            id = stableNotificationId("new-${event.id}"),
            targetUrl = event.sourceUrl,
            withSound = true
        )
    }

    fun notifyChangedDeadline(context: Context, event: DeadlineEvent): Boolean {
        return show(
            context = context,
            title = "Thay đổi: ${EventLabels.kind(event)}",
            message = "Giáo viên đã cập nhật lịch này.\n${eventMessage(context, event)}",
            id = stableNotificationId("changed-${event.id}"),
            targetUrl = event.sourceUrl,
            withSound = true
        )
    }

    fun notifyReminder(context: Context, event: DeadlineEvent, leadText: String): Boolean {
        return show(
            context = context,
            title = "Cảnh báo: $leadText",
            message = eventMessage(context, event),
            id = stableNotificationId("reminder-${event.id}-$leadText"),
            targetUrl = event.sourceUrl,
            withSound = true
        )
    }

    fun notifySummary(context: Context, count: Int): Boolean {
        return show(
            context = context,
            title = "UTE Notice đã sẵn sàng",
            message = "Đã tìm thấy $count deadline sắp tới. App sẽ nhắc khi có lịch mới hoặc gần tới hạn.",
            id = stableNotificationId("summary-first-sync")
        )
    }

    fun notifyTest(context: Context): Boolean {
        return show(
            context = context,
            title = "Test thông báo UTE Notice",
            message = "Nếu bạn thấy thông báo này thì quyền thông báo đang hoạt động.\nMốc nhắc đang bật: ${EventStore.reminderOffsetsText(context)} trước hạn.",
            id = stableNotificationId("test-notification"),
            withSound = true
        )
    }

    fun notifyDailySummary(context: Context, events: List<DeadlineEvent>): Boolean {
        val now = System.currentTimeMillis()
        val summaryEvents = events
            .filterNot { EventStore.isDone(context, it.id) }
            .filter { it.startAtMillis in now..(now + DAILY_SUMMARY_WINDOW_MILLIS) }
            .sortedBy { it.startAtMillis }

        val upcoming = summaryEvents
            .take(3)

        if (upcoming.isEmpty()) {
            return false
        }

        val lines = upcoming.joinToString("\n") { event ->
            "- ${EventLabels.kind(event)}: ${event.title} - ${formatTime(event.startAtMillis)}"
        }
        return show(
            context = context,
            title = "Nhắc lịch sắp tới",
            message = "Có ${summaryEvents.size} mục sắp tới trong 3 ngày.\n$lines",
            id = stableNotificationId("daily-summary")
        )
    }

    fun flushPendingDeadlineNotifications(context: Context): Int {
        ensureChannel(context)
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled() || (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        )) {
            return 0
        }

        val pending = EventStore.loadPendingDeadlineNotifications(context)
        if (pending.isEmpty()) return 0

        val remaining = mutableListOf<PendingDeadlineNotification>()
        var sent = 0
        pending.forEach { item ->
            val shown = when (item.type) {
                "new" -> notifyNewDeadline(context, item.event)
                "changed" -> notifyChangedDeadline(context, item.event)
                else -> true
            }
            if (shown) {
                sent += 1
            } else {
                remaining += item
            }
        }
        EventStore.savePendingDeadlineNotifications(context, remaining)
        return sent
    }

    private fun show(context: Context, title: String, message: String, id: Int, targetUrl: String? = null, withSound: Boolean = false): Boolean {
        ensureChannel(context)
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled() || (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        )) {
            return false
        }
        val intent = targetUrl
            ?.takeIf { it.isNotBlank() }
            ?.let { Intent(Intent.ACTION_VIEW, Uri.parse(it)) }
            ?: Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context,
            id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val channelId = if (withSound) ALERT_CHANNEL_ID else SUMMARY_CHANNEL_ID
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_stat_ute_notice)
            .setContentTitle(title)
            .setContentText(message.lineSequence().firstOrNull().orEmpty())
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(if (withSound) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
        
        if (withSound) {
            builder.setDefaults(NotificationCompat.DEFAULT_SOUND or NotificationCompat.DEFAULT_VIBRATE)
            builder.setVibrate(vibrationPattern)
        } else {
            builder.setSilent(true)
        }
        
        return try {
            manager.notify(id, builder.build())
            true
        } catch (_: SecurityException) {
            false
        } catch (_: RuntimeException) {
            false
        }
    }

    fun formatTime(millis: Long): String = timeFormatter.format(Instant.ofEpochMilli(millis))

    private fun eventMessage(context: Context, event: DeadlineEvent): String {
        val lines = mutableListOf<String>()
        lines += "${EventLabels.kind(event)}: ${event.title}"
        EventLabels.course(event)?.let { lines += "Môn/Lớp: $it" }
        EventLabels.cleanDescription(event)?.let { lines += it }
        lines += "${EventLabels.timeLabel(event)}: ${formatTime(event.startAtMillis)}"
        lines += "Mốc nhắc đang bật: ${EventStore.reminderOffsetsText(context)} trước hạn."
        return lines.joinToString("\n")
    }

    private fun stableNotificationId(text: String): Int {
        val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
        val value = ((digest[0].toInt() and 0x7F) shl 24) or
            ((digest[1].toInt() and 0xFF) shl 16) or
            ((digest[2].toInt() and 0xFF) shl 8) or
            (digest[3].toInt() and 0xFF)
        return value.takeIf { it != 0 } ?: 1
    }
}
