package com.utex.deadline

import java.text.Normalizer
import java.util.Locale

enum class EventGroup {
    SUBMISSION,
    TEST,
    EXAM,
    DEADLINE,
    MOODLE
}

object EventLabels {
    private val viLocale = Locale.forLanguageTag("vi-VN")

    fun kind(event: DeadlineEvent): String {
        if (event.isPersonal) return "Cá nhân"
        val title = searchable(event.title)
        return when (group(event)) {
            EventGroup.SUBMISSION -> "Bài nộp"
            EventGroup.EXAM -> "Thi"
            EventGroup.TEST -> when {
                containsAny(title, "bat dau", "mo bai", "open") -> "Bắt đầu kiểm tra"
                containsAny(title, "ket thuc", "het han", "close", "closing") -> "Hết giờ kiểm tra"
                else -> "Kiểm tra"
            }
            EventGroup.DEADLINE -> "Deadline"
            EventGroup.MOODLE -> "Lịch Moodle"
        }
    }

    fun group(event: DeadlineEvent): EventGroup {
        if (event.isPersonal) return EventGroup.DEADLINE
        val text = searchable("${event.title} ${event.description.orEmpty()} ${event.rawType.orEmpty()}")
        return when {
            containsAny(text, "nop", "bai nop", "assignment", "lab", "tieu luan", "project") -> EventGroup.SUBMISSION
            containsAny(text, "lich thi", "thi ", " exam", "exam ", "midterm", "final") -> EventGroup.EXAM
            containsAny(text, "online test", "quiz", "test", "kiem tra") -> EventGroup.TEST
            containsAny(text, "deadline", "due", "toi han", "den han", "han chot", "ket thuc") -> EventGroup.DEADLINE
            else -> EventGroup.MOODLE
        }
    }

    fun broadGroup(event: DeadlineEvent): EventGroup {
        return when (group(event)) {
            EventGroup.SUBMISSION -> EventGroup.SUBMISSION
            EventGroup.TEST -> EventGroup.TEST
            EventGroup.EXAM -> EventGroup.EXAM
            else -> EventGroup.DEADLINE
        }
    }

    fun broadKind(event: DeadlineEvent): String {
        return when (broadGroup(event)) {
            EventGroup.SUBMISSION -> "Bài nộp"
            EventGroup.TEST -> "Kiểm tra"
            EventGroup.EXAM -> "Thi"
            else -> "Deadline"
        }
    }

    fun course(event: DeadlineEvent): String? {
        if (event.isPersonal) return "Cá nhân"
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
