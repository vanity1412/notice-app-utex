package com.utex.deadline

import android.net.Uri

object MoodleUrlValidator {
    private const val LMS_HOST = "utexlms.hcmute.edu.vn"
    private const val EXPORT_PATH = "/calendar/export.php"
    private const val EXECUTE_PATH = "/calendar/export_execute.php"

    fun validate(urlText: String): ValidationResult {
        val trimmed = urlText.trim()
        if (trimmed.isBlank()) {
            return ValidationResult(false, "Bạn chưa dán Calendar URL Moodle.")
        }

        val uri = try {
            Uri.parse(trimmed)
        } catch (_: Exception) {
            return ValidationResult(false, "Link không đúng định dạng URL.")
        }

        if (uri.scheme != "https") {
            return ValidationResult(false, "Link phải bắt đầu bằng https://")
        }
        if (uri.host != LMS_HOST) {
            return ValidationResult(false, "Link phải thuộc utexlms.hcmute.edu.vn.")
        }
        if (uri.path == EXPORT_PATH) {
            return ValidationResult(false, "Đây mới là trang xuất lịch. Hãy bấm Lấy địa chỉ mạng của lịch trên Moodle rồi copy Calendar URL.")
        }
        if (uri.path != EXECUTE_PATH) {
            return ValidationResult(false, "Link phải là Calendar URL dạng /calendar/export_execute.php.")
        }
        if (uri.getQueryParameter("userid").isNullOrBlank()) {
            return ValidationResult(false, "Link thiếu userid. Hãy copy lại đúng Calendar URL từ Moodle.")
        }
        if (uri.getQueryParameter("authtoken").isNullOrBlank()) {
            return ValidationResult(false, "Link thiếu authtoken. Hãy copy lại đúng Calendar URL từ Moodle.")
        }

        return ValidationResult(true, "Calendar URL hợp lệ.", trimmed)
    }

    fun mask(urlText: String): String {
        val uri = try {
            Uri.parse(urlText.trim())
        } catch (_: Exception) {
            return "Calendar URL đã lưu"
        }
        val userId = uri.getQueryParameter("userid").orEmpty().ifBlank { "..." }
        val preset = uri.getQueryParameter("preset_time").orEmpty().ifBlank { "..." }
        return "utexlms.hcmute.edu.vn - userid=$userId - token=*** - $preset"
    }

    data class ValidationResult(
        val ok: Boolean,
        val message: String,
        val normalizedUrl: String = ""
    )
}
