import { useEffect, useMemo, useState } from "react";
import type * as React from "react";
import {
  Bell,
  BookOpen,
  CalendarDays,
  Check,
  ChevronLeft,
  ChevronRight,
  ClipboardPaste,
  Copy,
  Download,
  ExternalLink,
  List,
  Mail,
  Pencil,
  Plus,
  RefreshCcw,
  Save,
  Search,
  Send,
  Settings,
  Smartphone,
  Trash2,
  X
} from "lucide-react";
import {
  ALL_DAYS_MASK,
  GITHUB_GUIDE_URL,
  MOODLE_EXPORT_URL,
  SUPPORT_CONTACT,
  allReminderOptions,
  broadGroup,
  cleanDescription,
  eventCourse,
  eventKind,
  reminderOptionLabel,
  sanitizeReminderOffsets,
  searchable,
  timeLabel,
  type AppSnapshot,
  type DeadlineEvent,
  type EventWithState,
  type UserSettings
} from "@ute-notice/shared";
import {
  createPersonalEvent,
  deleteCalendar,
  deletePersonalEvent,
  loadSnapshot,
  saveCalendar,
  saveEmail,
  saveSettings,
  sendEmailTest,
  sendPushTest,
  setDone,
  syncNow,
  updatePersonalEvent
} from "./api";
import { dayLabel, formatFull, formatShort, fromDateInputValue, remainText, toDateInputValue } from "./date";
import { enableWebPush, ensureServiceWorker, isPushSupported } from "./push";

type Tab = "calendar" | "guide" | "settings";
type ViewMode = "list" | "month";
type Filter = "ALL" | "PERSONAL" | "SUBMISSION" | "TEST" | "EXAM";

const filterLabels: Record<Filter, string> = {
  ALL: "Tất cả",
  PERSONAL: "Cá nhân",
  SUBMISSION: "Bài nộp",
  TEST: "Kiểm tra",
  EXAM: "Thi"
};

const dayOptions = [
  ["T2", 0],
  ["T3", 1],
  ["T4", 2],
  ["T5", 3],
  ["T6", 4],
  ["T7", 5],
  ["CN", 6]
] as const;

interface BeforeInstallPromptEvent extends Event {
  readonly platforms: string[];
  readonly userChoice: Promise<{ outcome: "accepted" | "dismissed"; platform: string }>;
  prompt(): Promise<void>;
}

function isStandaloneApp(): boolean {
  return window.matchMedia("(display-mode: standalone)").matches || Boolean((navigator as Navigator & { standalone?: boolean }).standalone);
}

function isIosDevice(): boolean {
  return /iphone|ipad|ipod/i.test(navigator.userAgent);
}

