package com.utex.deadline

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private lateinit var urlInput: EditText
    private lateinit var statusText: TextView
    private lateinit var eventsContainer: LinearLayout

    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm - EEEE dd/MM/yyyy", Locale.forLanguageTag("vi-VN"))
        .withZone(ZoneId.of("Asia/Ho_Chi_Minh"))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NotificationHelper.ensureChannel(this)
        requestNotificationPermissionIfNeeded()
        buildUi()
        ReminderScheduler.schedulePeriodicSync(this)
        refreshEventsList()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(12))
        }

        val title = TextView(this).apply {
            text = "UTE Deadline"
            textSize = 26f
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        }
        root.addView(title)

        val subtitle = TextView(this).apply {
            text = "Dán Moodle iCal URL một lần. App tự kiểm tra deadline mới và thông báo trên điện thoại."
            textSize = 14f
            setPadding(0, dp(6), 0, dp(14))
        }
        root.addView(subtitle)

        urlInput = EditText(this).apply {
            hint = "Dán iCal URL của Moodle UTEx"
            setSingleLine(false)
            minLines = 2
            inputType = InputType.TYPE_TEXT_VARIATION_URI
            setText(EventStore.getIcalUrl(this@MainActivity))
        }
        root.addView(urlInput, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)

        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(10), 0, dp(8))
        }

        val saveButton = Button(this).apply {
            text = "Lưu URL"
            setOnClickListener {
                EventStore.setIcalUrl(this@MainActivity, urlInput.text.toString())
                ReminderScheduler.schedulePeriodicSync(this@MainActivity)
                setStatus("Đã lưu iCal URL. App sẽ tự kiểm tra định kỳ.")
            }
        }
        buttons.addView(saveButton, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        val syncButton = Button(this).apply {
            text = "Kiểm tra ngay"
            setOnClickListener {
                EventStore.setIcalUrl(this@MainActivity, urlInput.text.toString())
                syncNow()
            }
        }
        val syncLp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = dp(8)
        }
        buttons.addView(syncButton, syncLp)
        root.addView(buttons)

        val resetButton = Button(this).apply {
            text = "Test lại thông báo deadline mới"
            setOnClickListener {
                EventStore.resetKnownIds(this@MainActivity)
                setStatus("Đã reset danh sách đã biết. Bấm 'Kiểm tra ngay' để test thông báo.")
            }
        }
        root.addView(resetButton, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)

        statusText = TextView(this).apply {
            textSize = 14f
            setPadding(0, dp(10), 0, dp(10))
        }
        root.addView(statusText)

        val listTitle = TextView(this).apply {
            text = "Deadline sắp tới"
            textSize = 18f
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            setPadding(0, dp(8), 0, dp(8))
        }
        root.addView(listTitle)

        eventsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        val scroll = ScrollView(this).apply {
            addView(eventsContainer)
        }
        root.addView(scroll, LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)

        setContentView(root)
        updateLastSyncStatus()
    }

    private fun syncNow() {
        setStatus("Đang kiểm tra lịch Moodle...")
        thread {
            val result = DeadlineSync.sync(this, notifyNew = true)
            runOnUiThread {
                setStatus(result.message)
                refreshEventsList()
            }
        }
    }

    private fun refreshEventsList() {
        eventsContainer.removeAllViews()
        val events = EventStore.loadEvents(this)
        if (events.isEmpty()) {
            eventsContainer.addView(TextView(this).apply {
                text = "Chưa có deadline. Dán iCal URL rồi bấm 'Kiểm tra ngay'."
                textSize = 15f
                setPadding(0, dp(8), 0, dp(8))
            })
            updateLastSyncStatus()
            return
        }

        events.forEach { event ->
            eventsContainer.addView(eventView(event))
        }
        updateLastSyncStatus()
    }

    private fun eventView(event: DeadlineEvent): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = ContextCompat.getDrawable(this@MainActivity, android.R.drawable.dialog_holo_light_frame)
        }
        val title = TextView(this).apply {
            text = event.title
            textSize = 16f
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        }
        val time = TextView(this).apply {
            text = "Hạn: ${timeFormatter.format(Instant.ofEpochMilli(event.startAtMillis))}"
            textSize = 14f
            setPadding(0, dp(4), 0, 0)
        }
        val remain = TextView(this).apply {
            text = remainText(event.startAtMillis)
            textSize = 14f
            setPadding(0, dp(2), 0, 0)
        }
        box.addView(title)
        box.addView(time)
        box.addView(remain)
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(10))
            addView(box)
        }
    }

    private fun remainText(millis: Long): String {
        val diff = millis - System.currentTimeMillis()
        if (diff <= 0) return "Đã tới hạn hoặc vừa qua hạn."
        val days = diff / (24L * 60L * 60L * 1000L)
        val hours = (diff / (60L * 60L * 1000L)) % 24
        val minutes = (diff / (60L * 1000L)) % 60
        return when {
            days > 0 -> "Còn khoảng $days ngày $hours giờ."
            hours > 0 -> "Còn khoảng $hours giờ $minutes phút."
            else -> "Còn khoảng $minutes phút."
        }
    }

    private fun updateLastSyncStatus() {
        val lastSync = EventStore.getLastSync(this)
        if (lastSync > 0L && statusText.text.isBlank()) {
            setStatus("Lần cập nhật gần nhất: ${timeFormatter.format(Instant.ofEpochMilli(lastSync))}")
        }
    }

    private fun setStatus(text: String) {
        statusText.text = text
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33) {
            val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
            }
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
