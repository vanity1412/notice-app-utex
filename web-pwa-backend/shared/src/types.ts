export type DeadlineSource = "MOODLE" | "PERSONAL";

export interface DeadlineEvent {
  id: string;
  title: string;
  startAtMillis: number;
  sourceUrl?: string | null;
  rawType?: string | null;
  description?: string | null;
  source: DeadlineSource;
}

export interface EventWithState extends DeadlineEvent {
  done: boolean;
}

export interface ValidationResult {
  ok: boolean;
  message: string;
  normalizedUrl: string;
}

export interface SyncResult {
  ok: boolean;
  message: string;
  totalEvents: number;
  changedEvents: number;
  retryable: boolean;
}

export interface UserSettings {
  reminderOffsetsMinutes: number[];
  customReminderOffsetsMinutes: number[];
  dailySummaryEnabled: boolean;
  dailySummaryTimes: number[];
  dailySummaryDaysMask: number;
}

export interface CalendarConnection {
  url: string;
  maskedUrl: string;
  lastSyncAt: number | null;
  lastSyncMessage: string | null;
}

export interface AppSnapshot {
  userId: string;
  calendar: Omit<CalendarConnection, "url"> | null;
  events: EventWithState[];
  settings: UserSettings;
  push: {
    enabled: boolean;
    publicKey: string | null;
  };
  email: {
    address: string;
    enabled: boolean;
  };
}

export interface PushPayload {
  title: string;
  body: string;
  url?: string;
  tag?: string;
  silent?: boolean;
}