export function App() {
  const [snapshot, setSnapshot] = useState<AppSnapshot | null>(null);
  const [tab, setTab] = useState<Tab>("calendar");
  const [status, setStatus] = useState({ text: "Đang tải dữ liệu...", type: "info" as "info" | "success" | "error" });
  const [toast, setToast] = useState("");
  const [busy, setBusy] = useState(false);
  const [urlDraft, setUrlDraft] = useState("");
  const [connectionExpanded, setConnectionExpanded] = useState(false);
  const [viewMode, setViewMode] = useState<ViewMode>("list");
  const [query, setQuery] = useState("");
  const [filter, setFilter] = useState<Filter>("ALL");
  const [hideDone, setHideDone] = useState(true);
  const [monthCursor, setMonthCursor] = useState(() => firstDayOfMonth(new Date()));
  const [personalEdit, setPersonalEdit] = useState<DeadlineEvent | "new" | null>(null);
  const [customReminder, setCustomReminder] = useState({ days: "", hours: "", minutes: "" });
  const [summaryTime, setSummaryTime] = useState("06:00");
  const [emailDraft, setEmailDraft] = useState("");
  const [installPrompt, setInstallPrompt] = useState<BeforeInstallPromptEvent | null>(null);
  const [installed, setInstalled] = useState(() => isStandaloneApp());
  const [installHelpOpen, setInstallHelpOpen] = useState(false);

  useEffect(() => {
    loadSnapshot()
      .then((data) => {
        setSnapshot(data);
        setUrlDraft("");
        setEmailDraft(data.email.address);
        setStatus({
          text: data.calendar?.lastSyncMessage || (data.calendar ? "Moodle đã kết nối." : "Dán iCal URL để bắt đầu theo dõi lịch Moodle."),
          type: "info"
        });
      })
      .catch((error) => setStatus({ text: String(error.message || error), type: "error" }));
  }, []);

  useEffect(() => {
    ensureServiceWorker().catch(() => undefined);
  }, []);

  useEffect(() => {
    const handleBeforeInstallPrompt = (event: Event) => {
      event.preventDefault();
      setInstallPrompt(event as BeforeInstallPromptEvent);
    };
    const handleAppInstalled = () => {
      setInstalled(true);
      setInstallPrompt(null);
      setInstallHelpOpen(false);
      setToast("UTE Notice đã được cài vào thiết bị.");
    };

    window.addEventListener("beforeinstallprompt", handleBeforeInstallPrompt);
    window.addEventListener("appinstalled", handleAppInstalled);

    return () => {
      window.removeEventListener("beforeinstallprompt", handleBeforeInstallPrompt);
      window.removeEventListener("appinstalled", handleAppInstalled);
    };
  }, []);

  useEffect(() => {
    if (!toast) return;
    const timer = window.setTimeout(() => setToast(""), 3200);
    return () => window.clearTimeout(timer);
  }, [toast]);

  const filteredEvents = useMemo(() => {
    if (!snapshot) return [];
    const needle = searchable(query.trim());
    return snapshot.events.filter((event) => {
      if (hideDone && event.done) return false;
      if (filter === "PERSONAL" && event.source !== "PERSONAL") return false;
      if (filter !== "ALL" && filter !== "PERSONAL" && broadGroup(event) !== filter) return false;
      if (!needle) return true;
      return searchable(
        `${event.title} ${event.rawType ?? ""} ${event.description ?? ""} ${eventKind(event)} ${event.source}`
      ).includes(needle);
    });
  }, [filter, hideDone, query, snapshot]);

  async function run<T>(work: () => Promise<T>, success?: (value: T) => void) {
    setBusy(true);
    try {
      const value = await work();
      success?.(value);
      return value;
    } catch (error) {
      setStatus({ text: String((error as Error).message || error), type: "error" });
      return null;
    } finally {
      setBusy(false);
    }
  }

  async function onInstallApp() {
    if (installed || isStandaloneApp()) {
      setInstalled(true);
      setToast("UTE Notice đã được cài trên thiết bị này.");
      return;
    }

    if (!installPrompt) {
      setInstallHelpOpen(true);
      return;
    }

    await installPrompt.prompt();
    const choice = await installPrompt.userChoice.catch(() => null);
    setInstallPrompt(null);
    if (choice?.outcome === "accepted") {
      setInstalled(true);
      setToast("Đang cài UTE Notice...");
    } else {
      setInstallHelpOpen(true);
    }
  }

  async function onSaveCalendar() {
    if (!urlDraft.trim()) {
      setStatus({
        text: snapshot?.calendar ? "Dán Calendar URL mới nếu muốn cập nhật kết nối Moodle." : "Dán iCal URL trước khi lưu.",
        type: "error"
      });
      return;
    }
    await run(
      async () => saveCalendar(urlDraft),
      (data) => {
        setSnapshot(data);
        setUrlDraft("");
        setConnectionExpanded(false);
        setStatus({ text: data.calendar?.lastSyncMessage || "Đã lưu và đồng bộ.", type: "success" });
      }
    );
  }

  async function onSyncNow() {
    if (urlDraft.trim()) {
      await onSaveCalendar();
      return;
    }
    if (!snapshot?.calendar) {
      setStatus({ text: "Dán iCal URL trước khi kiểm tra.", type: "error" });
      return;
    }
    await run(syncNow, ({ snapshot: data, message }) => {
      setSnapshot(data);
      setStatus({ text: message, type: "success" });
    });
  }

  async function onDeleteCalendar() {
    if (!window.confirm("Xóa kết nối Moodle khỏi backend?")) return;
    await run(deleteCalendar, (data) => {
      setSnapshot(data);
      setConnectionExpanded(true);
      setUrlDraft("");
      setStatus({ text: "Đã xóa kết nối Moodle.", type: "success" });
    });
  }

  async function onPasteUrl() {
    try {
      const text = await navigator.clipboard.readText();
      setUrlDraft(text.trim());
      setToast("Đã dán từ clipboard.");
    } catch {
      setToast("Trình duyệt chưa cho phép đọc clipboard.");
    }
  }

  async function onToggleDone(event: EventWithState) {
    await run(() => setDone(event.id, !event.done), (data) => {
      setSnapshot(data);
      setToast(!event.done ? "Đã đánh dấu xong." : "Đã bỏ đánh dấu xong.");
    });
  }

  async function onDeletePersonal(event: DeadlineEvent) {
    if (!window.confirm(`Xóa deadline "${event.title}"?`)) return;
    await run(() => deletePersonalEvent(event.id), (data) => {
      setSnapshot(data);
      setToast("Đã xóa deadline cá nhân.");
    });
  }

  async function onEnablePush() {
    if (!snapshot?.push.publicKey) {
      setStatus({ text: "Backend chưa có public key Web Push.", type: "error" });
      return;
    }
    await run(() => enableWebPush(snapshot.push.publicKey!), (data) => {
      setSnapshot(data);
      setStatus({ text: "Đã bật Web Push cho thiết bị này.", type: "success" });
    });
  }

  async function onTestPush() {
    await run(sendPushTest, (message) => setToast(message));
  }

  async function updateSettings(next: UserSettings) {
    await run(() => saveSettings(next), (data) => setSnapshot(data));
  }

  if (!snapshot) {
    return (
      <div className="app-shell">
        <Header ready={false} installed={installed} onInstall={onInstallApp} />
        <main className="main narrow">
          <StatusBox text={status.text} type={status.type} />
        </main>
        {installHelpOpen && <InstallDialog onClose={() => setInstallHelpOpen(false)} />}
      </div>
    );
  }

  return (
    <div className="app-shell">
      <Header ready={snapshot.push.enabled} installed={installed} onInstall={onInstallApp} />
      <main className="main">
        <nav className="tabs" aria-label="UTE Notice tabs">
          <TabButton active={tab === "calendar"} icon={<CalendarDays size={17} />} onClick={() => setTab("calendar")}>
            Lịch sắp tới
          </TabButton>
          <TabButton active={tab === "guide"} icon={<BookOpen size={17} />} onClick={() => setTab("guide")}>
            Hướng dẫn
          </TabButton>
          <TabButton active={tab === "settings"} icon={<Settings size={17} />} onClick={() => setTab("settings")}>
            Thông báo
          </TabButton>
        </nav>

        {tab === "calendar" && (
          <CalendarTab
            snapshot={snapshot}
            status={status}
            busy={busy}
            urlDraft={urlDraft}
            setUrlDraft={setUrlDraft}
            connectionExpanded={connectionExpanded}
            setConnectionExpanded={setConnectionExpanded}
            onPasteUrl={onPasteUrl}
            onSaveCalendar={onSaveCalendar}
            onDeleteCalendar={onDeleteCalendar}
            onSyncNow={onSyncNow}
            onShowGuide={() => setTab("guide")}
            events={filteredEvents}
            allCount={snapshot.events.length}
            viewMode={viewMode}
            setViewMode={setViewMode}
            query={query}
            setQuery={setQuery}
            filter={filter}
            setFilter={setFilter}
            hideDone={hideDone}
            setHideDone={setHideDone}
            monthCursor={monthCursor}
            setMonthCursor={setMonthCursor}
            onToggleDone={onToggleDone}
            onEditPersonal={setPersonalEdit}
            onDeletePersonal={onDeletePersonal}
          />
        )}

        {tab === "guide" && <GuideTab />}

        {tab === "settings" && (
          <SettingsTab
            snapshot={snapshot}
            busy={busy}
            customReminder={customReminder}
            setCustomReminder={setCustomReminder}
            summaryTime={summaryTime}
            setSummaryTime={setSummaryTime}
            emailDraft={emailDraft}
            setEmailDraft={setEmailDraft}
            onEnablePush={onEnablePush}
            onTestPush={onTestPush}
            onUpdateSettings={updateSettings}
            onSaveEmail={async (email, enabled) => {
              await run(() => saveEmail(email, enabled), (data) => {
                setSnapshot(data);
                setToast("Đã lưu cài đặt email.");
              });
            }}
            onTestEmail={async () => {
              await run(sendEmailTest, (message) => setToast(message));
            }}
          />
        )}
      </main>

      {personalEdit && (
        <PersonalDialog
          event={personalEdit === "new" ? null : personalEdit}
          onClose={() => setPersonalEdit(null)}
          onSave={async (input) => {
            await run(
              () => (personalEdit === "new" ? createPersonalEvent(input) : updatePersonalEvent(personalEdit.id, input)),
              (data) => {
                setSnapshot(data);
                setPersonalEdit(null);
                setToast(personalEdit === "new" ? "Đã thêm deadline cá nhân." : "Đã lưu deadline cá nhân.");
              }
            );
          }}
        />
      )}

      {installHelpOpen && <InstallDialog onClose={() => setInstallHelpOpen(false)} />}
      {toast && <div className="toast">{toast}</div>}
    </div>
  );
}

