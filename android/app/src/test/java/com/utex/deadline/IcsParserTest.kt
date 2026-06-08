package com.utex.deadline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class IcsParserTest {
    private val zone = ZoneId.of("Asia/Ho_Chi_Minh")
    private val formatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")

    @Test
    fun parsesMoodleDeadlineKindsFromHcmuteCalendar() {
        val base = LocalDate.now(zone).plusYears(1)
        val ics = calendarOf(
            event("481069@utexlms.hcmute.edu.vn", "Online Test N0-02 bắt đầu", base.atTime(12, 45).format(formatter), "ANMA432880_25_2_02"),
            event("481072@utexlms.hcmute.edu.vn", "Online Test N0-02 kết thúc", base.atTime(14, 50).format(formatter), "ANMA432880_25_2_02"),
            event("475573@utexlms.hcmute.edu.vn", "Nộp bài Lab 03 tới hạn", base.plusDays(1).atTime(23, 59).format(formatter), "NSMS432280_25_2_02"),
            event("475813@utexlms.hcmute.edu.vn", "Nộp bài Lab 04 tới hạn", base.plusDays(7).atTime(23, 59).format(formatter), "NSMS432280_25_2_02"),
            event(
                uid = "475819@utexlms.hcmute.edu.vn",
                summary = "Nộp bài Tiểu luận tới hạn",
                dtStart = base.plusDays(14).atTime(23, 59).format(formatter),
                category = "NSMS432280_25_2_02",
                description = "Đề tài: Hệ thống giám sát an toàn mạng\\n"
            )
        )

        val events = IcsParser.parse(ics)

        assertEquals(5, events.size)
        assertEquals("Bắt đầu kiểm tra", EventLabels.kind(events[0]))
        assertEquals("Hết giờ kiểm tra", EventLabels.kind(events[1]))
        assertEquals("Bài nộp", EventLabels.kind(events[2]))
        assertEquals("NSMS432280_25_2_02", EventLabels.course(events[2]))
        assertTrue(EventLabels.cleanDescription(events[4]).orEmpty().contains("Hệ thống giám sát"))
    }

    @Test
    fun ignoresCalendarItemsThatAreNotDeadlines() {
        val base = LocalDate.now(zone).plusYears(1).plusDays(3)
        val ics = calendarOf(
            event("note-1@utexlms.hcmute.edu.vn", "Sinh hoạt lớp", base.atTime(8, 0).format(formatter), "NOTICE"),
            event("deadline-1@utexlms.hcmute.edu.vn", "Nộp bài Project tới hạn", base.atTime(23, 59).format(formatter), "PROJECT101")
        )

        val events = IcsParser.parse(ics)

        assertEquals(1, events.size)
        assertEquals("Nộp bài Project tới hạn", events.first().title)
    }

    @Test
    fun treatsAllDayDtendAsExclusiveEndDate() {
        val base = LocalDate.now(zone).plusYears(1).plusDays(5)
        val ics = calendarOf(
            listOf(
                "BEGIN:VEVENT",
                "UID:all-day-deadline@utexlms.hcmute.edu.vn",
                "SUMMARY:Nộp bài all-day tới hạn",
                "DTSTART;VALUE=DATE:${base.format(DateTimeFormatter.BASIC_ISO_DATE)}",
                "DTEND;VALUE=DATE:${base.plusDays(1).format(DateTimeFormatter.BASIC_ISO_DATE)}",
                "CATEGORIES:PROJECT101",
                "END:VEVENT"
            ).joinToString("\n")
        )

        val eventDate = Instant.ofEpochMilli(IcsParser.parse(ics).first().startAtMillis)
            .atZone(zone)
            .toLocalDate()

        assertEquals(base, eventDate)
    }

    @Test
    fun respectsTimezoneIdWhenDateTimeIsNotUtcSuffixed() {
        val base = LocalDate.now(zone).plusYears(1).plusDays(6)
        val ics = calendarOf(
            listOf(
                "BEGIN:VEVENT",
                "UID:tz-deadline@utexlms.hcmute.edu.vn",
                "SUMMARY:Nộp bài theo TZID tới hạn",
                "DTSTART;TZID=UTC:${base.atTime(10, 0).format(formatter)}",
                "CATEGORIES:PROJECT101",
                "END:VEVENT"
            ).joinToString("\n")
        )

        val localHour = Instant.ofEpochMilli(IcsParser.parse(ics).first().startAtMillis)
            .atZone(zone)
            .hour

        assertEquals(17, localHour)
    }

    @Test
    fun doesNotTreatVietnameseWordsContainingThiAsExam() {
        val base = LocalDate.now(zone).plusYears(1).plusDays(7)
        val ics = calendarOf(
            event(
                uid = "design-note@utexlms.hcmute.edu.vn",
                summary = "Thiết kế giao diện",
                dtStart = base.atTime(8, 0).format(formatter),
                category = "NOTICE"
            )
        )

        assertTrue(IcsParser.parse(ics).isEmpty())
    }

    @Test
    fun doesNotUseCourseCategoryAsDeadlineSignal() {
        val base = LocalDate.now(zone).plusYears(1).plusDays(8)
        val ics = calendarOf(
            event(
                uid = "project-course-note@utexlms.hcmute.edu.vn",
                summary = "Sinh hoạt lớp",
                dtStart = base.atTime(8, 0).format(formatter),
                category = "PROJECT101"
            )
        )

        assertTrue(IcsParser.parse(ics).isEmpty())
    }

    private fun calendarOf(vararg events: String): String {
        return listOf(
            "BEGIN:VCALENDAR",
            "METHOD:PUBLISH",
            "PRODID:-//Moodle Pty Ltd//NONSGML Moodle Version 2024042201.05//EN",
            "VERSION:2.0",
            events.joinToString("\n"),
            "END:VCALENDAR"
        ).joinToString("\n")
    }

    private fun event(
        uid: String,
        summary: String,
        dtStart: String,
        category: String,
        description: String = ""
    ): String {
        return listOf(
            "BEGIN:VEVENT",
            "UID:$uid",
            "SUMMARY:$summary",
            "DESCRIPTION:$description",
            "CLASS:PUBLIC",
            "DTSTART:$dtStart",
            "DTEND:$dtStart",
            "CATEGORIES:$category",
            "END:VEVENT"
        ).joinToString("\n")
    }
}
