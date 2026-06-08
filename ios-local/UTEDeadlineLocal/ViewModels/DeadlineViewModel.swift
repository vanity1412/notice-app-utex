import Foundation
import SwiftUI
import UIKit

@MainActor
final class DeadlineViewModel: ObservableObject {
    struct SummaryDay: Identifiable {
        let bit: Int
        let label: String

        var id: Int { bit }
    }

    enum EventFilter: String, CaseIterable, Identifiable {
        case all
        case submission
        case test
        case exam

        var id: String { rawValue }

        var label: String {
            switch self {
            case .all:
                return "Tất cả"
            case .submission:
                return "Bài nộp"
            case .test:
                return "Kiểm tra"
            case .exam:
                return "Thi"
            }
        }
    }

    @Published var iCalURLInput: String = ""
    @Published var statusMessage: String = "Dán iCal URL để bắt đầu theo dõi lịch Moodle."
    @Published var statusIsError = false
    @Published var events: [DeadlineEvent] = []
    @Published var isSyncing = false
    @Published var hideDoneEvents = true
    @Published var searchText = ""
    @Published var selectedFilter: EventFilter = .all
    @Published var notificationGranted = false
    @Published var dailySummaryEnabled = false
    @Published var dailySummaryTime = Date()

    private let store = EventStore.shared
    private let notifier = NotificationService.shared

    init() {
        refreshFromStore()
        Task {
            await refreshNotificationStatus()
            await notifier.flushPendingDeadlineNotifications()
        }
    }

    var filteredEvents: [DeadlineEvent] {
        let doneIds = store.doneIds()
        let query = EventLabels.searchable(searchText.trimmingCharacters(in: .whitespacesAndNewlines))

        return events.filter { event in
            let doneOK = !hideDoneEvents || !doneIds.contains(event.id)
            let kindOK: Bool
            switch selectedFilter {
            case .all:
                kindOK = true
            case .submission:
                kindOK = EventLabels.broadKind(for: event) == "Bài nộp"
            case .test:
                kindOK = EventLabels.broadKind(for: event) == "Kiểm tra"
            case .exam:
                kindOK = EventLabels.broadKind(for: event) == "Thi"
            }
            let haystack = EventLabels.searchable("\(event.title) \(event.rawType ?? "") \(event.description ?? "") \(EventLabels.kind(for: event))")
            return doneOK && kindOK && (query.isEmpty || haystack.contains(query))
        }
    }

    var connectionSummary: String {
        store.iCalURL.isEmpty ? "Chưa kết nối Moodle" : MoodleURLValidator.mask(store.iCalURL)
    }

    var hasConnection: Bool {
        !store.iCalURL.isEmpty
    }

    var reminderOffsets: [Int] {
        store.allowedReminderMinutes
    }

    var activeReminderOffsets: [Int] {
        store.reminderOffsetsMinutes()
    }

    let dailySummaryDays: [SummaryDay] = [
        SummaryDay(bit: 0, label: "T2"),
        SummaryDay(bit: 1, label: "T3"),
        SummaryDay(bit: 2, label: "T4"),
        SummaryDay(bit: 3, label: "T5"),
        SummaryDay(bit: 4, label: "T6"),
        SummaryDay(bit: 5, label: "T7"),
        SummaryDay(bit: 6, label: "CN")
    ]

    func refreshFromStore() {
        iCalURLInput = store.iCalURL
        events = store.loadEvents()
        dailySummaryEnabled = store.isDailySummaryEnabled
        dailySummaryTime = makeTimeDate(hour: store.dailySummaryHour, minute: store.dailySummaryMinute)
        if let lastSync = store.lastSync {
            statusMessage = "Cập nhật gần nhất: \(Self.fullDateFormatter.string(from: lastSync))"
            statusIsError = false
        }
    }

    func pasteFromClipboard() {
        iCalURLInput = UIPasteboard.general.string ?? iCalURLInput
    }

    func saveAndSync() {
        let validation = MoodleURLValidator.validate(iCalURLInput)
        guard validation.ok else {
            statusMessage = validation.message
            statusIsError = true
            return
        }

        do {
            try store.setIcalURL(validation.normalizedURL)
        } catch {
            statusMessage = error.localizedDescription
            statusIsError = true
            return
        }

        iCalURLInput = validation.normalizedURL
        Task {
            await syncNow()
        }
    }

    func syncNow() async {
        guard !store.iCalURL.isEmpty else {
            statusMessage = "Bạn chưa lưu Calendar URL Moodle."
            statusIsError = true
            return
        }
        guard !isSyncing else { return }

        isSyncing = true
        statusMessage = "Đang đồng bộ lịch Moodle..."
        statusIsError = false

        let result = await DeadlineSyncService.shared.sync(notifyNew: true)
        events = store.loadEvents()
        dailySummaryEnabled = store.isDailySummaryEnabled
        statusMessage = result.message
        statusIsError = !result.ok
        isSyncing = false
        await refreshNotificationStatus()
    }

