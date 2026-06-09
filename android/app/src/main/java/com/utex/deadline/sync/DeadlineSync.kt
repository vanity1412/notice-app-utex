package com.utex.deadline

import android.content.Context
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.net.URL
import javax.net.ssl.SSLHandshakeException

object DeadlineSync {
    private val syncLock = Any()
    private const val NEW_EVENT_GRACE_MILLIS = 60L * 60L * 1000L

    fun sync(context: Context, notifyNew: Boolean): SyncResult {
        return synchronized(syncLock) {
            syncLocked(context.applicationContext, notifyNew)
        }
    }

    private fun syncLocked(context: Context, notifyNew: Boolean): SyncResult {
        val url = EventStore.getIcalUrl(context)
        val validation = MoodleUrlValidator.validate(url)
        if (!validation.ok) return SyncResult(false, validation.message)

        return try {
            val ics = fetchText(validation.normalizedUrl)
            if (!ics.contains("BEGIN:VCALENDAR", ignoreCase = true)) {
                return SyncResult(
                    false,
                    "Moodle chưa trả về file lịch. Có thể bạn dán nhầm link export.php, token hết hạn, hoặc cần mở Moodle trên trình duyệt để lấy lại Calendar URL."
                )
            }

            val events = IcsParser.parse(ics).map { it.copy(source = DeadlineSource.MOODLE) }
            val knownIds = EventStore.getKnownIds(context)
            val previousEvents = EventStore.loadMoodleEvents(context)
            val firstSync = knownIds.isEmpty()
            val now = System.currentTimeMillis()
            
            // Phát hiện deadline mới
            val newEvents = events.filter { it.startAtMillis >= now - NEW_EVENT_GRACE_MILLIS && it.id !in knownIds }
            
            // Phát hiện deadline bị thay đổi (thời gian hoặc nội dung)
            val changedEvents = if (!firstSync) {
                val previousMap = previousEvents.associateBy { it.id }
                events.filter { event ->
                    event.id in knownIds && previousMap[event.id]?.let { old ->
                        old.startAtMillis != event.startAtMillis ||
                        old.title != event.title ||
                        old.description != event.description ||
                        old.rawType != event.rawType ||
                        old.sourceUrl != event.sourceUrl
                    } == true
                }
            } else emptyList()

            EventStore.saveMoodleEvents(context, events)
            EventStore.saveKnownIds(context, knownIds + events.map { it.id })
            EventStore.setLastSync(context, System.currentTimeMillis())

            EventStore.prepareDailySummaryAfterSetup(context)
            ReminderScheduler.scheduleAll(context, EventStore.loadAllEvents(context).filterNot { EventStore.isDone(context, it.id) })
            ReminderScheduler.scheduleDailySummary(context)

            if (notifyNew) {
                if (firstSync && events.isNotEmpty()) {
                    val now = System.currentTimeMillis()
                    if (!NotificationHelper.notifySummary(context, events.size)) {
                        EventStore.upsertPendingDeadlineNotifications(
                            context,
                            listOf(
                                PendingDeadlineNotification(
                                    key = "summary-first-sync",
                                    type = "initial-summary",
                                    event = events.first(),
                                    timestamp = now
                                )
                            )
                        )
                    }
                    // Flush pending notifications nếu có
                    NotificationHelper.flushPendingDeadlineNotifications(context)
                } else {
                    val now = System.currentTimeMillis()
                    val pendingNotifications = mutableListOf<PendingDeadlineNotification>()
                    
                    newEvents.forEach { event ->
                        // Atomic check-and-notify để tránh race condition
                        synchronized(NotificationHelper) {
                            if (!NotificationHelper.notifyNewDeadline(context, event)) {
                                pendingNotifications += PendingDeadlineNotification(
                                    key = "new-${event.id}",
                                    type = "new",
                                    event = event,
                                    timestamp = now
                                )
                            }
                        }
                        
                        // Gửi email notification nếu được bật
                        if (EventStore.isEmailNotificationEnabled(context)) {
                            EmailNotificationService.sendNewDeadlineEmail(context, event) { _, _ -> }
                        }
                    }
                    
                    changedEvents.forEach { event ->
                        synchronized(NotificationHelper) {
                            if (!NotificationHelper.notifyChangedDeadline(context, event)) {
                                pendingNotifications += PendingDeadlineNotification(
                                    key = "changed-${event.id}",
                                    type = "changed",
                                    event = event,
                                    timestamp = now
                                )
                            }
                        }

                        // Gửi email khi giáo viên cập nhật deadline đã có trên Moodle
                        if (EventStore.isEmailNotificationEnabled(context)) {
                            EmailNotificationService.sendChangedDeadlineEmail(context, event) { _, _ -> }
                        }
                    }
                    
                    if (pendingNotifications.isNotEmpty()) {
                        EventStore.upsertPendingDeadlineNotifications(context, pendingNotifications)
                    }
                    
                    // Flush pending notifications nếu quyền đã được cấp
                    NotificationHelper.flushPendingDeadlineNotifications(context)
                }
            }

            val totalChanges = newEvents.size + changedEvents.size
            SyncResult(
                ok = true,
                message = when {
                    totalChanges > 0 && !firstSync -> {
                        val parts = mutableListOf<String>()
                        if (newEvents.isNotEmpty()) parts += "${newEvents.size} mới"
                        if (changedEvents.isNotEmpty()) parts += "${changedEvents.size} thay đổi"
                        "Có ${parts.joinToString(", ")} deadline."
                    }
                    events.isEmpty() -> "Đã kết nối Moodle nhưng chưa thấy deadline. Kiểm tra mục lịch đã chọn khi export."
                    else -> "Đã cập nhật ${events.size} deadline."
                },
                totalEvents = events.size,
                newEvents = if (firstSync) 0 else totalChanges
            )
        } catch (e: FriendlySyncException) {
            SyncResult(false, e.message ?: "Không đồng bộ được lịch Moodle.", retryable = e.retryable)
        } catch (_: UnknownHostException) {
            SyncResult(false, "Không có mạng hoặc không truy cập được utexlms.hcmute.edu.vn.", retryable = true)
        } catch (_: SocketTimeoutException) {
            SyncResult(false, "Moodle phản hồi quá lâu. Hãy thử lại khi mạng ổn hơn.", retryable = true)
        } catch (_: SSLHandshakeException) {
            SyncResult(false, "Lỗi chứng chỉ khi kết nối Moodle. Hãy kiểm tra ngày giờ điện thoại hoặc thử mạng khác.")
        } catch (e: IOException) {
            SyncResult(false, "Không đồng bộ được lịch Moodle: ${e.message ?: e.javaClass.simpleName}", retryable = true)
        } catch (e: Exception) {
            SyncResult(false, "Không đồng bộ được lịch Moodle: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    private fun fetchText(urlText: String): String {
        val conn = (URL(urlText).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 20_000
            requestMethod = "GET"
            setRequestProperty("User-Agent", "UTE-Deadline-Android/1.0")
        }
        val responseCode = conn.responseCode
        if (responseCode !in 200..299) {
            val message = when (responseCode) {
                401, 403 -> "Token Moodle hết hạn hoặc không có quyền truy cập. Hãy vào Moodle tạo lại Calendar URL."
                404 -> "Link Calendar URL không còn đúng. Hãy copy lại từ trang xuất lịch Moodle."
                in 500..599 -> "Moodle đang lỗi máy chủ ($responseCode). Hãy thử lại sau."
                else -> "Moodle trả lỗi HTTP $responseCode. Hãy kiểm tra lại Calendar URL."
            }
            throw FriendlySyncException(message, responseCode in 500..599)
        }
        conn.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
            return reader.readText()
        }
    }

    private class FriendlySyncException(message: String, val retryable: Boolean) : Exception(message)
}
