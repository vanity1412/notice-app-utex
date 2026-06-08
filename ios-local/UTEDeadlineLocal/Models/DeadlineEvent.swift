import Foundation

struct DeadlineEvent: Identifiable, Codable, Hashable {
    let id: String
    let title: String
    let startAtMillis: Int64
    let sourceURL: String?
    let rawType: String?
    let description: String?

    var startAt: Date {
        Date(timeIntervalSince1970: TimeInterval(startAtMillis) / 1000.0)
    }
}

struct SyncResult {
    let ok: Bool
    let message: String
    let totalEvents: Int
    let changedEvents: Int
    let retryable: Bool

    static func failure(_ message: String, retryable: Bool = false) -> SyncResult {
        SyncResult(ok: false, message: message, totalEvents: 0, changedEvents: 0, retryable: retryable)
    }
}

struct PendingDeadlineNotification: Codable, Hashable {
    enum Kind: String, Codable {
        case new
        case changed
    }

    let key: String
    let kind: Kind
    let event: DeadlineEvent
    let timestamp: Date?

    init(key: String, kind: Kind, event: DeadlineEvent, timestamp: Date? = Date()) {
        self.key = key
        self.kind = kind
        self.event = event
        self.timestamp = timestamp
    }
}

enum EventLabels {
    private static let locale = Locale(identifier: "vi_VN")

    static func kind(for event: DeadlineEvent) -> String {
        let text = searchable("\(event.title) \(event.description ?? "")")
        let title = searchable(event.title)

        if containsAny(text, ["nop", "bai nop", "assignment", "lab", "tieu luan", "project"]) {
            return "Bài nộp"
        }
        if containsAny(text, ["lich thi", "thi ", " exam", "exam ", "midterm", "final"]) {
            return "Thi"
        }
        if containsAny(text, ["online test", "quiz", "test", "kiem tra"]) {
            if containsAny(title, ["bat dau", "mo bai", "open"]) {
                return "Bắt đầu kiểm tra"
            }
            if containsAny(title, ["ket thuc", "het han", "close", "closing"]) {
                return "Hết giờ kiểm tra"
            }
            return "Kiểm tra"
        }
        if containsAny(text, ["deadline", "due", "toi han", "den han", "han chot", "ket thuc"]) {
            return "Deadline"
        }
        return "Lịch Moodle"
    }

    static func broadKind(for event: DeadlineEvent) -> String {
        let value = kind(for: event)
        return value.contains("kiểm tra") || value.contains("Kiểm tra") ? "Kiểm tra" : value
    }

    static func course(for event: DeadlineEvent) -> String? {
        event.rawType?.trimmingCharacters(in: .whitespacesAndNewlines).nilIfBlank
    }

    static func cleanDescription(for event: DeadlineEvent) -> String? {
        event.description?
            .split(whereSeparator: \.isNewline)
            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty }
            .joined(separator: " ")
            .nilIfBlank
    }

    static func timeLabel(for event: DeadlineEvent) -> String {
        switch kind(for: event) {
        case "Bắt đầu kiểm tra":
            return "Bắt đầu"
        case "Hết giờ kiểm tra":
            return "Kết thúc"
        default:
            return "Hạn"
        }
    }

    static func searchable(_ text: String) -> String {
        text
            .lowercased(with: locale)
            .folding(options: [.diacriticInsensitive, .caseInsensitive], locale: locale)
            .replacingOccurrences(of: "đ", with: "d")
            .replacingOccurrences(of: "Đ", with: "d")
    }

    private static func containsAny(_ text: String, _ keywords: [String]) -> Bool {
        keywords.contains { text.contains($0) }
    }
}

extension String {
    var nilIfBlank: String? {
        let trimmed = trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty ? nil : trimmed
    }
}
