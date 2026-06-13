import type { Request, Response } from "express";
import { Router } from "express";
import type { PushSubscription } from "web-push";
import { z } from "zod";
import {
  ALL_DAYS_MASK,
  MAX_CUSTOM_REMINDER_DAYS,
  MAX_DAILY_SUMMARY_TIMES,
  maskMoodleUrl,
  sanitizeReminderOffsets,
  validateMoodleUrl
} from "@ute-notice/shared";
import { createSession, requireAuth, type AuthedRequest } from "./auth";
import { buildSnapshot } from "./snapshot";
import { createPersonalEvent, SyncService } from "./sync";
import type { EmailService } from "./email";
import type { PushService } from "./push";
import { defaultSettings, type JsonStore } from "./store";

const urlSchema = z.object({
  url: z.string().min(1)
});

const doneSchema = z.object({
  done: z.boolean()
});

const personalEventSchema = z.object({
  title: z.string().trim().min(1).max(180),
  startAtMillis: z.number().int().positive(),
  description: z.string().trim().max(1000).optional().nullable()
});

const settingsSchema = z.object({
  reminderOffsetsMinutes: z.array(z.number().int().min(0)).min(1),
  customReminderOffsetsMinutes: z.array(z.number().int().min(1)).default([]),
  dailySummaryEnabled: z.boolean(),
  dailySummaryTimes: z.array(z.number().int().min(0).max(24 * 60 - 1)).max(MAX_DAILY_SUMMARY_TIMES),
  dailySummaryDaysMask: z.number().int().min(0).max(ALL_DAYS_MASK)
});

const pushSubscriptionSchema = z.object({
  endpoint: z.string().url(),
  keys: z.object({
    p256dh: z.string().min(1),
    auth: z.string().min(1)
  }),
  expirationTime: z.number().nullable().optional()
});

const emailSchema = z.object({
  email: z.string().trim().email().or(z.literal("")),
  enabled: z.boolean()
});

