@file:Suppress("DEPRECATION", "unused")

package com.utex.deadline

import android.Manifest
import android.app.Activity
import android.app.DatePickerDialog
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
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.concurrent.thread

internal fun MainActivity.sectionHeader(): View {
    val activity = this
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
        titleRow.addView(eventCountText, LinearLayout.LayoutParams(wrap(), wrap()).apply {
            marginEnd = dp(6)
        })
        titleRow.addView(primaryButton("+ Cá nhân").apply {
            setOnClickListener { showPersonalDeadlineDialog() }
        }, LinearLayout.LayoutParams(dp(108), dp(34)))
        header.addView(titleRow)
        header.addView(TextView(this).apply {
            text = "Mốc nhắc: ${EventStore.reminderOffsetsText(activity)} trước hạn"
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

internal fun MainActivity.viewModeButton(text: String, mode: EventViewMode): Button {
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

internal fun MainActivity.filterPanel(): View {
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
                    if (hasEventsContainer()) refreshEventsList()
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
            EventFilter.PERSONAL,
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
            text = if (hideDoneEvents) "Deadline đã xong đang ẩn" else "Đang hiện deadline đã xong"
            textSize = 11f
            setTextColor(muted)
        }, LinearLayout.LayoutParams(0, wrap(), 1f))
        doneRow.addView(secondaryButton(if (hideDoneEvents) "Hiện đã xong" else "Ẩn đã xong").apply {
            setOnClickListener {
                hideDoneEvents = !hideDoneEvents
                showCalendarTab()
            }
        }, LinearLayout.LayoutParams(dp(116), dp(34)))
        panel.addView(doneRow)
        return panel
    }

internal fun MainActivity.filterButton(filter: EventFilter): Button {
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

internal fun MainActivity.toggleDone(event: DeadlineEvent) {
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

internal fun MainActivity.activeEventsForReminders(): List<DeadlineEvent> {
        val doneIds = EventStore.getDoneIds(this)
        return EventStore.loadEvents(this).filterNot { it.id in doneIds }
    }

internal fun MainActivity.refreshEventsList() {
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

internal fun MainActivity.filterEvents(events: List<DeadlineEvent>, doneIds: Set<String>): List<DeadlineEvent> {
        val query = searchable(eventSearchText.trim())
        return events.filter { event ->
            val doneOk = !hideDoneEvents || event.id !in doneIds
            val kindOk = when (selectedFilter) {
                EventFilter.ALL -> true
                EventFilter.SUBMISSION -> EventLabels.broadGroup(event) == EventGroup.SUBMISSION
                EventFilter.TEST -> EventLabels.broadGroup(event) == EventGroup.TEST
                EventFilter.EXAM -> EventLabels.broadGroup(event) == EventGroup.EXAM
                EventFilter.PERSONAL -> event.isPersonal
            }
            val searchOk = query.isBlank() || searchable(
                "${event.title} ${event.rawType.orEmpty()} ${event.description.orEmpty()} ${EventLabels.kind(event)} ${event.source.name}"
            ).contains(query)
            doneOk && kindOk && searchOk
        }
    }

internal fun MainActivity.renderListView(events: List<DeadlineEvent>, doneIds: Set<String>) {
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

internal fun MainActivity.renderMonthView(events: List<DeadlineEvent>, doneIds: Set<String>) {
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

internal fun MainActivity.monthNavigation(month: YearMonth): View {
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

internal fun MainActivity.monthGrid(month: YearMonth, monthEvents: List<DeadlineEvent>): View {
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

internal fun MainActivity.monthCell(date: LocalDate?, events: List<DeadlineEvent>): View {
    val activity = this
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
            addView(TextView(activity).apply {
                text = date.dayOfMonth.toString()
                textSize = 12f
                setTextColor(if (date == today) blue else ink)
                setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            })
            if (hasEvents) {
                addView(TextView(activity).apply {
                    text = "${events.size} mục"
                    textSize = 10f
                    setTextColor(red)
                    setTypeface(Typeface.DEFAULT, Typeface.BOLD)
                    maxLines = 1
                })
                addView(TextView(activity).apply {
                    text = events.first().title
                    textSize = 9f
                    setTextColor(muted)
                    maxLines = 2
                    ellipsize = TextUtils.TruncateAt.END
                })
            }
        }
    }

internal fun MainActivity.dayGroupHeader(date: LocalDate): View {
        return TextView(this).apply {
            text = dayGroupFormatter.format(date)
            textSize = 13f
            setTextColor(blueDark)
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            setPadding(dp(2), dp(4), 0, 0)
        }
    }

internal fun MainActivity.eventDate(event: DeadlineEvent): LocalDate {
        return Instant.ofEpochMilli(event.startAtMillis).atZone(localZone).toLocalDate()
    }

internal fun MainActivity.eventView(event: DeadlineEvent, isDone: Boolean): View {
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
        if (event.isPersonal) {
            actionRow.addView(secondaryButton("Sửa").apply {
                setOnClickListener { showPersonalDeadlineDialog(event) }
            }, LinearLayout.LayoutParams(0, dp(38), 1f))
            actionRow.addView(outlineButton("Xóa").apply {
                setOnClickListener { confirmDeletePersonalDeadline(event) }
            }, LinearLayout.LayoutParams(0, dp(38), 1f).apply {
                marginStart = dp(6)
            })
        } else {
            actionRow.addView(secondaryButton("Mở").apply {
                setOnClickListener { openEventMoodle(event) }
            }, LinearLayout.LayoutParams(0, dp(38), 1f))
            actionRow.addView(secondaryButton("Copy").apply {
                setOnClickListener { copyEventInfo(event) }
            }, LinearLayout.LayoutParams(0, dp(38), 1f).apply {
                marginStart = dp(6)
            })
        }
        actionRow.addView((if (isDone) outlineButton("Bỏ xong") else primaryButton("Xong")).apply {
            setOnClickListener { toggleDone(event) }
        }, LinearLayout.LayoutParams(0, dp(38), 1f).apply {
            marginStart = dp(6)
        })
        info.addView(actionRow)
        return cardView
    }

internal fun MainActivity.showPersonalDeadlineDialog(event: DeadlineEvent? = null) {
    val editing = event != null
    val initialDateTime = event
        ?.startAtMillis
        ?.let { Instant.ofEpochMilli(it).atZone(localZone).toLocalDateTime() }
        ?: LocalDateTime.now(localZone).plusHours(1).withSecond(0).withNano(0)
    var selectedDateTime = initialDateTime

    val dialogLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(12), dp(18), dp(4))
    }

    val titleInput = EditText(this).apply {
        hint = "Tên deadline cá nhân"
        setSingleLine(false)
        minLines = 1
        maxLines = 2
        textSize = 14f
        setTextColor(ink)
        setHintTextColor(Color.rgb(148, 163, 184))
        setPadding(dp(12), dp(9), dp(12), dp(9))
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        background = rounded(Color.rgb(248, 250, 252), 8, Color.rgb(203, 213, 225), 1)
        setText(event?.title.orEmpty())
    }
    dialogLayout.addView(titleInput, LinearLayout.LayoutParams(match(), wrap()))
    addSpacer(dialogLayout, 8)

    val descriptionInput = EditText(this).apply {
        hint = "Ghi chú / môn / việc cần làm (không bắt buộc)"
        setSingleLine(false)
        minLines = 2
        maxLines = 4
        textSize = 13f
        setTextColor(ink)
        setHintTextColor(Color.rgb(148, 163, 184))
        setPadding(dp(12), dp(9), dp(12), dp(9))
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        background = rounded(Color.rgb(248, 250, 252), 8, Color.rgb(203, 213, 225), 1)
        setText(event?.description.orEmpty())
    }
    dialogLayout.addView(descriptionInput, LinearLayout.LayoutParams(match(), wrap()))
    addSpacer(dialogLayout, 8)

    val timeText = TextView(this).apply {
        textSize = 14f
        setTextColor(blueDark)
        setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        setPadding(dp(12), dp(10), dp(12), dp(10))
        background = rounded(Color.rgb(231, 242, 255), 8, Color.rgb(190, 218, 248), 1)
    }

    fun refreshTimeText() {
        timeText.text = "Hạn: ${timeFormatter.format(selectedDateTime.atZone(localZone).toInstant())}"
    }
    refreshTimeText()
    dialogLayout.addView(timeText, LinearLayout.LayoutParams(match(), wrap()))
    addSpacer(dialogLayout, 8)

    val timeButtons = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }
    timeButtons.addView(secondaryButton("Chọn ngày").apply {
        setOnClickListener {
            DatePickerDialog(
                this@showPersonalDeadlineDialog,
                { _, year, month, dayOfMonth ->
                    selectedDateTime = LocalDateTime.of(
                        year,
                        month + 1,
                        dayOfMonth,
                        selectedDateTime.hour,
                        selectedDateTime.minute
                    )
                    refreshTimeText()
                },
                selectedDateTime.year,
                selectedDateTime.monthValue - 1,
                selectedDateTime.dayOfMonth
            ).show()
        }
    }, LinearLayout.LayoutParams(0, dp(42), 1f))
    timeButtons.addView(secondaryButton("Chọn giờ/phút").apply {
        setOnClickListener {
            TimePickerDialog(
                this@showPersonalDeadlineDialog,
                { _, hourOfDay, minute ->
                    selectedDateTime = selectedDateTime
                        .withHour(hourOfDay)
                        .withMinute(minute)
                        .withSecond(0)
                        .withNano(0)
                    refreshTimeText()
                },
                selectedDateTime.hour,
                selectedDateTime.minute,
                true
            ).show()
        }
    }, LinearLayout.LayoutParams(0, dp(42), 1f).apply {
        marginStart = dp(8)
    })
    dialogLayout.addView(timeButtons)

    val dialog = android.app.AlertDialog.Builder(this)
        .setTitle(if (editing) "Sửa deadline cá nhân" else "Thêm deadline cá nhân")
        .setView(dialogLayout)
        .setPositiveButton(if (editing) "Lưu" else "Thêm", null)
        .setNegativeButton("Hủy", null)
        .create()

    dialog.setOnShowListener {
        dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val title = titleInput.text.toString().trim()
            val description = descriptionInput.text.toString().trim().takeIf { it.isNotBlank() }
            val millis = selectedDateTime.atZone(localZone).toInstant().toEpochMilli()
            when {
                title.isBlank() -> {
                    Toast.makeText(this, "Hãy nhập tên deadline.", Toast.LENGTH_SHORT).show()
                }
                millis <= System.currentTimeMillis() -> {
                    Toast.makeText(this, "Hãy chọn thời gian còn ở tương lai.", Toast.LENGTH_SHORT).show()
                }
                editing && event != null -> {
                    EventStore.upsertPersonalEvent(
                        this,
                        event.copy(
                            title = title,
                            startAtMillis = millis,
                            description = description,
                            rawType = "Cá nhân",
                            source = DeadlineSource.PERSONAL,
                            sourceUrl = null
                        )
                    )
                    afterPersonalDeadlineChanged("Đã lưu deadline cá nhân.")
                    dialog.dismiss()
                }
                else -> {
                    EventStore.createPersonalEvent(
                        this,
                        title = title,
                        startAtMillis = millis,
                        description = description,
                        rawType = "Cá nhân"
                    )
                    afterPersonalDeadlineChanged("Đã thêm deadline cá nhân.")
                    dialog.dismiss()
                }
            }
        }
    }
    dialog.show()
}

internal fun MainActivity.confirmDeletePersonalDeadline(event: DeadlineEvent) {
    if (!event.isPersonal) return
    android.app.AlertDialog.Builder(this)
        .setTitle("Xóa deadline cá nhân")
        .setMessage("Xóa deadline \"${event.title}\"?")
        .setPositiveButton("Xóa") { _, _ ->
            EventStore.deletePersonalEvent(this, event.id)
            afterPersonalDeadlineChanged("Đã xóa deadline cá nhân.")
        }
        .setNegativeButton("Hủy", null)
        .show()
}

internal fun MainActivity.afterPersonalDeadlineChanged(message: String) {
    ReminderScheduler.scheduleAll(this, activeEventsForReminders())
    ReminderScheduler.scheduleDailySummary(this)
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    refreshEventsList()
}

internal fun MainActivity.emptyStateView(): View {
    val activity = this
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(18), dp(22), dp(18), dp(22))
            background = rounded(card, 8, line, 1)
            addView(hcmuteLogoView(58), LinearLayout.LayoutParams(dp(58), dp(58)))
            addSpacer(this, 10)
            addView(TextView(activity).apply {
                text = "Chưa có deadline"
                textSize = 16f
                setTextColor(ink)
                setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            })
            addView(TextView(activity).apply {
                text = "Dán iCal URL rồi bấm Đồng bộ để tải lịch."
                textSize = 13f
                setTextColor(muted)
                gravity = Gravity.CENTER
                setPadding(0, dp(5), 0, 0)
            })
        }
    }

internal fun MainActivity.filteredEmptyStateView(): View {
    val activity = this
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(18), dp(18), dp(18), dp(18))
            background = rounded(card, 8, line, 1)
            addView(TextView(activity).apply {
                text = "Không có mục phù hợp"
                textSize = 16f
                setTextColor(ink)
                setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            })
            addView(TextView(activity).apply {
                text = "Thử đổi từ khóa, bộ lọc hoặc bật hiển thị deadline đã xong."
                textSize = 13f
                setTextColor(muted)
                gravity = Gravity.CENTER
                setPadding(0, dp(5), 0, 0)
            })
        }
    }

