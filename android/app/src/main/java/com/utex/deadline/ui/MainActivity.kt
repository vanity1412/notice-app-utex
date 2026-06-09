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

    internal val moodleExportUrl = "https://utexlms.hcmute.edu.vn/calendar/export.php?"
    internal val githubGuideUrl = "https://vanity1412.github.io/notice-app-utex/"
    internal val supportContact = "Vũ Văn Thông - 0968046024"

    internal lateinit var urlInput: EditText
    internal lateinit var statusText: TextView
    internal lateinit var eventsContainer: LinearLayout
    internal lateinit var eventCountText: TextView
    internal lateinit var notificationChip: TextView
    internal lateinit var dailySummaryText: TextView
    internal lateinit var notificationHealthText: TextView
    internal lateinit var tabContent: LinearLayout
    internal lateinit var calendarTab: TextView
    internal lateinit var guideTab: TextView
    internal lateinit var notificationSettingsTab: TextView
    internal var activeTab = ScreenTab.CALENDAR
    internal var connectionExpanded = false
    internal var eventViewMode = EventViewMode.LIST
    internal var visibleMonth: YearMonth? = null
    internal var eventSearchText = ""
    internal var selectedFilter = EventFilter.ALL
    internal var hideDoneEvents = true
    internal var syncInProgress = false
    internal var setupPromptShown = false

    internal val localZone = ZoneId.of("Asia/Ho_Chi_Minh")
    internal val timeFormatter = DateTimeFormatter.ofPattern("HH:mm - EEEE dd/MM/yyyy", Locale.forLanguageTag("vi-VN"))
        .withZone(localZone)
    internal val dayFormatter = DateTimeFormatter.ofPattern("dd", Locale.forLanguageTag("vi-VN"))
        .withZone(localZone)
    internal val monthFormatter = DateTimeFormatter.ofPattern("MM/yyyy", Locale.forLanguageTag("vi-VN"))
        .withZone(localZone)
    internal val clockFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.forLanguageTag("vi-VN"))
        .withZone(localZone)
    internal val monthTitleFormatter = DateTimeFormatter.ofPattern("'Tháng' M yyyy", Locale.forLanguageTag("vi-VN"))
    internal val dayGroupFormatter = DateTimeFormatter.ofPattern("EEEE dd/MM/yyyy", Locale.forLanguageTag("vi-VN"))

    internal val blue = Color.rgb(0, 82, 156)
    internal val blueDark = Color.rgb(0, 54, 111)
    internal val red = Color.rgb(218, 37, 41)
    internal val green = Color.rgb(24, 128, 88)
    internal val amber = Color.rgb(202, 116, 0)
    internal val ink = Color.rgb(30, 41, 59)
    internal val muted = Color.rgb(100, 116, 139)
    internal val page = Color.rgb(244, 247, 251)
    internal val card = Color.WHITE
    internal val line = Color.rgb(222, 229, 238)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applySystemBars()
        NotificationHelper.ensureChannel(this)
        requestNotificationPermissionIfNeeded()
        buildUi()
        ReminderScheduler.schedulePeriodicSync(this)
        refreshScheduledNotifications()
        promptRequiredAlertSetupIfNeeded(force = false)
    }

override fun onResume() {
        super.onResume()
        queueSyncIfStale()
        refreshScheduledNotifications()
        if (hasNotificationChip()) updateNotificationChip()
        if (hasNotificationHealthText()) refreshNotificationHealthText()
        NotificationHelper.flushPendingDeadlineNotifications(this)
        promptRequiredAlertSetupIfNeeded(force = false)
    }

override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1001 && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            NotificationHelper.flushPendingDeadlineNotifications(this)
        }
        if (requestCode == 1001) {
            setupPromptShown = false
            promptRequiredAlertSetupIfNeeded(force = false)
        }
        if (hasNotificationChip()) updateNotificationChip()
        if (hasNotificationHealthText()) refreshNotificationHealthText()
    }


    internal fun hasNotificationChip(): Boolean = ::notificationChip.isInitialized

    internal fun hasNotificationHealthText(): Boolean = ::notificationHealthText.isInitialized

    internal fun hasDailySummaryText(): Boolean = ::dailySummaryText.isInitialized

    internal fun hasEventsContainer(): Boolean = ::eventsContainer.isInitialized

    internal fun hasTabs(): Boolean =
        ::calendarTab.isInitialized && ::guideTab.isInitialized && ::notificationSettingsTab.isInitialized

}