function Header({ ready, installed, onInstall }: { ready: boolean; installed: boolean; onInstall: () => void }) {
  return (
    <header className="header">
      <img src="/icons/hcmute_logo.png" alt="HCMUTE" className="logo" />
      <div className="header-title">
        <strong>UTE Notice</strong>
        <span>HCM-UTE Moodle Calendar</span>
      </div>
      <div className="header-actions">
        <button className={`install-btn ${installed ? "installed" : ""}`} onClick={onInstall} type="button">
          {installed ? <Check size={15} /> : <Download size={15} />}
          <span>{installed ? "Đã cài" : "Cài app"}</span>
        </button>
        <span className={`header-chip ${ready ? "ready" : "warn"}`}>{ready ? "Cảnh báo sẵn sàng" : "Cần bật cảnh báo"}</span>
      </div>
    </header>
  );
}

function TabButton({
  active,
  icon,
  onClick,
  children
}: {
  active: boolean;
  icon: React.ReactNode;
  onClick: () => void;
  children: React.ReactNode;
}) {
  return (
    <button className={`tab ${active ? "active" : ""}`} onClick={onClick}>
      {icon}
      <span>{children}</span>
    </button>
  );
}

function CalendarTab(props: {
  snapshot: AppSnapshot;
  status: { text: string; type: "info" | "success" | "error" };
  busy: boolean;
  urlDraft: string;
  setUrlDraft: (value: string) => void;
  connectionExpanded: boolean;
  setConnectionExpanded: (value: boolean) => void;
  onPasteUrl: () => void;
  onSaveCalendar: () => void;
  onDeleteCalendar: () => void;
  onSyncNow: () => void;
  onShowGuide: () => void;
  events: EventWithState[];
  allCount: number;
  viewMode: ViewMode;
  setViewMode: (mode: ViewMode) => void;
  query: string;
  setQuery: (value: string) => void;
  filter: Filter;
  setFilter: (value: Filter) => void;
  hideDone: boolean;
  setHideDone: (value: boolean) => void;
  monthCursor: Date;
  setMonthCursor: (value: Date) => void;
  onToggleDone: (event: EventWithState) => void;
  onEditPersonal: (event: DeadlineEvent | "new") => void;
  onDeletePersonal: (event: DeadlineEvent) => void;
}) {
  const hasCalendar = Boolean(props.snapshot.calendar);
  const compact = hasCalendar && !props.connectionExpanded;

  return (
    <section className="stack">
      {compact ? (
        <section className="card connection-compact">
          <div className="connection-summary-row">
            <div>
              <h2>Kết nối Moodle</h2>
              <p>{props.snapshot.calendar?.maskedUrl}</p>
            </div>
            <span className="pill ok">Đã kết nối</span>
          </div>
          <div className="button-row connection-actions">
            <Button variant="secondary" icon={<Pencil size={16} />} onClick={() => props.setConnectionExpanded(true)}>
              Chỉnh
            </Button>
            <Button variant="danger" icon={<Trash2 size={16} />} onClick={props.onDeleteCalendar}>
              Xóa
            </Button>
            <Button variant="danger" icon={<BookOpen size={16} />} onClick={props.onShowGuide}>
              Hướng dẫn
            </Button>
          </div>
        </section>
      ) : (
        <section className="card stack">
          <div className="section-title">
            <h2>{hasCalendar ? "Chỉnh kết nối Moodle" : "Kết nối Moodle"}</h2>
            <div className="section-actions">
              <Button variant="danger" icon={<BookOpen size={16} />} onClick={props.onShowGuide}>
                Hướng dẫn
              </Button>
              {hasCalendar && (
                <Button variant="ghost" icon={<X size={16} />} onClick={() => props.setConnectionExpanded(false)}>
                  Thu gọn
                </Button>
              )}
            </div>
          </div>
          {hasCalendar && <p className="connection-note">URL đã lưu được che token. Dán Calendar URL mới vào ô dưới nếu muốn đổi kết nối.</p>}
          <textarea
            className="url-input"
            value={props.urlDraft}
            onChange={(event) => props.setUrlDraft(event.target.value)}
            placeholder={hasCalendar ? "Dán link export_execute.php mới nếu muốn đổi kết nối" : "Dán link export_execute.php của Moodle"}
          />
          <div className="button-grid">
            <Button variant="secondary" icon={<ClipboardPaste size={16} />} onClick={props.onPasteUrl}>
              Dán clipboard
            </Button>
            {hasCalendar && (
              <Button variant="danger" icon={<Trash2 size={16} />} onClick={props.onDeleteCalendar}>
                Xóa kết nối
              </Button>
            )}
          </div>
          <div className="button-grid">
            <Button disabled={props.busy} icon={<Save size={16} />} onClick={props.onSaveCalendar}>
              Lưu & đồng bộ
            </Button>
            <Button disabled={props.busy} variant="secondary" icon={<RefreshCcw size={16} />} onClick={props.onSyncNow}>
              Kiểm tra
            </Button>
          </div>
        </section>
      )}

      <StatusBox text={props.status.text} type={props.status.type} />

      <section className="section-heading">
        <div>
          <h1>Lịch sắp tới</h1>
          <p>Mốc nhắc: {props.snapshot.settings.reminderOffsetsMinutes.map(reminderOptionLabel).join(", ")}</p>
        </div>
        <span className="count-chip">
          {props.events.length === props.allCount ? `${props.allCount} mục` : `${props.events.length}/${props.allCount} mục`}
        </span>
        <Button icon={<Plus size={16} />} onClick={() => props.onEditPersonal("new")}>
          Cá nhân
        </Button>
      </section>

      <div className="segmented">
        <button className={props.viewMode === "list" ? "selected" : ""} onClick={() => props.setViewMode("list")}>
          <List size={16} />
          Danh sách
        </button>
        <button className={props.viewMode === "month" ? "selected" : ""} onClick={() => props.setViewMode("month")}>
          <CalendarDays size={16} />
          Lịch tháng
        </button>
      </div>

      <section className="card filter-card">
        <label className="search-box">
          <Search size={17} />
          <input value={props.query} onChange={(event) => props.setQuery(event.target.value)} placeholder="Tìm theo tên bài, môn/lớp" />
        </label>
        <div className="filter-row">
          {(Object.keys(filterLabels) as Filter[]).map((item) => (
            <button key={item} className={props.filter === item ? "selected" : ""} onClick={() => props.setFilter(item)}>
              {filterLabels[item]}
            </button>
          ))}
        </div>
        <div className="done-row">
          <span>{props.hideDone ? "Deadline đã xong đang ẩn" : "Đang hiện deadline đã xong"}</span>
          <Button variant="secondary" onClick={() => props.setHideDone(!props.hideDone)}>
            {props.hideDone ? "Hiện đã xong" : "Ẩn đã xong"}
          </Button>
        </div>
      </section>

      {props.allCount === 0 ? (
        <EmptyState title="Chưa có deadline" text="Dán iCal URL rồi bấm đồng bộ để tải lịch." />
      ) : props.events.length === 0 ? (
        <EmptyState title="Không có mục phù hợp" text="Thử đổi từ khóa, bộ lọc hoặc bật hiển thị deadline đã xong." />
      ) : props.viewMode === "month" ? (
        <MonthView {...props} />
      ) : (
        <EventList
          events={props.events}
          onToggleDone={props.onToggleDone}
          onEditPersonal={props.onEditPersonal}
          onDeletePersonal={props.onDeletePersonal}
        />
      )}
    </section>
  );
}