    func clearConnection() {
        store.clearConnection()
        notifier.cancelScheduledNotifications()
        BackgroundRefreshService.shared.schedule()
        iCalURLInput = ""
        events = []
        dailySummaryEnabled = false
        statusMessage = "Đã xóa kết nối Moodle."
        statusIsError = false
    }

    func requestNotifications() async {
        notificationGranted = await notifier.requestAuthorization()
        await notifier.flushPendingDeadlineNotifications()
        await notifier.scheduleReminders(for: events.filter { !store.isDone($0.id) })
        await notifier.scheduleDailySummaries(events: events)
    }

    func refreshNotificationStatus() async {
        notificationGranted = await notifier.isAuthorized()
    }

    func toggleDone(_ event: DeadlineEvent) {
        store.setDone(event.id, done: !store.isDone(event.id))
        Task {
            await notifier.scheduleReminders(for: events.filter { !store.isDone($0.id) })
            await notifier.scheduleDailySummaries(events: events)
        }
        objectWillChange.send()
    }

    func isDone(_ event: DeadlineEvent) -> Bool {
        store.isDone(event.id)
    }

    func setDailySummaryEnabled(_ enabled: Bool) {
        store.isDailySummaryEnabled = enabled
        dailySummaryEnabled = enabled
        Task {
            await notifier.scheduleDailySummaries(events: events)
        }
    }

    func setDailySummaryTime(_ date: Date) {
        let components = Calendar.current.dateComponents([.hour, .minute], from: date)
        store.dailySummaryHour = components.hour ?? 6
        store.dailySummaryMinute = components.minute ?? 0
        dailySummaryTime = makeTimeDate(hour: store.dailySummaryHour, minute: store.dailySummaryMinute)
        Task {
            await notifier.scheduleDailySummaries(events: events)
        }
    }

    func isDailySummaryDayEnabled(_ bit: Int) -> Bool {
        store.dailySummaryDaysMask & (1 << bit) != 0
    }

    func setDailySummaryDay(_ bit: Int, enabled: Bool) {
        var mask = store.dailySummaryDaysMask
        let flag = 1 << bit
        if enabled {
            mask |= flag
        } else {
            mask &= ~flag
        }
        store.dailySummaryDaysMask = mask
        dailySummaryEnabled = store.isDailySummaryEnabled
        Task {
            await notifier.scheduleDailySummaries(events: events)
        }
        objectWillChange.send()
    }

    func setDailySummaryEveryDay() {
        store.dailySummaryDaysMask = 0b1111111
        dailySummaryEnabled = store.isDailySummaryEnabled
        Task {
            await notifier.scheduleDailySummaries(events: events)
        }
        objectWillChange.send()
    }

    func isReminderOffsetEnabled(_ minutes: Int) -> Bool {
        store.reminderOffsetsMinutes().contains(minutes)
    }

    func setReminderOffset(_ minutes: Int, enabled: Bool) {
        store.setReminderOffset(minutes, enabled: enabled)
        Task {
            await notifier.scheduleReminders(for: events.filter { !store.isDone($0.id) })
        }
        objectWillChange.send()
    }

    func reminderLabel(_ minutes: Int) -> String {
        store.reminderOptionLabel(minutes)
    }

    func formattedDate(_ date: Date) -> String {
        Self.fullDateFormatter.string(from: date)
    }

    func remainingText(for event: DeadlineEvent) -> String {
        let diff = event.startAt.timeIntervalSinceNow
        if diff <= 0 {
            return "Đã tới hạn hoặc vừa qua hạn."
        }
        let days = Int(diff) / (24 * 60 * 60)
        let hours = (Int(diff) / (60 * 60)) % 24
        let minutes = (Int(diff) / 60) % 60
        if days > 0 {
            return "Còn \(days) ngày \(hours) giờ"
        }
        if hours > 0 {
            return "Còn \(hours) giờ \(minutes) phút"
        }
        return "Còn \(minutes) phút"
    }

    private func makeTimeDate(hour: Int, minute: Int) -> Date {
        var components = Calendar.current.dateComponents([.year, .month, .day], from: Date())
        components.hour = hour
        components.minute = minute
        return Calendar.current.date(from: components) ?? Date()
    }

    private static let fullDateFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "vi_VN")
        formatter.timeZone = TimeZone(identifier: "Asia/Ho_Chi_Minh")
        formatter.dateFormat = "HH:mm - EEEE dd/MM/yyyy"
        return formatter
    }()
}
