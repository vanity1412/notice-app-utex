import { MOODLE_HOST } from "./constants";

const EXPORT_PATH = "/calendar/export.php";
const EXECUTE_PATH = "/calendar/export_execute.php";

export function validateMoodleUrl(urlText: string): {
  ok: boolean;
  message: string;
  normalizedUrl: string;
} {
  const trimmed = urlText.trim();
  if (!trimmed) {
    return {
      ok: false,
      message: "Bạn chưa dán Calendar URL Moodle.",
      normalizedUrl: ""
    };
  }

  let url: URL;
  try {
    url = new URL(trimmed);
  } catch {
    return {
      ok: false,
      message: "Link không đúng định dạng URL.",
      normalizedUrl: ""
    };
  }

  if (url.protocol !== "https:") {
    return {
      ok: false,
      message: "Link phải bắt đầu bằng https://",
      normalizedUrl: ""
    };
  }

  if (url.hostname !== MOODLE_HOST) {
    return {
      ok: false,
      message: "Link phải thuộc utexlms.hcmute.edu.vn.",
      normalizedUrl: ""
    };
  }

  if (url.pathname === EXPORT_PATH) {
    return {
      ok: false,
      message: "Đây mới là trang xuất lịch. Hãy bấm Lấy địa chỉ mạng của lịch trên Moodle rồi copy Calendar URL.",
      normalizedUrl: ""
    };
  }

  if (url.pathname !== EXECUTE_PATH) {
    return {
      ok: false,
      message: "Link phải là Calendar URL dạng /calendar/export_execute.php.",
      normalizedUrl: ""
    };
  }

  if (!url.searchParams.get("userid")) {
    return {
      ok: false,
      message: "Link thiếu userid. Hãy copy lại đúng Calendar URL từ Moodle.",
      normalizedUrl: ""
    };
  }

  if (!url.searchParams.get("authtoken")) {
    return {
      ok: false,
      message: "Link thiếu authtoken. Hãy copy lại đúng Calendar URL từ Moodle.",
      normalizedUrl: ""
    };
  }

  return {
    ok: true,
    message: "Calendar URL hợp lệ.",
    normalizedUrl: trimmed
  };
}

export function maskMoodleUrl(urlText: string): string {
  try {
    const url = new URL(urlText.trim());
    const userId = url.searchParams.get("userid") || "...";
    const preset = url.searchParams.get("preset_time") || "...";
    return `utexlms.hcmute.edu.vn - userid=${userId} - token=*** - ${preset}`;
  } catch {
    return "Calendar URL đã lưu";
  }
}