function EventList({
  events,
  onToggleDone,
  onEditPersonal,
  onDeletePersonal
}: {
  events: EventWithState[];
  onToggleDone: (event: EventWithState) => void;
  onEditPersonal: (event: DeadlineEvent) => void;
  onDeletePersonal: (event: DeadlineEvent) => void;
}) {
  let lastDay = "";
  return (
    <div className="stack">
      {events.map((event) => {
        const day = dayLabel(event.startAtMillis);
        const showDay = day !== lastDay;
        lastDay = day;
        return (
          <div key={event.id} className="stack tight">
            {showDay && <h3 className="day-heading">{day}</h3>}
            <EventCard event={event} onToggleDone={onToggleDone} onEditPersonal={onEditPersonal} onDeletePersonal={onDeletePersonal} />
          </div>
        );
      })}
    </div>
  );
}

function MonthView({
  events,
  monthCursor,
  setMonthCursor,
  onToggleDone,
  onEditPersonal,
  onDeletePersonal
}: {
  events: EventWithState[];
  monthCursor: Date;
  setMonthCursor: (value: Date) => void;
  onToggleDone: (event: EventWithState) => void;
  onEditPersonal: (event: DeadlineEvent) => void;
  onDeletePersonal: (event: DeadlineEvent) => void;
}) {
  const monthEvents = events.filter((event) => sameMonth(new Date(event.startAtMillis), monthCursor));
  const cells = monthCells(monthCursor);
  const monthTitle = new Intl.DateTimeFormat("vi-VN", { month: "long", year: "numeric" }).format(monthCursor);

  return (
    <section className="stack">
      <div className="month-nav">
        <IconButton title="Tháng trước" onClick={() => setMonthCursor(addMonths(monthCursor, -1))}>
          <ChevronLeft size={18} />
        </IconButton>
        <strong>{monthTitle}</strong>
        <Button variant="secondary" onClick={() => setMonthCursor(firstDayOfMonth(new Date()))}>
          Tháng này
        </Button>
        <IconButton title="Tháng sau" onClick={() => setMonthCursor(addMonths(monthCursor, 1))}>
          <ChevronRight size={18} />
        </IconButton>
      </div>
      <div className="month-grid">
        {["T2", "T3", "T4", "T5", "T6", "T7", "CN"].map((label) => (
          <div key={label} className="weekday">
            {label}
          </div>
        ))}
        {cells.map((date, index) => {
          const dayEvents = date ? monthEvents.filter((event) => sameDay(new Date(event.startAtMillis), date)) : [];
          return (
            <div key={index} className={`month-cell ${date ? "" : "muted"} ${dayEvents.length ? "has-event" : ""}`}>
              {date && <strong>{date.getDate()}</strong>}
              {dayEvents.length > 0 && (
                <>
                  <span>{dayEvents.length} mục</span>
                  <p>{dayEvents[0].title}</p>
                </>
              )}
            </div>
          );
        })}
      </div>
      <h2 className="month-list-title">Sự kiện trong {monthTitle}</h2>
      {monthEvents.length ? (
        <EventList events={monthEvents} onToggleDone={onToggleDone} onEditPersonal={onEditPersonal} onDeletePersonal={onDeletePersonal} />
      ) : (
        <EmptyState title="Tháng này chưa có deadline" text="Không có mục nào trong bộ lọc hiện tại." />
      )}
    </section>
  );
}

