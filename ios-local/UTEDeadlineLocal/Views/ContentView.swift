import SwiftUI

struct ContentView: View {
    @EnvironmentObject private var viewModel: DeadlineViewModel
    @Environment(\.scenePhase) private var scenePhase

    var body: some View {
        TabView {
            CalendarScreen()
                .tabItem {
                    Label("Lịch", systemImage: "calendar")
                }

            SettingsView()
                .tabItem {
                    Label("Cài đặt", systemImage: "bell.badge")
                }
        }
        .task {
            await viewModel.refreshNotificationStatus()
            await viewModel.syncIfStale()
        }
        .onChange(of: scenePhase) { phase in
            guard phase == .active else { return }
            Task {
                await viewModel.refreshNotificationStatus()
                await viewModel.syncIfStale()
            }
        }
    }
}

private struct CalendarScreen: View {
    @EnvironmentObject private var viewModel: DeadlineViewModel

    var body: some View {
        NavigationView {
            ScrollView {
                VStack(spacing: 14) {
                    connectionCard
                    statusCard
                    filterCard
                    eventsList
                }
                .padding(16)
            }
            .background(Color(.systemGroupedBackground))
            .navigationTitle("UTE Notice")
            .navigationBarTitleDisplayMode(.inline)
        }
    }

    private var connectionCard: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                VStack(alignment: .leading, spacing: 3) {
                    Text("Kết nối Moodle")
                        .font(.headline)
                    Text(viewModel.connectionSummary)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                }
                Spacer()
                Button("Dán") {
                    viewModel.pasteFromClipboard()
                }
                .buttonStyle(.bordered)
            }

            TextEditor(text: $viewModel.iCalURLInput)
                .font(.footnote)
                .frame(minHeight: 74)
                .padding(6)
                .background(Color(.secondarySystemGroupedBackground))
                .clipShape(RoundedRectangle(cornerRadius: 8))

            HStack {
                Button {
                    viewModel.saveAndSync()
                } label: {
                    Label(viewModel.isSyncing ? "Đang sync" : "Lưu & đồng bộ", systemImage: "arrow.triangle.2.circlepath")
                }
                .buttonStyle(.borderedProminent)
                .disabled(viewModel.isSyncing)

                Button(role: .destructive) {
                    viewModel.clearConnection()
                } label: {
                    Label("Xóa", systemImage: "trash")
                }
                .buttonStyle(.bordered)
                .disabled(!viewModel.hasConnection)
            }
        }
        .cardStyle()
    }

    private var statusCard: some View {
        Text(viewModel.statusMessage)
            .font(.footnote)
            .foregroundStyle(viewModel.statusIsError ? Color.red : Color.blue)
            .frame(maxWidth: .infinity, alignment: .leading)
            .cardStyle(background: viewModel.statusIsError ? Color.red.opacity(0.08) : Color.blue.opacity(0.08))
    }

    private var filterCard: some View {
        VStack(alignment: .leading, spacing: 10) {
            TextField("Tìm theo tên bài, môn/lớp", text: $viewModel.searchText)
                .textInputAutocapitalization(.never)
                .disableAutocorrection(true)
                .textFieldStyle(.roundedBorder)

            Picker("Chế độ xem", selection: $viewModel.eventViewMode) {
                ForEach(DeadlineViewModel.EventViewMode.allCases) { mode in
                    Text(mode.label).tag(mode)
                }
            }
            .pickerStyle(.segmented)

            Picker("Loại", selection: $viewModel.selectedFilter) {
                ForEach(DeadlineViewModel.EventFilter.allCases) { filter in
                    Text(filter.label).tag(filter)
                }
            }
            .pickerStyle(.segmented)

            HStack {
                Text(viewModel.hideDoneEvents ? "Deadline đã xong đang ẩn" : "Đang hiện deadline đã xong")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                Spacer()
                Button(viewModel.hideDoneEvents ? "Hiện đã xong" : "Ẩn đã xong") {
                    viewModel.hideDoneEvents.toggle()
                }
                .buttonStyle(.bordered)
            }
        }
        .cardStyle()
    }

    private var eventsList: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Text("Lịch sắp tới")
                    .font(.headline)
                Spacer()
                Text("\(viewModel.filteredEvents.count)/\(viewModel.events.count) mục")
                    .font(.caption)
                    .padding(.horizontal, 10)
                    .padding(.vertical, 5)
                    .background(Color.blue.opacity(0.12))
                    .clipShape(Capsule())
            }

            if viewModel.events.isEmpty {
                EmptyStateView(
                    title: "Chưa có deadline",
                    message: "Dán iCal URL rồi bấm Lưu & đồng bộ để tải lịch."
                )
            } else if viewModel.filteredEvents.isEmpty {
                EmptyStateView(
                    title: "Không có mục phù hợp",
                    message: "Thử đổi từ khóa, bộ lọc hoặc bấm Hiện đã xong."
                )
            } else if viewModel.eventViewMode == .month {
                MonthCalendarView()
            } else {
                LazyVStack(spacing: 10) {
                    ForEach(viewModel.filteredEvents) { event in
                        EventRow(event: event)
                    }
                }
            }
        }
    }
}

private struct MonthCalendarView: View {
    @EnvironmentObject private var viewModel: DeadlineViewModel

