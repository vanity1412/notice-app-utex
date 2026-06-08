import Foundation

final class EventStore {
    static let shared = EventStore()

    private enum Keys {
        static let events = "events_json"
        static let knownIds = "known_ids"
        static let doneIds = "done_ids"
        static let pendingNotifications = "pending_notifications_json"
        static let lastSync = "last_sync"
        static let dailySummaryTouched = "daily_summary_touched"
        static let dailySummaryEnabled = "daily_summary_enabled"
        static let dailySummaryHour = "daily_summary_hour"
        static let dailySummaryMinute = "daily_summary_minute"
        static let dailySummaryDays = "daily_summary_days"
        static let reminderOffsets = "reminder_offsets"
        static let customReminderOffsets = "custom_reminder_offsets"
    }

    let presetReminderMinutes: [Int] = [
        7 * 24 * 60,
        3 * 24 * 60,
        2 * 24 * 60,
        24 * 60,
        12 * 60,
        6 * 60,
        3 * 60,
        60,
        30
    ]
    private let defaultReminderMinutes: [Int] = [24 * 60, 12 * 60, 60]
    private let allDaysMask = 0b1111111
    private let defaults = UserDefaults.standard
    private let encoder = JSONEncoder()
    private let decoder = JSONDecoder()

    private init() {}

    var iCalURL: String {
        KeychainStore.getIcalURL()
    }

    func setIcalURL(_ url: String) throws {
        try KeychainStore.setIcalURL(url)
    }

    func clearConnection() {
        KeychainStore.clearIcalURL()
        [
            Keys.events,
            Keys.knownIds,
            Keys.doneIds,
            Keys.pendingNotifications,
            Keys.lastSync,
            Keys.dailySummaryTouched
        ].forEach { defaults.removeObject(forKey: $0) }
        defaults.set(false, forKey: Keys.dailySummaryEnabled)
    }

    func loadEvents() -> [DeadlineEvent] {
        decode([DeadlineEvent].self, key: Keys.events, fallback: [])
            .sorted { $0.startAtMillis < $1.startAtMillis }
    }

    func saveEvents(_ events: [DeadlineEvent]) {
        let sorted = events.sorted { $0.startAtMillis < $1.startAtMillis }
        encode(sorted, key: Keys.events)
        clearDoneForMissingEvents(Set(sorted.map(\.id)))
    }

    func knownIds() -> Set<String> {
        Set(defaults.stringArray(forKey: Keys.knownIds) ?? [])
    }

    func saveKnownIds(_ ids: Set<String>) {
        defaults.set(Array(ids), forKey: Keys.knownIds)
    }

    func doneIds() -> Set<String> {
        Set(defaults.stringArray(forKey: Keys.doneIds) ?? [])
    }

    func isDone(_ id: String) -> Bool {
        doneIds().contains(id)
    }

    func setDone(_ id: String, done: Bool) {
        var ids = doneIds()
        if done {
            ids.insert(id)
        } else {
            ids.remove(id)
        }
        defaults.set(Array(ids), forKey: Keys.doneIds)
    }

    var lastSync: Date? {
        let seconds = defaults.double(forKey: Keys.lastSync)
        return seconds > 0 ? Date(timeIntervalSince1970: seconds) : nil
    }

    func setLastSync(_ date: Date) {
        defaults.set(date.timeIntervalSince1970, forKey: Keys.lastSync)
    }

    func resetKnownIds() {
        defaults.removeObject(forKey: Keys.knownIds)
    }

    func loadPendingNotifications() -> [PendingDeadlineNotification] {
        decode([PendingDeadlineNotification].self, key: Keys.pendingNotifications, fallback: [])
    }

    func upsertPendingNotifications(_ notifications: [PendingDeadlineNotification]) {
        guard !notifications.isEmpty else { return }
        var merged: [String: PendingDeadlineNotification] = [:]
        loadPendingNotifications().forEach { merged[$0.key] = $0 }
        notifications.forEach { merged[$0.key] = $0 }
        savePendingNotifications(Array(merged.values))
    }

    func savePendingNotifications(_ notifications: [PendingDeadlineNotification]) {
        var seen = Set<String>()
        let unique = notifications.filter { seen.insert($0.key).inserted }
        encode(unique, key: Keys.pendingNotifications)
    }

    func prepareDailySummaryDefaultsAfterSetup() {
        guard !defaults.bool(forKey: Keys.dailySummaryTouched) else { return }
        defaults.register(defaults: [
            Keys.dailySummaryHour: 6,
            Keys.dailySummaryMinute: 0,
            Keys.dailySummaryDays: allDaysMask,
            Keys.dailySummaryEnabled: false
        ])
    }

    var isDailySummaryEnabled: Bool {
        get { !iCalURL.isEmpty && defaults.bool(forKey: Keys.dailySummaryEnabled) }
        set {
            defaults.set(true, forKey: Keys.dailySummaryTouched)
            defaults.set(newValue, forKey: Keys.dailySummaryEnabled)
        }
    }

    var dailySummaryHour: Int {
        get { defaults.object(forKey: Keys.dailySummaryHour) as? Int ?? 6 }
        set { defaults.set(max(0, min(23, newValue)), forKey: Keys.dailySummaryHour) }
    }

    var dailySummaryMinute: Int {
        get { defaults.object(forKey: Keys.dailySummaryMinute) as? Int ?? 0 }
        set { defaults.set(max(0, min(59, newValue)), forKey: Keys.dailySummaryMinute) }
    }

