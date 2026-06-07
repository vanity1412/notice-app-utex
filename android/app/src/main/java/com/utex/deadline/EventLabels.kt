package com.utex.deadline

import java.text.Normalizer
import java.util.Locale

object EventLabels {
    private val viLocale = Locale.forLanguageTag("vi-VN")

    fun kind(event: DeadlineEvent): String {
        val text = searchable("${event.title} ${event.description.orEmpty()}")
        val title = searchable(event.title)

        if (containsAny(text, "nop", "bai nop", "assignment", "lab", "tieu luan", "project")) {
            return "Bài nộp"
        }

        if (containsAny(text, "lich thi", "thi ", " exam", "exam ", "midterm", "final")) {
            return "Thi"
        }

        if (containsAny(text, "online test", "quiz", "test", "kiem tra")) {
            return when {
                containsAny(title, "bat dau", "mo bai", "open") -> "Bắt đầu kiểm tra"
                containsAny(title, "ket thuc", "het han", "close", "closing") -> "Hết giờ kiểm tra"
                else -> "Kiểm tra"
            }
        }

        if (containsAny(text, "deadline", "due", "toi han", "den han", "han chot", "ket thuc")) {
            return "Deadline"
        }

        return "Lịch Moodle"
    }

    fun broadKind(event: DeadlineEvent): String {
        val kind = kind(event)
        return when {
            kind.contains("kiểm tra", ignoreCase = true) -> "Kiểm tra"
            else -> kind
        }
    }

    fun course(event: DeadlineEvent): String? {
        return event.rawType
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.replace("\\,", ",")
    }

    fun cleanDescription(event: DeadlineEvent): String? {
        return event.description
            ?.lineSequence()
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?.joinToString(" ")
            ?.takeIf { it.isNotBlank() }
    }

    fun timeLabel(event: DeadlineEvent): String {
        return when (kind(event)) {
            "Bắt đầu kiểm tra" -> "Bắt đầu"
            "Hết giờ kiểm tra" -> "Kết thúc"
            else -> "Hạn"
        }
    }

    private fun containsAny(text: String, vararg keywords: String): Boolean {
        return keywords.any { text.contains(it.lowercase(viLocale)) }
    }

    private fun searchable(text: String): String {
        return Normalizer.normalize(text.lowercase(viLocale), Normalizer.Form.NFD)
            .replace("\\p{M}+".toRegex(), "")
            .replace('đ', 'd')
    }
}