    private let columns = Array(repeating: GridItem(.flexible(), spacing: 4), count: 7)
    private let weekdays = ["T2", "T3", "T4", "T5", "T6", "T7", "CN"]
    private var calendar: Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "Asia/Ho_Chi_Minh") ?? .current
        calendar.firstWeekday = 2
        return calendar
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            monthControls

            LazyVGrid(columns: columns, spacing: 4) {
                ForEach(weekdays, id: \.self) { label in
                    Text(label)
                        .font(.caption2.weight(.semibold))
                        .foregroundStyle(.secondary)
                        .frame(maxWidth: .infinity)
                }

                ForEach(Array(monthDates.enumerated()), id: \.offset) { _, date in
                    MonthDayCell(
                        date: date,
                        events: date.map(eventsForDate) ?? [],
                        isToday: date.map(calendar.isDateInToday) ?? false,
                        isCurrentMonth: date.map(isInVisibleMonth) ?? false
                    )
                }
            }

            Text("Sự kiện trong \(monthTitle)")
                .font(.subheadline.weight(.semibold))
                .padding(.top, 4)

            let monthEvents = filteredEventsInVisibleMonth
            if monthEvents.isEmpty {
                Text("Không có deadline phù hợp trong tháng này.")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                    .frame(maxWidth: .infinity, alignment: .center)
                    .padding(12)
                    .background(Color(.systemBackground))
                    .clipShape(RoundedRectangle(cornerRadius: 8))
            } else {
                LazyVStack(spacing: 10) {
                    ForEach(monthEvents) { event in
                        EventRow(event: event)
                    }
                }
            }
        }
    }

    private var monthControls: some View {
        HStack(spacing: 8) {
            Button {
                moveMonth(by: -1)
            } label: {
                Image(systemName: "chevron.left")
            }
            .buttonStyle(.bordered)

            Text(monthTitle)
                .font(.headline)
                .frame(maxWidth: .infinity)

            Button("Tháng này") {
                viewModel.visibleMonth = Date()
            }
            .buttonStyle(.bordered)

            Button {
                moveMonth(by: 1)
            } label: {
                Image(systemName: "chevron.right")
            }
            .buttonStyle(.bordered)
        }
    }

    private var monthTitle: String {
        Self.monthTitleFormatter.string(from: viewModel.visibleMonth)
    }

    private var monthDates: [Date?] {
        guard let monthInterval = calendar.dateInterval(of: .month, for: viewModel.visibleMonth),
              let firstWeek = calendar.dateInterval(of: .weekOfMonth, for: monthInterval.start)
        else {
            return []
        }

        return (0..<42).map { offset in
            guard let date = calendar.date(byAdding: .day, value: offset, to: firstWeek.start) else {
                return nil
            }
            return date
        }
    }

    private var filteredEventsInVisibleMonth: [DeadlineEvent] {
        viewModel.filteredEvents.filter { isInVisibleMonth($0.startAt) }
    }

    private func eventsForDate(_ date: Date) -> [DeadlineEvent] {
        viewModel.filteredEvents.filter { calendar.isDate($0.startAt, inSameDayAs: date) }
    }

    private func isInVisibleMonth(_ date: Date) -> Bool {
        calendar.isDate(date, equalTo: viewModel.visibleMonth, toGranularity: .month)
    }

    private func moveMonth(by value: Int) {
        if let next = calendar.date(byAdding: .month, value: value, to: viewModel.visibleMonth) {
            viewModel.visibleMonth = next
        }
    }

    private static let monthTitleFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "vi_VN")
        formatter.timeZone = TimeZone(identifier: "Asia/Ho_Chi_Minh")
        formatter.dateFormat = "'Tháng' M yyyy"
        return formatter
    }()
}

private struct MonthDayCell: View {
    let date: Date?
    let events: [DeadlineEvent]
    let isToday: Bool
    let isCurrentMonth: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: 3) {
            if let date {
                Text(Self.dayFormatter.string(from: date))
                    .font(.caption.weight(.bold))
                    .foregroundStyle(isToday ? Color.blue : (isCurrentMonth ? Color.primary : Color.secondary))

                if !events.isEmpty {
                    Text("\(events.count) mục")
                        .font(.caption2.weight(.bold))
                        .foregroundStyle(.red)
                        .lineLimit(1)

                    Text(events[0].title)
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                        .lineLimit(2)
                }
            }
            Spacer(minLength: 0)
        }
        .frame(maxWidth: .infinity, minHeight: 72, alignment: .topLeading)
        .padding(5)
        .background(backgroundColor)
        .overlay(
            RoundedRectangle(cornerRadius: 8)
                .stroke(borderColor, lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: 8))
    }

    private var backgroundColor: Color {
        if date == nil {
            return Color(.secondarySystemGroupedBackground)
        }
        if isToday {
            return Color.blue.opacity(0.12)
        }
        if !events.isEmpty {
            return Color.orange.opacity(0.10)
        }
        return Color(.systemBackground)
    }

    private var borderColor: Color {
        if isToday {
            return .blue
        }
        if !events.isEmpty {
            return .orange.opacity(0.55)
        }
        return Color(.separator).opacity(0.35)
    }

    private static let dayFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.timeZone = TimeZone(identifier: "Asia/Ho_Chi_Minh")
        formatter.dateFormat = "d"
        return formatter
    }()
}

private struct EmptyStateView: View {
    let title: String
    let message: String

    var body: some View {
        VStack(spacing: 8) {
            Image(systemName: "calendar.badge.exclamationmark")
                .font(.system(size: 34))
                .foregroundStyle(.blue)
            Text(title)
                .font(.headline)
            Text(message)
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity)
        .cardStyle()
    }
}

private extension View {
    func cardStyle(background: Color = Color(.systemBackground)) -> some View {
        padding(12)
            .background(background)
            .clipShape(RoundedRectangle(cornerRadius: 8))
    }
}