    var dailySummaryDaysMask: Int {
        get {
            let value = defaults.object(forKey: Keys.dailySummaryDays) as? Int ?? allDaysMask
            return value == 0 ? allDaysMask : value
        }
        set {
            defaults.set(true, forKey: Keys.dailySummaryTouched)
            let safe = newValue & allDaysMask
            defaults.set(safe, forKey: Keys.dailySummaryDays)
            defaults.set(safe != 0, forKey: Keys.dailySummaryEnabled)
        }
    }

    func isDailySummaryAllowedToday(date: Date = Date()) -> Bool {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "Asia/Ho_Chi_Minh") ?? .current
        let weekday = calendar.component(.weekday, from: date)
        let mondayBasedIndex = (weekday + 5) % 7
        return dailySummaryDaysMask & (1 << mondayBasedIndex) != 0
    }

    var allowedReminderMinutes: [Int] {
        allReminderOffsetOptions()
    }

    func allReminderOffsetOptions() -> [Int] {
        Array(Set(presetReminderMinutes + customReminderOffsets()))
            .filter { $0 > 0 }
            .sorted(by: >)
    }

    func customReminderOffsets() -> [Int] {
        (defaults.stringArray(forKey: Keys.customReminderOffsets) ?? [])
            .compactMap { Int($0) }
            .filter { $0 > 0 }
            .sorted(by: >)
    }

    func addCustomReminderOffset(_ minutes: Int) {
        guard minutes > 0 else { return }
        var custom = Set(customReminderOffsets())
        custom.insert(minutes)
        defaults.set(custom.sorted(by: >).map { String($0) }, forKey: Keys.customReminderOffsets)

        var selected = Set(reminderOffsetsMinutes())
        selected.insert(minutes)
        saveReminderOffsetsMinutes(Array(selected))
    }

    func removeCustomReminderOffset(_ minutes: Int) {
        var custom = Set(customReminderOffsets())
        custom.remove(minutes)
        defaults.set(custom.sorted(by: >).map { String($0) }, forKey: Keys.customReminderOffsets)

        if !presetReminderMinutes.contains(minutes) {
            var selected = Set(reminderOffsetsMinutes())
            selected.remove(minutes)
            if selected.isEmpty {
                selected.insert(60)
            }
            saveReminderOffsetsMinutes(Array(selected))
        }
    }

    func reminderOffsetsMinutes() -> [Int] {
        let allOptions = allReminderOffsetOptions()
        let saved = defaults.stringArray(forKey: Keys.reminderOffsets)?
            .compactMap { Int($0) }
            .filter { allOptions.contains($0) }
            ?? []
        let selected = saved.isEmpty ? defaultReminderMinutes : saved
        return allOptions.filter { selected.contains($0) }
    }

    func setReminderOffset(_ minutes: Int, enabled: Bool) {
        guard allReminderOffsetOptions().contains(minutes) else { return }
        var selected = Set(reminderOffsetsMinutes())
        if enabled {
            selected.insert(minutes)
        } else {
            selected.remove(minutes)
        }
        if selected.isEmpty {
            selected.insert(60)
        }
        saveReminderOffsetsMinutes(Array(selected))
    }

    func reminderOptionLabel(_ minutes: Int) -> String {
        switch minutes {
        case 7 * 24 * 60:
            return "7 ngày"
        case 3 * 24 * 60:
            return "3 ngày"
        case 2 * 24 * 60:
            return "2 ngày"
        case 24 * 60:
            return "1 ngày"
        case 12 * 60:
            return "12 giờ"
        case 6 * 60:
            return "6 giờ"
        case 3 * 60:
            return "3 giờ"
        case 60:
            return "1 giờ"
        case 30:
            return "30 phút"
        default:
            return customReminderLabel(minutes)
        }
    }

    private func saveReminderOffsetsMinutes(_ offsets: [Int]) {
        let allOptions = allReminderOffsetOptions()
        let safe = offsets
            .filter { $0 > 0 && allOptions.contains($0) }
            .sorted(by: >)
        defaults.set(safe.map { String($0) }, forKey: Keys.reminderOffsets)
    }

    private func customReminderLabel(_ minutes: Int) -> String {
        let days = minutes / (24 * 60)
        let hours = (minutes % (24 * 60)) / 60
        let mins = minutes % 60

        switch (days, hours, mins) {
        case let (days, 0, 0) where days > 0:
            return "\(days) ngày"
        case let (days, hours, 0) where days > 0:
            return "\(days) ngày \(hours) giờ"
        case let (days, hours, mins) where days > 0:
            return "\(days) ngày \(hours) giờ \(mins) phút"
        case let (0, hours, 0) where hours > 0:
            return "\(hours) giờ"
        case let (0, hours, mins) where hours > 0:
            return "\(hours) giờ \(mins) phút"
        default:
            return "\(mins) phút"
        }
    }

    private func clearDoneForMissingEvents(_ eventIds: Set<String>) {
        let kept = doneIds().intersection(eventIds)
        defaults.set(Array(kept), forKey: Keys.doneIds)
    }

    private func encode<T: Encodable>(_ value: T, key: String) {
        if let data = try? encoder.encode(value) {
            defaults.set(data, forKey: key)
        }
    }

    private func decode<T: Decodable>(_ type: T.Type, key: String, fallback: T) -> T {
        guard let data = defaults.data(forKey: key), let value = try? decoder.decode(type, from: data) else {
            return fallback
        }
        return value
    }
}
