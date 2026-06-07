package com.utex.deadline

import java.security.MessageDigest
import java.text.Normalizer
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

object IcsParser {
    private val localZone: ZoneId = ZoneId.of("Asia/Ho_Chi_Minh")
    private val dateTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss", Locale.US)

    fun parse(text: String): List<DeadlineEvent> {
        val lines = unfoldLines(text)
        val events = mutableListOf<Map<String, String>>()
        var current: MutableMap<String, String>? = null

        for (line in lines) {
            when (line.trim()) {
                "BEGIN:VEVENT" -> current = mutableMapOf()
                "END:VEVENT" -> {
                    current?.let { events.add(it.toMap()) }
                    current = null
                }
                else -> {
                    val map = current ?: continue
                    val colon = line.indexOf(':')
                    if (colon <= 0) continue
                    val keyPart = line.substring(0, colon)
                    val value = decodeIcsText(line.substring(colon + 1))
                    val key = keyPart.substringBefore(';').uppercase(Locale.US)
                    val params = keyPart.substringAfter(';', "")
                    if (key in listOf("UID", "SUMMARY", "DESCRIPTION", "URL", "DTSTART", "DTEND", "DUE", "CATEGORIES")) {
                        map[key] = value
                        if (params.isNotBlank()) map["${key}_PARAMS"] = params
                    }
                }
            }
        }

        val now = System.currentTimeMillis()
        val oneDayAgo = now - 24L * 60L * 60L * 1000L

        return events.mapNotNull { raw ->
            val title = raw["SUMMARY"]?.trim().orEmpty()
            if (title.isBlank()) return@mapNotNull null
            if (!looksLikeDeadline(title, raw["DESCRIPTION"].orEmpty(), raw["CATEGORIES"].orEmpty())) return@mapNotNull null

            val time = parseIcsDate(raw["DUE"], raw["DUE_PARAMS"])
                ?: parseIcsDate(raw["DTEND"], raw["DTEND_PARAMS"])
                ?: parseIcsDate(raw["DTSTART"], raw["DTSTART_PARAMS"])
                ?: return@mapNotNull null

            if (time < oneDayAgo) return@mapNotNull null

            val stableId = raw["UID"]?.takeIf { it.isNotBlank() }
                ?: sha256("$title|$time|${raw["URL"].orEmpty()}")

            DeadlineEvent(
                id = stableId,
                title = title,
                startAtMillis = time,
                sourceUrl = raw["URL"],
                rawType = raw["CATEGORIES"],
                description = raw["DESCRIPTION"]?.trim()?.takeIf { it.isNotBlank() }
            )
        }.distinctBy { it.id }.sortedBy { it.startAtMillis }
    }

    private fun unfoldLines(text: String): List<String> {
        val result = mutableListOf<String>()
        text.replace("\r\n", "\n").replace('\r', '\n').split('\n').forEach { raw ->
            if ((raw.startsWith(" ") || raw.startsWith("\t")) && result.isNotEmpty()) {
                result[result.lastIndex] = result.last() + raw.drop(1)
            } else {
                result.add(raw)
            }
        }
        return result
    }

    private fun parseIcsDate(value: String?, params: String?): Long? {
        val v = value?.trim().orEmpty()
        if (v.isBlank()) return null
        return try {
            when {
                v.length == 8 || params?.contains("VALUE=DATE", ignoreCase = true) == true -> {
                    LocalDate.parse(v.take(8), DateTimeFormatter.BASIC_ISO_DATE)
                        .atTime(23, 59)
                        .atZone(localZone)
                        .toInstant()
                        .toEpochMilli()
                }
                v.endsWith("Z", ignoreCase = true) -> {
                    LocalDateTime.parse(v.removeSuffix("Z"), dateTimeFormatter)
                        .toInstant(ZoneOffset.UTC)
                        .toEpochMilli()
                }
                else -> {
                    LocalDateTime.parse(v.take(15), dateTimeFormatter)
                        .atZone(localZone)
                        .toInstant()
                        .toEpochMilli()
                }
            }
        } catch (_: Exception) {
            try {
                Instant.parse(v).toEpochMilli()
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun looksLikeDeadline(title: String, description: String, categories: String): Boolean {
        val text = searchable("$title $description $categories")
        val keywords = listOf(
            "toi han", "den han", "nop", "bai nop", "deadline", "due",
            "quiz", "test", "kiem tra", "ket thuc", "close", "closing",
            "thi", "assignment", "lab", "tieu luan", "project"
        )
        return keywords.any { text.contains(it) }
    }

    private fun searchable(text: String): String {
        return Normalizer.normalize(text.lowercase(Locale.forLanguageTag("vi-VN")), Normalizer.Form.NFD)
            .replace("\\p{M}+".toRegex(), "")
            .replace('đ', 'd')
    }

    private fun decodeIcsText(input: String): String {
        return input
            .replace("\\n", "\n")
            .replace("\\N", "\n")
            .replace("\\,", ",")
            .replace("\\;", ";")
            .replace("\\\\", "\\")
    }

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
