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
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

object NotificationHelper {
    private const val CHANNEL_ID = "ute_deadline_channel"
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy", Locale.forLanguageTag("vi-VN"))
        .withZone(ZoneId.of("Asia/Ho_Chi_Minh"))

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "UTE Notice",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Báo lịch kiểm tra và deadline Moodle HCM-UTE"
                // Bật âm thanh và rung cho channel
                enableVibration(true)
                enableLights(true)
                lightColor = android.graphics.Color.BLUE
                // Sử dụng âm thanh thông báo mặc định của hệ thống
                setSound(
                    android.provider.Settings.System.DEFAULT_NOTIFICATION_URI,
                    android.media.AudioAttributes.Builder()
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
                        .build()
                )
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    fun notifyNewDeadline(context: Context, event: DeadlineEvent) {
        show(
            context = context,
            title = "Lịch mới: ${EventLabels.kind(event)}",
            message = eventMessage(context, event),
            id = stableNotificationId("new-${event.id}"),
            targetUrl = event.sourceUrl,
            withSound = true
        )
    }

    fun notifyChangedDeadline(context: Context, event: DeadlineEvent) {
        show(
            context = context,
            title = "Thay đổi: ${EventLabels.kind(event)}",
            message = "Giáo viên đã cập nhật lịch này.\n${eventMessage(context, event)}",
            id = stableNotificationId("changed-${event.id}"),
            targetUrl = event.sourceUrl,
            withSound = true
        )
    }

    fun notifyReminder(context: Context, event: DeadlineEvent, leadText: String) {
        show(
            context = context,
            title = "Cảnh báo: $leadText",
            message = eventMessage(context, event),
            id = stableNotificationId("reminder-${event.id}-$leadText"),
            targetUrl = event.sourceUrl,
            withSound = true
        )
    }

    fun notifySummary(context: Context, count: Int) {
        show(
            context = context,
            title = "UTE Notice đã sẵn sàng",
            message = "Đã tìm thấy $count deadline sắp tới. App sẽ nhắc khi có lịch mới hoặc gần tới hạn.",
            id = stableNotificationId("summary-first-sync")
        )
    }

    fun notifyTest(context: Context) {
        show(
            context = context,
            title = "Test thông báo UTE Notice",
            message = "Nếu bạn thấy thông báo này thì quyền thông báo đang hoạt động.\nMốc nhắc đang bật: ${EventStore.reminderOffsetsText(context)} trước hạn.",
            id = stableNotificationId("test-notification")
        )
    }

    fun notifyDailySummary(context: Context, events: List<DeadlineEvent>) {
        val upcoming = events
            .filterNot { EventStore.isDone(context, it.id) }
            .filter { it.startAtMillis >= System.currentTimeMillis() - 60_000L }
            .sortedBy { it.startAtMillis }
            .take(3)

        if (upcoming.isEmpty()) {
            show(
                context = context,
                title = "UTE Notice",
                message = "Hôm nay chưa có deadline sắp tới trong lịch đã lưu.",
                id = stableNotificationId("daily-summary-empty")
            )
            return
        }

        val lines = upcoming.joinToString("\n") { event ->
            "- ${EventLabels.kind(event)}: ${event.title} - ${formatTime(event.startAtMillis)}"
        }
        show(
            context = context,
            title = "Nhắc lịch hôm nay",
            message = "Có ${events.count { !EventStore.isDone(context, it.id) }} mục đang theo dõi.\n$lines",
            id = stableNotificationId("daily-summary")
        )
    }

    private fun show(context: Context, title: String, message: String, id: Int, targetUrl: String? = null, withSound: Boolean = false) {
        ensureChannel(context)
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return
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
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_ute_notice)
            .setContentTitle(title)
            .setContentText(message.lineSequence().firstOrNull().orEmpty())
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
        
        // Thêm âm thanh và rung khi cần
        if (withSound) {
            builder.setDefaults(NotificationCompat.DEFAULT_SOUND or NotificationCompat.DEFAULT_VIBRATE)
        }
        
        NotificationManagerCompat.from(context).notify(id, builder.build())
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

    private fun stableNotificationId(text: String): Int = abs(text.hashCode())
}
