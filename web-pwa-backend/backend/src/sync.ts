import fs from "node:fs";
import https from "node:https";
import path from "node:path";
import { rootCertificates } from "node:tls";
import { fileURLToPath } from "node:url";
import { DateTime } from "luxon";
import { nanoid } from "nanoid";
import {
  ALL_DAYS_MASK,
  LOCAL_TIME_ZONE,
  cleanDescription,
  eventCourse,
  eventKind,
  maskMoodleUrl,
  parseIcs,
  reminderLeadLabel,
  sanitizeReminderOffsets,
  timeLabel,
  validateMoodleUrl,
  type DeadlineEvent,
  type PushPayload,
  type SyncResult
} from "@ute-notice/shared";
import { env } from "./env";
import type { EmailService } from "./email";
import type { PushService } from "./push";
import type { Database, JsonStore } from "./store";

const NEW_EVENT_GRACE_MILLIS = 60 * 60 * 1000;
const DAILY_SUMMARY_LOOKAHEAD_MILLIS = 3 * 24 * 60 * 60 * 1000;
const dirname = path.dirname(fileURLToPath(import.meta.url));
const moodleCaPath = path.resolve(dirname, "../certs/globalsign_rsa_ov_ssl_ca_2018.pem");
const moodleCa = fs.existsSync(moodleCaPath) ? fs.readFileSync(moodleCaPath, "utf8") : "";
const httpsAgent = new https.Agent({
  ca: moodleCa ? [...rootCertificates, moodleCa] : [...rootCertificates]
});

export class SyncService {
  constructor(
    private readonly store: JsonStore,
    private readonly push: PushService,
    private readonly email: EmailService
  ) {}

  async syncUser(userId: string, notifyNew: boolean): Promise<SyncResult> {
    const calendar = this.store.snapshot().calendars[userId];
    if (!calendar?.url) {
      return {
        ok: false,
        message: "Bạn chưa lưu Calendar URL Moodle.",
        totalEvents: 0,
        changedEvents: 0,
        retryable: false
      };
    }

    const validation = validateMoodleUrl(calendar.url);
    if (!validation.ok) {
      return {
        ok: false,
        message: validation.message,
        totalEvents: 0,
        changedEvents: 0,
        retryable: false
      };
    }

    try {
      const ics = await fetchText(validation.normalizedUrl);
      if (!ics.toUpperCase().includes("BEGIN:VCALENDAR")) {
        return await this.saveSyncFailure(userId, "Moodle chưa trả về file lịch. Có thể token hết hạn hoặc bạn đã dán nhầm link.", false);
      }

      const events = parseIcs(ics).map((event) => ({ ...event, source: "MOODLE" as const }));
      const previousData = this.store.snapshot();
      const knownIds = new Set(previousData.knownIds[userId] ?? []);
      const previousEvents = previousData.moodleEvents[userId] ?? [];
      const previousMap = new Map(previousEvents.map((event) => [event.id, event]));
      const firstSync = knownIds.size === 0;
      const now = Date.now();

      const newEvents = events.filter((event) => event.startAtMillis >= now - NEW_EVENT_GRACE_MILLIS && !knownIds.has(event.id));
      const changedEvents = firstSync
        ? []
        : events.filter((event) => {
            if (!knownIds.has(event.id)) return false;
            const old = previousMap.get(event.id);
            return Boolean(
              old &&
                (old.startAtMillis !== event.startAtMillis ||
                  old.title !== event.title ||
                  old.description !== event.description ||
                  old.rawType !== event.rawType ||
                  old.sourceUrl !== event.sourceUrl)
            );
          });

      const totalChanges = newEvents.length + changedEvents.length;
      const message =
        totalChanges > 0 && !firstSync
          ? `Có ${[
              newEvents.length ? `${newEvents.length} mới` : "",
              changedEvents.length ? `${changedEvents.length} thay đổi` : ""
            ]
              .filter(Boolean)
              .join(", ")} deadline.`
          : events.length === 0
            ? "Đã kết nối Moodle nhưng chưa thấy deadline. Kiểm tra mục lịch đã chọn khi export."
            : `Đã cập nhật ${events.length} deadline.`;

      await this.store.update((data) => {
        data.moodleEvents[userId] = events;
        data.knownIds[userId] = [...new Set([...(data.knownIds[userId] ?? []), ...events.map((event) => event.id)])];
        if (!data.settings[userId]) data.settings[userId] = defaultUserSettings();
        if (!data.settings[userId].dailySummaryEnabled) {
          data.settings[userId].dailySummaryEnabled = true;
        }
        data.calendars[userId] = {
          ...data.calendars[userId],
          url: validation.normalizedUrl,
          maskedUrl: maskMoodleUrl(validation.normalizedUrl),
          lastSyncAt: now,
          lastSyncMessage: message
        };
      });

      if (notifyNew) {
        if (firstSync && events.length > 0) {
          await this.notifyUser(userId, {
            title: "UTE Notice đã sẵn sàng",
            body: `Đã tìm thấy ${events.length} deadline sắp tới. Web sẽ nhắc khi có lịch mới hoặc gần tới hạn.`,
            tag: "initial-summary"
          }, "initial-summary");
        } else {
          for (const event of newEvents) {
            await this.notifyEvent(userId, "new", "Lịch mới", event);
          }
          for (const event of changedEvents) {
            await this.notifyEvent(userId, "changed", "Thay đổi", event);
          }
        }
      }

      await this.dispatchDueNotifications(userId);

      return {
        ok: true,
        message,
        totalEvents: events.length,
        changedEvents: firstSync ? 0 : totalChanges,
        retryable: false
      };
    } catch (error) {
      const message = friendlyFetchError(error);
      return await this.saveSyncFailure(userId, message, true);
    }
  }

