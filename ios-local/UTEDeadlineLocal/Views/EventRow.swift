import SwiftUI
import UIKit

struct EventRow: View {
    @EnvironmentObject private var viewModel: DeadlineViewModel
    @Environment(\.openURL) private var openURL

    let event: DeadlineEvent

    private var isDone: Bool {
        viewModel.isDone(event)
    }

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            dateBadge

            VStack(alignment: .leading, spacing: 8) {
                HStack(spacing: 6) {
                    pill(EventLabels.kind(for: event), color: accentColor)
                    if isDone {
                        pill("Đã xong", color: .green)
                    }
                    if let course = EventLabels.course(for: event) {
                        pill(course, color: .blue)
                    }
                }

                Text(event.title)
                    .font(.headline)
                    .foregroundStyle(isDone ? Color.secondary : Color.primary)
                    .lineLimit(2)

                if let description = EventLabels.cleanDescription(for: event) {
                    Text(description)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .lineLimit(2)
                }

                Text("\(EventLabels.timeLabel(for: event)): \(viewModel.formattedDate(event.startAt))")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)

                Text(viewModel.remainingText(for: event))
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(isDone ? Color.secondary : accentColor)

                HStack {
                    Button {
                        if let urlText = event.sourceURL, let url = URL(string: urlText) {
                            openURL(url)
                        } else if let url = URL(string: "https://utexlms.hcmute.edu.vn/calendar/view.php?view=month") {
                            openURL(url)
                        }
                    } label: {
                        Label("Mở", systemImage: "safari")
                    }
                    .buttonStyle(.bordered)

                    Button {
                        UIPasteboard.general.string = copiedText
                    } label: {
                        Label("Copy", systemImage: "doc.on.doc")
                    }
                    .buttonStyle(.bordered)

                    Button {
                        viewModel.toggleDone(event)
                    } label: {
                        Label(isDone ? "Bỏ xong" : "Xong", systemImage: isDone ? "arrow.uturn.backward" : "checkmark")
                    }
                    .buttonStyle(.bordered)
                    .tint(isDone ? .red : .blue)
                }
                .font(.caption)
            }
        }
        .padding(12)
        .background(isDone ? Color(.secondarySystemGroupedBackground) : Color(.systemBackground))
        .clipShape(RoundedRectangle(cornerRadius: 8))
    }

    private var dateBadge: some View {
        VStack(spacing: 3) {
            Text(dayFormatter.string(from: event.startAt))
                .font(.title2.weight(.bold))
            Text(monthFormatter.string(from: event.startAt))
                .font(.caption2)
            Text(clockFormatter.string(from: event.startAt))
                .font(.caption.weight(.semibold))
        }
        .foregroundStyle(accentColor)
        .frame(width: 62)
        .padding(.vertical, 8)
        .background(accentColor.opacity(0.12))
        .clipShape(RoundedRectangle(cornerRadius: 8))
    }

    private func pill(_ text: String, color: Color) -> some View {
        Text(text)
            .font(.caption2.weight(.semibold))
            .lineLimit(1)
            .padding(.horizontal, 7)
            .padding(.vertical, 4)
            .foregroundStyle(color)
            .background(color.opacity(0.12))
            .clipShape(Capsule())
    }

    private var accentColor: Color {
        let diff = event.startAt.timeIntervalSinceNow
        if diff <= 24 * 60 * 60 {
            return .red
        }
        if diff <= 3 * 24 * 60 * 60 {
            return .orange
        }
        if EventLabels.broadKind(for: event) == "Bài nộp" {
            return .green
        }
        if EventLabels.broadKind(for: event) == "Thi" {
            return .red
        }
        return .blue
    }

    private var copiedText: String {
        var lines = [
            "\(EventLabels.kind(for: event)): \(event.title)",
            "\(EventLabels.timeLabel(for: event)): \(viewModel.formattedDate(event.startAt))"
        ]
        if let course = EventLabels.course(for: event) {
            lines.append("Môn/Lớp: \(course)")
        }
        if let description = EventLabels.cleanDescription(for: event) {
            lines.append(description)
        }
        if let sourceURL = event.sourceURL {
            lines.append("Link Moodle: \(sourceURL)")
        }
        return lines.joined(separator: "\n")
    }

    private var dayFormatter: DateFormatter {
        Self.dayFormatter
    }

    private var monthFormatter: DateFormatter {
        Self.monthFormatter
    }

    private var clockFormatter: DateFormatter {
        Self.clockFormatter
    }

    private static let dayFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.timeZone = TimeZone(identifier: "Asia/Ho_Chi_Minh")
        formatter.dateFormat = "dd"
        return formatter
    }()

    private static let monthFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.timeZone = TimeZone(identifier: "Asia/Ho_Chi_Minh")
        formatter.dateFormat = "MM/yyyy"
        return formatter
    }()

    private static let clockFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.timeZone = TimeZone(identifier: "Asia/Ho_Chi_Minh")
        formatter.dateFormat = "HH:mm"
        return formatter
    }()
}
