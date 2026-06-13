import {
  maskMoodleUrl,
  type AppSnapshot,
  type DeadlineEvent,
  type EventWithState
} from "@ute-notice/shared";
import type { Database } from "./store";
import { defaultSettings } from "./store";

export function buildSnapshot(data: Database, userId: string, publicKey: string | null): AppSnapshot {
  const doneIds = new Set(data.doneIds[userId] ?? []);
  const moodle = data.moodleEvents[userId] ?? [];
  const personal = data.personalEvents[userId] ?? [];
  const events = [...moodle, ...personal]
    .filter(uniqueById())
    .sort((a, b) => a.startAtMillis - b.startAtMillis)
    .map<EventWithState>((event) => ({ ...event, done: doneIds.has(event.id) }));
  const calendar = data.calendars[userId];
  const pushEnabled = Object.values(data.pushSubscriptions).some((subscription) => subscription.userId === userId);

  return {
    userId,
    calendar: calendar
      ? {
          maskedUrl: calendar.maskedUrl || maskMoodleUrl(calendar.url),
          lastSyncAt: calendar.lastSyncAt,
          lastSyncMessage: calendar.lastSyncMessage
        }
      : null,
    events,
    settings: data.settings[userId] ?? defaultSettings(),
    push: {
      enabled: pushEnabled,
      publicKey
    },
    email: {
      address: data.users[userId]?.email ?? "",
      enabled: data.users[userId]?.emailEnabled ?? false
    }
  };
}

function uniqueById() {
  const seen = new Set<string>();
  return (event: DeadlineEvent) => {
    if (seen.has(event.id)) return false;
    seen.add(event.id);
    return true;
  };
}
