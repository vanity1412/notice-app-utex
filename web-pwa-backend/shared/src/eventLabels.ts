import type { DeadlineEvent } from "./types";
import { compactWhitespace, containsAny, searchable } from "./text";

export type EventGroup = "SUBMISSION" | "TEST" | "EXAM" | "DEADLINE" | "MOODLE";

export function eventGroup(event: DeadlineEvent): EventGroup {
  if (event.source === "PERSONAL") return "DEADLINE";
  const text = searchable(`${event.title} ${event.description ?? ""} ${event.rawType ?? ""}`);

  if (containsAny(text, ["nop", "bai nop", "assignment", "lab", "tieu luan", "project"])) {
    return "SUBMISSION";
  }
  if (containsAny(text, ["lich thi", "thi ", " exam", "exam ", "midterm", "final"])) {
    return "EXAM";
  }
  if (containsAny(text, ["online test", "quiz", "test", "kiem tra"])) {
    return "TEST";
  }
  if (containsAny(text, ["deadline", "due", "toi han", "den han", "han chot", "ket thuc"])) {
    return "DEADLINE";
  }
  return "MOODLE";
}

export function broadGroup(event: DeadlineEvent): EventGroup {
  const group = eventGroup(event);
  return group === "SUBMISSION" || group === "TEST" || group === "EXAM" ? group : "DEADLINE";
}

export function eventKind(event: DeadlineEvent): string {
  if (event.source === "PERSONAL") return "Cá nhân";
  const title = searchable(event.title);
  const group = eventGroup(event);

  if (group === "SUBMISSION") return "Bài nộp";
  if (group === "EXAM") return "Thi";
  if (group === "TEST") {
    if (containsAny(title, ["bat dau", "mo bai", "open"])) return "Bắt đầu kiểm tra";
    if (containsAny(title, ["ket thuc", "het han", "close", "closing"])) return "Hết giờ kiểm tra";
    return "Kiểm tra";
  }
  if (group === "DEADLINE") return "Deadline";
  return "Lịch Moodle";
}

export function eventCourse(event: DeadlineEvent): string | null {
  if (event.source === "PERSONAL") return "Cá nhân";
  const course = event.rawType?.trim().replace(/\\,/g, ",");
  return course || null;
}

export function cleanDescription(event: DeadlineEvent): string | null {
  const cleaned = compactWhitespace(
    (event.description ?? "")
      .split(/\r?\n/)
      .map((line) => line.trim())
      .filter(Boolean)
      .join(" ")
  );
  return cleaned || null;
}

export function timeLabel(event: DeadlineEvent): string {
  const kind = eventKind(event);
  if (kind === "Bắt đầu kiểm tra") return "Bắt đầu";
  if (kind === "Hết giờ kiểm tra") return "Kết thúc";
  return "Hạn";
}