internal fun MainActivity.remainText(millis: Long): String {
        val diff = millis - System.currentTimeMillis()
        if (diff <= 0) return "Đã tới hạn hoặc vừa qua hạn."
        val days = diff / (24L * 60L * 60L * 1000L)
        val hours = (diff / (60L * 60L * 1000L)) % 24
        val minutes = (diff / (60L * 1000L)) % 60
        return when {
            days > 0 -> "Còn $days ngày $hours giờ $minutes phút"
            hours > 0 -> "Còn $hours giờ $minutes phút"
            else -> "Còn $minutes phút"
        }
    }

internal fun MainActivity.updateLastSyncStatus() {
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

internal fun MainActivity.calendarDefaultStatus(): String {
        val url = EventStore.getIcalUrl(this)
        return if (url.isBlank()) {
            "Dán iCal URL để bắt đầu theo dõi lịch Moodle."
        } else {
            "Moodle đã kết nối: ${MoodleUrlValidator.mask(url)}"
        }
    }

internal fun MainActivity.setStatus(text: String, type: StatusType) {
        statusText.text = text
        val colors = when (type) {
            StatusType.INFO -> Triple(blueDark, Color.rgb(231, 242, 255), Color.rgb(190, 218, 248))
            StatusType.SUCCESS -> Triple(green, Color.rgb(229, 248, 239), Color.rgb(178, 229, 204))
            StatusType.ERROR -> Triple(red, Color.rgb(255, 235, 235), Color.rgb(249, 188, 188))
        }
        statusText.setTextColor(colors.first)
        statusText.background = rounded(colors.second, 8, colors.third, 1)
    }

internal fun MainActivity.updateNotificationChip() {
        if (isAlertSetupReady()) {
            notificationChip.text = "Cảnh báo sẵn sàng"
            notificationChip.setTextColor(Color.WHITE)
            notificationChip.background = rounded(Color.argb(45, 255, 255, 255), 8, Color.argb(90, 255, 255, 255), 1)
        } else {
            notificationChip.text = if (!isIgnoringBatteryOptimizations()) "Cần tắt tiết kiệm pin" else "Cần bật cảnh báo"
            notificationChip.setTextColor(Color.WHITE)
            notificationChip.background = rounded(red, 8)
        }
    }

internal fun MainActivity.requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33) {
            val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
            }
        }
    }

internal fun MainActivity.eventKind(event: DeadlineEvent): String {
        return EventLabels.kind(event)
    }

internal fun MainActivity.accentFor(event: DeadlineEvent): Int {
        val diff = event.startAtMillis - System.currentTimeMillis()
        val group = EventLabels.broadGroup(event)
        return when {
            diff <= 24L * 60L * 60L * 1000L -> red
            diff <= 3L * 24L * 60L * 60L * 1000L -> amber
            group == EventGroup.SUBMISSION -> green
            group == EventGroup.EXAM -> red
            else -> blue
        }
    }

internal fun MainActivity.tint(color: Int): Int {
        return when (color) {
            red -> Color.rgb(255, 235, 235)
            amber -> Color.rgb(255, 244, 222)
            green -> Color.rgb(229, 248, 239)
            else -> Color.rgb(226, 238, 252)
        }
    }

internal fun MainActivity.searchable(text: String): String {
        return Normalizer.normalize(text.lowercase(Locale.forLanguageTag("vi-VN")), Normalizer.Form.NFD)
            .replace("\\p{M}+".toRegex(), "")
            .replace('đ', 'd')
    }
