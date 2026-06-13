import cron from "node-cron";
import { env } from "./env";
import type { SyncService } from "./sync";

export function startScheduler(sync: SyncService): void {
  if (!cron.validate(env.syncCron)) {
    console.warn(`[scheduler] Invalid SYNC_CRON="${env.syncCron}", scheduled sync disabled.`);
    return;
  }

  cron.schedule(env.syncCron, async () => {
    try {
      console.log("[scheduler] Running Moodle sync job...");
      await sync.syncAll();
      console.log("[scheduler] Sync job finished.");
    } catch (error) {
      console.error("[scheduler] Sync job failed:", error);
    }
  });

  console.log(`[scheduler] Scheduled Moodle sync with cron: ${env.syncCron}`);
}