  async syncAll(): Promise<void> {
    const data = this.store.snapshot();
    const userIds = Object.keys(data.calendars);
    for (const userId of userIds) {
      await this.syncUser(userId, true);
    }
    await this.dispatchAllDueNotifications();
  }

  async dispatchAllDueNotifications(): Promise<void> {
    const userIds = Object.keys(this.store.snapshot().users);
    for (const userId of userIds) {
      await this.dispatchDueNotifications(userId);
    }
  }

  async dispatchDueNotifications(userId: string): Promise<void> {
    await this.dispatchReminderNotifications(userId);
    await this.dispatchDailySummary(userId);
  }

  private async dispatchReminderNotifications(userId: string): Promise<void> {
    const data = this.store.snapshot();
    const settings = data.settings[userId] ?? defaultUserSettings();
    const doneIds = new Set(data.doneIds[userId] ?? []);
    const events = [...(data.moodleEvents[userId] ?? []), ...(data.personalEvents[userId] ?? [])]
      .filter((event) => !doneIds.has(event.id))
      .filter((event) => event.startAtMillis > Date.now() - 60 * 60 * 1000)
      .sort((a, b) => a.startAtMillis - b.startAtMillis);
    const now = Date.now();
    const graceMillis = env.reminderGraceMinutes * 60 * 1000;

    for (const event of events) {
      for (const minutes of sanitizeReminderOffsets(settings.reminderOffsetsMinutes)) {
        const triggerAt = event.startAtMillis - minutes * 60 * 1000;
        if (now < triggerAt || now - triggerAt > graceMillis) continue;

        const key = `reminder-${event.id}-${event.startAtMillis}-${minutes}`;
        if (!this.canDeliver(data, userId, key)) continue;

        const leadText = reminderLeadLabel(minutes);
        const sent = await this.notifyUser(userId, {
          title: minutes <= 30 ? `KHẨN CẤP: ${leadText}` : `Cảnh báo: ${leadText}`,
          body: eventMessage(event, settings.reminderOffsetsMinutes),
          url: event.sourceUrl ?? undefined,
          tag: key
        }, "reminder");
        if (sent) await this.markDelivered(userId, key);
      }
    }
  }

