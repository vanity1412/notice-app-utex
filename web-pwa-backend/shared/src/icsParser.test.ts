import { DateTime } from "luxon";
import { describe, expect, it } from "vitest";
import { LOCAL_TIME_ZONE } from "./constants";
import { cleanDescription, eventCourse, eventKind } from "./eventLabels";
import { parseIcs } from "./icsParser";

const formatter = "yyyyLLdd'T'HHmmss";

describe("parseIcs", () => {
  it("parses Moodle deadline kinds from HCMUTE calendar", () => {
    const base = DateTime.now().setZone(LOCAL_TIME_ZONE).plus({ years: 1 }).startOf("day");
    const ics = calendarOf(
      event("481069@utexlms.hcmute.edu.vn", "Online Test N0-02 bắt đầu", base.set({ hour: 12, minute: 45 }).toFormat(formatter), "ANMA432880_25_2_02"),
      event("481072@utexlms.hcmute.edu.vn", "Online Test N0-02 kết thúc", base.set({ hour: 14, minute: 50 }).toFormat(formatter), "ANMA432880_25_2_02"),
      event("475573@utexlms.hcmute.edu.vn", "Nộp bài Lab 03 tới hạn", base.plus({ days: 1 }).set({ hour: 23, minute: 59 }).toFormat(formatter), "NSMS432280_25_2_02"),
      event("475813@utexlms.hcmute.edu.vn", "Nộp bài Lab 04 tới hạn", base.plus({ days: 7 }).set({ hour: 23, minute: 59 }).toFormat(formatter), "NSMS432280_25_2_02"),
      event(
        "475819@utexlms.hcmute.edu.vn",
        "Nộp bài Tiểu luận tới hạn",
        base.plus({ days: 14 }).set({ hour: 23, minute: 59 }).toFormat(formatter),
        "NSMS432280_25_2_02",
        "Đề tài: Hệ thống giám sát an toàn mạng\\n"
      )
    );

    const events = parseIcs(ics);

    expect(events).toHaveLength(5);
    expect(eventKind(events[0])).toBe("Bắt đầu kiểm tra");
    expect(eventKind(events[1])).toBe("Hết giờ kiểm tra");
    expect(eventKind(events[2])).toBe("Bài nộp");
    expect(eventCourse(events[2])).toBe("NSMS432280_25_2_02");
    expect(cleanDescription(events[4]) ?? "").toContain("Hệ thống giám sát");
  });

  it("ignores calendar items that are not deadlines", () => {
    const base = DateTime.now().setZone(LOCAL_TIME_ZONE).plus({ years: 1, days: 3 }).startOf("day");
    const ics = calendarOf(
      event("note-1@utexlms.hcmute.edu.vn", "Sinh hoạt lớp", base.set({ hour: 8 }).toFormat(formatter), "NOTICE"),
      event("deadline-1@utexlms.hcmute.edu.vn", "Nộp bài Project tới hạn", base.set({ hour: 23, minute: 59 }).toFormat(formatter), "PROJECT101")
    );

    const events = parseIcs(ics);

    expect(events).toHaveLength(1);
    expect(events[0].title).toBe("Nộp bài Project tới hạn");
  });

  it("treats all-day DTEND as exclusive end date", () => {
    const base = DateTime.now().setZone(LOCAL_TIME_ZONE).plus({ years: 1, days: 5 }).startOf("day");
    const ics = calendarOf(
      [
        "BEGIN:VEVENT",
        "UID:all-day-deadline@utexlms.hcmute.edu.vn",
        "SUMMARY:Nộp bài all-day tới hạn",
        `DTSTART;VALUE=DATE:${base.toFormat("yyyyLLdd")}`,
        `DTEND;VALUE=DATE:${base.plus({ days: 1 }).toFormat("yyyyLLdd")}`,
        "CATEGORIES:PROJECT101",
        "END:VEVENT"
      ].join("\n")
    );

    const eventDate = DateTime.fromMillis(parseIcs(ics)[0].startAtMillis, { zone: LOCAL_TIME_ZONE });

    expect(eventDate.toISODate()).toBe(base.toISODate());
  });

  it("respects TZID when date-time is not UTC suffixed", () => {
    const base = DateTime.now().setZone(LOCAL_TIME_ZONE).plus({ years: 1, days: 6 }).startOf("day");
    const ics = calendarOf(
      [
        "BEGIN:VEVENT",
        "UID:tz-deadline@utexlms.hcmute.edu.vn",
        "SUMMARY:Nộp bài theo TZID tới hạn",
        `DTSTART;TZID=UTC:${base.set({ hour: 10 }).toFormat(formatter)}`,
        "CATEGORIES:PROJECT101",
        "END:VEVENT"
      ].join("\n")
    );

    const localHour = DateTime.fromMillis(parseIcs(ics)[0].startAtMillis, { zone: LOCAL_TIME_ZONE }).hour;

    expect(localHour).toBe(17);
  });

  it("does not treat Vietnamese words containing thi as exam", () => {
    const base = DateTime.now().setZone(LOCAL_TIME_ZONE).plus({ years: 1, days: 7 }).startOf("day");
    const ics = calendarOf(
      event("design-note@utexlms.hcmute.edu.vn", "Thiết kế giao diện", base.set({ hour: 8 }).toFormat(formatter), "NOTICE")
    );

    expect(parseIcs(ics)).toHaveLength(0);
  });
});

function calendarOf(...events: string[]): string {
  return [
    "BEGIN:VCALENDAR",
    "METHOD:PUBLISH",
    "PRODID:-//Moodle Pty Ltd//NONSGML Moodle Version 2024042201.05//EN",
    "VERSION:2.0",
    events.join("\n"),
    "END:VCALENDAR"
  ].join("\n");
}

function event(uid: string, summary: string, dtStart: string, category: string, description = ""): string {
  return [
    "BEGIN:VEVENT",
    `UID:${uid}`,
    `SUMMARY:${summary}`,
    `DESCRIPTION:${description}`,
    "CLASS:PUBLIC",
    `DTSTART:${dtStart}`,
    `DTEND:${dtStart}`,
    `CATEGORIES:${category}`,
    "END:VEVENT"
  ].join("\n");
}