function EventCard({
  event,
  onToggleDone,
  onEditPersonal,
  onDeletePersonal
}: {
  event: EventWithState;
  onToggleDone: (event: EventWithState) => void;
  onEditPersonal: (event: DeadlineEvent) => void;
  onDeletePersonal: (event: DeadlineEvent) => void;
}) {
  const accent = accentClass(event);
  const date = new Date(event.startAtMillis);
  const description = cleanDescription(event);
  const course = eventCourse(event);

  async function copyInfo() {
    const text = [`${eventKind(event)}: ${event.title}`, course ? `Môn/Lớp: ${course}` : "", `${timeLabel(event)}: ${formatShort(event.startAtMillis)}`, event.sourceUrl || ""]
      .filter(Boolean)
      .join("\n");
    await navigator.clipboard.writeText(text);
  }

  return (
    <article className={`event-card ${event.done ? "done" : ""}`}>
      <div className={`date-badge ${accent}`}>
        <strong>{String(date.getDate()).padStart(2, "0")}</strong>
        <span>{String(date.getMonth() + 1).padStart(2, "0")}/{date.getFullYear()}</span>
        <b>{date.toLocaleTimeString("vi-VN", { hour: "2-digit", minute: "2-digit" })}</b>
      </div>
      <div className="event-info">
        <div className="meta-row">
          <span className={`kind-chip ${accent}`}>{eventKind(event)}</span>
          {event.done && <span className="kind-chip green">Đã xong</span>}
          {course && <span className="course-chip">{course}</span>}
        </div>
        <h2>{event.title}</h2>
        {description && <p className="description">{description}</p>}
        <p className="time-line">{timeLabel(event)}: {formatFull(event.startAtMillis)}</p>
        <p className={`remain ${accent}`}>{remainText(event.startAtMillis)}</p>
        <div className="event-actions">
          {event.source === "PERSONAL" ? (
            <>
              <Button variant="secondary" icon={<Pencil size={15} />} onClick={() => onEditPersonal(event)}>
                Sửa
              </Button>
              <Button variant="danger" icon={<Trash2 size={15} />} onClick={() => onDeletePersonal(event)}>
                Xóa
              </Button>
            </>
          ) : (
            <>
              <Button variant="secondary" icon={<ExternalLink size={15} />} onClick={() => event.sourceUrl && window.open(event.sourceUrl, "_blank")}>
                Mở
              </Button>
              <Button variant="secondary" icon={<Copy size={15} />} onClick={copyInfo}>
                Copy
              </Button>
            </>
          )}
          <Button variant={event.done ? "danger" : "primary"} icon={<Check size={15} />} onClick={() => onToggleDone(event)}>
            {event.done ? "Bỏ xong" : "Xong"}
          </Button>
        </div>
      </div>
    </article>
  );
}

