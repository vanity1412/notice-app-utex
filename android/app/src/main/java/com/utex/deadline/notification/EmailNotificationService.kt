package com.utex.deadline

import android.content.Context
import android.util.Log
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.Properties
import javax.mail.Authenticator
import javax.mail.Message
import javax.mail.PasswordAuthentication
import javax.mail.Session
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage
import kotlin.concurrent.thread

/**
 * Service gửi email thông báo deadline qua Gmail SMTP
 */
object EmailNotificationService {
    private const val TAG = "EmailNotificationService"
    private const val SMTP_HOST = "smtp.gmail.com"
    private const val SMTP_PORT = "587" // STARTTLS
    private const val DEFAULT_SENDER_EMAIL = "watershoputetea@gmail.com"
    private const val DEFAULT_APP_PASSWORD = "jhjdwthgjpezsvfa" // App password từ Google
    
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy", Locale.forLanguageTag("vi-VN"))
        .withZone(ZoneId.of("Asia/Ho_Chi_Minh"))

    /**
     * Gửi email thông báo deadline mới
     */
    fun sendNewDeadlineEmail(context: Context, event: DeadlineEvent, callback: (Boolean, String) -> Unit) {
        val userEmail = EventStore.getUserEmail(context)
        if (userEmail.isBlank()) {
            callback(false, "Email chưa được cấu hình")
            return
        }
        
        if (EventStore.isDone(context, event.id)) {
            callback(true, "Deadline đã xong, không gửi email")
            return
        }

        val subject = "🔔 Deadline mới: ${EventLabels.kind(event)}"
        val message = buildEmailMessage(context, event, "Bạn có deadline mới từ Moodle UTEx")
        
        sendEmailAsync(userEmail, subject, message, callback)
    }

    /**
     * Gửi email cảnh báo trước hạn
     */
    fun sendReminderEmail(context: Context, event: DeadlineEvent, leadText: String, callback: (Boolean, String) -> Unit) {
        val userEmail = EventStore.getUserEmail(context)
        if (userEmail.isBlank()) {
            callback(false, "Email chưa được cấu hình")
            return
        }
        
        if (EventStore.isDone(context, event.id)) {
            callback(true, "Deadline đã xong, không gửi email")
            return
        }

        val subject = "⚠️ Nhắc deadline: $leadText - ${event.title}"
        val message = buildEmailMessage(context, event, "Deadline sắp tới hạn!")
        
        sendEmailAsync(userEmail, subject, message, callback)
    }

    /**
     * Gửi email tổng hợp hàng ngày
     */
    fun sendDailySummaryEmail(context: Context, events: List<DeadlineEvent>, callback: (Boolean, String) -> Unit) {
        val userEmail = EventStore.getUserEmail(context)
        if (userEmail.isBlank()) {
            callback(false, "Email chưa được cấu hình")
            return
        }

        val now = System.currentTimeMillis()
        val summaryEvents = events
            .filterNot { EventStore.isDone(context, it.id) }
            .filter { it.startAtMillis > now }
            .sortedBy { it.startAtMillis }
            .take(10)

        if (summaryEvents.isEmpty()) {
            callback(true, "Không có deadline sắp tới")
            return
        }

        val subject = "📋 UTE Notice - Tổng hợp ${summaryEvents.size} deadline sắp tới"
        val message = buildDailySummaryMessage(context, summaryEvents)
        
        sendEmailAsync(userEmail, subject, message, callback)
    }

    /**
     * Gửi email test
     */
    fun sendTestEmail(context: Context, callback: (Boolean, String) -> Unit) {
        val userEmail = EventStore.getUserEmail(context)
        if (userEmail.isBlank()) {
            callback(false, "Email chưa được cấu hình")
            return
        }

        val subject = "✅ Test Email - UTE Notice"
        val safeUserEmail = escapeHtml(userEmail)
        val message = """
            <!DOCTYPE html>
            <html>
            <body style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px;">
                <div style="background: linear-gradient(135deg, #00529c 0%, #003670 100%); color: white; padding: 20px; border-radius: 8px; margin-bottom: 20px;">
                    <h1 style="margin: 0; font-size: 24px;">🎉 Email hoạt động tốt!</h1>
                </div>
                
                <div style="background: #f4f7fb; padding: 20px; border-radius: 8px;">
                    <p style="font-size: 16px; color: #1e293b;">Đây là email test từ <strong>UTE Notice</strong>.</p>
                    <p style="color: #64748b;">Nếu bạn nhận được email này, nghĩa là cấu hình email đã hoạt động đúng.</p>
                    <p style="color: #64748b;">Bạn sẽ nhận được thông báo qua email khi:</p>
                    <ul style="color: #64748b;">
                        <li>Có deadline mới từ Moodle</li>
                        <li>Deadline sắp tới hạn</li>
                        <li>Tổng hợp hàng ngày (nếu bật)</li>
                    </ul>
                </div>
                
                <div style="margin-top: 20px; padding: 15px; background: #e0f2fe; border-left: 4px solid #00529c; border-radius: 4px;">
                    <p style="margin: 0; color: #003670; font-size: 14px;">
                        <strong>Email của bạn:</strong> $safeUserEmail<br>
                        <strong>Thời gian test:</strong> ${formatTime(System.currentTimeMillis())}
                    </p>
                </div>
                
                <div style="margin-top: 30px; text-align: center; color: #94a3b8; font-size: 12px;">
                    <p>UTE Notice - HCM University of Technology and Education</p>
                </div>
            </body>
            </html>
        """.trimIndent()
        
        sendEmailAsync(userEmail, subject, message, callback)
    }

