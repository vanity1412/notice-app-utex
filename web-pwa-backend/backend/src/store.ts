import fs from "node:fs/promises";
import path from "node:path";
import type { PushSubscription } from "web-push";
import {
  ALL_DAYS_MASK,
  DEFAULT_REMINDER_MINUTES,
  type CalendarConnection,
  type DeadlineEvent,
  type UserSettings
} from "@ute-notice/shared";

export interface DbUser {
  id: string;
  secretHash: string;
  createdAt: number;
  email: string;
  emailEnabled: boolean;
}

export interface DbPushSubscription {
  id: string;
  userId: string;
  endpoint: string;
  subscription: PushSubscription;
  createdAt: number;
  updatedAt: number;
  userAgent?: string;
}

export interface DbNotificationLog {
  id: string;
  userId: string;
  type: string;
  title: string;
  body: string;
  createdAt: number;
  error?: string;
}

export interface Database {
  users: Record<string, DbUser>;
  calendars: Record<string, CalendarConnection>;
  moodleEvents: Record<string, DeadlineEvent[]>;
  personalEvents: Record<string, DeadlineEvent[]>;
  knownIds: Record<string, string[]>;
  doneIds: Record<string, string[]>;
  settings: Record<string, UserSettings>;
  pushSubscriptions: Record<string, DbPushSubscription>;
  notificationDeliveries: Record<string, number>;
  notificationLog: DbNotificationLog[];
}

export class JsonStore {
  private data: Database = emptyDatabase();
  private queue = Promise.resolve();

  constructor(private readonly filePath: string) {}

  async init(): Promise<void> {
    await fs.mkdir(path.dirname(this.filePath), { recursive: true });
    try {
      const raw = await fs.readFile(this.filePath, "utf8");
      this.data = normalizeDatabase(JSON.parse(raw) as Partial<Database>);
    } catch (error) {
      const code = (error as NodeJS.ErrnoException).code;
      if (code !== "ENOENT") throw error;
      this.data = emptyDatabase();
      await this.save();
    }
  }

  snapshot(): Database {
    return structuredClone(this.data);
  }

  async update<T>(mutator: (data: Database) => T | Promise<T>): Promise<T> {
    const run = async () => {
      const result = await mutator(this.data);
      await this.save();
      return result;
    };
    const next = this.queue.then(run, run);
    this.queue = next.then(
      () => undefined,
      () => undefined
    );
    return next;
  }

  private async save(): Promise<void> {
    const tmpPath = `${this.filePath}.tmp`;
    await fs.writeFile(tmpPath, `${JSON.stringify(this.data, null, 2)}\n`, "utf8");
    await fs.rename(tmpPath, this.filePath);
  }
}

export function defaultSettings(): UserSettings {
  return {
    reminderOffsetsMinutes: [...DEFAULT_REMINDER_MINUTES],
    customReminderOffsetsMinutes: [],
    dailySummaryEnabled: false,
    dailySummaryTimes: [6 * 60],
    dailySummaryDaysMask: ALL_DAYS_MASK
  };
}

function emptyDatabase(): Database {
  return {
    users: {},
    calendars: {},
    moodleEvents: {},
    personalEvents: {},
    knownIds: {},
    doneIds: {},
    settings: {},
    pushSubscriptions: {},
    notificationDeliveries: {},
    notificationLog: []
  };
}

function normalizeDatabase(partial: Partial<Database>): Database {
  return {
    users: partial.users ?? {},
    calendars: partial.calendars ?? {},
    moodleEvents: partial.moodleEvents ?? {},
    personalEvents: partial.personalEvents ?? {},
    knownIds: partial.knownIds ?? {},
    doneIds: partial.doneIds ?? {},
    settings: partial.settings ?? {},
    pushSubscriptions: partial.pushSubscriptions ?? {},
    notificationDeliveries: partial.notificationDeliveries ?? {},
    notificationLog: partial.notificationLog ?? []
  };
}
