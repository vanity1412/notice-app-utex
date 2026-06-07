package com.utex.deadline

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    fun notifyNewDeadline(context: Context, event: DeadlineEvent) {
        show(
            context = context,
            title = "Lịch mới: ${EventLabels.kind(event)}",
            message = eventMessage(event),
            id = stableNotificationId("new-${event.id}")
        )
    }

    fun notifyReminder(context: Context, event: DeadlineEvent, leadText: String) {
        show(
            context = context,
            title = "Cảnh báo: $leadText",
            message = eventMessage(event),
            id = stableNotificationId("reminder-${event.id}-$leadText")
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

    fun notifyDailySummary(context: Context, events: List<DeadlineEvent>) {
        val upcoming = events
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
            message = "Có ${events.size} mục đang theo dõi.\n$lines",
            id = stableNotificationId("daily-summary")
        )
    }

    private fun show(context: Context, title: String, message: String, id: Int) {
        ensureChannel(context)
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message.lineSequence().firstOrNull().orEmpty())
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        NotificationManagerCompat.from(context).notify(id, notification)
    }

    fun formatTime(millis: Long): String = timeFormatter.format(Instant.ofEpochMilli(millis))

    private fun eventMessage(event: DeadlineEvent): String {
        val lines = mutableListOf<String>()
        lines += "${EventLabels.kind(event)}: ${event.title}"
        EventLabels.course(event)?.let { lines += "Môn/Lớp: $it" }
        EventLabels.cleanDescription(event)?.let { lines += it }
        lines += "${EventLabels.timeLabel(event)}: ${formatTime(event.startAtMillis)}"
        lines += "Mốc nhắc cố định: 1 ngày, 12 giờ, 1 giờ trước hạn."
        return lines.joinToString("\n")
    }

    private fun stableNotificationId(text: String): Int = abs(text.hashCode())
}
