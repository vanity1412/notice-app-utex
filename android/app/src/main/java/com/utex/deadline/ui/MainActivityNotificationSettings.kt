@file:Suppress("DEPRECATION", "unused")

package com.utex.deadline

import android.Manifest
import android.app.Activity
import android.app.TimePickerDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.Editable
import android.text.InputType
import android.text.TextUtils
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.text.Normalizer
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.concurrent.thread

internal fun MainActivity.showNotificationSettingsTab() {
        activeTab = ScreenTab.NOTIFICATION_SETTINGS
        updateTabButtons()
        tabContent.removeAllViews()

        tabContent.addView(notificationSettingsPanel())
        addSpacer(tabContent, 8)
        tabContent.addView(emailNotificationPanel())
        addSpacer(tabContent, 16)

        refreshDailySummaryText()
        refreshNotificationHealthText()
    }

internal fun MainActivity.notificationSettingsPanel(): View {
    val activity = this
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = rounded(card, 8, line, 1)
        }
        panel.addView(TextView(this).apply {
            text = "Cài đặt thông báo"
            textSize = 15f
            setTextColor(ink)
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        })
        dailySummaryText = TextView(this).apply {
            textSize = 12f
            setTextColor(muted)
            setPadding(0, dp(5), 0, dp(8))
        }
        panel.addView(dailySummaryText)

        panel.addView(TextView(this).apply {
            text = "Khung giờ tổng hợp"
            textSize = 12f
            setTextColor(ink)
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            setPadding(0, dp(2), 0, dp(6))
        })
        val summaryTimes = EventStore.getDailySummaryTimes(this)
        if (summaryTimes.isEmpty() || !EventStore.isDailySummaryEnabled(this)) {
            panel.addView(TextView(this).apply {
                text = "Chưa chọn khung giờ. Bấm + Thêm giờ để bật tổng hợp."
                textSize = 11f
                setTextColor(muted)
                setPadding(0, 0, 0, dp(6))
            })
        } else {
            summaryTimes.chunked(3).forEach { chunk ->
                panel.addView(dailySummaryTimesRow(chunk))
                addSpacer(panel, 6)
            }
        }

        val timeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        timeRow.addView(primaryButton("+ Thêm giờ").apply {
            setOnClickListener { showAddDailySummaryTimePicker() }
        }, LinearLayout.LayoutParams(0, dp(42), 1f))
        timeRow.addView(secondaryButton("Mỗi ngày").apply {
            setOnClickListener {
                EventStore.setDailySummaryDaysMask(activity, EventStore.ALL_DAYS_MASK)
                ReminderScheduler.scheduleDailySummary(activity)
                showNotificationSettingsTab()
            }
        }, LinearLayout.LayoutParams(0, dp(42), 1f).apply {
            marginStart = dp(6)
        })
        timeRow.addView(outlineButton("Tắt").apply {
            setOnClickListener {
                EventStore.setDailySummaryEnabled(activity, false)
                ReminderScheduler.scheduleDailySummary(activity)
                showNotificationSettingsTab()
            }
        }, LinearLayout.LayoutParams(0, dp(42), 1f).apply {
            marginStart = dp(6)
        })
        panel.addView(timeRow)

        addSpacer(panel, 8)
        panel.addView(TextView(this).apply {
            text = "Ngày thông báo"
            textSize = 12f
            setTextColor(ink)
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        })
        addSpacer(panel, 6)

        panel.addView(daysRow(listOf(
            DayOption("T2", 0),
            DayOption("T3", 1),
            DayOption("T4", 2),
            DayOption("T5", 3)
        )))
        addSpacer(panel, 6)
        panel.addView(daysRow(listOf(
            DayOption("T6", 4),
            DayOption("T7", 5),
            DayOption("CN", 6)
        )))

        addSpacer(panel, 10)
        panel.addView(TextView(this).apply {
            text = "Mốc nhắc trước hạn"
            textSize = 12f
            setTextColor(ink)
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        })
        panel.addView(TextView(this).apply {
            text = "Chọn mốc có sẵn hoặc thêm mốc tùy chỉnh. App luôn giữ ít nhất 1 mốc nhắc."
            textSize = 12f
            setTextColor(muted)
            setPadding(0, dp(4), 0, dp(6))
        })

        // Render preset reminders
        val presetOptions = EventStore.getReminderOffsetOptions()
        var rowOptions = mutableListOf<Long>()
        presetOptions.forEach { minutes ->
            rowOptions.add(minutes)
            if (rowOptions.size == 3) {
                panel.addView(reminderOffsetsRow(rowOptions.toList()))
                addSpacer(panel, 6)
                rowOptions.clear()
            }
        }
        if (rowOptions.isNotEmpty()) {
            panel.addView(reminderOffsetsRow(rowOptions.toList()))
            addSpacer(panel, 6)
        }

        // Custom reminders
        val customOffsets = EventStore.getCustomReminderOffsets(this)
        if (customOffsets.isNotEmpty()) {
            panel.addView(TextView(this).apply {
                text = "Mốc tùy chỉnh"
                textSize = 11f
                setTextColor(muted)
                setTypeface(Typeface.DEFAULT, Typeface.BOLD)
                setPadding(0, dp(4), 0, dp(4))
            })
            customOffsets.chunked(2).forEach { chunk ->
                panel.addView(customReminderOffsetsRow(chunk))
                addSpacer(panel, 6)
            }
        }

        // Add custom reminder button
        panel.addView(secondaryButton("+ Thêm mốc tùy chỉnh").apply {
            setOnClickListener { showAddCustomReminderDialog() }
        }, LinearLayout.LayoutParams(match(), dp(42)))

        addSpacer(panel, 10)
        panel.addView(TextView(this).apply {
            text = "Kiểm tra hoạt động"
            textSize = 12f
            setTextColor(ink)
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        })
        addSpacer(panel, 4)
        panel.addView(TextView(this).apply {
            text = "App tự động kiểm tra deadline mới khoảng mỗi 5 phút khi có mạng. Khi phát hiện deadline mới hoặc giáo viên thay đổi sẽ thông báo kèm âm thanh."
            textSize = 11f
            setTextColor(muted)
            setPadding(0, 0, 0, dp(6))
        })
        notificationHealthText = TextView(this).apply {
            textSize = 11f
            setTextColor(muted)
            setPadding(0, 0, 0, dp(8))
        }
        panel.addView(notificationHealthText)

        panel.addView(primaryButton("Gửi test").apply {
            setOnClickListener { sendTestNotification() }
        }, LinearLayout.LayoutParams(match(), dp(42)))

        panel.addView(secondaryButton("Cài đặt thông báo Android").apply {
            setOnClickListener { openNotificationPermissionOrSettings() }
        }, LinearLayout.LayoutParams(match(), dp(42)).apply {
            topMargin = dp(6)
        })

        val healthSettingsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        healthSettingsRow.addView(secondaryButton("Cài đặt pin").apply {
            setOnClickListener { openBatteryOptimizationSettings() }
        }, LinearLayout.LayoutParams(0, dp(42), 1f))
        healthSettingsRow.addView(secondaryButton("Báo đúng giờ").apply {
            setOnClickListener { openExactAlarmSettings() }
        }, LinearLayout.LayoutParams(0, dp(42), 1f).apply {
            marginStart = dp(6)
        })
        panel.addView(healthSettingsRow, LinearLayout.LayoutParams(match(), wrap()).apply {
            topMargin = dp(6)
        })

        val resetButton = outlineButton("Test lại thông báo deadline mới").apply {
            setOnClickListener {
                EventStore.resetKnownIds(activity)
                Toast.makeText(
                    activity,
                    "Đã reset danh sách đã biết. Qua tab Lịch bấm Kiểm tra để test thông báo mới.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
        panel.addView(resetButton, LinearLayout.LayoutParams(match(), dp(42)).apply {
            topMargin = dp(8)
        })
        return panel
    }

internal fun MainActivity.emailNotificationPanel(): View {
    val activity = this
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = rounded(card, 8, line, 1)
        }

        panel.addView(TextView(this).apply {
            text = "Cảnh báo qua Email"
            textSize = 15f
            setTextColor(ink)
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        })

        panel.addView(TextView(this).apply {
            val emailStatus = if (EventStore.isEmailNotificationEnabled(activity)) {
                "Email cảnh báo đang bật cho: ${EventStore.getUserEmail(activity)}"
            } else if (EventStore.getUserEmail(activity).isNotBlank()) {
                "Email cảnh báo đang tắt. Email đã lưu: ${EventStore.getUserEmail(activity)}"
            } else {
                "Nhập email của bạn để nhận thông báo deadline qua email."
            }
            text = emailStatus
            textSize = 12f
            setTextColor(muted)
            setPadding(0, dp(5), 0, dp(8))
        })

        // Email input field
        val emailInput = EditText(this).apply {
            hint = "Nhập email của bạn (ví dụ: student@hcmute.edu.vn)"
            setSingleLine(true)
            textSize = 13f
            setTextColor(ink)
            setHintTextColor(Color.rgb(148, 163, 184))
            setPadding(dp(12), dp(10), dp(12), dp(10))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            background = rounded(Color.rgb(248, 250, 252), 8, Color.rgb(203, 213, 225), 1)
            setText(EventStore.getUserEmail(activity))
        }
        panel.addView(emailInput, LinearLayout.LayoutParams(match(), dp(44)).apply {
            bottomMargin = dp(8)
        })

        // Save and enable buttons
        val actionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        actionRow.addView(primaryButton("Lưu email").apply {
            setOnClickListener {
                val email = emailInput.text.toString().trim()
                if (email.isBlank()) {
                    Toast.makeText(activity, "Hãy nhập email của bạn.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                    Toast.makeText(activity, "Email không hợp lệ.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                EventStore.setUserEmail(activity, email)
                Toast.makeText(activity, "Đã lưu email.", Toast.LENGTH_SHORT).show()
                showNotificationSettingsTab()
            }
        }, LinearLayout.LayoutParams(0, dp(42), 1f))

        val toggleButton = if (EventStore.isEmailNotificationEnabled(this)) {
            outlineButton("Tắt email").apply {
                setOnClickListener {
                    EventStore.setEmailNotificationEnabled(activity, false)
                    Toast.makeText(activity, "Đã tắt thông báo email.", Toast.LENGTH_SHORT).show()
                    showNotificationSettingsTab()
                }
            }
        } else {
            secondaryButton("Bật email").apply {
                setOnClickListener {
                    if (EventStore.getUserEmail(activity).isBlank()) {
                        Toast.makeText(activity, "Hãy lưu email trước.", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    EventStore.setEmailNotificationEnabled(activity, true)
                    Toast.makeText(activity, "Đã bật thông báo email.", Toast.LENGTH_SHORT).show()
                    showNotificationSettingsTab()
                }
            }
        }

        actionRow.addView(toggleButton, LinearLayout.LayoutParams(0, dp(42), 1f).apply {
            marginStart = dp(8)
        })

        panel.addView(actionRow)

        // Test email button
        addSpacer(panel, 8)
        panel.addView(TextView(this).apply {
            text = "Gửi email test để kiểm tra"
            textSize = 12f
            setTextColor(ink)
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        })

        panel.addView(TextView(this).apply {
            text = "Kiểm tra xem email có nhận được thông báo không. Email test sẽ được gửi ngay lập tức."
            textSize = 11f
            setTextColor(muted)
            setPadding(0, dp(4), 0, dp(8))
        })

        panel.addView(primaryButton("Gửi email test").apply {
            setOnClickListener { sendTestEmail() }
        }, LinearLayout.LayoutParams(match(), dp(42)))

        // Info về email notification
        addSpacer(panel, 8)
        panel.addView(TextView(this).apply {
            text = "📧 Bạn sẽ nhận email khi:\n• Có deadline mới từ Moodle\n• Deadline sắp tới hạn (theo mốc nhắc đã cài)\n• Tổng hợp hàng ngày (nếu bật)"
            textSize = 11f
            setTextColor(blueDark)
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = rounded(Color.rgb(231, 242, 255), 8, Color.rgb(190, 218, 248), 1)
            setLineSpacing(0f, 1.2f)
        })

        return panel
    }

internal fun MainActivity.sendTestEmail() {
        val email = EventStore.getUserEmail(this)
        if (email.isBlank()) {
            Toast.makeText(this, "Hãy lưu email trước khi test.", Toast.LENGTH_SHORT).show()
            return
        }

        Toast.makeText(this, "Đang gửi email test...", Toast.LENGTH_SHORT).show()

        EmailNotificationService.sendTestEmail(this) { success, message ->
            runOnUiThread {
                if (success) {
                    Toast.makeText(this, "✅ $message\nKiểm tra hộp thư của bạn.", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, "❌ $message", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

internal fun MainActivity.sendTestNotification() {
        if (!NotificationHelper.canPostNotifications(this)) {
            openNotificationPermissionOrSettings()
            Toast.makeText(this, "Hãy bật quyền thông báo và kênh UTE Notice rồi bấm Gửi test lại.", Toast.LENGTH_LONG).show()
            return
        }
        if (!isIgnoringBatteryOptimizations()) {
            promptRequiredAlertSetupIfNeeded(force = true)
            Toast.makeText(this, "Cần đặt pin ở Không hạn chế để nhận cảnh báo nền.", Toast.LENGTH_LONG).show()
            return
        }
        if (!ReminderScheduler.canScheduleExactAlarms(this)) {
            promptRequiredAlertSetupIfNeeded(force = true)
            Toast.makeText(this, "Cần bật Báo đúng giờ để nhắc sát thời điểm deadline.", Toast.LENGTH_LONG).show()
            return
        }
        val sent = NotificationHelper.notifyTest(this)
        Toast.makeText(
            this,
            if (sent) "Đã gửi thông báo test." else "Chưa gửi được thông báo test. Hãy kiểm tra cài đặt thông báo.",
            Toast.LENGTH_SHORT
        ).show()
        updateNotificationChip()
        refreshNotificationHealthText()
    }

internal fun MainActivity.refreshNotificationHealthText() {
        if (!hasNotificationHealthText()) return
        val readyStatus = if (isAlertSetupReady()) {
            "✓ Trạng thái cảnh báo: SẴN SÀNG"
        } else {
            "⚠ Trạng thái cảnh báo: CHƯA SẴN SÀNG"
        }
        val notificationStatus = if (NotificationHelper.canPostNotifications(this)) {
            "✓ Thông báo/kênh cảnh báo: đã bật"
        } else {
            "✗ Thông báo/kênh cảnh báo: chưa bật hoặc bị tắt trong cài đặt Android"
        }
        val batteryStatus = if (isIgnoringBatteryOptimizations()) {
            "✓ Pin: Không hạn chế / đã bỏ tối ưu pin"
        } else {
            "✗ BẮT BUỘC: đặt Pin thành Không hạn chế để nhận cảnh báo nền"
        }
        val exactAlarmStatus = if (ReminderScheduler.canScheduleExactAlarms(this)) {
            "✓ Báo đúng giờ: đã cho phép exact alarm"
        } else {
            "✗ Báo đúng giờ: chưa cho phép, cảnh báo có thể bị trễ"
        }
        val syncStatus = EventStore.getLastSync(this).takeIf { it > 0L }?.let {
            "Sync gần nhất: ${timeFormatter.format(Instant.ofEpochMilli(it))}"
        } ?: "Chưa đồng bộ lần nào"
        notificationHealthText.text = "$readyStatus\n$notificationStatus\n$batteryStatus\n$exactAlarmStatus\n$syncStatus\nMốc nhắc: ${EventStore.reminderOffsetsText(this)} trước hạn"
    }

internal fun MainActivity.isAlertSetupReady(): Boolean {
        return NotificationHelper.canPostNotifications(this) &&
            isIgnoringBatteryOptimizations() &&
            ReminderScheduler.canScheduleExactAlarms(this)
    }

internal fun MainActivity.isIgnoringBatteryOptimizations(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isIgnoringBatteryOptimizations(packageName)
    }

internal fun MainActivity.promptRequiredAlertSetupIfNeeded(force: Boolean) {
        if (!force && setupPromptShown) return
        if (isAlertSetupReady()) return
        setupPromptShown = true

        val missing = mutableListOf<String>()
        if (!NotificationHelper.canPostNotifications(this)) {
            missing += "• Bật quyền thông báo và kênh UTE Notice - Cảnh báo/Khẩn cấp"
        }
        if (!isIgnoringBatteryOptimizations()) {
            missing += "• Đặt pin của app thành Không hạn chế / bỏ tối ưu pin"
        }
        if (!ReminderScheduler.canScheduleExactAlarms(this)) {
            missing += "• Cho phép Báo đúng giờ / Alarms & reminders"
        }

        val nextAction = when {
            !NotificationHelper.canPostNotifications(this) -> "Mở thông báo"
            !isIgnoringBatteryOptimizations() -> "Mở cài đặt pin"
            !ReminderScheduler.canScheduleExactAlarms(this) -> "Mở báo đúng giờ"
            else -> "OK"
        }

        android.app.AlertDialog.Builder(this)
            .setTitle("Cần bật đủ quyền cảnh báo")
            .setMessage("Để UTE Notice báo deadline ổn định khi tắt màn hình, cần hoàn tất:\n\n${missing.joinToString("\n")}\n\nNếu máy có mục Pin của ứng dụng, hãy chọn Không hạn chế.")
            .setPositiveButton(nextAction) { _, _ ->
                when {
                    !NotificationHelper.canPostNotifications(this) -> openNotificationPermissionOrSettings()
                    !isIgnoringBatteryOptimizations() -> openBatteryOptimizationSettings()
                    !ReminderScheduler.canScheduleExactAlarms(this) -> openExactAlarmSettings()
                }
            }
            .setNegativeButton("Để sau", null)
            .show()
    }

internal fun MainActivity.openNotificationPermissionOrSettings() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            setupPromptShown = false
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
            return
        }
        setupPromptShown = false
        openAppNotificationSettings()
    }

internal fun MainActivity.openAppNotificationSettings() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                setupPromptShown = false
                startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                })
            } else {
                setupPromptShown = false
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:$packageName")
                })
            }
        } catch (_: Exception) {
            try {
                setupPromptShown = false
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:$packageName")
                })
            } catch (_: Exception) {
                Toast.makeText(this, "Không mở được cài đặt thông báo trên máy này.", Toast.LENGTH_LONG).show()
            }
        }
    }

