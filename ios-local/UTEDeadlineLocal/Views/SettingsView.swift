import SwiftUI
import UIKit

struct SettingsView: View {
    @EnvironmentObject private var viewModel: DeadlineViewModel
    @Environment(\.openURL) private var openURL
    private let dayColumns = [GridItem(.adaptive(minimum: 44), spacing: 8)]
    private let moodleExportURLText = "https://utexlms.hcmute.edu.vn/calendar/export.php?"
    private let githubGuideURLText = "https://vanity1412.github.io/notice-app-utex/"
    @State private var customReminderDays = 0
    @State private var customReminderHours = 0
    @State private var customReminderMinutes = 15
    @State private var customReminderMessage = ""

    var body: some View {
        NavigationView {
            ScrollView {
                VStack(spacing: 14) {
                    guideCard
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

    private var guideCard: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Hướng dẫn nhanh")
                .font(.headline)

            Text("Copy link xuất lịch, mở Moodle đã đăng nhập, chọn lịch cần theo dõi rồi copy Calendar URL dạng export_execute.php để dán vào app.")
                .font(.caption)
                .foregroundStyle(.secondary)

            Text(moodleExportURLText)
                .font(.caption.monospaced())
                .foregroundStyle(.secondary)
                .lineLimit(1)
                .truncationMode(.middle)
                .padding(8)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(Color(.secondarySystemGroupedBackground))
                .clipShape(RoundedRectangle(cornerRadius: 8))

            HStack {
                Button {
                    if let url = URL(string: moodleExportURLText) {
                        openURL(url)
                    }
                } label: {
                    Label("Mở Moodle", systemImage: "safari")
                }
                .buttonStyle(.bordered)

                Button {
                    UIPasteboard.general.string = moodleExportURLText
                } label: {
                    Label("Copy", systemImage: "doc.on.doc")
                }
                .buttonStyle(.bordered)

                Button {
                    if let url = URL(string: githubGuideURLText) {
                        openURL(url)
                    }
                } label: {
                    Label("GitHub", systemImage: "questionmark.circle")
                }
                .buttonStyle(.bordered)
            }
            .font(.caption)

            Text("Hỗ trợ: Vũ Văn Thông - 0968046024")
                .font(.caption)
                .foregroundStyle(.secondary)
        }
        .settingsCard()
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

            HStack {
                Button {
                    Task {
                        await viewModel.sendTestNotification()
                    }
                } label: {
                    Label("Gửi test", systemImage: "paperplane")
                }
                .buttonStyle(.bordered)

                Button {
                    if let url = URL(string: UIApplication.openSettingsURLString) {
                        UIApplication.shared.open(url)
                    }
                } label: {
                    Label("Cài đặt iOS", systemImage: "gearshape")
                }
                .buttonStyle(.bordered)
            }
            .font(.caption)

            Button(role: .destructive) {
                viewModel.resetKnownIdsForNotificationTest()
            } label: {
                Label("Reset test lịch mới", systemImage: "arrow.counterclockwise")
            }
            .buttonStyle(.bordered)
            .font(.caption)

            if !viewModel.notificationFeedbackMessage.isEmpty {
                Text(viewModel.notificationFeedbackMessage)
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
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

            Divider()

            VStack(alignment: .leading, spacing: 10) {
                Text("Mốc nhắc tự chọn")
                    .font(.subheadline.weight(.semibold))

                Stepper("Ngày \(customReminderDays)", value: $customReminderDays, in: 0...30)
                Stepper("Giờ \(customReminderHours)", value: $customReminderHours, in: 0...23)
                Stepper("Phút \(customReminderMinutes)", value: $customReminderMinutes, in: 0...59, step: 5)

                Button {
                    customReminderMessage = viewModel.addCustomReminder(
                        days: customReminderDays,
                        hours: customReminderHours,
                        minutes: customReminderMinutes
                    )
                } label: {
                    Label("Thêm mốc", systemImage: "plus.circle")
                }
                .buttonStyle(.borderedProminent)

                if !customReminderMessage.isEmpty {
                    Text(customReminderMessage)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }

                if !viewModel.customReminderOffsets.isEmpty {
                    ForEach(viewModel.customReminderOffsets, id: \.self) { minutes in
                        HStack {
                            Text(viewModel.reminderLabel(minutes))
                                .font(.subheadline)
                            Spacer()
                            Button(role: .destructive) {
                                viewModel.removeCustomReminder(minutes)
                            } label: {
                                Image(systemName: "trash")
                            }
                            .buttonStyle(.bordered)
                        }
                    }
                }
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