function SettingsTab({
  snapshot,
  busy,
  customReminder,
  setCustomReminder,
  summaryTime,
  setSummaryTime,
  emailDraft,
  setEmailDraft,
  onEnablePush,
  onTestPush,
  onUpdateSettings,
  onSaveEmail,
  onTestEmail
}: {
  snapshot: AppSnapshot;
  busy: boolean;
  customReminder: { days: string; hours: string; minutes: string };
  setCustomReminder: (value: { days: string; hours: string; minutes: string }) => void;
  summaryTime: string;
  setSummaryTime: (value: string) => void;
  emailDraft: string;
  setEmailDraft: (value: string) => void;
  onEnablePush: () => void;
  onTestPush: () => void;
  onUpdateSettings: (settings: UserSettings) => Promise<void>;
  onSaveEmail: (email: string, enabled: boolean) => Promise<void>;
  onTestEmail: () => Promise<void>;
}) {
  const settings = snapshot.settings;
  const reminderOptions = allReminderOptions(settings.customReminderOffsetsMinutes);
  const pushSupported = isPushSupported();

  function nextSettings(patch: Partial<UserSettings>) {
    return { ...settings, ...patch };
  }

  async function toggleReminder(minutes: number) {
    const selected = settings.reminderOffsetsMinutes.includes(minutes);
    const next = selected
      ? settings.reminderOffsetsMinutes.filter((value) => value !== minutes)
      : [...settings.reminderOffsetsMinutes, minutes];
    if (next.length === 0) return;
    await onUpdateSettings(nextSettings({ reminderOffsetsMinutes: sanitizeReminderOffsets(next) }));
  }

  async function addCustomReminder() {
    const total =
      (Number(customReminder.days) || 0) * 24 * 60 +
      (Number(customReminder.hours) || 0) * 60 +
      (Number(customReminder.minutes) || 0);
    if (total <= 0 || total > 30 * 24 * 60) return;
    const custom = [...new Set([...settings.customReminderOffsetsMinutes, total])].sort((a, b) => b - a);
    await onUpdateSettings(
      nextSettings({
        customReminderOffsetsMinutes: custom,
        reminderOffsetsMinutes: sanitizeReminderOffsets([...settings.reminderOffsetsMinutes, total])
      })
    );
    setCustomReminder({ days: "", hours: "", minutes: "" });
  }

  async function removeCustomReminder(minutes: number) {
    const custom = settings.customReminderOffsetsMinutes.filter((value) => value !== minutes);
    const selected = settings.reminderOffsetsMinutes.filter((value) => value !== minutes);
    await onUpdateSettings(
      nextSettings({
        customReminderOffsetsMinutes: custom,
        reminderOffsetsMinutes: sanitizeReminderOffsets(selected)
      })
    );
  }

  async function addSummaryTime() {
    const [hour, minute] = summaryTime.split(":").map(Number);
    const value = hour * 60 + minute;
    const times = [...new Set([...settings.dailySummaryTimes, value])].sort((a, b) => a - b).slice(0, 6);
    await onUpdateSettings(nextSettings({ dailySummaryEnabled: true, dailySummaryTimes: times, dailySummaryDaysMask: settings.dailySummaryDaysMask || ALL_DAYS_MASK }));
  }

  async function removeSummaryTime(minutes: number) {
    const times = settings.dailySummaryTimes.filter((value) => value !== minutes);
    await onUpdateSettings(nextSettings({ dailySummaryTimes: times, dailySummaryEnabled: times.length > 0 && settings.dailySummaryDaysMask !== 0 }));
  }

  async function toggleDay(bitIndex: number) {
    const bit = 1 << bitIndex;
    const current = settings.dailySummaryEnabled ? settings.dailySummaryDaysMask : 0;
    const mask = current & bit ? current & ~bit : current | bit;
    await onUpdateSettings(nextSettings({ dailySummaryDaysMask: mask, dailySummaryEnabled: mask !== 0 && settings.dailySummaryTimes.length > 0 }));
  }

  return (
    <section className="stack">
      <section className="card stack">
        <div className="section-title">
          <h2>Cài đặt Web Push</h2>
          <span className={`pill ${snapshot.push.enabled ? "ok" : "warn"}`}>{snapshot.push.enabled ? "Đã bật" : "Chưa bật"}</span>
        </div>
        <div className="notice">
          <Smartphone size={18} />
          <span>iPhone cần mở bằng icon đã thêm vào màn hình chính. Android/desktop có thể bật trực tiếp nếu trình duyệt hỗ trợ.</span>
        </div>
        <div className="button-grid">
          <Button disabled={busy || !pushSupported} icon={<Bell size={16} />} onClick={onEnablePush}>
            Bật Web Push
          </Button>
          <Button disabled={busy} variant="secondary" icon={<Send size={16} />} onClick={onTestPush}>
            Gửi test
          </Button>
        </div>
      </section>

      <section className="card stack">
        <h2>Cài đặt thông báo</h2>
        <p className="muted-text">
          Tổng hợp: {settings.dailySummaryEnabled ? `bật lúc ${settings.dailySummaryTimes.map(minutesText).join(", ")}` : "đang tắt"}.
        </p>
        <h3>Khung giờ tổng hợp</h3>
        <div className="chip-row">
          {settings.dailySummaryTimes.map((minutes) => (
            <button key={minutes} className="removable-chip" onClick={() => removeSummaryTime(minutes)}>
              {minutesText(minutes)}
              <X size={13} />
            </button>
          ))}
        </div>
        <div className="inline-form">
          <input type="time" value={summaryTime} onChange={(event) => setSummaryTime(event.target.value)} />
          <Button icon={<Plus size={16} />} onClick={addSummaryTime}>
            Thêm giờ
          </Button>
          <Button variant="danger" onClick={() => onUpdateSettings(nextSettings({ dailySummaryEnabled: false }))}>
            Tắt
          </Button>
        </div>

        <h3>Ngày thông báo</h3>
        <div className="filter-row">
          {dayOptions.map(([label, index]) => {
            const selected = settings.dailySummaryEnabled && (settings.dailySummaryDaysMask & (1 << index)) !== 0;
            return (
              <button key={label} className={selected ? "selected" : ""} onClick={() => toggleDay(index)}>
                {label}
              </button>
            );
          })}
        </div>

        <h3>Mốc nhắc trước hạn</h3>
        <div className="filter-row wrap">
          {reminderOptions.map((minutes) => {
            const selected = settings.reminderOffsetsMinutes.includes(minutes);
            return (
              <button key={minutes} className={selected ? "selected" : ""} onClick={() => toggleReminder(minutes)}>
                {reminderOptionLabel(minutes)}
              </button>
            );
          })}
        </div>

        {settings.customReminderOffsetsMinutes.length > 0 && (
          <div className="chip-row">
            {settings.customReminderOffsetsMinutes.map((minutes) => (
              <button key={minutes} className="removable-chip" onClick={() => removeCustomReminder(minutes)}>
                {reminderOptionLabel(minutes)}
                <X size={13} />
              </button>
            ))}
          </div>
        )}

        <div className="custom-reminder">
          <input inputMode="numeric" placeholder="Ngày" value={customReminder.days} onChange={(event) => setCustomReminder({ ...customReminder, days: event.target.value })} />
          <input inputMode="numeric" placeholder="Giờ" value={customReminder.hours} onChange={(event) => setCustomReminder({ ...customReminder, hours: event.target.value })} />
          <input inputMode="numeric" placeholder="Phút" value={customReminder.minutes} onChange={(event) => setCustomReminder({ ...customReminder, minutes: event.target.value })} />
          <Button variant="secondary" icon={<Plus size={16} />} onClick={addCustomReminder}>
            Thêm mốc
          </Button>
        </div>
      </section>

      <section className="card stack">
        <div className="section-title">
          <h2>Cảnh báo qua Email</h2>
          <span className={`pill ${snapshot.email.enabled ? "ok" : ""}`}>{snapshot.email.enabled ? "Đang bật" : "Đang tắt"}</span>
        </div>
        <input className="text-input" type="email" placeholder="student@hcmute.edu.vn" value={emailDraft} onChange={(event) => setEmailDraft(event.target.value)} />
        <div className="button-grid">
          <Button icon={<Mail size={16} />} onClick={() => onSaveEmail(emailDraft, true)}>
            Lưu & bật email
          </Button>
          <Button variant="danger" onClick={() => onSaveEmail(emailDraft, false)}>
            Tắt email
          </Button>
          <Button variant="secondary" icon={<Send size={16} />} onClick={onTestEmail}>
            Gửi email test
          </Button>
        </div>
      </section>
    </section>
  );
}

