import CryptoKit
import Foundation

enum IcsParser {
    private static let localTimeZone = TimeZone(identifier: "Asia/Ho_Chi_Minh") ?? .current
    private static let localCalendar: Calendar = {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = localTimeZone
        return calendar
    }()

    static func parse(_ text: String) -> [DeadlineEvent] {
        let lines = unfoldLines(text)
        var rawEvents: [[String: String]] = []
        var current: [String: String]?

        for line in lines {
            switch line.trimmingCharacters(in: .whitespacesAndNewlines) {
            case "BEGIN:VEVENT":
                current = [:]
            case "END:VEVENT":
                if let current {
                    rawEvents.append(current)
                }
                current = nil
            default:
                guard current != nil, let colon = line.firstIndex(of: ":") else { continue }
                let keyPart = String(line[..<colon])
                let value = decodeIcsText(String(line[line.index(after: colon)...]))
                let keyParts = keyPart.split(separator: ";", maxSplits: 1, omittingEmptySubsequences: false)
                let key = keyParts.first.map { String($0).uppercased() } ?? ""
                let params = keyParts.count > 1 ? String(keyParts[1]) : ""

                if ["UID", "SUMMARY", "DESCRIPTION", "URL", "DTSTART", "DTEND", "DUE", "CATEGORIES"].contains(key) {
                    current?[key] = value
                    if !params.isEmpty {
                        current?["\(key)_PARAMS"] = params
                    }
                }
            }
        }

        let oneDayAgo = Date().addingTimeInterval(-24 * 60 * 60)
        var seen = Set<String>()

        return rawEvents.compactMap { raw -> DeadlineEvent? in
            let title = raw["SUMMARY"]?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
            guard !title.isEmpty else { return nil }
            guard looksLikeDeadline(title: title, description: raw["DESCRIPTION"] ?? "", categories: raw["CATEGORIES"] ?? "") else {
                return nil
            }

            guard let time = parseIcsDate(raw["DUE"], params: raw["DUE_PARAMS"])
                ?? parseIcsDate(raw["DTEND"], params: raw["DTEND_PARAMS"])
                ?? parseIcsDate(raw["DTSTART"], params: raw["DTSTART_PARAMS"])
            else {
                return nil
            }

            guard time >= oneDayAgo else { return nil }

            let millis = Int64((time.timeIntervalSince1970 * 1000.0).rounded())
            let id = raw["UID"]?.nilIfBlank ?? sha256("\(title)|\(millis)|\(raw["URL"] ?? "")")
            guard seen.insert(id).inserted else { return nil }

            return DeadlineEvent(
                id: id,
                title: title,
                startAtMillis: millis,
                sourceURL: raw["URL"]?.nilIfBlank,
                rawType: raw["CATEGORIES"]?.nilIfBlank,
                description: raw["DESCRIPTION"]?.nilIfBlank
            )
        }
        .sorted { $0.startAtMillis < $1.startAtMillis }
    }

    private static func unfoldLines(_ text: String) -> [String] {
        var result: [String] = []
        text
            .replacingOccurrences(of: "\r\n", with: "\n")
            .replacingOccurrences(of: "\r", with: "\n")
            .split(separator: "\n", omittingEmptySubsequences: false)
            .map { String($0) }
            .forEach { raw in
                if (raw.hasPrefix(" ") || raw.hasPrefix("\t")), let last = result.indices.last {
                    result[last] += String(raw.dropFirst())
                } else {
                    result.append(raw)
                }
            }
        return result
    }

    private static func parseIcsDate(_ value: String?, params: String?) -> Date? {
        guard let value = value?.trimmingCharacters(in: .whitespacesAndNewlines), !value.isEmpty else {
            return nil
        }

        if value.count == 8 || params?.range(of: "VALUE=DATE", options: .caseInsensitive) != nil {
            guard let date = dateFormatter("yyyyMMdd", timeZone: localTimeZone).date(from: String(value.prefix(8))) else {
                return nil
            }
            return localCalendar.date(bySettingHour: 23, minute: 59, second: 0, of: date)
        }

        if value.uppercased().hasSuffix("Z") {
            let utcTimeZone = TimeZone(secondsFromGMT: 0) ?? localTimeZone
            return dateFormatter("yyyyMMdd'T'HHmmss'Z'", timeZone: utcTimeZone).date(from: value)
        }

        let timeZone = timeZoneFromParams(params) ?? localTimeZone
        let compactValue = String(value.prefix(15))
        return dateFormatter("yyyyMMdd'T'HHmmss", timeZone: timeZone).date(from: compactValue)
            ?? ISO8601DateFormatter().date(from: value)
    }

    private static func timeZoneFromParams(_ params: String?) -> TimeZone? {
        guard let params else { return nil }
        let parts = params.split(separator: ";").map { String($0) }
        guard let tzid = parts.first(where: { $0.uppercased().hasPrefix("TZID=") })?.dropFirst(5) else {
            return nil
        }
        return TimeZone(identifier: String(tzid))
    }

    private static func dateFormatter(_ format: String, timeZone: TimeZone) -> DateFormatter {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.calendar = Calendar(identifier: .gregorian)
        formatter.timeZone = timeZone
        formatter.dateFormat = format
        return formatter
    }

    private static func looksLikeDeadline(title: String, description: String, categories: String) -> Bool {
        let text = EventLabels.searchable("\(title) \(description) \(categories)")
        let keywords = [
            "toi han", "den han", "nop", "bai nop", "deadline", "due",
            "quiz", "test", "kiem tra", "ket thuc", "close", "closing",
            "thi", "assignment", "lab", "tieu luan", "project"
        ]
        return keywords.contains { text.contains($0) }
    }

    private static func decodeIcsText(_ input: String) -> String {
        input
            .replacingOccurrences(of: "\\n", with: "\n")
            .replacingOccurrences(of: "\\N", with: "\n")
            .replacingOccurrences(of: "\\,", with: ",")
            .replacingOccurrences(of: "\\;", with: ";")
            .replacingOccurrences(of: "\\\\", with: "\\")
    }

    private static func sha256(_ input: String) -> String {
        let digest = SHA256.hash(data: Data(input.utf8))
        return digest.map { String(format: "%02x", $0) }.joined()
    }
}
