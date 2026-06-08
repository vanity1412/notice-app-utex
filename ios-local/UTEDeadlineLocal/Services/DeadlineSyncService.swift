import Foundation

actor DeadlineSyncService {
    static let shared = DeadlineSyncService()

    private let store = EventStore.shared
    private let notifier = NotificationService.shared
    private let newEventGraceInterval: TimeInterval = 60 * 60

    func sync(notifyNew: Bool) async -> SyncResult {
        let urlText = store.iCalURL
        let validation = MoodleURLValidator.validate(urlText)
        guard validation.ok else {
            return .failure(validation.message)
        }
        guard let url = URL(string: validation.normalizedURL) else {
            return .failure("Calendar URL không hợp lệ.")
        }

        do {
            let ics = try await fetchText(url: url)
            guard ics.range(of: "BEGIN:VCALENDAR", options: .caseInsensitive) != nil else {
                return .failure("Moodle chưa trả về file lịch. Có thể token hết hạn hoặc bạn đã dán nhầm link.")
            }

            let events = IcsParser.parse(ics)
            let knownIds = store.knownIds()
            let previousEvents = store.loadEvents()
            let firstSync = knownIds.isEmpty
            let now = Date()
            let previousMap = Dictionary(uniqueKeysWithValues: previousEvents.map { ($0.id, $0) })

            let newEvents = events.filter { $0.startAt >= now.addingTimeInterval(-newEventGraceInterval) && !knownIds.contains($0.id) }
            let changedEvents: [DeadlineEvent] = firstSync ? [] : events.filter { event in
                guard knownIds.contains(event.id), let old = previousMap[event.id] else { return false }
                return old.startAtMillis != event.startAtMillis ||
                    old.title != event.title ||
                    old.description != event.description
            }

            store.saveEvents(events)
            store.saveKnownIds(knownIds.union(events.map(\.id)))
            store.setLastSync(Date())
            store.prepareDailySummaryDefaultsAfterSetup()

            let activeEvents = events.filter { !store.isDone($0.id) }
            await notifier.scheduleReminders(for: activeEvents)
            await notifier.scheduleDailySummaries(events: events)
            BackgroundRefreshService.shared.schedule()

            if notifyNew {
                await notifier.flushPendingDeadlineNotifications()
                if firstSync, !events.isEmpty {
                    if !(await notifier.notifyInitialSummary(count: events.count)) {
                        store.upsertPendingNotifications([
                            PendingDeadlineNotification(
                                key: "summary-first-sync",
                                kind: .initialSummary,
                                event: events[0],
                                summaryCount: events.count
                            )
                        ])
                    }
                } else {
                    var pending: [PendingDeadlineNotification] = []
                    for event in newEvents {
                        if !(await notifier.notifyNewDeadline(event)) {
                            pending.append(PendingDeadlineNotification(key: "new-\(event.id)", kind: .new, event: event))
                        }
                    }
                    for event in changedEvents {
                        if !(await notifier.notifyChangedDeadline(event)) {
                            pending.append(PendingDeadlineNotification(key: "changed-\(event.id)", kind: .changed, event: event))
                        }
                    }
                    store.upsertPendingNotifications(pending)
                }
            }

            let totalChanges = newEvents.count + changedEvents.count
            let message: String
            if totalChanges > 0, !firstSync {
                var parts: [String] = []
                if !newEvents.isEmpty {
                    parts.append("\(newEvents.count) mới")
                }
                if !changedEvents.isEmpty {
                    parts.append("\(changedEvents.count) thay đổi")
                }
                message = "Có \(parts.joined(separator: ", ")) deadline."
            } else if events.isEmpty {
                message = "Đã kết nối Moodle nhưng chưa thấy deadline. Kiểm tra mục lịch đã chọn khi export."
            } else {
                message = "Đã cập nhật \(events.count) deadline."
            }

            return SyncResult(
                ok: true,
                message: message,
                totalEvents: events.count,
                changedEvents: firstSync ? 0 : totalChanges,
                retryable: false
            )
        } catch let error as HTTPError {
            return .failure(error.message, retryable: error.retryable)
        } catch let error as URLError {
            return .failure("Không đồng bộ được lịch Moodle: \(error.localizedDescription)", retryable: error.isRetryableSyncError)
        } catch {
            return .failure("Không đồng bộ được lịch Moodle: \(error.localizedDescription)")
        }
    }

    private func fetchText(url: URL) async throws -> String {
        var request = URLRequest(url: url)
        request.httpMethod = "GET"
        request.timeoutInterval = 20
        request.setValue("UTE-Deadline-iOS/1.0", forHTTPHeaderField: "User-Agent")

        let (data, response) = try await URLSession.shared.data(for: request)
        if let http = response as? HTTPURLResponse, !(200...299).contains(http.statusCode) {
            throw HTTPError(statusCode: http.statusCode)
        }
        return String(data: data, encoding: .utf8) ?? String(decoding: data, as: UTF8.self)
    }
}

private struct HTTPError: Error {
    let statusCode: Int

    var retryable: Bool {
        (500...599).contains(statusCode)
    }

    var message: String {
        switch statusCode {
        case 401, 403:
            return "Token Moodle hết hạn hoặc không có quyền truy cập. Hãy vào Moodle tạo lại Calendar URL."
        case 404:
            return "Link Calendar URL không còn đúng. Hãy copy lại từ trang xuất lịch Moodle."
        case 500...599:
            return "Moodle đang lỗi máy chủ (\(statusCode)). Hãy thử lại sau."
        default:
            return "Moodle trả lỗi HTTP \(statusCode). Hãy kiểm tra lại Calendar URL."
        }
    }
}

private extension URLError {
    var isRetryableSyncError: Bool {
        switch code {
        case .notConnectedToInternet, .networkConnectionLost, .timedOut, .cannotFindHost, .cannotConnectToHost, .dnsLookupFailed, .dataNotAllowed:
            return true
        default:
            return false
        }
    }
}
