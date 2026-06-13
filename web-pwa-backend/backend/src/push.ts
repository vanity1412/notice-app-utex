import webPush, { type PushSubscription } from "web-push";
import { nanoid } from "nanoid";
import type { PushPayload } from "@ute-notice/shared";
import { env } from "./env";
import type { JsonStore } from "./store";

export class PushService {
  private readonly publicKey: string;
  private readonly privateKey: string;

  constructor(private readonly store: JsonStore) {
    if (env.vapidPublicKey && env.vapidPrivateKey) {
      this.publicKey = env.vapidPublicKey;
      this.privateKey = env.vapidPrivateKey;
    } else {
      const generated = webPush.generateVAPIDKeys();
      this.publicKey = generated.publicKey;
      this.privateKey = generated.privateKey;
      console.warn(
        "[push] VAPID keys missing. Generated temporary dev keys; subscriptions will break after restart."
      );
    }

    webPush.setVapidDetails(env.vapidSubject, this.publicKey, this.privateKey);
  }

  getPublicKey(): string {
    return this.publicKey;
  }

  async saveSubscription(userId: string, subscription: PushSubscription, userAgent?: string): Promise<void> {
    const now = Date.now();
    await this.store.update((data) => {
      const existing = Object.values(data.pushSubscriptions).find((item) => item.endpoint === subscription.endpoint);
      if (existing) {
        existing.userId = userId;
        existing.subscription = subscription;
        existing.updatedAt = now;
        existing.userAgent = userAgent;
        return;
      }

      const id = `sub_${nanoid(16)}`;
      data.pushSubscriptions[id] = {
        id,
        userId,
        endpoint: subscription.endpoint,
        subscription,
        createdAt: now,
        updatedAt: now,
        userAgent
      };
    });
  }

  async removeSubscription(userId: string, endpoint: string): Promise<void> {
    await this.store.update((data) => {
      for (const [id, item] of Object.entries(data.pushSubscriptions)) {
        if (item.userId === userId && item.endpoint === endpoint) {
          delete data.pushSubscriptions[id];
        }
      }
    });
  }

  async sendToUser(userId: string, payload: PushPayload, type = "push"): Promise<number> {
    const data = this.store.snapshot();
    const subscriptions = Object.values(data.pushSubscriptions).filter((item) => item.userId === userId);
    let sent = 0;

    for (const item of subscriptions) {
      try {
        await webPush.sendNotification(item.subscription, JSON.stringify(payload));
        sent += 1;
        await this.log(userId, type, payload.title, payload.body);
      } catch (error) {
        const statusCode = (error as { statusCode?: number }).statusCode;
        if (statusCode === 404 || statusCode === 410) {
          await this.removeSubscription(userId, item.endpoint);
        }
        await this.log(userId, type, payload.title, payload.body, String((error as Error).message ?? error));
      }
    }

    return sent;
  }

  private async log(userId: string, type: string, title: string, body: string, error?: string): Promise<void> {
    await this.store.update((data) => {
      data.notificationLog.unshift({
        id: `log_${nanoid(12)}`,
        userId,
        type,
        title,
        body,
        createdAt: Date.now(),
        error
      });
      data.notificationLog = data.notificationLog.slice(0, 1000);
    });
  }
}
