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

internal fun MainActivity.showCalendarTab() {
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

internal fun MainActivity.connectionPanel(): View {
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

internal fun MainActivity.compactConnectionRow(): View {
    val activity = this
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = rounded(card, 8, line, 1)
            setOnClickListener {
                connectionExpanded = true
                showCalendarTab()
            }

            val titleRow = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            titleRow.addView(TextView(activity).apply {
                text = "Kết nối Moodle"
                textSize = 14f
                setTextColor(ink)
                setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            }, LinearLayout.LayoutParams(0, wrap(), 1f))
            titleRow.addView(chip("Đã kết nối", green, Color.rgb(229, 248, 239)))
            addView(titleRow)

            addView(TextView(activity).apply {
                text = MoodleUrlValidator.mask(EventStore.getIcalUrl(activity))
                textSize = 11f
                setTextColor(muted)
                setPadding(0, dp(4), 0, dp(6))
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            })

            val actionRow = LinearLayout(activity).apply {
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

internal fun MainActivity.saveCalendarUrlAndSync() {
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

internal fun MainActivity.pasteCalendarUrlFromClipboard() {
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

internal fun MainActivity.clearMoodleConnection() {
        EventStore.clearConnection(this)
        ReminderScheduler.cancelAll(this)
        ReminderScheduler.scheduleAll(this, activeEventsForReminders())
        ReminderScheduler.scheduleDailySummary(this)
        connectionExpanded = true
        Toast.makeText(this, "Đã xóa kết nối Moodle.", Toast.LENGTH_SHORT).show()
        showCalendarTab()
    }

internal fun MainActivity.syncNow() {
        if (syncInProgress) {
            setStatus("Đang đồng bộ, vui lòng chờ trong giây lát...", StatusType.INFO)
            return
        }
        syncInProgress = true
        setStatus("Đang đồng bộ lịch Moodle...", StatusType.INFO)
        thread {
            val result = DeadlineSync.sync(this, notifyNew = true)
            runOnUiThread {
                syncInProgress = false
                if (result.ok) {
                    ReminderScheduler.reschedulePeriodicSync(this)
                }
                setStatus(result.message, if (result.ok) StatusType.SUCCESS else StatusType.ERROR)
                refreshEventsList()
            }
        }
    }

internal fun MainActivity.queueSyncIfStale() {
        if (EventStore.getIcalUrl(this).isBlank()) return
        val lastSync = EventStore.getLastSync(this)
        val staleMillis = 30L * 60L * 1000L
        if (lastSync == 0L || System.currentTimeMillis() - lastSync > staleMillis) {
            ReminderScheduler.scheduleImmediateSync(this)
        }
    }

internal fun MainActivity.refreshScheduledNotifications() {
        ReminderScheduler.scheduleDailySummary(this)
        ReminderScheduler.scheduleAll(this, activeEventsForReminders())
    }
