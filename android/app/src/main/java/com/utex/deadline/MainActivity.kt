package com.utex.deadline

import android.Manifest
import android.app.Activity
import android.app.TimePickerDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private val moodleExportUrl = "https://utexlms.hcmute.edu.vn/calendar/export.php?"
    private val githubGuideUrl = "https://github.com/vanity1412/notice-app-utex"
    private val supportContact = "Vũ Văn Thông - 0968046024"

    private lateinit var urlInput: EditText
    private lateinit var statusText: TextView
    private lateinit var eventsContainer: LinearLayout
    private lateinit var eventCountText: TextView
    private lateinit var notificationChip: TextView
    private lateinit var dailySummaryText: TextView
    private lateinit var tabContent: LinearLayout
    private lateinit var calendarTab: TextView
    private lateinit var guideTab: TextView
    private var activeTab = ScreenTab.CALENDAR
    private var connectionExpanded = false

    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm - EEEE dd/MM/yyyy", Locale.forLanguageTag("vi-VN"))
        .withZone(ZoneId.of("Asia/Ho_Chi_Minh"))
    private val dayFormatter = DateTimeFormatter.ofPattern("dd", Locale.forLanguageTag("vi-VN"))
        .withZone(ZoneId.of("Asia/Ho_Chi_Minh"))
    private val monthFormatter = DateTimeFormatter.ofPattern("MM/yyyy", Locale.forLanguageTag("vi-VN"))
        .withZone(ZoneId.of("Asia/Ho_Chi_Minh"))
    private val clockFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.forLanguageTag("vi-VN"))
        .withZone(ZoneId.of("Asia/Ho_Chi_Minh"))

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
        if (::notificationChip.isInitialized) updateNotificationChip()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(page)
        }

        root.addView(headerView())

        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), 0)
        }
        root.addView(body, LinearLayout.LayoutParams(match(), 0, 1f))

        body.addView(tabBar())
        addSpacer(body, 12)

        tabContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        body.addView(tabContent, LinearLayout.LayoutParams(match(), 0, 1f))

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
            setOnClickListener { showCalendarTab() }
        }
        guideTab = tabButton("Hướng dẫn").apply {
            setOnClickListener { showGuideTab() }
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
        addSpacer(tabContent, 10)

        statusText = TextView(this).apply {
            textSize = 13f
            setTextColor(blueDark)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = rounded(Color.rgb(231, 242, 255), 8, Color.rgb(190, 218, 248), 1)
        }
        tabContent.addView(statusText, LinearLayout.LayoutParams(match(), wrap()))

        addSpacer(tabContent, 12)
        tabContent.addView(sectionHeader())

        eventsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(4), 0, dp(16))
        }
        val scroll = ScrollView(this).apply {
            isFillViewport = false
            clipToPadding = false
            addView(eventsContainer)
        }
        tabContent.addView(scroll, LinearLayout.LayoutParams(match(), 0, 1f))

        setStatus(calendarDefaultStatus(), StatusType.INFO)
        updateLastSyncStatus()
        refreshEventsList()
    }

    private fun showGuideTab() {
        activeTab = ScreenTab.GUIDE
        updateTabButtons()
        tabContent.removeAllViews()

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(16))
        }
        content.addView(quickGuidePanel())
        addSpacer(content, 10)
        content.addView(notificationSettingsPanel())

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            addView(content)
        }
        tabContent.addView(scroll, LinearLayout.LayoutParams(match(), match()))
        refreshDailySummaryText()
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
            setPadding(dp(18), dp(18), dp(18), dp(16))
            background = rounded(blue, 0)
        }
        header.addView(HcmuteMarkView(this), LinearLayout.LayoutParams(dp(70), dp(70)))

        val textGroup = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), 0, 0, 0)
        }
        header.addView(textGroup, LinearLayout.LayoutParams(0, wrap(), 1f))

        textGroup.addView(TextView(this).apply {
            text = "UTE Notice"
            textSize = 25f
            setTextColor(Color.WHITE)
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            includeFontPadding = false
        })
        textGroup.addView(TextView(this).apply {
            text = "HCM-UTE Moodle Calendar"
            textSize = 13f
            setTextColor(Color.rgb(218, 235, 255))
            setPadding(0, dp(5), 0, 0)
        })

        notificationChip = chip("Đang kiểm tra thông báo", Color.WHITE, Color.argb(40, 255, 255, 255))
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
            setPadding(dp(14), dp(14), dp(14), dp(14))
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

        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(10), 0, 0)
        }

        val saveButton = primaryButton("Lưu & đồng bộ").apply {
            setOnClickListener {
                EventStore.setIcalUrl(this@MainActivity, urlInput.text.toString())
                ReminderScheduler.schedulePeriodicSync(this@MainActivity)
                connectionExpanded = false
                showCalendarTab()
                syncNow()
            }
        }
        buttons.addView(saveButton, LinearLayout.LayoutParams(0, dp(48), 1f))

        val syncButton = secondaryButton("Kiểm tra").apply {
            setOnClickListener {
                EventStore.setIcalUrl(this@MainActivity, urlInput.text.toString())
                syncNow()
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
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = rounded(card, 8, line, 1)
            setOnClickListener {
                connectionExpanded = true
                showCalendarTab()
            }

            addView(TextView(this@MainActivity).apply {
                text = "Kết nối Moodle"
                textSize = 15f
                setTextColor(ink)
                setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            }, LinearLayout.LayoutParams(0, wrap(), 1f))

            addView(TextView(this@MainActivity).apply {
                text = "Đã kết nối"
                textSize = 12f
                setTextColor(green)
                setTypeface(Typeface.DEFAULT, Typeface.BOLD)
                gravity = Gravity.END
            }, LinearLayout.LayoutParams(wrap(), wrap()).apply {
                marginEnd = dp(8)
            })

            addView(outlineButton("Hướng dẫn").apply {
                setOnClickListener { showGuideTab() }
            }, LinearLayout.LayoutParams(dp(100), dp(36)))
        }
    }

    private fun quickGuidePanel(): View {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = rounded(card, 8, line, 1)
        }
        panel.addView(TextView(this).apply {
            text = "Hướng dẫn nhanh"
            textSize = 15f
            setTextColor(ink)
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        })
        addSpacer(panel, 6)
        panel.addView(TextView(this).apply {
            text = "1. Copy link ở dưới: https://utexlms.hcmute.edu.vn/calendar/export.php?\n2. Dán link vào trình duyệt đã đăng nhập UTExLMS để có quyền truy cập.\n3. Chọn lịch muốn thông báo, bấm Lấy địa chỉ mạng của lịch.\n4. Copy Calendar URL hiện ra, quay lại app dán vào Kết nối Moodle rồi Lưu & đồng bộ."
            textSize = 12f
            setTextColor(muted)
            setLineSpacing(0f, 1.08f)
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
            setPadding(dp(14), dp(12), dp(14), dp(12))
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

    private fun sectionHeader(): View {
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(8))
        }
        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        titleRow.addView(TextView(this).apply {
            text = "Lịch sắp tới"
            textSize = 18f
            setTextColor(ink)
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        }, LinearLayout.LayoutParams(0, wrap(), 1f))
        eventCountText = chip("0 mục", blue, Color.rgb(226, 238, 252))
        titleRow.addView(eventCountText)
        header.addView(titleRow)
        header.addView(TextView(this).apply {
            text = "Loại lịch, môn/lớp và thời hạn được tách riêng. App luôn nhắc 1 ngày, 12 giờ và 1 giờ trước hạn."
            textSize = 12f
            setTextColor(muted)
            setPadding(0, dp(4), 0, 0)
        })
        return header
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
                setStatus(result.message, if (result.ok) StatusType.SUCCESS else StatusType.ERROR)
                refreshEventsList()
            }
        }
    }

    private fun refreshEventsList() {
        eventsContainer.removeAllViews()
        val events = EventStore.loadEvents(this)
        eventCountText.text = "${events.size} mục"
        if (events.isEmpty()) {
            eventsContainer.addView(emptyStateView())
            updateLastSyncStatus()
            return
        }

        events.forEach { event ->
            val lp = LinearLayout.LayoutParams(match(), wrap()).apply {
                bottomMargin = dp(10)
            }
            eventsContainer.addView(eventView(event), lp)
        }
        updateLastSyncStatus()
    }

    private fun eventView(event: DeadlineEvent): View {
        val accent = accentFor(event)
        val cardView = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(10), dp(10), dp(12), dp(10))
            background = rounded(card, 8, line, 1)
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
        EventLabels.course(event)?.let { course ->
            meta.addView(chip(course, blueDark, Color.rgb(248, 250, 252)), LinearLayout.LayoutParams(wrap(), wrap()).apply {
                marginStart = dp(6)
            })
        }
        info.addView(meta)

        info.addView(TextView(this).apply {
            text = event.title
            textSize = 15f
            setTextColor(ink)
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
            setTextColor(accent)
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            setPadding(0, dp(4), 0, 0)
        })
        return cardView
    }

    private fun emptyStateView(): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(18), dp(22), dp(18), dp(22))
            background = rounded(card, 8, line, 1)
            addView(HcmuteMarkView(this@MainActivity), LinearLayout.LayoutParams(dp(58), dp(58)))
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
            setStatus("Cập nhật gần nhất: ${timeFormatter.format(Instant.ofEpochMilli(lastSync))}", StatusType.INFO)
        }
    }

    private fun calendarDefaultStatus(): String {
        return if (EventStore.getIcalUrl(this).isBlank()) {
            "Dán iCal URL để bắt đầu theo dõi lịch Moodle."
        } else {
            "Moodle đã kết nối. App sẽ tự đồng bộ và nhắc lịch."
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

    private data class DayOption(
        val label: String,
        val bitIndex: Int
    )

    private class HcmuteMarkView(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val rect = RectF()
        private val flame = Path()

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val size = width.coerceAtMost(height).toFloat()
            val cx = width / 2f
            val cy = height / 2f
            val blue = Color.rgb(0, 82, 156)
            val red = Color.rgb(218, 37, 41)

            paint.style = Paint.Style.FILL
            paint.color = Color.WHITE
            canvas.drawCircle(cx, cy, size * 0.47f, paint)

            paint.style = Paint.Style.STROKE
            paint.strokeWidth = size * 0.045f
            paint.color = blue
            canvas.drawCircle(cx, cy, size * 0.43f, paint)

            paint.style = Paint.Style.FILL
            paint.color = blue
            rect.set(cx - size * 0.23f, cy + size * 0.06f, cx + size * 0.23f, cy + size * 0.19f)
            canvas.drawRoundRect(rect, size * 0.025f, size * 0.025f, paint)
            rect.set(cx - size * 0.18f, cy + size * 0.21f, cx + size * 0.18f, cy + size * 0.28f)
            canvas.drawRoundRect(rect, size * 0.02f, size * 0.02f, paint)

            paint.color = red
            flame.reset()
            flame.moveTo(cx, cy - size * 0.29f)
            flame.cubicTo(cx + size * 0.18f, cy - size * 0.13f, cx + size * 0.13f, cy + size * 0.03f, cx, cy + size * 0.06f)
            flame.cubicTo(cx - size * 0.15f, cy - size * 0.02f, cx - size * 0.12f, cy - size * 0.18f, cx, cy - size * 0.29f)
            canvas.drawPath(flame, paint)

            paint.color = Color.WHITE
            paint.textAlign = Paint.Align.CENTER
            paint.typeface = Typeface.DEFAULT_BOLD
            paint.textSize = size * 0.17f
            canvas.drawText("UTE", cx, cy + size * 0.03f, paint)

            paint.color = blue
            paint.textSize = size * 0.12f
            canvas.drawText("HCM-UTE", cx, cy + size * 0.39f, paint)
        }
    }
}
