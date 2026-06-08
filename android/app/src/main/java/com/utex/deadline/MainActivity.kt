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

class MainActivity : Activity() {
    private val moodleExportUrl = "https://utexlms.hcmute.edu.vn/calendar/export.php?"
    private val githubGuideUrl = "https://vanity1412.github.io/notice-app-utex/"
    private val supportContact = "Vũ Văn Thông - 0968046024"

    private lateinit var urlInput: EditText
    private lateinit var statusText: TextView
    private lateinit var eventsContainer: LinearLayout
    private lateinit var eventCountText: TextView
    private lateinit var notificationChip: TextView
    private lateinit var dailySummaryText: TextView
    private lateinit var notificationHealthText: TextView
    private lateinit var tabContent: LinearLayout
    private lateinit var calendarTab: TextView
    private lateinit var guideTab: TextView
    private var activeTab = ScreenTab.CALENDAR
    private var connectionExpanded = false
    private var eventViewMode = EventViewMode.LIST
    private var visibleMonth: YearMonth? = null
    private var eventSearchText = ""
    private var selectedFilter = EventFilter.ALL
    private var hideDoneEvents = true

    private val localZone = ZoneId.of("Asia/Ho_Chi_Minh")
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm - EEEE dd/MM/yyyy", Locale.forLanguageTag("vi-VN"))
        .withZone(localZone)
    private val dayFormatter = DateTimeFormatter.ofPattern("dd", Locale.forLanguageTag("vi-VN"))
        .withZone(localZone)
    private val monthFormatter = DateTimeFormatter.ofPattern("MM/yyyy", Locale.forLanguageTag("vi-VN"))
        .withZone(localZone)
    private val clockFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.forLanguageTag("vi-VN"))
        .withZone(localZone)
    private val monthTitleFormatter = DateTimeFormatter.ofPattern("'Tháng' M yyyy", Locale.forLanguageTag("vi-VN"))
    private val dayGroupFormatter = DateTimeFormatter.ofPattern("EEEE dd/MM/yyyy", Locale.forLanguageTag("vi-VN"))

    private val blue = Color.rgb(0, 82, 156)
    private val blueDark = Color.rgb(0, 54, 111)
    private val red = Color.rgb(218, 37, 41)
    private val green = Color.rgb(24, 128, 88)
    private val amber = Color.rgb(202, 116, 0)
    private val ink = Color.rgb(30, 41, 59)
    private val muted = Color.rgb(100, 116, 139)
    private val page = Color.rgb(244, 247, 251)
    private val card = Color.WHITE
    private val line = Color.rgb(222, 229, 238)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applySystemBars()
        NotificationHelper.ensureChannel(this)
        requestNotificationPermissionIfNeeded()
        buildUi()
        ReminderScheduler.schedulePeriodicSync(this)
        ReminderScheduler.scheduleDailySummary(this)
    }

    override fun onResume() {
        super.onResume()
        queueSyncIfStale()
        if (::notificationChip.isInitialized) updateNotificationChip()
        if (::notificationHealthText.isInitialized) refreshNotificationHealthText()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(page)
        }

        root.addView(headerView())

        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), 0)
        }
        root.addView(body, LinearLayout.LayoutParams(match(), 0, 1f))

        body.addView(tabBar())
        addSpacer(body, 8)

        tabContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        
        val scrollView = ScrollView(this).apply {
            isFillViewport = false
            clipToPadding = false
            addView(tabContent)
        }
        body.addView(scrollView, LinearLayout.LayoutParams(match(), 0, 1f))

        setContentView(root)
        showCalendarTab()
        updateNotificationChip()
    }

    private fun tabBar(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(Color.rgb(232, 239, 248), 8)
            setPadding(dp(3), dp(3), dp(3), dp(3))
        }
        calendarTab = tabButton("Lịch sắp tới").apply {
            setOnClickListener {
                if (activeTab != ScreenTab.CALENDAR) showCalendarTab()
            }
        }
        guideTab = tabButton("Hướng dẫn").apply {
            setOnClickListener {
                if (activeTab != ScreenTab.GUIDE) showGuideTab()
            }
        }
        row.addView(calendarTab, LinearLayout.LayoutParams(0, dp(42), 1f))
        row.addView(guideTab, LinearLayout.LayoutParams(0, dp(42), 1f).apply {
            marginStart = dp(4)
        })
        return row
    }

    private fun tabButton(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 13f
            gravity = Gravity.CENTER
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            setPadding(dp(10), 0, dp(10), 0)
        }
    }

    private fun showCalendarTab() {
        activeTab = ScreenTab.CALENDAR
        updateTabButtons()
        tabContent.removeAllViews()

        tabContent.addView(connectionPanel())
        addSpacer(tabContent, 8)

        statusText = TextView(this).apply {
            textSize = 12f
            setTextColor(blueDark)
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = rounded(Color.rgb(231, 242, 255), 8, Color.rgb(190, 218, 248), 1)
        }
        tabContent.addView(statusText, LinearLayout.LayoutParams(match(), wrap()))

        addSpacer(tabContent, 10)
        tabContent.addView(sectionHeader())
        addSpacer(tabContent, 8)
        tabContent.addView(filterPanel())
        addSpacer(tabContent, 8)

        eventsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(16))
        }
        tabContent.addView(eventsContainer, LinearLayout.LayoutParams(match(), wrap()))

        setStatus(calendarDefaultStatus(), StatusType.INFO)
        updateLastSyncStatus()
        refreshEventsList()
    }

    private fun showGuideTab() {
        activeTab = ScreenTab.GUIDE
        updateTabButtons()
        tabContent.removeAllViews()

        tabContent.addView(quickGuidePanel())
        addSpacer(tabContent, 8)
        tabContent.addView(notificationSettingsPanel())
        addSpacer(tabContent, 16)

        refreshDailySummaryText()
        refreshNotificationHealthText()
    }

    private fun updateTabButtons() {
        if (!::calendarTab.isInitialized || !::guideTab.isInitialized) return
        val selectedBackground = rounded(card, 8, line, 1)
        val idleBackground = rounded(Color.TRANSPARENT, 8)
        calendarTab.background = if (activeTab == ScreenTab.CALENDAR) selectedBackground else idleBackground
        guideTab.background = if (activeTab == ScreenTab.GUIDE) selectedBackground else idleBackground
        calendarTab.setTextColor(if (activeTab == ScreenTab.CALENDAR) blueDark else muted)
        guideTab.setTextColor(if (activeTab == ScreenTab.GUIDE) blueDark else muted)
    }

    private fun headerView(): View {
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = rounded(blue, 0)
        }
        header.addView(hcmuteLogoView(48), LinearLayout.LayoutParams(dp(48), dp(48)))

        val textGroup = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), 0, 0, 0)
        }
        header.addView(textGroup, LinearLayout.LayoutParams(0, wrap(), 1f))

        textGroup.addView(TextView(this).apply {
            text = "UTE Notice"
            textSize = 20f
            setTextColor(Color.WHITE)
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            includeFontPadding = false
        })
        textGroup.addView(TextView(this).apply {
            text = "HCM-UTE Moodle Calendar"
            textSize = 11f
            setTextColor(Color.rgb(218, 235, 255))
            setPadding(0, dp(2), 0, 0)
        })

        notificationChip = chip("Đang kiểm tra", Color.WHITE, Color.argb(40, 255, 255, 255))
        header.addView(notificationChip)
        return header
    }

    private fun connectionPanel(): View {
        val savedUrl = EventStore.getIcalUrl(this)
        val hasUrl = savedUrl.isNotBlank()
        if (hasUrl && !connectionExpanded) {
            return compactConnectionRow()
        }

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(10), dp(10), dp(10))
            background = rounded(card, 8, line, 1)
        }
        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        titleRow.addView(TextView(this).apply {
            text = if (hasUrl) "Chỉnh kết nối Moodle" else "Kết nối Moodle"
            textSize = 16f
            setTextColor(ink)
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        }, LinearLayout.LayoutParams(0, wrap(), 1f))
        titleRow.addView(outlineButton("Hướng dẫn").apply {
            setOnClickListener { showGuideTab() }
        }, LinearLayout.LayoutParams(dp(104), dp(36)).apply {
            marginStart = dp(8)
        })
        if (hasUrl) {
            titleRow.addView(chip("Thu gọn", blue, Color.rgb(226, 238, 252)).apply {
                setOnClickListener {
                    connectionExpanded = false
                    showCalendarTab()
                }
            }, LinearLayout.LayoutParams(wrap(), dp(36)).apply {
                marginStart = dp(8)
            })
        }
        panel.addView(titleRow)
        addSpacer(panel, 8)

        urlInput = EditText(this).apply {
            hint = "Dán link export_execute.php của Moodle"
            setSingleLine(false)
            minLines = 2
            maxLines = 4
            textSize = 13f
            setTextColor(ink)
            setHintTextColor(Color.rgb(148, 163, 184))
            setPadding(dp(12), dp(10), dp(12), dp(10))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            background = rounded(Color.rgb(248, 250, 252), 8, Color.rgb(203, 213, 225), 1)
            setText(savedUrl)
        }
        panel.addView(urlInput, LinearLayout.LayoutParams(match(), wrap()))

        val urlActions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, 0)
        }
        urlActions.addView(secondaryButton("Dán clipboard").apply {
            setOnClickListener { pasteCalendarUrlFromClipboard() }
        }, LinearLayout.LayoutParams(0, dp(42), 1f))
        if (hasUrl) {
            urlActions.addView(outlineButton("Xóa kết nối").apply {
                setOnClickListener { clearMoodleConnection() }
            }, LinearLayout.LayoutParams(0, dp(42), 1f).apply {
                marginStart = dp(8)
            })
        }
        panel.addView(urlActions)

        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(10), 0, 0)
        }

        val saveButton = primaryButton("Lưu & đồng bộ").apply {
            setOnClickListener {
                saveCalendarUrlAndSync()
            }
        }
        buttons.addView(saveButton, LinearLayout.LayoutParams(0, dp(48), 1f))

        val syncButton = secondaryButton("Kiểm tra").apply {
            setOnClickListener {
                saveCalendarUrlAndSync()
            }
        }
        val syncLp = LinearLayout.LayoutParams(0, dp(48), 1f).apply {
            marginStart = dp(8)
        }
        buttons.addView(syncButton, syncLp)
        panel.addView(buttons)
        return panel
    }

    private fun compactConnectionRow(): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = rounded(card, 8, line, 1)
            setOnClickListener {
                connectionExpanded = true
                showCalendarTab()
            }

            val titleRow = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            titleRow.addView(TextView(this@MainActivity).apply {
                text = "Kết nối Moodle"
                textSize = 14f
                setTextColor(ink)
                setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            }, LinearLayout.LayoutParams(0, wrap(), 1f))
            titleRow.addView(chip("Đã kết nối", green, Color.rgb(229, 248, 239)))
            addView(titleRow)

            addView(TextView(this@MainActivity).apply {
                text = MoodleUrlValidator.mask(EventStore.getIcalUrl(this@MainActivity))
                textSize = 11f
                setTextColor(muted)
                setPadding(0, dp(4), 0, dp(6))
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            })

            val actionRow = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            actionRow.addView(secondaryButton("Chỉnh").apply {
                setOnClickListener {
                    connectionExpanded = true
                    showCalendarTab()
                }
            }, LinearLayout.LayoutParams(0, dp(34), 1f))
            actionRow.addView(outlineButton("Xóa").apply {
                setOnClickListener { clearMoodleConnection() }
            }, LinearLayout.LayoutParams(0, dp(34), 1f).apply {
                marginStart = dp(5)
            })
            actionRow.addView(outlineButton("Hướng dẫn").apply {
                setOnClickListener { showGuideTab() }
            }, LinearLayout.LayoutParams(0, dp(34), 1f).apply {
                marginStart = dp(5)
            })
            addView(actionRow)
        }
    }

    private fun saveCalendarUrlAndSync() {
        val validation = MoodleUrlValidator.validate(urlInput.text.toString())
        if (!validation.ok) {
            setStatus(validation.message, StatusType.ERROR)
            urlInput.requestFocus()
            return
        }
        EventStore.setIcalUrl(this, validation.normalizedUrl)
        ReminderScheduler.schedulePeriodicSync(this)
        connectionExpanded = false
        showCalendarTab()
        syncNow()
    }

    private fun pasteCalendarUrlFromClipboard() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = clipboard.primaryClip
            ?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)
            ?.coerceToText(this)
            ?.toString()
            ?.trim()
            .orEmpty()
        if (text.isBlank()) {
            Toast.makeText(this, "Clipboard đang trống.", Toast.LENGTH_SHORT).show()
            return
        }
        urlInput.setText(text)
        urlInput.setSelection(urlInput.text.length)
        val validation = MoodleUrlValidator.validate(text)
        setStatus(validation.message, if (validation.ok) StatusType.SUCCESS else StatusType.ERROR)
    }

    private fun clearMoodleConnection() {
        EventStore.clearConnection(this)
        ReminderScheduler.cancelAll(this)
        connectionExpanded = true
        Toast.makeText(this, "Đã xóa kết nối Moodle.", Toast.LENGTH_SHORT).show()
        showCalendarTab()
    }

    private fun quickGuidePanel(): View {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = rounded(card, 8, line, 1)
        }
        panel.addView(TextView(this).apply {
            text = "Hướng dẫn nhanh"
            textSize = 14f
            setTextColor(ink)
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        })
        addSpacer(panel, 4)
        panel.addView(TextView(this).apply {
            text = "1. Copy link ở dưới\n2. Dán vào trình duyệt đã đăng nhập UTExLMS\n3. Chọn lịch, bấm Lấy địa chỉ mạng\n4. Copy Calendar URL, dán vào app & Lưu"
            textSize = 11f
            setTextColor(muted)
            setLineSpacing(0f, 1.05f)
        })
        addSpacer(panel, 8)

        val links = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        links.addView(secondaryButton("Mở Moodle").apply {
            setOnClickListener { openUrl(moodleExportUrl) }
        }, LinearLayout.LayoutParams(0, dp(42), 1f))
        val guideLp = LinearLayout.LayoutParams(0, dp(42), 1f).apply {
            marginStart = dp(8)
        }
        links.addView(outlineButton("Xem GitHub").apply {
            setOnClickListener { openUrl(githubGuideUrl) }
        }, guideLp)
        panel.addView(links)

        addSpacer(panel, 8)
        panel.addView(TextView(this).apply {
            text = "Link trang xuất lịch Moodle:"
            textSize = 12f
            setTextColor(muted)
        })
        addSpacer(panel, 6)
        panel.addView(exportUrlRow())

        addSpacer(panel, 8)
        panel.addView(TextView(this).apply {
            text = "Hỗ trợ: $supportContact"
            textSize = 12f
            setTextColor(blueDark)
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        })
        return panel
    }

    private fun exportUrlRow(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(8), dp(8), dp(8))
            background = rounded(Color.rgb(248, 250, 252), 8, Color.rgb(203, 213, 225), 1)
        }
        row.addView(TextView(this).apply {
            text = moodleExportUrl
            textSize = 12f
            setTextColor(blueDark)
            setSingleLine(false)
        }, LinearLayout.LayoutParams(0, wrap(), 1f))
        row.addView(secondaryButton("Copy").apply {
            setOnClickListener { copyMoodleExportUrl() }
        }, LinearLayout.LayoutParams(dp(82), dp(38)))
        return row
    }

    private fun copyMoodleExportUrl() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Moodle export URL", moodleExportUrl))
        Toast.makeText(this, "Đã copy link Moodle", Toast.LENGTH_SHORT).show()
    }

    private fun notificationSettingsPanel(): View {
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

        val timeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        timeRow.addView(primaryButton("Chọn giờ").apply {
            setOnClickListener { showDailyTimePicker() }
        }, LinearLayout.LayoutParams(0, dp(42), 1f))
        timeRow.addView(secondaryButton("Mỗi ngày").apply {
            setOnClickListener {
                EventStore.setDailySummaryDaysMask(this@MainActivity, EventStore.ALL_DAYS_MASK)
                ReminderScheduler.scheduleDailySummary(this@MainActivity)
                showGuideTab()
            }
        }, LinearLayout.LayoutParams(0, dp(42), 1f).apply {
            marginStart = dp(6)
        })
        timeRow.addView(outlineButton("Tắt").apply {
            setOnClickListener {
                EventStore.setDailySummaryEnabled(this@MainActivity, false)
                ReminderScheduler.scheduleDailySummary(this@MainActivity)
                showGuideTab()
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
            text = "Mặc định bật 1 ngày, 12 giờ và 1 giờ. Có thể thêm/bớt mốc, nhưng app luôn giữ ít nhất 1 mốc nhắc."
            textSize = 12f
            setTextColor(muted)
            setPadding(0, dp(4), 0, dp(6))
        })
        panel.addView(reminderOffsetsRow(listOf(2L * 24L * 60L, 24L * 60L, 12L * 60L)))
        addSpacer(panel, 6)
        panel.addView(reminderOffsetsRow(listOf(3L * 60L, 60L, 30L)))

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

        val healthRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        healthRow.addView(primaryButton("Gửi test").apply {
            setOnClickListener { sendTestNotification() }
        }, LinearLayout.LayoutParams(0, dp(42), 1f))
        healthRow.addView(secondaryButton("Cài đặt pin").apply {
            setOnClickListener { openBatteryOptimizationSettings() }
        }, LinearLayout.LayoutParams(0, dp(42), 1f).apply {
            marginStart = dp(6)
        })
        panel.addView(healthRow)

        val resetButton = outlineButton("Test lại thông báo deadline mới").apply {
            setOnClickListener {
                EventStore.resetKnownIds(this@MainActivity)
                setStatus("Đã reset danh sách đã biết. Qua tab Lịch bấm Kiểm tra để test thông báo mới.", StatusType.SUCCESS)
            }
        }
        panel.addView(resetButton, LinearLayout.LayoutParams(match(), dp(42)).apply {
            topMargin = dp(8)
        })
        return panel
    }

    private fun sendTestNotification() {
        val granted = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
            Toast.makeText(this, "Hãy cấp quyền thông báo rồi bấm Gửi test lại.", Toast.LENGTH_LONG).show()
            return
        }
        NotificationHelper.notifyTest(this)
        Toast.makeText(this, "Đã gửi thông báo test.", Toast.LENGTH_SHORT).show()
        updateNotificationChip()
        refreshNotificationHealthText()
    }

    private fun refreshNotificationHealthText() {
        if (!::notificationHealthText.isInitialized) return
        val notificationStatus = if (Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        ) {
            "✓ Quyền thông báo: đã cấp"
        } else {
            "✗ Quyền thông báo: chưa cấp"
        }
        val batteryStatus = if (isIgnoringBatteryOptimizations()) {
            "✓ Tối ưu pin: đã tắt (tốt)"
        } else {
            "⚠ Tối ưu pin: nên tắt để nhận thông báo đầy đủ"
        }
        val syncStatus = EventStore.getLastSync(this).takeIf { it > 0L }?.let {
            "Sync gần nhất: ${timeFormatter.format(Instant.ofEpochMilli(it))}"
        } ?: "Chưa đồng bộ lần nào"
        notificationHealthText.text = "$notificationStatus\n$batteryStatus\n$syncStatus\nMốc nhắc: ${EventStore.reminderOffsetsText(this)} trước hạn"
    }

    private fun isIgnoringBatteryOptimizations(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isIgnoringBatteryOptimizations(packageName)
    }

    private fun openBatteryOptimizationSettings() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !isIgnoringBatteryOptimizations()) {
                startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                })
            } else {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        } catch (_: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:$packageName")
                })
            } catch (_: Exception) {
                Toast.makeText(this, "Không mở được cài đặt pin trên máy này.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun daysRow(options: List<DayOption>): View {
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

    private fun dayButton(option: DayOption): Button {
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
                val currentMask = if (EventStore.isDailySummaryEnabled(this@MainActivity)) {
                    EventStore.getDailySummaryDaysMask(this@MainActivity)
                } else {
                    0
                }
                val nextMask = if (currentMask and bit != 0) currentMask and bit.inv() else currentMask or bit
                EventStore.setDailySummaryDaysMask(this@MainActivity, nextMask)
                ReminderScheduler.scheduleDailySummary(this@MainActivity)
                showGuideTab()
            }
        }
    }

    private fun reminderOffsetsRow(minutesOptions: List<Long>): View {
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

    private fun reminderOffsetButton(minutes: Long): Button {
        val selected = EventStore.isReminderOffsetEnabled(this, minutes)
        return Button(this).apply {
            text = EventStore.reminderOptionLabel(minutes)
            textSize = 11f
            setAllCaps(false)
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            setTextColor(if (selected) Color.WHITE else blue)
            background = if (selected) rounded(blue, 8) else rounded(Color.rgb(232, 242, 255), 8, Color.rgb(178, 209, 245), 1)
            setOnClickListener {
                val enabledCount = EventStore.getReminderOffsetsMinutes(this@MainActivity).size
                if (selected && enabledCount <= 1) {
                    Toast.makeText(this@MainActivity, "Cần giữ ít nhất 1 mốc nhắc.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                EventStore.setReminderOffsetEnabled(this@MainActivity, minutes, !selected)
                ReminderScheduler.scheduleAll(this@MainActivity, activeEventsForReminders())
                showGuideTab()
            }
        }
    }

    private fun showDailyTimePicker() {
        TimePickerDialog(
            this,
            { _, hourOfDay, minute ->
                EventStore.setDailySummaryTime(this, hourOfDay, minute)
                if (EventStore.getDailySummaryDaysMask(this) == 0) {
                    EventStore.setDailySummaryDaysMask(this, EventStore.ALL_DAYS_MASK)
                }
                ReminderScheduler.scheduleDailySummary(this)
                showGuideTab()
            },
            EventStore.getDailySummaryHour(this),
            EventStore.getDailySummaryMinute(this),
            true
        ).show()
    }

    private fun refreshDailySummaryText() {
        if (!::dailySummaryText.isInitialized) return
        dailySummaryText.text = if (EventStore.isDailySummaryEnabled(this)) {
            "App nhắc tổng hợp lúc ${summaryTimeText()} vào ${summaryDaysText()}."
        } else {
            "Thông báo tổng hợp hằng ngày đang tắt. Nhắc trước hạn vẫn hoạt động."
        }
    }

    private fun summaryTimeText(): String {
        return "%02d:%02d".format(EventStore.getDailySummaryHour(this), EventStore.getDailySummaryMinute(this))
    }

    private fun summaryDaysText(): String {
        val mask = EventStore.getDailySummaryDaysMask(this)
        if (mask == EventStore.ALL_DAYS_MASK) return "mỗi ngày"
        val labels = listOf("T2", "T3", "T4", "T5", "T6", "T7", "CN")
        return labels.filterIndexed { index, _ -> mask and (1 shl index) != 0 }
            .joinToString(", ")
            .ifBlank { "chưa chọn ngày" }
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: Exception) {
            setStatus("Không mở được liên kết trên máy này.", StatusType.ERROR)
        }
    }

    private fun openEventMoodle(event: DeadlineEvent) {
        openUrl(event.sourceUrl?.takeIf { it.isNotBlank() } ?: "https://utexlms.hcmute.edu.vn/calendar/view.php?view=month")
    }

    private fun copyEventInfo(event: DeadlineEvent) {
        val instant = Instant.ofEpochMilli(event.startAtMillis)
        val lines = mutableListOf(
            "${EventLabels.kind(event)}: ${event.title}",
            "${EventLabels.timeLabel(event)}: ${timeFormatter.format(instant)}"
        )
        EventLabels.course(event)?.let { lines += "Môn/Lớp: $it" }
        EventLabels.cleanDescription(event)?.let { lines += it }
        event.sourceUrl?.takeIf { it.isNotBlank() }?.let { lines += "Link Moodle: $it" }
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("UTE Notice deadline", lines.joinToString("\n")))
        Toast.makeText(this, "Đã copy thông tin deadline.", Toast.LENGTH_SHORT).show()
    }

    private fun sectionHeader(): View {
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, 0)
        }
        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        titleRow.addView(TextView(this).apply {
            text = "Lịch sắp tới"
            textSize = 16f
            setTextColor(ink)
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        }, LinearLayout.LayoutParams(0, wrap(), 1f))
        eventCountText = chip("0 mục", blue, Color.rgb(226, 238, 252))
        titleRow.addView(eventCountText)
        header.addView(titleRow)
        header.addView(TextView(this).apply {
            text = "Mốc nhắc: ${EventStore.reminderOffsetsText(this@MainActivity)} trước hạn"
            textSize = 11f
            setTextColor(muted)
            setPadding(0, dp(3), 0, dp(6))
        })
        val modeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        modeRow.addView(viewModeButton("Danh sách", EventViewMode.LIST), LinearLayout.LayoutParams(0, dp(36), 1f))
        modeRow.addView(viewModeButton("Lịch tháng", EventViewMode.MONTH), LinearLayout.LayoutParams(0, dp(36), 1f).apply {
            marginStart = dp(8)
        })
        header.addView(modeRow)
        return header
    }

    private fun viewModeButton(text: String, mode: EventViewMode): Button {
        val selected = eventViewMode == mode
        return Button(this).apply {
            this.text = text
            textSize = 12f
            setAllCaps(false)
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            setTextColor(if (selected) Color.WHITE else blue)
            background = if (selected) rounded(blue, 8) else rounded(Color.rgb(232, 242, 255), 8, Color.rgb(178, 209, 245), 1)
            setOnClickListener {
                eventViewMode = mode
                showCalendarTab()
            }
        }
    }

    private fun filterPanel(): View {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
            background = rounded(card, 8, line, 1)
        }

        val search = EditText(this).apply {
            hint = "Tìm theo tên bài, môn/lớp"
            setSingleLine(true)
            textSize = 12f
            setTextColor(ink)
            setHintTextColor(Color.rgb(148, 163, 184))
            setPadding(dp(10), 0, dp(10), 0)
            inputType = InputType.TYPE_CLASS_TEXT
            background = rounded(Color.rgb(248, 250, 252), 8, Color.rgb(203, 213, 225), 1)
            setText(eventSearchText)
            setSelection(text.length)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: Editable?) {
                    eventSearchText = s?.toString().orEmpty()
                    if (::eventsContainer.isInitialized) refreshEventsList()
                }
            })
        }
        panel.addView(search, LinearLayout.LayoutParams(match(), dp(38)))

        addSpacer(panel, 6)
        val kindRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val filters = listOf(
            EventFilter.ALL,
            EventFilter.SUBMISSION,
            EventFilter.TEST,
            EventFilter.EXAM
        )
        filters.forEachIndexed { index, filter ->
            kindRow.addView(filterButton(filter), LinearLayout.LayoutParams(0, dp(34), 1f).apply {
                if (index > 0) marginStart = dp(5)
            })
        }
        panel.addView(kindRow)

        addSpacer(panel, 6)
        val doneRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        doneRow.addView(TextView(this).apply {
            text = if (hideDoneEvents) "Ẩn đã xong" else "Hiện đã xong"
            textSize = 11f
            setTextColor(muted)
        }, LinearLayout.LayoutParams(0, wrap(), 1f))
        doneRow.addView(secondaryButton(if (hideDoneEvents) "Hiện" else "Ẩn").apply {
            setOnClickListener {
                hideDoneEvents = !hideDoneEvents
                showCalendarTab()
            }
        }, LinearLayout.LayoutParams(dp(70), dp(34)))
        panel.addView(doneRow)
        return panel
    }

    private fun filterButton(filter: EventFilter): Button {
        val selected = selectedFilter == filter
        return Button(this).apply {
            text = filter.label
            textSize = 11f
            setAllCaps(false)
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            setTextColor(if (selected) Color.WHITE else blue)
            background = if (selected) rounded(blue, 8) else rounded(Color.rgb(232, 242, 255), 8, Color.rgb(178, 209, 245), 1)
            setOnClickListener {
                selectedFilter = filter
                showCalendarTab()
            }
        }
    }

    private fun primaryButton(text: String): Button {
        return Button(this).apply {
            this.text = text
            textSize = 13f
            setTextColor(Color.WHITE)
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            setAllCaps(false)
            background = rounded(blue, 8)
        }
    }

    private fun secondaryButton(text: String): Button {
        return Button(this).apply {
            this.text = text
            textSize = 13f
            setTextColor(blue)
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            setAllCaps(false)
            background = rounded(Color.rgb(232, 242, 255), 8, Color.rgb(178, 209, 245), 1)
        }
    }

    private fun outlineButton(text: String): Button {
        return Button(this).apply {
            this.text = text
            textSize = 12f
            setTextColor(red)
            setAllCaps(false)
            background = rounded(Color.WHITE, 8, Color.rgb(252, 190, 190), 1)
        }
    }

    private fun syncNow() {
        setStatus("Đang đồng bộ lịch Moodle...", StatusType.INFO)
        thread {
            val result = DeadlineSync.sync(this, notifyNew = true)
            runOnUiThread {
                if (result.ok) {
                    ReminderScheduler.reschedulePeriodicSync(this)
                }
                setStatus(result.message, if (result.ok) StatusType.SUCCESS else StatusType.ERROR)
                refreshEventsList()
            }
        }
    }

    private fun queueSyncIfStale() {
        if (EventStore.getIcalUrl(this).isBlank()) return
        val lastSync = EventStore.getLastSync(this)
        val staleMillis = 30L * 60L * 1000L
        if (lastSync == 0L || System.currentTimeMillis() - lastSync > staleMillis) {
            ReminderScheduler.scheduleImmediateSync(this)
        }
    }

    private fun toggleDone(event: DeadlineEvent) {
        val nextDone = !EventStore.isDone(this, event.id)
        EventStore.setDone(this, event.id, nextDone)
        ReminderScheduler.scheduleAll(this, activeEventsForReminders())
        Toast.makeText(
            this,
            if (nextDone) "Đã đánh dấu xong." else "Đã bỏ đánh dấu xong.",
            Toast.LENGTH_SHORT
        ).show()
        refreshEventsList()
    }

    private fun activeEventsForReminders(): List<DeadlineEvent> {
        val doneIds = EventStore.getDoneIds(this)
        return EventStore.loadEvents(this).filterNot { it.id in doneIds }
    }

    private fun refreshEventsList() {
        eventsContainer.removeAllViews()
        val allEvents = EventStore.loadEvents(this)
        val doneIds = EventStore.getDoneIds(this)
        val events = filterEvents(allEvents, doneIds)
        eventCountText.text = if (events.size == allEvents.size) {
            "${allEvents.size} mục"
        } else {
            "${events.size}/${allEvents.size} mục"
        }
        if (allEvents.isEmpty()) {
            eventsContainer.addView(emptyStateView())
            updateLastSyncStatus()
            return
        }
        if (events.isEmpty()) {
            eventsContainer.addView(filteredEmptyStateView())
            updateLastSyncStatus()
            return
        }

        if (eventViewMode == EventViewMode.MONTH) {
            renderMonthView(events, doneIds)
        } else {
            renderListView(events, doneIds)
        }
        updateLastSyncStatus()
    }

    private fun filterEvents(events: List<DeadlineEvent>, doneIds: Set<String>): List<DeadlineEvent> {
        val query = searchable(eventSearchText.trim())
        return events.filter { event ->
            val doneOk = !hideDoneEvents || event.id !in doneIds
            val kindOk = when (selectedFilter) {
                EventFilter.ALL -> true
                EventFilter.SUBMISSION -> EventLabels.broadKind(event) == "Bài nộp"
                EventFilter.TEST -> EventLabels.broadKind(event) == "Kiểm tra"
                EventFilter.EXAM -> EventLabels.broadKind(event) == "Thi"
            }
            val searchOk = query.isBlank() || searchable(
                "${event.title} ${event.rawType.orEmpty()} ${event.description.orEmpty()} ${EventLabels.kind(event)}"
            ).contains(query)
            doneOk && kindOk && searchOk
        }
    }

    private fun renderListView(events: List<DeadlineEvent>, doneIds: Set<String>) {
        var lastDate: LocalDate? = null
        events.forEach { event ->
            val date = eventDate(event)
            if (date != lastDate) {
                eventsContainer.addView(dayGroupHeader(date), LinearLayout.LayoutParams(match(), wrap()).apply {
                    topMargin = if (lastDate == null) 0 else dp(2)
                    bottomMargin = dp(6)
                })
                lastDate = date
            }
            val lp = LinearLayout.LayoutParams(match(), wrap()).apply {
                bottomMargin = dp(8)
            }
            eventsContainer.addView(eventView(event, event.id in doneIds), lp)
        }
    }

    private fun renderMonthView(events: List<DeadlineEvent>, doneIds: Set<String>) {
        val month = visibleMonth ?: YearMonth.now(localZone)
        visibleMonth = month
        val monthEvents = events.filter { YearMonth.from(eventDate(it)) == month }
        eventsContainer.addView(monthNavigation(month), LinearLayout.LayoutParams(match(), wrap()).apply {
            bottomMargin = dp(8)
        })
        eventsContainer.addView(monthGrid(month, monthEvents), LinearLayout.LayoutParams(match(), wrap()))

        addSpacer(eventsContainer, 10)
        eventsContainer.addView(TextView(this).apply {
            text = "Sự kiện trong ${monthTitleFormatter.format(month.atDay(1))}"
            textSize = 15f
            setTextColor(ink)
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        }, LinearLayout.LayoutParams(match(), wrap()).apply {
            bottomMargin = dp(8)
        })

        if (monthEvents.isEmpty()) {
            eventsContainer.addView(TextView(this).apply {
                text = "Tháng này chưa có deadline trong lịch đã đồng bộ."
                textSize = 13f
                setTextColor(muted)
                gravity = Gravity.CENTER
                setPadding(dp(12), dp(14), dp(12), dp(14))
                background = rounded(card, 8, line, 1)
            })
            return
        }

        monthEvents.forEach { event ->
            eventsContainer.addView(eventView(event, event.id in doneIds), LinearLayout.LayoutParams(match(), wrap()).apply {
                bottomMargin = dp(10)
            })
        }
    }

    private fun monthNavigation(month: YearMonth): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(2), 0, dp(2))
        }
        row.addView(outlineButton("<").apply {
            setOnClickListener {
                visibleMonth = month.minusMonths(1)
                refreshEventsList()
            }
        }, LinearLayout.LayoutParams(dp(48), dp(38)))
        row.addView(TextView(this).apply {
            text = monthTitleFormatter.format(month.atDay(1))
            textSize = 16f
            setTextColor(ink)
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(0, wrap(), 1f))
        row.addView(secondaryButton("Tháng này").apply {
            setOnClickListener {
                visibleMonth = YearMonth.now(localZone)
                refreshEventsList()
            }
        }, LinearLayout.LayoutParams(dp(88), dp(38)).apply {
            marginEnd = dp(6)
        })
        row.addView(outlineButton(">").apply {
            setOnClickListener {
                visibleMonth = month.plusMonths(1)
                refreshEventsList()
            }
        }, LinearLayout.LayoutParams(dp(48), dp(38)))
        return row
    }

    private fun monthGrid(month: YearMonth, monthEvents: List<DeadlineEvent>): View {
        val grid = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(6), dp(6), dp(6), dp(6))
            background = rounded(card, 8, line, 1)
        }
        val labels = listOf("T2", "T3", "T4", "T5", "T6", "T7", "CN")
        val labelRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        labels.forEach { label ->
            labelRow.addView(TextView(this).apply {
                text = label
                textSize = 11f
                setTextColor(muted)
                setTypeface(Typeface.DEFAULT, Typeface.BOLD)
                gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(0, dp(24), 1f))
        }
        grid.addView(labelRow)

        val first = month.atDay(1)
        val startOffset = first.dayOfWeek.value - 1
        val daysInMonth = month.lengthOfMonth()
        val eventsByDate = monthEvents.groupBy { eventDate(it) }
        var dayNumber = 1 - startOffset
        repeat(6) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
            }
            repeat(7) {
                val date = if (dayNumber in 1..daysInMonth) month.atDay(dayNumber) else null
                val dateEvents = if (date != null) eventsByDate[date].orEmpty() else emptyList()
                row.addView(monthCell(date, dateEvents), LinearLayout.LayoutParams(0, dp(76), 1f).apply {
                    marginStart = dp(1)
                    marginEnd = dp(1)
                    topMargin = dp(1)
                    bottomMargin = dp(1)
                })
                dayNumber += 1
            }
            grid.addView(row)
        }
        return grid
    }

    private fun monthCell(date: LocalDate?, events: List<DeadlineEvent>): View {
        val today = LocalDate.now(localZone)
        val hasEvents = events.isNotEmpty()
        val backgroundColor = when {
            date == null -> Color.rgb(248, 250, 252)
            date == today -> Color.rgb(231, 242, 255)
            hasEvents -> Color.rgb(255, 248, 235)
            else -> Color.WHITE
        }
        val strokeColor = when {
            date == today -> blue
            hasEvents -> Color.rgb(245, 166, 35)
            else -> line
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(4), dp(4), dp(4))
            background = rounded(backgroundColor, 6, strokeColor, 1)
            if (date == null) return@apply
            addView(TextView(this@MainActivity).apply {
                text = date.dayOfMonth.toString()
                textSize = 12f
                setTextColor(if (date == today) blue else ink)
                setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            })
            if (hasEvents) {
                addView(TextView(this@MainActivity).apply {
                    text = "${events.size} mục"
                    textSize = 10f
                    setTextColor(red)
                    setTypeface(Typeface.DEFAULT, Typeface.BOLD)
                    maxLines = 1
                })
                addView(TextView(this@MainActivity).apply {
                    text = events.first().title
                    textSize = 9f
                    setTextColor(muted)
                    maxLines = 2
                    ellipsize = TextUtils.TruncateAt.END
                })
            }
        }
    }

    private fun dayGroupHeader(date: LocalDate): View {
        return TextView(this).apply {
            text = dayGroupFormatter.format(date)
            textSize = 13f
            setTextColor(blueDark)
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            setPadding(dp(2), dp(4), 0, 0)
        }
    }

    private fun eventDate(event: DeadlineEvent): LocalDate {
        return Instant.ofEpochMilli(event.startAtMillis).atZone(localZone).toLocalDate()
    }

    private fun eventView(event: DeadlineEvent, isDone: Boolean): View {
        val accent = accentFor(event)
        val cardView = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
            background = rounded(if (isDone) Color.rgb(248, 250, 252) else card, 8, line, 1)
            gravity = Gravity.CENTER_VERTICAL
        }

        val dateBadge = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(8), dp(6), dp(8))
            background = rounded(tint(accent), 8)
        }
        val instant = Instant.ofEpochMilli(event.startAtMillis)
        dateBadge.addView(TextView(this).apply {
            text = dayFormatter.format(instant)
            textSize = 23f
            setTextColor(accent)
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            includeFontPadding = false
        })
        dateBadge.addView(TextView(this).apply {
            text = monthFormatter.format(instant)
            textSize = 10f
            setTextColor(accent)
            gravity = Gravity.CENTER
        })
        dateBadge.addView(TextView(this).apply {
            text = clockFormatter.format(instant)
            textSize = 12f
            setTextColor(ink)
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        })
        cardView.addView(dateBadge, LinearLayout.LayoutParams(dp(72), match()))

        val info = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, 0, 0)
        }
        cardView.addView(info, LinearLayout.LayoutParams(0, wrap(), 1f))

        val meta = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        meta.addView(chip(eventKind(event), accent, tint(accent)))
        if (isDone) {
            meta.addView(chip("Đã xong", green, Color.rgb(229, 248, 239)), LinearLayout.LayoutParams(wrap(), wrap()).apply {
                marginStart = dp(6)
            })
        }
        EventLabels.course(event)?.let { course ->
            meta.addView(chip(course, blueDark, Color.rgb(248, 250, 252)), LinearLayout.LayoutParams(wrap(), wrap()).apply {
                marginStart = dp(6)
            })
        }
        info.addView(meta)

        info.addView(TextView(this).apply {
            text = event.title
            textSize = 15f
            setTextColor(if (isDone) muted else ink)
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            setPadding(0, dp(7), 0, 0)
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
        })
        EventLabels.cleanDescription(event)?.let { description ->
            info.addView(TextView(this).apply {
                text = description
                textSize = 12f
                setTextColor(blueDark)
                setPadding(0, dp(4), 0, 0)
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
            })
        }
        info.addView(TextView(this).apply {
            text = "${EventLabels.timeLabel(event)}: ${timeFormatter.format(instant)}"
            textSize = 13f
            setTextColor(muted)
            setPadding(0, dp(5), 0, 0)
        })
        info.addView(TextView(this).apply {
            text = remainText(event.startAtMillis)
            textSize = 13f
            setTextColor(if (isDone) muted else accent)
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            setPadding(0, dp(4), 0, 0)
        })
        val actionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, 0)
        }
        actionRow.addView(secondaryButton("Mở").apply {
            setOnClickListener { openEventMoodle(event) }
        }, LinearLayout.LayoutParams(0, dp(38), 1f))
        actionRow.addView(secondaryButton("Copy").apply {
            setOnClickListener { copyEventInfo(event) }
        }, LinearLayout.LayoutParams(0, dp(38), 1f).apply {
            marginStart = dp(6)
        })
        actionRow.addView((if (isDone) outlineButton("Bỏ xong") else primaryButton("Xong")).apply {
            setOnClickListener { toggleDone(event) }
        }, LinearLayout.LayoutParams(0, dp(38), 1f).apply {
            marginStart = dp(6)
        })
        info.addView(actionRow)
        return cardView
    }

    private fun emptyStateView(): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(18), dp(22), dp(18), dp(22))
            background = rounded(card, 8, line, 1)
            addView(hcmuteLogoView(58), LinearLayout.LayoutParams(dp(58), dp(58)))
            addSpacer(this, 10)
            addView(TextView(this@MainActivity).apply {
                text = "Chưa có deadline"
                textSize = 16f
                setTextColor(ink)
                setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            })
            addView(TextView(this@MainActivity).apply {
                text = "Dán iCal URL rồi bấm Đồng bộ để tải lịch."
                textSize = 13f
                setTextColor(muted)
                gravity = Gravity.CENTER
                setPadding(0, dp(5), 0, 0)
            })
        }
    }

    private fun filteredEmptyStateView(): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(18), dp(18), dp(18), dp(18))
            background = rounded(card, 8, line, 1)
            addView(TextView(this@MainActivity).apply {
                text = "Không có mục phù hợp"
                textSize = 16f
                setTextColor(ink)
                setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            })
            addView(TextView(this@MainActivity).apply {
                text = "Thử đổi từ khóa, bộ lọc hoặc bật hiển thị deadline đã xong."
                textSize = 13f
                setTextColor(muted)
                gravity = Gravity.CENTER
                setPadding(0, dp(5), 0, 0)
            })
        }
    }

    private fun remainText(millis: Long): String {
        val diff = millis - System.currentTimeMillis()
        if (diff <= 0) return "Đã tới hạn hoặc vừa qua hạn."
        val days = diff / (24L * 60L * 60L * 1000L)
        val hours = (diff / (60L * 60L * 1000L)) % 24
        val minutes = (diff / (60L * 1000L)) % 60
        return when {
            days > 0 -> "Còn $days ngày $hours giờ"
            hours > 0 -> "Còn $hours giờ $minutes phút"
            else -> "Còn $minutes phút"
        }
    }

    private fun updateLastSyncStatus() {
        val lastSync = EventStore.getLastSync(this)
        val currentStatus = statusText.text.toString()
        if (lastSync > 0L && (currentStatus.startsWith("Dán") || currentStatus.startsWith("Moodle"))) {
            val ageMillis = System.currentTimeMillis() - lastSync
            if (ageMillis > 24L * 60L * 60L * 1000L) {
                setStatus("Cảnh báo: lần sync gần nhất đã hơn 24 giờ (${timeFormatter.format(Instant.ofEpochMilli(lastSync))}). Bấm Kiểm tra để cập nhật lại.", StatusType.ERROR)
            } else {
                setStatus("Cập nhật gần nhất: ${timeFormatter.format(Instant.ofEpochMilli(lastSync))}", StatusType.INFO)
            }
        }
    }

    private fun calendarDefaultStatus(): String {
        val url = EventStore.getIcalUrl(this)
        return if (url.isBlank()) {
            "Dán iCal URL để bắt đầu theo dõi lịch Moodle."
        } else {
            "Moodle đã kết nối: ${MoodleUrlValidator.mask(url)}"
        }
    }

    private fun setStatus(text: String, type: StatusType) {
        statusText.text = text
        val colors = when (type) {
            StatusType.INFO -> Triple(blueDark, Color.rgb(231, 242, 255), Color.rgb(190, 218, 248))
            StatusType.SUCCESS -> Triple(green, Color.rgb(229, 248, 239), Color.rgb(178, 229, 204))
            StatusType.ERROR -> Triple(red, Color.rgb(255, 235, 235), Color.rgb(249, 188, 188))
        }
        statusText.setTextColor(colors.first)
        statusText.background = rounded(colors.second, 8, colors.third, 1)
    }

    private fun updateNotificationChip() {
        val granted = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            notificationChip.text = "Thông báo sẵn sàng"
            notificationChip.setTextColor(Color.WHITE)
            notificationChip.background = rounded(Color.argb(45, 255, 255, 255), 8, Color.argb(90, 255, 255, 255), 1)
        } else {
            notificationChip.text = "Chưa cấp quyền"
            notificationChip.setTextColor(Color.WHITE)
            notificationChip.background = rounded(red, 8)
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33) {
            val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
            }
        }
    }

    private fun eventKind(event: DeadlineEvent): String {
        return EventLabels.kind(event)
    }

    private fun accentFor(event: DeadlineEvent): Int {
        val diff = event.startAtMillis - System.currentTimeMillis()
        val kind = EventLabels.broadKind(event)
        return when {
            diff <= 24L * 60L * 60L * 1000L -> red
            diff <= 3L * 24L * 60L * 60L * 1000L -> amber
            kind == "Bài nộp" -> green
            kind == "Thi" -> red
            else -> blue
        }
    }

    private fun tint(color: Int): Int {
        return when (color) {
            red -> Color.rgb(255, 235, 235)
            amber -> Color.rgb(255, 244, 222)
            green -> Color.rgb(229, 248, 239)
            else -> Color.rgb(226, 238, 252)
        }
    }

    private fun searchable(text: String): String {
        return Normalizer.normalize(text.lowercase(Locale.forLanguageTag("vi-VN")), Normalizer.Form.NFD)
            .replace("\\p{M}+".toRegex(), "")
            .replace('đ', 'd')
    }

    private fun chip(text: String, textColor: Int, backgroundColor: Int): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 11f
            setTextColor(textColor)
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            setPadding(dp(9), dp(5), dp(9), dp(5))
            gravity = Gravity.CENTER
            background = rounded(backgroundColor, 8)
        }
    }

    private fun rounded(color: Int, radiusDp: Int, strokeColor: Int? = null, strokeDp: Int = 0): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color)
            cornerRadius = dp(radiusDp).toFloat()
            if (strokeColor != null && strokeDp > 0) {
                setStroke(dp(strokeDp), strokeColor)
            }
        }
    }

    private fun addSpacer(parent: LinearLayout, heightDp: Int) {
        parent.addView(View(this), LinearLayout.LayoutParams(match(), dp(heightDp)))
    }

    private fun hcmuteLogoView(sizeDp: Int): ImageView {
        return ImageView(this).apply {
            setImageResource(R.drawable.hcmute_logo)
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true
            setPadding(dp(4), dp(4), dp(4), dp(4))
            background = rounded(Color.WHITE, sizeDp / 2, Color.argb(70, 255, 255, 255), 1)
            contentDescription = "Logo HCMUTE"
        }
    }

    private fun applySystemBars() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.statusBarColor = blueDark
            window.navigationBarColor = page
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        }
    }

    private fun match(): Int = ViewGroup.LayoutParams.MATCH_PARENT
    private fun wrap(): Int = ViewGroup.LayoutParams.WRAP_CONTENT
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private enum class StatusType {
        INFO,
        SUCCESS,
        ERROR
    }

    private enum class ScreenTab {
        CALENDAR,
        GUIDE
    }

    private enum class EventViewMode {
        LIST,
        MONTH
    }

    private enum class EventFilter(val label: String) {
        ALL("Tất cả"),
        SUBMISSION("Bài nộp"),
        TEST("Kiểm tra"),
        EXAM("Thi")
    }

    private data class DayOption(
        val label: String,
        val bitIndex: Int
    )

}
