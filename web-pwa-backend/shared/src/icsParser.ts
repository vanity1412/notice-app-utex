import { DateTime } from "luxon";
import { LOCAL_TIME_ZONE } from "./constants";
import { searchable } from "./text";
import type { DeadlineEvent } from "./types";

const DATE_TIME_FORMAT = "yyyyLLdd'T'HHmmss";
const DATE_FORMAT = "yyyyLLdd";

export function parseIcs(text: string, nowMillis = Date.now()): DeadlineEvent[] {
  const lines = unfoldLines(text);
  const rawEvents: Array<Record<string, string>> = [];
  let current: Record<string, string> | null = null;

  for (const line of lines) {
    const trimmed = line.trim();
    if (trimmed === "BEGIN:VEVENT") {
      current = {};
      continue;
    }
    if (trimmed === "END:VEVENT") {
      if (current) rawEvents.push({ ...current });
      current = null;
      continue;
    }
    if (!current) continue;

    const colon = line.indexOf(":");
    if (colon <= 0) continue;

    const keyPart = line.slice(0, colon);
    const value = decodeIcsText(line.slice(colon + 1));
    const key = keyPart.split(";")[0].toUpperCase();
    const params = keyPart.includes(";") ? keyPart.slice(keyPart.indexOf(";") + 1) : "";

    if (["UID", "SUMMARY", "DESCRIPTION", "URL", "DTSTART", "DTEND", "DUE", "CATEGORIES"].includes(key)) {
      current[key] = value;
      if (params) current[`${key}_PARAMS`] = params;
    }
  }

  const oneDayAgo = nowMillis - 24 * 60 * 60 * 1000;
  const seen = new Set<string>();
  const events = rawEvents
    .map((raw): DeadlineEvent | null => {
      const title = raw.SUMMARY?.trim() ?? "";
      if (!title) return null;
      if (!looksLikeDeadline(title, raw.DESCRIPTION ?? "")) return null;

      const startTime = parseIcsDate(raw.DTSTART, raw.DTSTART_PARAMS);
      const time =
        parseIcsDate(raw.DUE, raw.DUE_PARAMS) ??
        parseIcsDate(raw.DTEND, raw.DTEND_PARAMS, raw.DTSTART) ??
        startTime;

      if (!time || time < oneDayAgo) return null;

      const id = raw.UID?.trim() || stableHash(`${title}|${time}|${raw.URL ?? ""}`);
      return {
        id,
        title,
        startAtMillis: time,
        sourceUrl: raw.URL || null,
        rawType: raw.CATEGORIES || null,
        description: raw.DESCRIPTION?.trim() || null,
        source: "MOODLE"
      };
    })
    .filter((event): event is DeadlineEvent => Boolean(event))
    .filter((event) => {
      if (seen.has(event.id)) return false;
      seen.add(event.id);
      return true;
    })
    .sort((a, b) => a.startAtMillis - b.startAtMillis);

  return events;
}

function unfoldLines(text: string): string[] {
  const result: string[] = [];
  for (const raw of text.replace(/\r\n/g, "\n").replace(/\r/g, "\n").split("\n")) {
    if ((raw.startsWith(" ") || raw.startsWith("\t")) && result.length > 0) {
      result[result.length - 1] += raw.slice(1);
    } else {
      result.push(raw);
    }
  }
  return result;
}

function parseIcsDate(value?: string, params?: string, allDayEndStart?: string): number | null {
  const v = value?.trim() ?? "";
  if (!v) return null;

  try {
    if (isDateOnly(v, params)) {
      const parsed = DateTime.fromFormat(v.slice(0, 8), DATE_FORMAT, { zone: LOCAL_TIME_ZONE });
      if (!parsed.isValid) return null;

      const startDate = allDayEndStart
        ? DateTime.fromFormat(allDayEndStart.trim().slice(0, 8), DATE_FORMAT, { zone: LOCAL_TIME_ZONE })
        : null;
      const eventDate = startDate?.isValid && parsed > startDate ? parsed.minus({ days: 1 }) : parsed;

      return eventDate.set({ hour: 23, minute: 59, second: 0, millisecond: 0 }).toMillis();
    }

    if (v.toUpperCase().endsWith("Z")) {
      const parsed = DateTime.fromFormat(v.slice(0, -1), DATE_TIME_FORMAT, { zone: "utc" });
      return parsed.isValid ? parsed.toMillis() : null;
    }

    const parsed = DateTime.fromFormat(v.slice(0, 15), DATE_TIME_FORMAT, {
      zone: zoneFromParams(params) ?? LOCAL_TIME_ZONE
    });
    return parsed.isValid ? parsed.toMillis() : null;
  } catch {
    const fallback = Date.parse(v);
    return Number.isFinite(fallback) ? fallback : null;
  }
}

function isDateOnly(value: string, params?: string): boolean {
  return value.length === 8 || Boolean(params?.toUpperCase().includes("VALUE=DATE"));
}

function zoneFromParams(params?: string): string | null {
  if (!params) return null;
  const tzParam = params
    .split(";")
    .find((part) => part.toUpperCase().startsWith("TZID="))
    ?.split("=")[1]
    ?.trim()
    ?.replace(/^"|"$/g, "");
  return tzParam || null;
}

function looksLikeDeadline(title: string, description: string): boolean {
  const text = searchable(`${title} ${description}`);
  const paddedText = ` ${text} `;
  const keywords = [
    "toi han",
    "den han",
    "nop",
    "bai nop",
    "deadline",
    "due",
    "quiz",
    "test",
    "kiem tra",
    "ket thuc",
    "close",
    "closing",
    "lich thi",
    "exam",
    "midterm",
    "final",
    "assignment",
    "lab",
    "tieu luan",
    "project"
  ];
  return keywords.some((keyword) => text.includes(keyword)) || paddedText.includes(" thi ");
}

function decodeIcsText(input: string): string {
  return input
    .replace(/\\n/g, "\n")
    .replace(/\\N/g, "\n")
    .replace(/\\,/g, ",")
    .replace(/\\;/g, ";")
    .replace(/\\\\/g, "\\");
}

function stableHash(input: string): string {
  let h1 = 0xdeadbeef;
  let h2 = 0x41c6ce57;
  for (let i = 0; i < input.length; i += 1) {
    const ch = input.charCodeAt(i);
    h1 = Math.imul(h1 ^ ch, 2654435761);
    h2 = Math.imul(h2 ^ ch, 1597334677);
  }
  h1 = Math.imul(h1 ^ (h1 >>> 16), 2246822507) ^ Math.imul(h2 ^ (h2 >>> 13), 3266489909);
  h2 = Math.imul(h2 ^ (h2 >>> 16), 2246822507) ^ Math.imul(h1 ^ (h1 >>> 13), 3266489909);
  return `${(h2 >>> 0).toString(16).padStart(8, "0")}${(h1 >>> 0).toString(16).padStart(8, "0")}`;
}
