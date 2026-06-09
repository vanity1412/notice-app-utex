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

internal fun MainActivity.showGuideTab() {
        activeTab = ScreenTab.GUIDE
        updateTabButtons()
        tabContent.removeAllViews()

        tabContent.addView(quickGuidePanel())
        addSpacer(tabContent, 16)
    }

internal fun MainActivity.quickGuidePanel(): View {
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

internal fun MainActivity.exportUrlRow(): View {
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

internal fun MainActivity.copyMoodleExportUrl() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Moodle export URL", moodleExportUrl))
        Toast.makeText(this, "Đã copy link Moodle", Toast.LENGTH_SHORT).show()
    }