function GuideTab() {
  async function copyExportUrl() {
    await navigator.clipboard.writeText(MOODLE_EXPORT_URL);
  }

  return (
    <section className="stack">
      <section className="card stack guide-card">
        <div className="guide-head">
          <img src="/icons/hcmute_logo.png" alt="" />
          <div>
            <h2>Hướng dẫn nhanh</h2>
            <p>Lấy Calendar URL Moodle rồi dán vào tab Lịch để backend tự sync và gửi thông báo.</p>
          </div>
        </div>

        <ol className="guide-steps">
          <li>Copy link trang xuất lịch Moodle ở bên dưới.</li>
          <li>Dán vào trình duyệt đã đăng nhập UTExLMS.</li>
          <li>Chọn lịch, bấm Lấy địa chỉ mạng của lịch.</li>
          <li>Copy Calendar URL dạng export_execute.php.</li>
          <li>Dán URL đó vào tab Lịch rồi bấm Lưu & đồng bộ.</li>
        </ol>

        <div className="button-grid">
          <Button variant="secondary" icon={<ExternalLink size={16} />} onClick={() => window.open(MOODLE_EXPORT_URL, "_blank")}>
            Mở Moodle
          </Button>
          <Button variant="danger" icon={<ExternalLink size={16} />} onClick={() => window.open(GITHUB_GUIDE_URL, "_blank")}>
            Xem GitHub
          </Button>
        </div>

        <div className="export-url-row">
          <span>{MOODLE_EXPORT_URL}</span>
          <Button variant="secondary" icon={<Copy size={16} />} onClick={copyExportUrl}>
            Copy
          </Button>
        </div>

        <p className="support-line">Hỗ trợ: {SUPPORT_CONTACT}</p>
      </section>

      <section className="card stack guide-card">
        <h2>Ghi chú iPhone</h2>
        <p className="muted-text">
          iOS chỉ nhận Web Push ổn khi web đã được thêm vào màn hình chính và mở từ icon đó. Sau khi thêm, vào tab Thông báo để bật Web Push.
        </p>
      </section>
    </section>
  );
}