internal fun MainActivity.openBatteryOptimizationSettings() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !isIgnoringBatteryOptimizations()) {
                Toast.makeText(this, "Hãy chọn Cho phép / Không hạn chế pin cho UTE Notice.", Toast.LENGTH_LONG).show()
                setupPromptShown = false
                startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                })
            } else {
                Toast.makeText(this, "App đã ở chế độ Không hạn chế pin hoặc Android không hỗ trợ mục này.", Toast.LENGTH_LONG).show()
                setupPromptShown = false
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        } catch (_: Exception) {
            try {
                Toast.makeText(this, "Vào Pin > UTE Notice > chọn Không hạn chế.", Toast.LENGTH_LONG).show()
                setupPromptShown = false
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:$packageName")
                })
            } catch (_: Exception) {
                Toast.makeText(this, "Không mở được cài đặt pin trên máy này.", Toast.LENGTH_LONG).show()
            }
        }
    }

internal fun MainActivity.openExactAlarmSettings() {
        if (ReminderScheduler.canScheduleExactAlarms(this)) {
            Toast.makeText(this, "Máy đã cho phép app báo đúng giờ.", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setupPromptShown = false
                startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    data = Uri.parse("package:$packageName")
                })
            } else {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:$packageName")
                })
            }
        } catch (_: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:$packageName")
                })
            } catch (_: Exception) {
                Toast.makeText(this, "Không mở được cài đặt báo đúng giờ.", Toast.LENGTH_LONG).show()
            }
        }
    }