    /**
     * Xây dựng nội dung email cho deadline
     */
    private fun buildEmailMessage(context: Context, event: DeadlineEvent, headerText: String): String {
        val safeHeaderText = escapeHtml(headerText)
        val safeKind = escapeHtml(EventLabels.kind(event))
        val safeTitle = escapeHtml(event.title)
        val safeCourse = escapeHtml(EventLabels.course(event) ?: "Không rõ")
        val safeTimeLabel = escapeHtml(EventLabels.timeLabel(event))
        val safeDescription = escapeHtml(EventLabels.cleanDescription(event) ?: "Không có mô tả")
        val safeRemainText = escapeHtml(calculateRemainText(event.startAtMillis))
        val safeReminderOffsetsText = escapeHtml(EventStore.reminderOffsetsText(context))
        
        val accentColor = when {
            event.startAtMillis - System.currentTimeMillis() <= 24L * 60L * 60L * 1000L -> "#da2529"
            event.startAtMillis - System.currentTimeMillis() <= 3L * 24L * 60L * 60L * 1000L -> "#ca7400"
            else -> "#188058"
        }

        return """
            <!DOCTYPE html>
            <html>
            <body style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px;">
                <div style="background: linear-gradient(135deg, #00529c 0%, #003670 100%); color: white; padding: 20px; border-radius: 8px; margin-bottom: 20px;">
                    <h1 style="margin: 0; font-size: 24px;">$safeHeaderText</h1>
                </div>
                
                <div style="background: #ffffff; border: 2px solid #dee5ee; border-radius: 8px; padding: 20px; margin-bottom: 20px;">
                    <div style="background: ${accentColor}15; padding: 12px; border-radius: 6px; margin-bottom: 15px;">
                        <span style="background: $accentColor; color: white; padding: 6px 12px; border-radius: 6px; font-size: 12px; font-weight: bold;">
                            $safeKind
                        </span>
                    </div>
                    
                    <h2 style="color: #1e293b; margin: 15px 0 10px 0; font-size: 18px;">$safeTitle</h2>
                    
                    <div style="background: #f8fafc; padding: 15px; border-radius: 6px; margin: 15px 0;">
                        <p style="margin: 5px 0; color: #475569;"><strong>📚 Môn/Lớp:</strong> $safeCourse</p>
                        <p style="margin: 5px 0; color: #475569;"><strong>⏰ $safeTimeLabel:</strong> ${formatTime(event.startAtMillis)}</p>
                        <p style="margin: 5px 0; color: $accentColor; font-weight: bold;"><strong>⏳ Thời gian còn lại:</strong> $safeRemainText</p>
                    </div>
                    
                    <div style="background: #e0f2fe; padding: 15px; border-left: 4px solid #00529c; border-radius: 4px; margin: 15px 0;">
                        <p style="margin: 0; color: #003670; font-size: 14px;"><strong>📝 Mô tả:</strong></p>
                        <p style="margin: 10px 0 0 0; color: #003670;">$safeDescription</p>
                    </div>
                    
                    ${event.sourceUrl?.takeIf { it.isNotBlank() }?.let { url ->
                        val safeUrl = escapeHtml(url)
                        """
                    <div style="text-align: center; margin-top: 20px;">
                        <a href="$safeUrl" style="display: inline-block; background: #00529c; color: white; padding: 12px 30px; text-decoration: none; border-radius: 6px; font-weight: bold;">
                            Mở trên Moodle
                        </a>
                    </div>
                        """
                    } ?: ""}
                </div>
                
                <div style="margin-top: 20px; padding: 15px; background: #fef3c7; border-left: 4px solid #ca7400; border-radius: 4px;">
                    <p style="margin: 0; color: #92400e; font-size: 13px;">
                        <strong>💡 Mẹo:</strong> Mốc nhắc đang bật: $safeReminderOffsetsText trước hạn.
                    </p>
                </div>
                
                <div style="margin-top: 30px; text-align: center; color: #94a3b8; font-size: 12px;">
                    <p>Email tự động từ UTE Notice</p>
                    <p>HCM University of Technology and Education</p>
                </div>
            </body>
            </html>
        """.trimIndent()
    }

