import type { AppSnapshot, UserSettings } from "@ute-notice/shared";

const LOCAL_API_BASE = "http://localhost:8787/api";
const PRODUCTION_API_BASE = "https://hcmute-notice-backend.onrender.com/api";
const API_BASE = import.meta.env.VITE_API_BASE_URL ?? (import.meta.env.PROD ? PRODUCTION_API_BASE : LOCAL_API_BASE);
const SESSION_KEY = "ute-notice-web-session";

export interface Session {
  userId: string;
  secret: string;
}

interface ApiResult<T> {
  ok: boolean;
  message?: string;
  snapshot?: AppSnapshot;
  result?: T;
  publicKey?: string;
}

function readSession(): Session | null {
  try {
    const raw = localStorage.getItem(SESSION_KEY);
    return raw ? (JSON.parse(raw) as Session) : null;
  } catch {
    return null;
  }
}

function saveSession(session: Session): void {
  localStorage.setItem(SESSION_KEY, JSON.stringify(session));
}

export async function ensureSession(): Promise<Session> {
  const existing = readSession();
  if (existing?.userId && existing.secret) return existing;

  const response = await fetch(`${API_BASE}/session`, { method: "POST" });
  const data = (await response.json()) as Session & { ok: boolean };
  if (!response.ok || !data.ok) throw new Error("Không tạo được phiên sử dụng.");
  const session = { userId: data.userId, secret: data.secret };
  saveSession(session);
  return session;
}

export async function resetSession(): Promise<Session> {
  localStorage.removeItem(SESSION_KEY);
  return ensureSession();
}

export async function apiRequest<T = unknown>(
  path: string,
  options: RequestInit & { bodyJson?: unknown } = {}
): Promise<ApiResult<T>> {
  const session = await ensureSession();
  const headers = new Headers(options.headers);
  headers.set("x-user-id", session.userId);
  headers.set("x-user-secret", session.secret);
  if (options.bodyJson !== undefined) headers.set("content-type", "application/json");

  const response = await fetch(`${API_BASE}${path}`, {
    ...options,
    headers,
    body: options.bodyJson === undefined ? options.body : JSON.stringify(options.bodyJson)
  });
  const data = (await response.json()) as ApiResult<T>;

  if (response.status === 401) {
    await resetSession();
    return apiRequest<T>(path, options);
  }

  if (!response.ok || data.ok === false) {
    const resultMessage =
      typeof data.result === "object" && data.result && "message" in data.result
        ? String((data.result as { message?: unknown }).message ?? "")
        : "";
    throw new Error(data.message || resultMessage || "Server báo lỗi.");
  }
  return data;
}

export async function loadSnapshot(): Promise<AppSnapshot> {
  const data = await apiRequest("/snapshot");
  if (!data.snapshot) throw new Error("Không tải được dữ liệu.");
  return data.snapshot;
}

export async function saveCalendar(url: string): Promise<AppSnapshot> {
  const data = await apiRequest("/calendar", { method: "POST", bodyJson: { url } });
  return requireSnapshot(data);
}

export async function deleteCalendar(): Promise<AppSnapshot> {
  const data = await apiRequest("/calendar", { method: "DELETE" });
  return requireSnapshot(data);
}

export async function syncNow(): Promise<{ snapshot: AppSnapshot; message: string }> {
  const data = await apiRequest<{ message: string }>("/sync", { method: "POST" });
  return { snapshot: requireSnapshot(data), message: data.result?.message || data.message || "Đã đồng bộ." };
}

export async function setDone(eventId: string, done: boolean): Promise<AppSnapshot> {
  const data = await apiRequest(`/events/${encodeURIComponent(eventId)}/done`, {
    method: "PATCH",
    bodyJson: { done }
  });
  return requireSnapshot(data);
}

export async function createPersonalEvent(input: {
  title: string;
  startAtMillis: number;
  description?: string | null;
}): Promise<AppSnapshot> {
  const data = await apiRequest("/personal-events", { method: "POST", bodyJson: input });
  return requireSnapshot(data);
}

export async function updatePersonalEvent(
  id: string,
  input: { title: string; startAtMillis: number; description?: string | null }
): Promise<AppSnapshot> {
  const data = await apiRequest(`/personal-events/${encodeURIComponent(id)}`, {
    method: "PUT",
    bodyJson: input
  });
  return requireSnapshot(data);
}

export async function deletePersonalEvent(id: string): Promise<AppSnapshot> {
  const data = await apiRequest(`/personal-events/${encodeURIComponent(id)}`, { method: "DELETE" });
  return requireSnapshot(data);
}

export async function saveSettings(settings: UserSettings): Promise<AppSnapshot> {
  const data = await apiRequest("/settings", { method: "PUT", bodyJson: settings });
  return requireSnapshot(data);
}

export async function saveEmail(email: string, enabled: boolean): Promise<AppSnapshot> {
  const data = await apiRequest("/email", { method: "PUT", bodyJson: { email, enabled } });
  return requireSnapshot(data);
}

export async function sendEmailTest(): Promise<string> {
  const data = await apiRequest("/email/test", { method: "POST" });
  return data.message || "Đã gửi email test.";
}

export async function sendPushTest(): Promise<string> {
  const data = await apiRequest("/push/test", { method: "POST" });
  return data.message || "Đã gửi thông báo test.";
}

export async function subscribePush(subscription: PushSubscriptionJSON): Promise<AppSnapshot> {
  const data = await apiRequest("/push/subscribe", { method: "POST", bodyJson: subscription });
  return requireSnapshot(data);
}

function requireSnapshot(data: ApiResult<unknown>): AppSnapshot {
  if (!data.snapshot) throw new Error(data.message || "Server chưa trả snapshot.");
  return data.snapshot;
}