export function createRouter(
  store: JsonStore,
  push: PushService,
  sync: SyncService,
  email: EmailService
): Router {
  const router = Router();

  router.get("/health", (_req, res) => {
    res.json({ ok: true, time: Date.now() });
  });

  router.get("/push/public-key", (_req, res) => {
    res.json({ publicKey: push.getPublicKey() });
  });

  router.post("/session", async (_req, res, next) => {
    try {
      const session = await createSession(store);
      res.json({ ok: true, ...session });
    } catch (error) {
      next(error);
    }
  });

  router.use(requireAuth(store));
  router.use(ensureUserDefaults(store));

  router.get("/snapshot", (req, res) => {
    const userId = (req as AuthedRequest).userId;
    res.json({ ok: true, snapshot: buildSnapshot(store.snapshot(), userId, push.getPublicKey()) });
  });

  router.post("/calendar", async (req, res, next) => {
    try {
      const userId = (req as AuthedRequest).userId;
      const body = urlSchema.parse(req.body);
      const validation = validateMoodleUrl(body.url);
      if (!validation.ok) {
        res.status(400).json({ ok: false, message: validation.message });
        return;
      }

      await store.update((data) => {
        const previousUrl = data.calendars[userId]?.url;
        data.calendars[userId] = {
          url: validation.normalizedUrl,
          maskedUrl: maskMoodleUrl(validation.normalizedUrl),
          lastSyncAt: data.calendars[userId]?.lastSyncAt ?? null,
          lastSyncMessage: data.calendars[userId]?.lastSyncMessage ?? null
        };
        if (previousUrl !== validation.normalizedUrl) {
          data.moodleEvents[userId] = [];
          data.knownIds[userId] = [];
        }
      });

      const result = await sync.syncUser(userId, true);
      res.json({ ok: result.ok, message: result.message, result, snapshot: buildSnapshot(store.snapshot(), userId, push.getPublicKey()) });
    } catch (error) {
      next(error);
    }
  });

  router.delete("/calendar", async (req, res, next) => {
    try {
      const userId = (req as AuthedRequest).userId;
      await store.update((data) => {
        delete data.calendars[userId];
        data.moodleEvents[userId] = [];
        data.knownIds[userId] = [];
      });
      res.json({ ok: true, snapshot: buildSnapshot(store.snapshot(), userId, push.getPublicKey()) });
    } catch (error) {
      next(error);
    }
  });

  router.post("/sync", async (req, res, next) => {
    try {
      const userId = (req as AuthedRequest).userId;
      const result = await sync.syncUser(userId, true);
      res.json({ ok: result.ok, message: result.message, result, snapshot: buildSnapshot(store.snapshot(), userId, push.getPublicKey()) });
    } catch (error) {
      next(error);
    }
  });

  router.patch("/events/:id/done", async (req, res, next) => {
    try {
      const userId = (req as unknown as AuthedRequest).userId;
      const eventId = req.params.id;
      const body = doneSchema.parse(req.body);
      await store.update((data) => {
        const doneIds = new Set(data.doneIds[userId] ?? []);
        if (body.done) doneIds.add(eventId);
        else doneIds.delete(eventId);
        data.doneIds[userId] = [...doneIds];
      });
      res.json({ ok: true, snapshot: buildSnapshot(store.snapshot(), userId, push.getPublicKey()) });
    } catch (error) {
      next(error);
    }
  });

  router.post("/personal-events", async (req, res, next) => {
    try {
      const userId = (req as AuthedRequest).userId;
      const body = personalEventSchema.parse(req.body);
      if (body.startAtMillis <= Date.now()) {
        res.status(400).json({ ok: false, message: "Hãy chọn thời gian còn ở tương lai." });
        return;
      }
      const event = createPersonalEvent(body.title, body.startAtMillis, body.description);
      await store.update((data) => {
        data.personalEvents[userId] = [...(data.personalEvents[userId] ?? []), event].sort((a, b) => a.startAtMillis - b.startAtMillis);
      });
      await sync.dispatchDueNotifications(userId);
      res.json({ ok: true, event, snapshot: buildSnapshot(store.snapshot(), userId, push.getPublicKey()) });
    } catch (error) {
      next(error);
    }
  });

  router.put("/personal-events/:id", async (req, res, next) => {
    try {
      const userId = (req as unknown as AuthedRequest).userId;
      const eventId = req.params.id;
      const body = personalEventSchema.parse(req.body);
      if (body.startAtMillis <= Date.now()) {
        res.status(400).json({ ok: false, message: "Hãy chọn thời gian còn ở tương lai." });
        return;
      }
      await store.update((data) => {
        const events = data.personalEvents[userId] ?? [];
        data.personalEvents[userId] = events
          .map((event) =>
            event.id === eventId
              ? {
                  ...event,
                  title: body.title,
                  startAtMillis: body.startAtMillis,
                  description: body.description ?? null,
                  rawType: "Cá nhân",
                  source: "PERSONAL" as const,
                  sourceUrl: null
                }
              : event
          )
          .sort((a, b) => a.startAtMillis - b.startAtMillis);
      });
      res.json({ ok: true, snapshot: buildSnapshot(store.snapshot(), userId, push.getPublicKey()) });
    } catch (error) {
      next(error);
    }
  });

  router.delete("/personal-events/:id", async (req, res, next) => {
    try {
      const userId = (req as unknown as AuthedRequest).userId;
      const eventId = req.params.id;
      await store.update((data) => {
        data.personalEvents[userId] = (data.personalEvents[userId] ?? []).filter((event) => event.id !== eventId);
        data.doneIds[userId] = (data.doneIds[userId] ?? []).filter((id) => id !== eventId);
      });
      res.json({ ok: true, snapshot: buildSnapshot(store.snapshot(), userId, push.getPublicKey()) });
    } catch (error) {
      next(error);
    }
  });

  router.put("/settings", async (req, res, next) => {
    try {
      const userId = (req as AuthedRequest).userId;
      const body = settingsSchema.parse(req.body);
      const custom = body.customReminderOffsetsMinutes
        .map((value) => Math.floor(value))
        .filter((value, index, array) => value > 0 && value <= MAX_CUSTOM_REMINDER_DAYS * 24 * 60 && array.indexOf(value) === index)
        .sort((a, b) => b - a);
      await store.update((data) => {
        data.settings[userId] = {
          reminderOffsetsMinutes: sanitizeReminderOffsets(body.reminderOffsetsMinutes),
          customReminderOffsetsMinutes: custom,
          dailySummaryEnabled: body.dailySummaryEnabled && body.dailySummaryDaysMask !== 0 && body.dailySummaryTimes.length > 0,
          dailySummaryTimes: [...new Set(body.dailySummaryTimes)].sort((a, b) => a - b).slice(0, MAX_DAILY_SUMMARY_TIMES),
          dailySummaryDaysMask: body.dailySummaryDaysMask & ALL_DAYS_MASK
        };
      });
      res.json({ ok: true, snapshot: buildSnapshot(store.snapshot(), userId, push.getPublicKey()) });
    } catch (error) {
      next(error);
    }
  });

  router.post("/push/subscribe", async (req, res, next) => {
    try {
      const userId = (req as AuthedRequest).userId;
      const subscription = pushSubscriptionSchema.parse(req.body) as PushSubscription;
      await push.saveSubscription(userId, subscription, req.header("user-agent") ?? undefined);
      res.json({ ok: true, snapshot: buildSnapshot(store.snapshot(), userId, push.getPublicKey()) });
    } catch (error) {
      next(error);
    }
  });

  router.delete("/push/subscribe", async (req, res, next) => {
    try {
      const userId = (req as AuthedRequest).userId;
      const endpoint = z.object({ endpoint: z.string().url() }).parse(req.body).endpoint;
      await push.removeSubscription(userId, endpoint);
      res.json({ ok: true, snapshot: buildSnapshot(store.snapshot(), userId, push.getPublicKey()) });
    } catch (error) {
      next(error);
    }
  });

  router.post("/push/test", async (req, res, next) => {
    try {
      const userId = (req as AuthedRequest).userId;
      const sent = await push.sendToUser(
        userId,
        {
          title: "Test thông báo UTE Notice",
          body: "Nếu bạn thấy thông báo này thì Web Push đang hoạt động.",
          tag: `test-${Date.now()}`
        },
        "test"
      );
      res.json({
        ok: sent > 0,
        message: sent > 0 ? "Đã gửi thông báo test." : "Chưa có thiết bị nào đăng ký Web Push."
      });
    } catch (error) {
      next(error);
    }
  });

  router.put("/email", async (req, res, next) => {
    try {
      const userId = (req as AuthedRequest).userId;
      const body = emailSchema.parse(req.body);
      await store.update((data) => {
        const user = data.users[userId];
        user.email = body.email;
        user.emailEnabled = Boolean(body.email && body.enabled);
      });
      res.json({
        ok: true,
        emailConfigured: email.isConfigured(),
        snapshot: buildSnapshot(store.snapshot(), userId, push.getPublicKey())
      });
    } catch (error) {
      next(error);
    }
  });

  router.post("/email/test", async (req, res, next) => {
    try {
      const userId = (req as AuthedRequest).userId;
      const user = store.snapshot().users[userId];
      const result = await email.send(
        user.email,
        "Test email UTE Notice",
        "Nếu bạn nhận được email này thì cấu hình email UTE Notice đang hoạt động."
      );
      res.status(result.ok ? 200 : 400).json(result);
    } catch (error) {
      next(error);
    }
  });

  router.post("/jobs/run-due", async (req, res, next) => {
    try {
      const userId = (req as AuthedRequest).userId;
      await sync.dispatchDueNotifications(userId);
      res.json({ ok: true });
    } catch (error) {
      next(error);
    }
  });

  return router;
}

export function ensureUserDefaults(store: JsonStore) {
  return async (req: Request, _res: Response, next: (error?: unknown) => void) => {
    try {
      const userId = (req as AuthedRequest).userId;
      await store.update((data) => {
        data.settings[userId] ??= defaultSettings();
        data.moodleEvents[userId] ??= [];
        data.personalEvents[userId] ??= [];
        data.knownIds[userId] ??= [];
        data.doneIds[userId] ??= [];
      });
      next();
    } catch (error) {
      next(error);
    }
  };
}