    /**
     * Xây dựng nội dung email tổng hợp
     */
    private fun buildDailySummaryMessage(context: Context, events: List<DeadlineEvent>): String {
        val eventsHtml = events.joinToString("") { event ->
            val safeKind = escapeHtml(EventLabels.kind(event))
            val safeTitle = escapeHtml(event.title)
            val safeCourse = escapeHtml(EventLabels.course(event) ?: "Không rõ môn")
            val safeRemainText = escapeHtml(calculateRemainText(event.startAtMillis))
            val accentColor = when {
                event.startAtMillis - System.currentTimeMillis() <= 24L * 60L * 60L * 1000L -> "#da2529"
                event.startAtMillis - System.currentTimeMillis() <= 3L * 24L * 60L * 60L * 1000L -> "#ca7400"
                else -> "#188058"
            }
            
            """
            <div style="background: white; border: 1px solid #dee5ee; border-radius: 8px; padding: 15px; margin-bottom: 12px;">
                <div style="margin-bottom: 10px;">
                    <span style="background: $accentColor; color: white; padding: 4px 10px; border-radius: 4px; font-size: 11px; font-weight: bold;">
                        $safeKind
                    </span>
                </div>
                <h3 style="margin: 10px 0; color: #1e293b; font-size: 16px;">$safeTitle</h3>
                <p style="margin: 5px 0; color: #64748b; font-size: 13px;">📚 $safeCourse</p>
                <p style="margin: 5px 0; color: #64748b; font-size: 13px;">⏰ ${formatTime(event.startAtMillis)}</p>
                <p style="margin: 5px 0; color: $accentColor; font-weight: bold; font-size: 13px;">⏳ $safeRemainText</p>
            </div>
            """.trimIndent()
        }

        return """
            <!DOCTYPE html>
            <html>
            <body style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px;">
                <div style="background: linear-gradient(135deg, #00529c 0%, #003670 100%); color: white; padding: 20px; border-radius: 8px; margin-bottom: 20px;">
                    <h1 style="margin: 0; font-size: 24px;">📋 Tổng hợp deadline sắp tới</h1>
                    <p style="margin: 10px 0 0 0; opacity: 0.9;">Bạn có ${events.size} deadline cần chú ý</p>
                </div>
                
                <div>
                    $eventsHtml
                </div>
                
                <div style="margin-top: 20px; padding: 15px; background: #e0f2fe; border-left: 4px solid #00529c; border-radius: 4px;">
                    <p style="margin: 0; color: #003670; font-size: 13px;">
                        <strong>💡 Lưu ý:</strong> Đây là email tổng hợp hàng ngày. Bạn vẫn sẽ nhận email nhắc riêng khi deadline gần tới hạn.
                    </p>
                </div>
                
                <div style="margin-top: 30px; text-align: center; color: #94a3b8; font-size: 12px;">
                    <p>Email tự động từ UTE Notice</p>
                    <p>HCM University of Technology and Education</p>
                </div>
            </body>
            </html>
        """.trimIndent()
    }

    /**
     * Gửi email bất đồng bộ
     */
    private fun sendEmailAsync(toEmail: String, subject: String, htmlMessage: String, callback: (Boolean, String) -> Unit) {
        thread {
            try {
                val properties = Properties().apply {
                    put("mail.smtp.auth", "true")
                    put("mail.smtp.starttls.enable", "true")
                    put("mail.smtp.host", SMTP_HOST)
                    put("mail.smtp.port", SMTP_PORT)
                    put("mail.smtp.ssl.protocols", "TLSv1.2")
                    put("mail.smtp.connectiontimeout", "10000")
                    put("mail.smtp.timeout", "10000")
                }

                val session = Session.getInstance(properties, object : Authenticator() {
                    override fun getPasswordAuthentication(): PasswordAuthentication {
                        return PasswordAuthentication(DEFAULT_SENDER_EMAIL, DEFAULT_APP_PASSWORD)
                    }
                })

                val message = MimeMessage(session).apply {
                    setFrom(InternetAddress(DEFAULT_SENDER_EMAIL, "UTE Notice"))
                    setRecipients(Message.RecipientType.TO, InternetAddress.parse(toEmail))
                    setSubject(subject, "UTF-8")
                    setContent(htmlMessage, "text/html; charset=UTF-8")
                }

                Transport.send(message)
                Log.i(TAG, "Email sent successfully to: $toEmail")
                callback(true, "Đã gửi email thành công")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send email", e)
                callback(false, "Lỗi gửi email: ${e.message}")
            }
        }
    }

    private fun escapeHtml(value: String): String {
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }

    private fun formatTime(millis: Long): String = timeFormatter.format(Instant.ofEpochMilli(millis))

    private fun calculateRemainText(millis: Long): String {
        val diff = millis - System.currentTimeMillis()
        if (diff <= 0) return "Đã tới hạn"
        val days = diff / (24L * 60L * 60L * 1000L)
        val hours = (diff / (60L * 60L * 1000L)) % 24
        val minutes = (diff / (60L * 1000L)) % 60
        return when {
            days > 0 -> "Còn $days ngày $hours giờ"
            hours > 0 -> "Còn $hours giờ $minutes phút"
            else -> "Còn $minutes phút"
        }
    }
}