  private async dispatchDailySummary(userId: string): Promise<void> {
    const data = this.store.snapshot();
    const settings = data.settings[userId] ?? defaultUserSettings();
    if (!settings.dailySummaryEnabled || settings.dailySummaryTimes.length === 0) return;

    const now = DateTime.now().setZone(LOCAL_TIME_ZONE);
    const dayBit = 1 << (now.weekday - 1);
    if ((settings.dailySummaryDaysMask & dayBit) === 0) return;

    for (const minutesOfDay of settings.dailySummaryTimes) {
      const target = now.startOf("day").plus({ minutes: minutesOfDay });
      const diff = now.toMillis() - target.toMillis();
      if (diff < 0 || diff > env.reminderGraceMinutes * 60 * 1000) continue;

      const key = `daily-summary-${now.toFormat("yyyyLLdd")}-${minutesOfDay}`;
      if (!this.canDeliver(data, userId, key)) continue;

      const doneIds = new Set(data.doneIds[userId] ?? []);
      const upcoming = [...(data.moodleEvents[userId] ?? []), ...(data.personalEvents[userId] ?? [])]
        .filter((event) => !doneIds.has(event.id))
        .filter((event) => event.startAtMillis > now.toMillis() && event.startAtMillis <= now.toMillis() + DAILY_SUMMARY_LOOKAHEAD_MILLIS)
        .sort((a, b) => a.startAtMillis - b.startAtMillis);

      if (upcoming.length === 0) {
        await this.markDelivered(userId, key);
        continue;
      }

      const lines = upcoming
        .slice(0, 5)
        .map((event) => `- ${eventKind(event)}: ${event.title} - ${formatTime(event.startAtMillis)}`)
        .join("\n");
      const sent = await this.notifyUser(userId, {
        title: "Nhắc lịch sắp tới",
        body: `Có ${upcoming.length} mục sắp tới trong 3 ngày.\n${lines}`,
        tag: key
      }, "daily-summary");
      if (sent) await this.markDelivered(userId, key);
    }
  }

  private async notifyEvent(userId: string, type: "new" | "changed", prefix: string, event: DeadlineEvent): Promise<void> {
    const key = `${type}-${event.id}-${event.startAtMillis}`;
    const data = this.store.snapshot();
    if (!this.canDeliver(data, userId, key)) return;

    const sent = await this.notifyUser(userId, {
      title: `${prefix}: ${eventKind(event)}`,
      body: type === "changed" ? `Giáo viên đã cập nhật lịch này.\n${eventMessage(event, data.settings[userId]?.reminderOffsetsMinutes ?? [])}` : eventMessage(event, data.settings[userId]?.reminderOffsetsMinutes ?? []),
      url: event.sourceUrl ?? undefined,
      tag: key
    }, type);
    if (sent) await this.markDelivered(userId, key);
  }

  async notifyUser(userId: string, payload: PushPayload, type: string): Promise<boolean> {
    const pushCount = await this.push.sendToUser(userId, payload, type);
    const data = this.store.snapshot();
    const user = data.users[userId];
    if (user?.emailEnabled && user.email) {
      await this.email.send(user.email, payload.title, payload.body);
    }
    return pushCount > 0 || Boolean(user?.emailEnabled && user.email && this.email.isConfigured());
  }

  private canDeliver(data: Database, userId: string, key: string): boolean {
    return !data.notificationDeliveries[deliveryKey(userId, key)];
  }

  private async markDelivered(userId: string, key: string): Promise<void> {
    await this.store.update((data) => {
      data.notificationDeliveries[deliveryKey(userId, key)] = Date.now();
    });
  }

  private async saveSyncFailure(userId: string, message: string, retryable: boolean): Promise<SyncResult> {
    await this.store.update((data) => {
      if (data.calendars[userId]) {
        data.calendars[userId].lastSyncAt = Date.now();
        data.calendars[userId].lastSyncMessage = message;
      }
    });
    return {
      ok: false,
      message,
      totalEvents: 0,
      changedEvents: 0,
      retryable
    };
  }
}

export function defaultUserSettings() {
  return {
    reminderOffsetsMinutes: [24 * 60, 12 * 60, 60, 0],
    customReminderOffsetsMinutes: [],
    dailySummaryEnabled: false,
    dailySummaryTimes: [6 * 60],
    dailySummaryDaysMask: ALL_DAYS_MASK
  };
}

