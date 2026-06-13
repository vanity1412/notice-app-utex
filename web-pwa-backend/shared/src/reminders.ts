import { DEFAULT_REMINDER_MINUTES, PRESET_REMINDER_MINUTES } from "./constants";

export function sanitizeReminderOffsets(input: number[]): number[] {
  const selected = input
    .map((value) => Math.floor(Number(value)))
    .filter((value) => Number.isFinite(value) && value >= 0)
    .filter((value, index, array) => array.indexOf(value) === index)
    .sort((a, b) => b - a);

  return selected.length > 0 ? selected : [...DEFAULT_REMINDER_MINUTES];
}

export function reminderOptionLabel(minutes: number): string {
  if (minutes === 0) return "Đúng lúc deadline";
  if (minutes === 7 * 24 * 60) return "7 ngày";
  if (minutes === 3 * 24 * 60) return "3 ngày";
  if (minutes === 2 * 24 * 60) return "2 ngày";
  if (minutes === 24 * 60) return "1 ngày";
  if (minutes === 12 * 60) return "12 giờ";
  if (minutes === 6 * 60) return "6 giờ";
  if (minutes === 3 * 60) return "3 giờ";
  if (minutes === 60) return "1 giờ";
  if (minutes === 30) return "30 phút";
  if (minutes === 15) return "15 phút";

  const days = Math.floor(minutes / (24 * 60));
  const hours = Math.floor((minutes % (24 * 60)) / 60);
  const mins = minutes % 60;
  const parts: string[] = [];
  if (days > 0) parts.push(`${days} ngày`);
  if (hours > 0) parts.push(`${hours} giờ`);
  if (mins > 0) parts.push(`${mins} phút`);
  return parts.join(" ") || "1 phút";
}

export function reminderLeadLabel(minutes: number): string {
  return minutes === 0 ? "ĐÃ TỚI HẠN" : `${reminderOptionLabel(minutes)} trước hạn`;
}

export function allReminderOptions(custom: number[]): number[] {
  return [...PRESET_REMINDER_MINUTES, ...custom]
    .filter((value, index, array) => array.indexOf(value) === index)
    .sort((a, b) => b - a);
}
