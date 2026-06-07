package com.utex.deadline

import android.content.Context
import java.net.HttpURLConnection
import java.net.URL

object DeadlineSync {
    fun sync(context: Context, notifyNew: Boolean): SyncResult {
        val url = EventStore.getIcalUrl(context)
        if (url.isBlank()) {
            return SyncResult(false, "Bạn chưa dán iCal URL.")
        }

        return try {
            val ics = fetchText(url)
            val events = IcsParser.parse(ics)
            val knownIds = EventStore.getKnownIds(context)
            val firstSync = knownIds.isEmpty()
            val now = System.currentTimeMillis()
            val newEvents = events.filter { it.startAtMillis >= now - 60_000L && it.id !in knownIds }

            EventStore.saveEvents(context, events)
            EventStore.saveKnownIds(context, knownIds + events.map { it.id })
            EventStore.setLastSync(context, System.currentTimeMillis())

            ReminderScheduler.scheduleAll(context, events)

            if (notifyNew) {
                if (firstSync && events.isNotEmpty()) {
                    NotificationHelper.notifySummary(context, events.size)
                } else {
                    newEvents.forEach { NotificationHelper.notifyNewDeadline(context, it) }
                }
            }

            SyncResult(
                ok = true,
                message = if (newEvents.isNotEmpty() && !firstSync) "Có ${newEvents.size} deadline mới." else "Đã cập nhật ${events.size} deadline.",
                totalEvents = events.size,
                newEvents = if (firstSync) 0 else newEvents.size
            )
        } catch (e: Exception) {
            SyncResult(false, "Sync lỗi: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    private fun fetchText(urlText: String): String {
        val conn = (URL(urlText).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 20_000
            requestMethod = "GET"
            setRequestProperty("User-Agent", "UTE-Deadline-Android/1.0")
        }
        conn.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
            return reader.readText()
        }
    }
}