export function eventMessage(event: DeadlineEvent, reminderOffsets: number[]): string {
  const lines = [`${eventKind(event)}: ${event.title}`];
  const course = eventCourse(event);
  if (course) lines.push(`Môn/Lớp: ${course}`);
  const description = cleanDescription(event);
  if (description) lines.push(description);
  lines.push(`${timeLabel(event)}: ${formatTime(event.startAtMillis)}`);
  if (reminderOffsets.length > 0) {
    lines.push(`Mốc nhắc đang bật: ${sanitizeReminderOffsets(reminderOffsets).map(reminderLeadLabel).join(", ")}.`);
  }
  return lines.join("\n");
}

export function formatTime(millis: number): string {
  return DateTime.fromMillis(millis, { zone: LOCAL_TIME_ZONE }).toFormat("HH:mm dd/MM/yyyy");
}

async function fetchText(url: string): Promise<string> {
  return requestText(url, 0);
}

function friendlyFetchError(error: unknown): string {
  const message = (error as Error).message;
  const code = (error as NodeJS.ErrnoException).code;
  if (code === "UNABLE_TO_VERIFY_LEAF_SIGNATURE") {
    return "Backend chưa xác thực được chứng chỉ SSL của Moodle. Kiểm tra file CA trong backend/certs.";
  }
  if (code === "ETIMEDOUT" || code === "ECONNRESET" || code === "ENOTFOUND") {
    return "Không kết nối được Moodle từ backend. Kiểm tra mạng/server đang chạy backend.";
  }
  if (message) return message;
  return "Không đồng bộ được lịch Moodle.";
}

function requestText(urlText: string, redirects: number): Promise<string> {
  return new Promise((resolve, reject) => {
    const request = https.request(
      urlText,
      {
        method: "GET",
        timeout: 20_000,
        agent: httpsAgent,
        headers: {
          "User-Agent": "UTE-Notice-Web-Backend/1.0"
        }
      },
      (response) => {
        const statusCode = response.statusCode ?? 0;
        const location = response.headers.location;
        if (statusCode >= 300 && statusCode < 400 && location && redirects < 3) {
          response.resume();
          const nextUrl = new URL(location, urlText).toString();
          requestText(nextUrl, redirects + 1).then(resolve, reject);
          return;
        }

        const chunks: Buffer[] = [];
        response.on("data", (chunk: Buffer) => chunks.push(chunk));
        response.on("end", () => {
          const text = Buffer.concat(chunks).toString("utf8");
          if (statusCode < 200 || statusCode > 299) {
            reject(httpError(statusCode));
            return;
          }
          resolve(text);
        });
      }
    );

    request.on("timeout", () => {
      request.destroy(Object.assign(new Error("Moodle phản hồi quá lâu. Hãy thử lại khi mạng ổn hơn."), { code: "ETIMEDOUT" }));
    });
    request.on("error", reject);
    request.end();
  });
}

function httpError(statusCode: number): Error {
  if (statusCode === 401 || statusCode === 403) {
    return new Error("Token Moodle hết hạn hoặc không có quyền truy cập. Hãy vào Moodle tạo lại Calendar URL.");
  }
  if (statusCode === 404) {
    return new Error("Link Calendar URL không còn đúng. Hãy copy lại từ trang xuất lịch Moodle.");
  }
  if (statusCode >= 500) {
    return new Error(`Moodle đang lỗi máy chủ (${statusCode}). Hãy thử lại sau.`);
  }
  return new Error(`Moodle trả lỗi HTTP ${statusCode}. Hãy kiểm tra lại Calendar URL.`);
}

function deliveryKey(userId: string, key: string): string {
  return `${userId}:${key}`;
}

export function createPersonalEvent(title: string, startAtMillis: number, description?: string | null): DeadlineEvent {
  return {
    id: `personal-${nanoid(18)}`,
    title,
    startAtMillis,
    sourceUrl: null,
    rawType: "Cá nhân",
    description: description || null,
    source: "PERSONAL"
  };
}