internal fun MainActivity.daysRow(options: List<DayOption>): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        options.forEachIndexed { index, option ->
            row.addView(dayButton(option), LinearLayout.LayoutParams(0, dp(38), 1f).apply {
                if (index > 0) marginStart = dp(6)
            })
        }
        return row
    }

internal fun MainActivity.dayButton(option: DayOption): Button {
    val activity = this
        val mask = EventStore.getDailySummaryDaysMask(this)
        val bit = 1 shl option.bitIndex
        val selected = EventStore.isDailySummaryEnabled(this) && mask and bit != 0
        return Button(this).apply {
            text = option.label
            textSize = 12f
            setAllCaps(false)
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            setTextColor(if (selected) Color.WHITE else blue)
            background = if (selected) rounded(blue, 8) else rounded(Color.rgb(232, 242, 255), 8, Color.rgb(178, 209, 245), 1)
            setOnClickListener {
                val currentMask = if (EventStore.isDailySummaryEnabled(activity)) {
                    EventStore.getDailySummaryDaysMask(activity)
                } else {
                    0
                }
                val nextMask = if (currentMask and bit != 0) currentMask and bit.inv() else currentMask or bit
                EventStore.setDailySummaryDaysMask(activity, nextMask)
                ReminderScheduler.scheduleDailySummary(activity)
                showNotificationSettingsTab()
            }
        }
    }

