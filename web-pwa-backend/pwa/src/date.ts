import { LOCAL_TIME_ZONE } from "@ute-notice/shared";

const viLocale = "vi-VN";

export function formatFull(millis: number): string {
  return new Intl.DateTimeFormat(viLocale, {
    timeZone: LOCAL_TIME_ZONE,
    hour: "2-digit",
    minute: "2-digit",
    weekday: "long",
    day: "2-digit",
    month: "2-digit",
    year: "numeric"
  }).format(new Date(millis));
}

export function formatShort(millis: number): string {
  return new Intl.DateTimeFormat(viLocale, {
    timeZone: LOCAL_TIME_ZONE,
    hour: "2-digit",
    minute: "2-digit",
    day: "2-digit",
    month: "2-digit",
    year: "numeric"
  }).format(new Date(millis));
}

export function dayLabel(millis: number): string {
  return new Intl.DateTimeFormat(viLocale, {
    timeZone: LOCAL_TIME_ZONE,
    weekday: "long",
    day: "2-digit",
    month: "2-digit",
    year: "numeric"
  }).format(new Date(millis));
}

export function remainText(millis: number): string {
  const diff = millis - Date.now();
  if (diff <= 0) return "Đã tới hạn hoặc vừa qua hạn.";
  const days = Math.floor(diff / (24 * 60 * 60 * 1000));
  const hours = Math.floor(diff / (60 * 60 * 1000)) % 24;
  const minutes = Math.floor(diff / (60 * 1000)) % 60;
  if (days > 0) return `Còn ${days} ngày ${hours} giờ ${minutes} phút`;
  if (hours > 0) return `Còn ${hours} giờ ${minutes} phút`;
  return `Còn ${minutes} phút`;
}

export function toDateInputValue(millis: number): string {
  const date = new Date(millis);
  const offset = date.getTimezoneOffset() * 60_000;
  return new Date(date.getTime() - offset).toISOString().slice(0, 16);
}

export function fromDateInputValue(value: string): number {
  return new Date(value).getTime();
}
