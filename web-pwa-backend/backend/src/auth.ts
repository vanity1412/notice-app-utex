import crypto from "node:crypto";
import type { NextFunction, Request, Response } from "express";
import { nanoid } from "nanoid";
import type { Database, JsonStore } from "./store";
import { defaultSettings } from "./store";

export interface AuthedRequest extends Request {
  userId: string;
}

export function hashSecret(secret: string): string {
  return crypto.createHash("sha256").update(secret).digest("hex");
}

export async function createSession(store: JsonStore): Promise<{ userId: string; secret: string }> {
  const userId = `user_${nanoid(18)}`;
  const secret = nanoid(36);
  const now = Date.now();
  await store.update((data) => {
    data.users[userId] = {
      id: userId,
      secretHash: hashSecret(secret),
      createdAt: now,
      email: "",
      emailEnabled: false
    };
    data.settings[userId] = defaultSettings();
    data.moodleEvents[userId] = [];
    data.personalEvents[userId] = [];
    data.knownIds[userId] = [];
    data.doneIds[userId] = [];
  });
  return { userId, secret };
}

export function requireAuth(store: JsonStore) {
  return (req: Request, res: Response, next: NextFunction) => {
    const userId = String(req.header("x-user-id") ?? "");
    const secret = String(req.header("x-user-secret") ?? "");
    if (!userId || !secret) {
      res.status(401).json({ ok: false, message: "Thiếu phiên đăng nhập." });
      return;
    }

    const data = store.snapshot();
    if (!isValidSession(data, userId, secret)) {
      res.status(401).json({ ok: false, message: "Phiên đăng nhập không hợp lệ." });
      return;
    }

    (req as AuthedRequest).userId = userId;
    next();
  };
}

export function isValidSession(data: Database, userId: string, secret: string): boolean {
  const user = data.users[userId];
  return Boolean(user && crypto.timingSafeEqual(Buffer.from(user.secretHash), Buffer.from(hashSecret(secret))));
}
