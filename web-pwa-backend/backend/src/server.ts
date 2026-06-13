import cors from "cors";
import express, { type NextFunction, type Request, type Response } from "express";
import helmet from "helmet";
import morgan from "morgan";
import { ZodError } from "zod";
import { env } from "./env";
import { EmailService } from "./email";
import { PushService } from "./push";
import { createRouter } from "./routes";
import { startScheduler } from "./scheduler";
import { JsonStore } from "./store";
import { SyncService } from "./sync";

const store = new JsonStore(env.dataFile);
await store.init();

const push = new PushService(store);
const email = new EmailService();
const sync = new SyncService(store, push, email);

const app = express();

app.use(helmet());
app.use(
  cors({
    origin: env.corsOrigin === "*" ? true : env.corsOrigin,
    credentials: false
  })
);
app.use(express.json({ limit: "1mb" }));
app.use(morgan("dev"));

app.use("/api", createRouter(store, push, sync, email));

app.use((error: unknown, _req: Request, res: Response, _next: NextFunction) => {
  if (error instanceof ZodError) {
    res.status(400).json({
      ok: false,
      message: "Dữ liệu gửi lên chưa hợp lệ.",
      issues: error.issues
    });
    return;
  }

  console.error(error);
  res.status(500).json({
    ok: false,
    message: (error as Error).message || "Server lỗi."
  });
});

app.listen(env.port, () => {
  console.log(`UTE Notice backend running at http://localhost:${env.port}`);
  console.log(`CORS origin: ${env.corsOrigin}`);
  console.log(`Data file: ${env.dataFile}`);
  console.log(`Email configured: ${email.isConfigured() ? "yes" : "no"}`);
});

startScheduler(sync);
