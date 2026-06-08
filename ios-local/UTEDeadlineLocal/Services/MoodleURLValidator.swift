import Foundation

struct MoodleURLValidation {
    let ok: Bool
    let message: String
    let normalizedURL: String
}

enum MoodleURLValidator {
    private static let lmsHost = "utexlms.hcmute.edu.vn"
    private static let exportPath = "/calendar/export.php"
    private static let executePath = "/calendar/export_execute.php"

    static func validate(_ urlText: String) -> MoodleURLValidation {
        let trimmed = urlText.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else {
            return MoodleURLValidation(ok: false, message: "Bạn chưa dán Calendar URL Moodle.", normalizedURL: "")
        }
        guard let url = URL(string: trimmed), let components = URLComponents(url: url, resolvingAgainstBaseURL: false) else {
            return MoodleURLValidation(ok: false, message: "Link không đúng định dạng URL.", normalizedURL: "")
        }
        guard components.scheme == "https" else {
            return MoodleURLValidation(ok: false, message: "Link phải bắt đầu bằng https://", normalizedURL: "")
        }
        guard components.host == lmsHost else {
            return MoodleURLValidation(ok: false, message: "Link phải thuộc utexlms.hcmute.edu.vn.", normalizedURL: "")
        }
        if components.path == exportPath {
            return MoodleURLValidation(
                ok: false,
                message: "Đây mới là trang xuất lịch. Hãy copy Calendar URL dạng export_execute.php từ Moodle.",
                normalizedURL: ""
            )
        }
        guard components.path == executePath else {
            return MoodleURLValidation(ok: false, message: "Link phải là Calendar URL dạng /calendar/export_execute.php.", normalizedURL: "")
        }

        let query = components.queryItems ?? []
        guard query.first(where: { $0.name == "userid" })?.value?.nilIfBlank != nil else {
            return MoodleURLValidation(ok: false, message: "Link thiếu userid. Hãy copy lại đúng Calendar URL từ Moodle.", normalizedURL: "")
        }
        guard query.first(where: { $0.name == "authtoken" })?.value?.nilIfBlank != nil else {
            return MoodleURLValidation(ok: false, message: "Link thiếu authtoken. Hãy copy lại đúng Calendar URL từ Moodle.", normalizedURL: "")
        }

        return MoodleURLValidation(ok: true, message: "Calendar URL hợp lệ.", normalizedURL: trimmed)
    }

    static func mask(_ urlText: String) -> String {
        guard let components = URLComponents(string: urlText.trimmingCharacters(in: .whitespacesAndNewlines)) else {
            return "Calendar URL đã lưu"
        }
        let userId = components.queryItems?.first(where: { $0.name == "userid" })?.value?.nilIfBlank ?? "..."
        let preset = components.queryItems?.first(where: { $0.name == "preset_time" })?.value?.nilIfBlank ?? "..."
        return "\(lmsHost) - userid=\(userId) - token=*** - \(preset)"
    }
}
