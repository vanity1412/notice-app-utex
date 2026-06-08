import SwiftUI

struct SettingsView: View {
    @EnvironmentObject private var viewModel: DeadlineViewModel
    private let dayColumns = [GridItem(.adaptive(minimum: 44), spacing: 8)]

    var body: some View {
        NavigationView {
            ScrollView {
                VStack(spacing: 14) {
                    notificationCard
                    dailySummaryCard
                    reminderCard
                    backgroundCard
                }
                .padding(16)
            }
            .background(Color(.systemGroupedBackground))
            .navigationTitle("Cài đặt")
            .navigationBarTitleDisplayMode(.inline)
        }
        .task {
            await viewModel.refreshNotificationStatus()
        }
    }

    private var notificationCard: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Image(systemName: viewModel.notificationGranted ? "bell.badge.fill" : "bell.slash")
                    .foregroundStyle(viewModel.notificationGranted ? Color.green : Color.orange)
                VStack(alignment: .leading, spacing: 2) {
                    Text("Thông báo")
                        .font(.headline)
                    Text(viewModel.notificationGranted ? "Đã cấp quyền thông báo" : "Chưa cấp quyền thông báo")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                Spacer()
            }

            Button {
                Task {
                    await viewModel.requestNotifications()
                }
            } label: {
                Label(viewModel.notificationGranted ? "Kiểm tra lại" : "Cấp quyền", systemImage: "checkmark.shield")
            }
            .buttonStyle(.borderedProminent)
        }
        .settingsCard()
    }

    private var dailySummaryCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Tổng hợp hằng ngày")
                .font(.headline)

            Toggle("Bật tổng hợp", isOn: Binding(
                get: { viewModel.dailySummaryEnabled },
                set: { viewModel.setDailySummaryEnabled($0) }
            ))

            DatePicker(
                "Giờ nhắc",
                selection: Binding(
                    get: { viewModel.dailySummaryTime },
                    set: { viewModel.setDailySummaryTime($0) }
                ),
                displayedComponents: .hourAndMinute
            )
            .disabled(!viewModel.dailySummaryEnabled)

            VStack(alignment: .leading, spacing: 8) {
                Text("Ngày nhận")
                    .font(.subheadline.weight(.semibold))

                LazyVGrid(columns: dayColumns, spacing: 8) {
                    ForEach(viewModel.dailySummaryDays) { day in
                        Button(day.label) {
                            viewModel.setDailySummaryDay(
                                day.bit,
                                enabled: !viewModel.isDailySummaryDayEnabled(day.bit)
                            )
                        }
                        .buttonStyle(.bordered)
                        .tint(viewModel.isDailySummaryDayEnabled(day.bit) ? Color.blue : Color.gray)
                        .disabled(!viewModel.dailySummaryEnabled)
                    }
                }

                Button {
                    viewModel.setDailySummaryEveryDay()
                } label: {
                    Label("Mỗi ngày", systemImage: "calendar")
                }
                .buttonStyle(.bordered)
                .disabled(!viewModel.dailySummaryEnabled)
            }

            Text("iOS sẽ nhận summary bằng local notification nếu app đã biết deadline trong 3 ngày tới. Background refresh trên iOS là best-effort, không đảm bảo mỗi 5 phút.")
                .font(.caption)
                .foregroundStyle(.secondary)
        }
        .settingsCard()
    }

    private var reminderCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Mốc nhắc trước hạn")
                .font(.headline)

            ForEach(viewModel.reminderOffsets, id: \.self) { minutes in
                Toggle(viewModel.reminderLabel(minutes), isOn: Binding(
                    get: { viewModel.isReminderOffsetEnabled(minutes) },
                    set: { viewModel.setReminderOffset(minutes, enabled: $0) }
                ))
            }

            Text("App luôn giữ ít nhất một mốc nhắc. Các notification đã lên lịch sẽ được tạo lại khi đổi mốc.")
                .font(.caption)
                .foregroundStyle(.secondary)
        }
        .settingsCard()
    }

    private var backgroundCard: some View {
        VStack(alignment: .leading, spacing: 8) {
            Label("Background Refresh", systemImage: "clock.arrow.circlepath")
                .font(.headline)
            Text("Bản iOS local-only dùng BGAppRefreshTask để thử đồng bộ nền. iOS tự quyết định khi nào chạy; để báo deadline mới/thay đổi chắc hơn khi app đóng lâu, cần backend + APNs.")
                .font(.caption)
                .foregroundStyle(.secondary)
        }
        .settingsCard()
    }
}

private extension View {
    func settingsCard() -> some View {
        padding(12)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Color(.systemBackground))
            .clipShape(RoundedRectangle(cornerRadius: 8))
    }
}
