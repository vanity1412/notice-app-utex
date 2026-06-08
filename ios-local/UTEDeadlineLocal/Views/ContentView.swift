import SwiftUI

struct ContentView: View {
    @EnvironmentObject private var viewModel: DeadlineViewModel

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
