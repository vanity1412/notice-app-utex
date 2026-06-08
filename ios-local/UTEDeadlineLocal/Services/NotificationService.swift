import CryptoKit
import Foundation
import UserNotifications

final class NotificationService {
    static let shared = NotificationService()

    private let center = UNUserNotificationCenter.current()
    private let store = EventStore.shared
    private let localTimeZone = TimeZone(identifier: "Asia/Ho_Chi_Minh") ?? .current
    private let maxReminderNotifications = 55
    private let pendingDeadlineLifetime: TimeInterval = 3 * 24 * 60 * 60

    private lazy var timeFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "vi_VN")
        formatter.timeZone = localTimeZone
        formatter.dateFormat = "HH:mm dd/MM/yyyy"
        return formatter
    }()

    private init() {}

    func setDelegate(_ delegate: UNUserNotificationCenterDelegate) {
        center.delegate = delegate
    }

    func requestAuthorization() async -> Bool {
        do {
            return try await center.requestAuthorization(options: [.alert, .badge, .sound])
        } catch {
            return false
        }
    }

    func isAuthorized() async -> Bool {
        let settings = await center.notificationSettings()
        return settings.authorizationStatus == .authorized || settings.authorizationStatus == .provisional
    }

    @discardableResult
    func notifyNewDeadline(_ event: DeadlineEvent) async -> Bool {
        guard !store.isDone(event.id) else { return true }
        await showNow(
            identifier: stableIdentifier("new-\(event.id)"),
            title: "Lịch mới: \(EventLabels.kind(for: event))",
            body: eventMessage(event),
            url: event.sourceURL,
            withSound: true
        )
    }

    @discardableResult
    func notifyChangedDeadline(_ event: DeadlineEvent) async -> Bool {
        guard !store.isDone(event.id) else { return true }
        await showNow(
            identifier: stableIdentifier("changed-\(event.id)"),
            title: "Thay đổi: \(EventLabels.kind(for: event))",
            body: "Giáo viên đã cập nhật lịch này.\n\(eventMessage(event))",
            url: event.sourceURL,
            withSound: true
        )
    }

    @discardableResult
    func notifyInitialSummary(count: Int) async -> Bool {
        await showNow(
            identifier: stableIdentifier("summary-first-sync"),
            title: "UTE Notice đã sẵn sàng",
            body: "Đã tìm thấy \(count) deadline sắp tới. App sẽ nhắc khi gần tới hạn.",
            url: nil,
            withSound: false
        )
    }

    @discardableResult
    func notifyTest() async -> Bool {
        await showNow(
            identifier: stableIdentifier("test-notification"),
            title: "Test thông báo UTE Notice",
            body: "Nếu bạn thấy thông báo này thì quyền thông báo đang hoạt động.\nMốc nhắc đang bật: \(activeReminderText()) trước hạn.",
            url: nil,
            withSound: true
        )
    }

    @discardableResult
    func notifyDailySummary(events: [DeadlineEvent], referenceDate: Date = Date()) async -> Bool {
        guard let content = dailySummaryContent(events: events, referenceDate: referenceDate) else {
            return false
        }
        return await showNow(
            identifier: stableIdentifier("daily-summary"),
            title: content.title,
            body: content.body,
            url: nil,
            withSound: false
        )
    }

    func flushPendingDeadlineNotifications() async -> Int {
        guard await isAuthorized() else { return 0 }
        let pending = store.loadPendingNotifications()
        guard !pending.isEmpty else { return 0 }

        var remaining: [PendingDeadlineNotification] = []
        var sent = 0
        let now = Date()
        for item in pending {
            if let timestamp = item.timestamp, now.timeIntervalSince(timestamp) > pendingDeadlineLifetime {
                continue
            }
            if store.isDone(item.event.id) {
                continue
            }

            let shown: Bool
            switch item.kind {
            case .new:
                shown = await notifyNewDeadline(item.event)
            case .changed:
                shown = await notifyChangedDeadline(item.event)
            }
            if shown {
                sent += 1
            } else {
                remaining.append(item)
            }
        }
        store.savePendingNotifications(remaining)
        return sent
    }

    func cancelScheduledNotifications() {
        center.removeAllPendingNotificationRequests()
    }

    func scheduleReminders(for events: [DeadlineEvent]) async {
        let existing = await center.pendingNotificationRequests()
        let reminderIds = existing.map(\.identifier).filter { $0.hasPrefix("reminder-") }
        center.removePendingNotificationRequests(withIdentifiers: reminderIds)

        guard await isAuthorized() else { return }

        let now = Date()
        let plans = events
            .filter { !store.isDone($0.id) }
            .flatMap { event in
                store.reminderOffsetsMinutes().compactMap { minutes -> ReminderPlan? in
                    let triggerDate = event.startAt.addingTimeInterval(TimeInterval(-minutes * 60))
                    let delay = triggerDate.timeIntervalSince(now)
                    guard delay > 60, event.startAt > now else { return nil }
                    return ReminderPlan(
                        identifier: "reminder-\(stableIdentifier("\(event.id)-\(minutes)"))",
                        event: event,
                        leadText: "\(store.reminderOptionLabel(minutes)) trước hạn",
                        triggerDate: triggerDate
                    )
                }
            }
            .sorted { $0.triggerDate < $1.triggerDate }
            .prefix(maxReminderNotifications)

        for plan in plans {
            let content = UNMutableNotificationContent()
            content.title = "Cảnh báo: \(plan.leadText)"
            content.body = eventMessage(plan.event)
            content.sound = .default
            if let url = plan.event.sourceURL {
                content.userInfo = ["url": url]
            }
            let delay = max(60, plan.triggerDate.timeIntervalSince(Date()))
            let trigger = UNTimeIntervalNotificationTrigger(timeInterval: delay, repeats: false)
            let request = UNNotificationRequest(identifier: plan.identifier, content: content, trigger: trigger)
            try? await center.add(request)
        }
    }

    func scheduleDailySummaries(events: [DeadlineEvent]) async {
        let existing = await center.pendingNotificationRequests()
        let summaryIds = existing.map(\.identifier).filter { $0.hasPrefix("daily-summary-") }
        center.removePendingNotificationRequests(withIdentifiers: summaryIds)

        guard store.isDailySummaryEnabled, await isAuthorized() else { return }

        for (index, date) in nextDailySummaryDates(limit: 7).enumerated() {
            guard let contentText = dailySummaryContent(events: events, referenceDate: date) else {
                continue
            }
            let content = UNMutableNotificationContent()
            content.title = contentText.title
            content.body = contentText.body
            content.sound = nil

            var calendar = Calendar(identifier: .gregorian)
            calendar.timeZone = localTimeZone
            let components = calendar.dateComponents([.year, .month, .day, .hour, .minute], from: date)
            let trigger = UNCalendarNotificationTrigger(dateMatching: components, repeats: false)
            let request = UNNotificationRequest(
                identifier: "daily-summary-\(stableIdentifier("\(index)-\(date.timeIntervalSince1970)"))",
                content: content,
                trigger: trigger
            )
            try? await center.add(request)
        }
    }

    private func showNow(identifier: String, title: String, body: String, url: String?, withSound: Bool) async -> Bool {
        guard await isAuthorized() else { return false }
        let content = UNMutableNotificationContent()
        content.title = title
        content.body = body
        content.sound = withSound ? .default : nil
        if let url {
            content.userInfo = ["url": url]
        }

        do {
            try await center.add(UNNotificationRequest(identifier: identifier, content: content, trigger: nil))
            return true
        } catch {
            return false
        }
    }

    private func eventMessage(_ event: DeadlineEvent) -> String {
        var lines: [String] = []
        lines.append("\(EventLabels.kind(for: event)): \(event.title)")
        if let course = EventLabels.course(for: event) {
            lines.append("Môn/Lớp: \(course)")
        }
        if let description = EventLabels.cleanDescription(for: event) {
            lines.append(description)
        }
        lines.append("\(EventLabels.timeLabel(for: event)): \(timeFormatter.string(from: event.startAt))")
        lines.append("Mốc nhắc đang bật: \(activeReminderText()) trước hạn.")
        return lines.joined(separator: "\n")
    }

    private func activeReminderText() -> String {
        let text = store.reminderOffsetsMinutes()
            .map { store.reminderOptionLabel($0) }
            .joined(separator: ", ")
        return text.isEmpty ? "1 giờ" : text
    }

    private func dailySummaryContent(events: [DeadlineEvent], referenceDate: Date) -> (title: String, body: String)? {
        let upperBound = referenceDate.addingTimeInterval(3 * 24 * 60 * 60)
        let summaryEvents = events
            .filter { !store.isDone($0.id) }
            .filter { $0.startAt >= referenceDate && $0.startAt <= upperBound }
            .sorted { $0.startAt < $1.startAt }

        guard !summaryEvents.isEmpty else { return nil }

        let lines = summaryEvents.prefix(3).map { event in
            "- \(EventLabels.kind(for: event)): \(event.title) - \(timeFormatter.string(from: event.startAt))"
        }
        return (
            title: "Nhắc lịch sắp tới",
            body: "Có \(summaryEvents.count) mục sắp tới trong 3 ngày.\n\(lines.joined(separator: "\n"))"
        )
    }

    private func nextDailySummaryDates(limit: Int) -> [Date] {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = localTimeZone

        let now = Date()
        var dates: [Date] = []
        for offset in 0..<14 where dates.count < limit {
            guard let day = calendar.date(byAdding: .day, value: offset, to: now),
                  let candidate = calendar.date(
                    bySettingHour: store.dailySummaryHour,
                    minute: store.dailySummaryMinute,
                    second: 0,
                    of: day
                  ),
                  candidate > now,
                  store.isDailySummaryAllowedToday(date: candidate)
            else {
                continue
            }
            dates.append(candidate)
        }
        return dates
    }

    private func stableIdentifier(_ text: String) -> String {
        let digest = SHA256.hash(data: Data(text.utf8))
        return digest.prefix(8).map { String(format: "%02x", $0) }.joined()
    }

    private struct ReminderPlan {
        let identifier: String
        let event: DeadlineEvent
        let leadText: String
        let triggerDate: Date
    }
}
