import path from "node:path";
import { fileURLToPath } from "node:url";
import dotenv from "dotenv";

dotenv.config();

const dirname = path.dirname(fileURLToPath(import.meta.url));
const backendRoot = path.resolve(dirname, "..");

function numberEnv(name: string, fallback: number): number {
  const raw = process.env[name];
  if (!raw) return fallback;
  const parsed = Number(raw);
  return Number.isFinite(parsed) ? parsed : fallback;
}

function boolEnv(name: string, fallback: boolean): boolean {
  const raw = process.env[name];
  if (!raw) return fallback;
  return ["1", "true", "yes", "on"].includes(raw.toLowerCase());
}

export const env = {
  port: numberEnv("PORT", 8787),
  corsOrigin: process.env.CORS_ORIGIN ?? "http://localhost:5173",
  dataFile: path.resolve(backendRoot, process.env.DATA_FILE ?? "./data/ute-notice-db.json"),
  syncCron: process.env.SYNC_CRON ?? "*/15 * * * *",
  reminderGraceMinutes: numberEnv("REMINDER_GRACE_MINUTES", 20),
  vapidSubject: process.env.VAPID_SUBJECT ?? "mailto:admin@example.com",
  vapidPublicKey: process.env.VAPID_PUBLIC_KEY ?? "",
  vapidPrivateKey: process.env.VAPID_PRIVATE_KEY ?? "",
  smtp: {
    host: process.env.SMTP_HOST ?? "",
    port: numberEnv("SMTP_PORT", 587),
    secure: boolEnv("SMTP_SECURE", false),
    user: process.env.SMTP_USER ?? "",
    pass: process.env.SMTP_PASS ?? "",
    from: process.env.SMTP_FROM ?? "UTE Notice <no-reply@example.com>"
  }
};
