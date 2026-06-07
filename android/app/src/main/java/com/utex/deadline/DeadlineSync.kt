package com.utex.deadline

import android.content.Context
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.net.URL
import javax.net.ssl.SSLHandshakeException

object DeadlineSync {
    fun sync(context: Context, notifyNew: Boolean): SyncResult {
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

            val events = IcsParser.parse(ics)
            val knownIds = EventStore.getKnownIds(context)
            val firstSync = knownIds.isEmpty()
            val now = System.currentTimeMillis()
            val newEvents = events.filter { it.startAtMillis >= now - 60_000L && it.id !in knownIds }

            EventStore.saveEvents(context, events)
            EventStore.saveKnownIds(context, knownIds + events.map { it.id })
            EventStore.setLastSync(context, System.currentTimeMillis())

            EventStore.enableDailySummaryAfterSetup(context)
            ReminderScheduler.scheduleAll(context, events)
            ReminderScheduler.scheduleDailySummary(context)

            if (notifyNew) {
                if (firstSync && events.isNotEmpty()) {
                    NotificationHelper.notifySummary(context, events.size)
                } else {
                    newEvents.forEach { NotificationHelper.notifyNewDeadline(context, it) }
                }
            }

            SyncResult(
                ok = true,
                message = when {
                    newEvents.isNotEmpty() && !firstSync -> "Có ${newEvents.size} deadline mới."
                    events.isEmpty() -> "Đã kết nối Moodle nhưng chưa thấy deadline. Kiểm tra mục lịch đã chọn khi export."
                    else -> "Đã cập nhật ${events.size} deadline."
                },
                totalEvents = events.size,
                newEvents = if (firstSync) 0 else newEvents.size
            )
        } catch (e: FriendlySyncException) {
            SyncResult(false, e.message ?: "Không đồng bộ được lịch Moodle.")
        } catch (_: UnknownHostException) {
            SyncResult(false, "Không có mạng hoặc không truy cập được utexlms.hcmute.edu.vn.")
        } catch (_: SocketTimeoutException) {
            SyncResult(false, "Moodle phản hồi quá lâu. Hãy thử lại khi mạng ổn hơn.")
        } catch (_: SSLHandshakeException) {
            SyncResult(false, "Lỗi chứng chỉ khi kết nối Moodle. Hãy kiểm tra ngày giờ điện thoại hoặc thử mạng khác.")
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
            throw FriendlySyncException(message)
        }
        conn.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
            return reader.readText()
        }
    }

    private class FriendlySyncException(message: String) : Exception(message)
}