internal fun MainActivity.reminderOffsetsRow(minutesOptions: List<Long>): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        minutesOptions.forEachIndexed { index, minutes ->
            row.addView(reminderOffsetButton(minutes), LinearLayout.LayoutParams(0, dp(38), 1f).apply {
                if (index > 0) marginStart = dp(6)
            })
        }
        return row
    }

internal fun MainActivity.reminderOffsetButton(minutes: Long): Button {
    val activity = this
        val selected = EventStore.isReminderOffsetEnabled(this, minutes)
        return Button(this).apply {
            text = EventStore.reminderOptionLabel(minutes)
            textSize = 11f
            setAllCaps(false)
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            setTextColor(if (selected) Color.WHITE else blue)
            background = if (selected) rounded(blue, 8) else rounded(Color.rgb(232, 242, 255), 8, Color.rgb(178, 209, 245), 1)
            setOnClickListener {
                val enabledCount = EventStore.getReminderOffsetsMinutes(activity).size
                if (selected && enabledCount <= 1) {
                    Toast.makeText(activity, "Cần giữ ít nhất 1 mốc nhắc.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                EventStore.setReminderOffsetEnabled(activity, minutes, !selected)
                ReminderScheduler.scheduleAll(activity, activeEventsForReminders())
                showNotificationSettingsTab()
            }
        }
    }

internal fun MainActivity.customReminderOffsetsRow(minutesOptions: List<Long>): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        minutesOptions.forEachIndexed { index, minutes ->
            row.addView(customReminderOffsetButton(minutes), LinearLayout.LayoutParams(0, dp(38), 1f).apply {
                if (index > 0) marginStart = dp(6)
            })
        }
        return row
    }

internal fun MainActivity.customReminderOffsetButton(minutes: Long): View {
    val activity = this
        val selected = EventStore.isReminderOffsetEnabled(this, minutes)
        // Card bọc button + nút xóa
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = if (selected)
                rounded(blue, 8)
            else
                rounded(Color.rgb(232, 242, 255), 8, Color.rgb(178, 209, 245), 1)
        }
        val label = TextView(this).apply {
            text = EventStore.reminderOptionLabel(minutes)
            textSize = 11f
            setAllCaps(false)
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            setTextColor(if (selected) Color.WHITE else blue)
            gravity = Gravity.CENTER
            setPadding(dp(4), 0, 0, 0)
        }
        container.addView(label, LinearLayout.LayoutParams(0, dp(38), 1f))

        // Nút xóa nhỏ "✕"
        val removeBtn = TextView(this).apply {
            text = "✕"
            textSize = 11f
            setTextColor(if (selected) Color.argb(180, 255, 255, 255) else Color.rgb(150, 100, 100))
            gravity = Gravity.CENTER
            setPadding(0, 0, dp(6), 0)
            setOnClickListener {
                val enabledCount = EventStore.getReminderOffsetsMinutes(activity).size
                if (selected && enabledCount <= 1) {
                    Toast.makeText(activity, "Cần giữ ít nhất 1 mốc nhắc.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                showRemoveCustomReminderDialog(minutes)
            }
        }
        container.addView(removeBtn, LinearLayout.LayoutParams(dp(24), dp(38)))

        container.setOnClickListener {
            val enabledCount = EventStore.getReminderOffsetsMinutes(activity).size
            if (selected && enabledCount <= 1) {
                Toast.makeText(this, "Cần giữ ít nhất 1 mốc nhắc.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            EventStore.setReminderOffsetEnabled(this, minutes, !selected)
            ReminderScheduler.scheduleAll(this, activeEventsForReminders())
            showNotificationSettingsTab()
        }
        return container
    }

internal fun MainActivity.showRemoveCustomReminderDialog(minutes: Long) {
        val label = EventStore.reminderOptionLabel(minutes)
        android.app.AlertDialog.Builder(this)
            .setTitle("Xóa mốc tùy chỉnh")
            .setMessage("Xóa mốc nhắc \"$label\" khỏi danh sách?")
            .setPositiveButton("Xóa") { _, _ ->
                EventStore.removeCustomReminderOffset(this, minutes)
                ReminderScheduler.scheduleAll(this, activeEventsForReminders())
                showNotificationSettingsTab()
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

internal fun MainActivity.reminderTimeInput(labelText: String, input: EditText): View {
    val activity = this
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(8))

            addView(TextView(activity).apply {
                text = labelText
                textSize = 14f
                setTextColor(ink)
                setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            }, LinearLayout.LayoutParams(dp(72), dp(44)))

            addView(input, LinearLayout.LayoutParams(0, dp(44), 1f))
        }
    }

internal fun MainActivity.reminderNumberInput(): EditText {
        return EditText(this).apply {
            hint = "0"
            inputType = InputType.TYPE_CLASS_NUMBER
            textSize = 16f
            setTextColor(ink)
            setHintTextColor(Color.rgb(148, 163, 184))
            gravity = Gravity.CENTER
            background = rounded(Color.rgb(248, 250, 252), 8, Color.rgb(203, 213, 225), 1)
            setPadding(dp(10), dp(8), dp(10), dp(8))
            setSelectAllOnFocus(true)
        }
    }


internal fun MainActivity.dailySummaryTimesRow(times: List<Int>): View {
    val row = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    times.forEachIndexed { index, minutes ->
        row.addView(dailySummaryTimeButton(minutes), LinearLayout.LayoutParams(0, dp(38), 1f).apply {
            if (index > 0) marginStart = dp(6)
        })
    }
    return row
}

internal fun MainActivity.dailySummaryTimeButton(minutes: Int): View {
    val activity = this
    val label = "%02d:%02d".format(minutes / 60, minutes % 60)

    return LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = rounded(Color.rgb(232, 242, 255), 8, Color.rgb(178, 209, 245), 1)

        addView(TextView(activity).apply {
            text = label
            textSize = 12f
            setTextColor(blue)
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(0, dp(38), 1f))

        addView(TextView(activity).apply {
            text = "✕"
            textSize = 12f
            setTextColor(red)
            gravity = Gravity.CENTER
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            setOnClickListener {
                EventStore.removeDailySummaryTime(activity, minutes)
                ReminderScheduler.scheduleDailySummary(activity)
                Toast.makeText(activity, "Đã xóa giờ tổng hợp $label.", Toast.LENGTH_SHORT).show()
                showNotificationSettingsTab()
            }
        }, LinearLayout.LayoutParams(dp(34), dp(38)))
    }
}

internal fun MainActivity.showAddCustomReminderDialog() {
        val dialogLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(14), dp(18), dp(4))
        }

        dialogLayout.addView(TextView(this).apply {
            text = "Nhắc trước bao lâu?"
            textSize = 13f
            setTextColor(muted)
            setPadding(0, 0, 0, dp(10))
        })

        val daysInput = reminderNumberInput()
        val hoursInput = reminderNumberInput()
        val minutesInput = reminderNumberInput()

        dialogLayout.addView(reminderTimeInput("Ngày", daysInput))
        dialogLayout.addView(reminderTimeInput("Giờ", hoursInput))
        dialogLayout.addView(reminderTimeInput("Phút", minutesInput))

        val previewText = TextView(this).apply {
            text = "Nhập số ngày/giờ/phút"
            textSize = 12f
            setTextColor(muted)
            setPadding(0, dp(2), 0, dp(2))
            gravity = Gravity.CENTER
        }
        dialogLayout.addView(previewText, LinearLayout.LayoutParams(match(), wrap()))

        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                val d = daysInput.text.toString().toLongOrNull() ?: 0L
                val h = hoursInput.text.toString().toLongOrNull() ?: 0L
                val m = minutesInput.text.toString().toLongOrNull() ?: 0L
                val total = d * 24L * 60L + h * 60L + m
                previewText.text = if (total > 0) {
                    "→ Nhắc trước ${EventStore.reminderOptionLabel(total)}"
                } else {
                    "Nhập số ngày/giờ/phút"
                }
                previewText.setTextColor(if (total > 0) blueDark else muted)
            }
        }
        daysInput.addTextChangedListener(watcher)
        hoursInput.addTextChangedListener(watcher)
        minutesInput.addTextChangedListener(watcher)

        val dialog = android.app.AlertDialog.Builder(this)
            .setTitle("Thêm mốc nhắc tùy chỉnh")
            .setView(dialogLayout)
            .setPositiveButton("Thêm", null)
            .setNegativeButton("Hủy", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val d = daysInput.text.toString().toLongOrNull() ?: 0L
                val h = hoursInput.text.toString().toLongOrNull() ?: 0L
                val m = minutesInput.text.toString().toLongOrNull() ?: 0L
                val total = d * 24L * 60L + h * 60L + m
                when {
                    total <= 0 -> {
                        Toast.makeText(this, "Hãy nhập ít nhất 1 phút.", Toast.LENGTH_SHORT).show()
                    }
                    total > 30L * 24L * 60L -> {
                        Toast.makeText(this, "Mốc nhắc tối đa 30 ngày.", Toast.LENGTH_SHORT).show()
                    }
                    EventStore.getAllReminderOffsetOptions(this).contains(total) -> {
                        EventStore.setReminderOffsetEnabled(this, total, true)
                        ReminderScheduler.scheduleAll(this, activeEventsForReminders())
                        Toast.makeText(this, "Đã bật mốc ${EventStore.reminderOptionLabel(total)}.", Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                        showNotificationSettingsTab()
                    }
                    else -> {
                        EventStore.addCustomReminderOffset(this, total)
                        ReminderScheduler.scheduleAll(this, activeEventsForReminders())
                        Toast.makeText(this, "Đã thêm mốc ${EventStore.reminderOptionLabel(total)}.", Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                        showNotificationSettingsTab()
                    }
                }
            }
        }
        dialog.show()
    }

internal fun MainActivity.showAddDailySummaryTimePicker() {
        TimePickerDialog(
            this,
            { _, hourOfDay, minute ->
                val added = EventStore.addDailySummaryTime(this, hourOfDay, minute)
                if (EventStore.getDailySummaryDaysMask(this) == 0) {
                    EventStore.setDailySummaryDaysMask(this, EventStore.ALL_DAYS_MASK)
                }
                ReminderScheduler.scheduleDailySummary(this)
                Toast.makeText(
                    this,
                    if (added) {
                        "Đã thêm giờ tổng hợp %02d:%02d.".format(hourOfDay, minute)
                    } else {
                        "Giờ này đã có hoặc đã đạt giới hạn 6 khung giờ."
                    },
                    Toast.LENGTH_SHORT
                ).show()
                showNotificationSettingsTab()
            },
            EventStore.getDailySummaryTimes(this).firstOrNull()?.div(60) ?: 6,
            EventStore.getDailySummaryTimes(this).firstOrNull()?.rem(60) ?: 0,
            true
        ).show()
    }

internal fun MainActivity.showDailyTimePicker() {
        showAddDailySummaryTimePicker()
    }

internal fun MainActivity.refreshDailySummaryText() {
        if (!hasDailySummaryText()) return
        dailySummaryText.text = if (EventStore.isDailySummaryEnabled(this)) {
            "App nhắc tổng hợp lúc ${EventStore.dailySummaryTimesText(this)} vào ${summaryDaysText()}."
        } else {
            "Thông báo tổng hợp hằng ngày đang tắt. Nhắc trước hạn vẫn hoạt động."
        }
    }

internal fun MainActivity.summaryTimeText(): String {
        return "%02d:%02d".format(EventStore.getDailySummaryHour(this), EventStore.getDailySummaryMinute(this))
    }

internal fun MainActivity.summaryDaysText(): String {
        val mask = EventStore.getDailySummaryDaysMask(this)
        if (mask == EventStore.ALL_DAYS_MASK) return "mỗi ngày"
        val labels = listOf("T2", "T3", "T4", "T5", "T6", "T7", "CN")
        return labels.filterIndexed { index, _ -> mask and (1 shl index) != 0 }
            .joinToString(", ")
            .ifBlank { "chưa chọn ngày" }
    }