function InstallDialog({ onClose }: { onClose: () => void }) {
  const ios = isIosDevice();
  return (
    <div className="dialog-backdrop" role="dialog" aria-modal="true">
      <section className="dialog card stack">
        <div className="section-title">
          <h2>Cài UTE Notice</h2>
          <IconButton title="Đóng" onClick={onClose}>
            <X size={18} />
          </IconButton>
        </div>
        <div className="notice">
          <Download size={18} />
          <span>{ios ? "Trên iPhone/iPad, Apple yêu cầu thêm web app từ nút Chia sẻ của Safari." : "Nếu trình duyệt chưa mở hộp thoại cài, hãy dùng menu của trình duyệt để thêm app."}</span>
        </div>
        <ol className="install-steps">
          {ios ? (
            <>
              <li>Mở trang bằng Safari.</li>
              <li>Bấm nút Chia sẻ.</li>
              <li>Chọn Thêm vào Màn hình chính, rồi mở UTE Notice từ icon mới.</li>
            </>
          ) : (
            <>
              <li>Mở menu Chrome hoặc Edge.</li>
              <li>Chọn Cài đặt ứng dụng hoặc Thêm vào màn hình chính.</li>
              <li>Mở UTE Notice từ icon vừa cài để nhận trải nghiệm giống app.</li>
            </>
          )}
        </ol>
        <Button variant="secondary" type="button" onClick={onClose}>
          Đã hiểu
        </Button>
      </section>
    </div>
  );
}

function PersonalDialog({
  event,
  onClose,
  onSave
}: {
  event: DeadlineEvent | null;
  onClose: () => void;
  onSave: (input: { title: string; startAtMillis: number; description?: string | null }) => Promise<void>;
}) {
  const [title, setTitle] = useState(event?.title ?? "");
  const [description, setDescription] = useState(event?.description ?? "");
  const [time, setTime] = useState(toDateInputValue(event?.startAtMillis ?? Date.now() + 60 * 60 * 1000));

  return (
    <div className="dialog-backdrop" role="dialog" aria-modal="true">
      <form
        className="dialog card stack"
        onSubmit={(submitEvent) => {
          submitEvent.preventDefault();
          onSave({ title, description, startAtMillis: fromDateInputValue(time) });
        }}
      >
        <div className="section-title">
          <h2>{event ? "Sửa deadline cá nhân" : "Thêm deadline cá nhân"}</h2>
          <IconButton title="Đóng" onClick={onClose}>
            <X size={18} />
          </IconButton>
        </div>
        <input className="text-input" value={title} onChange={(changeEvent) => setTitle(changeEvent.target.value)} placeholder="Tên deadline cá nhân" />
        <textarea className="url-input small" value={description} onChange={(changeEvent) => setDescription(changeEvent.target.value)} placeholder="Ghi chú / môn / việc cần làm" />
        <input className="text-input" type="datetime-local" value={time} onChange={(changeEvent) => setTime(changeEvent.target.value)} />
        <div className="button-grid">
          <Button icon={<Save size={16} />} type="submit">
            Lưu
          </Button>
          <Button variant="secondary" type="button" onClick={onClose}>
            Hủy
          </Button>
        </div>
      </form>
    </div>
  );
}

function Button({
  children,
  icon,
  variant = "primary",
  ...props
}: React.ButtonHTMLAttributes<HTMLButtonElement> & {
  icon?: React.ReactNode;
  variant?: "primary" | "secondary" | "danger" | "ghost";
}) {
  return (
    <button {...props} className={`btn ${variant} ${props.className ?? ""}`.trim()}>
      {icon}
      <span>{children}</span>
    </button>
  );
}

function IconButton({ children, title, onClick }: { children: React.ReactNode; title: string; onClick: () => void }) {
  return (
    <button className="icon-btn" title={title} aria-label={title} onClick={onClick} type="button">
      {children}
    </button>
  );
}

function StatusBox({ text, type }: { text: string; type: "info" | "success" | "error" }) {
  return <div className={`status ${type}`}>{text}</div>;
}

function EmptyState({ title, text }: { title: string; text: string }) {
  return (
    <section className="card empty">
      <img src="/icons/hcmute_logo.png" alt="" />
      <h2>{title}</h2>
      <p>{text}</p>
    </section>
  );
}

function accentClass(event: DeadlineEvent): "red" | "amber" | "green" | "blue" {
  const diff = event.startAtMillis - Date.now();
  if (diff <= 24 * 60 * 60 * 1000) return "red";
  if (diff <= 3 * 24 * 60 * 60 * 1000) return "amber";
  if (broadGroup(event) === "SUBMISSION") return "green";
  if (broadGroup(event) === "EXAM") return "red";
  return "blue";
}

function minutesText(minutes: number): string {
  return `${String(Math.floor(minutes / 60)).padStart(2, "0")}:${String(minutes % 60).padStart(2, "0")}`;
}

function firstDayOfMonth(date: Date): Date {
  return new Date(date.getFullYear(), date.getMonth(), 1);
}

function addMonths(date: Date, months: number): Date {
  return new Date(date.getFullYear(), date.getMonth() + months, 1);
}

function sameMonth(a: Date, b: Date): boolean {
  return a.getFullYear() === b.getFullYear() && a.getMonth() === b.getMonth();
}

function sameDay(a: Date, b: Date): boolean {
  return sameMonth(a, b) && a.getDate() === b.getDate();
}

function monthCells(month: Date): Array<Date | null> {
  const first = firstDayOfMonth(month);
  const offset = (first.getDay() + 6) % 7;
  const days = new Date(first.getFullYear(), first.getMonth() + 1, 0).getDate();
  const cells: Array<Date | null> = [];
  for (let i = 0; i < offset; i += 1) cells.push(null);
  for (let day = 1; day <= days; day += 1) cells.push(new Date(first.getFullYear(), first.getMonth(), day));
  while (cells.length < 42) cells.push(null);
  return cells;
}
