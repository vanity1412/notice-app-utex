import nodemailer from "nodemailer";
import { env } from "./env";

export class EmailService {
  private readonly enabled = Boolean(env.smtp.host && env.smtp.user && env.smtp.pass);
  private readonly transporter = this.enabled
    ? nodemailer.createTransport({
        host: env.smtp.host,
        port: env.smtp.port,
        secure: env.smtp.secure,
        auth: {
          user: env.smtp.user,
          pass: env.smtp.pass
        }
      })
    : null;

  isConfigured(): boolean {
    return this.enabled;
  }

  async send(to: string, subject: string, text: string): Promise<{ ok: boolean; message: string }> {
    if (!this.transporter) {
      return {
        ok: false,
        message: "Chưa cấu hình SMTP nên chưa gửi được email."
      };
    }
    if (!to.trim()) {
      return { ok: false, message: "Chưa có email nhận thông báo." };
    }

    await this.transporter.sendMail({
      from: env.smtp.from,
      to,
      subject,
      text
    });

    return { ok: true, message: "Đã gửi email." };
  }
}
