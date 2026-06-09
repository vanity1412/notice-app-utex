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

internal fun MainActivity.buildUi() {
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

internal fun MainActivity.tabBar(): View {
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
        notificationSettingsTab = tabButton("Cài đặt\nthông báo").apply {
            setOnClickListener {
                if (activeTab != ScreenTab.NOTIFICATION_SETTINGS) showNotificationSettingsTab()
            }
        }
        row.addView(calendarTab, LinearLayout.LayoutParams(0, dp(46), 1f))
        row.addView(guideTab, LinearLayout.LayoutParams(0, dp(46), 1f).apply {
            marginStart = dp(4)
        })
        row.addView(notificationSettingsTab, LinearLayout.LayoutParams(0, dp(46), 1f).apply {
            marginStart = dp(4)
        })
        return row
    }

internal fun MainActivity.tabButton(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 12f
            gravity = Gravity.CENTER
            maxLines = 2
            setLineSpacing(0f, 0.95f)
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            setPadding(dp(6), 0, dp(6), 0)
        }
    }

internal fun MainActivity.updateTabButtons() {
        if (!hasTabs()) return
        val tabs = listOf(
            calendarTab to ScreenTab.CALENDAR,
            guideTab to ScreenTab.GUIDE,
            notificationSettingsTab to ScreenTab.NOTIFICATION_SETTINGS
        )
        tabs.forEach { (tab, screenTab) ->
            val selected = activeTab == screenTab
            tab.background = if (selected) rounded(card, 8, line, 1) else rounded(Color.TRANSPARENT, 8)
            tab.setTextColor(if (selected) blueDark else muted)
        }
    }

internal fun MainActivity.headerView(): View {
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

internal fun MainActivity.primaryButton(text: String): Button {
        return Button(this).apply {
            this.text = text
            textSize = 13f
            setTextColor(Color.WHITE)
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            setAllCaps(false)
            background = rounded(blue, 8)
        }
    }

internal fun MainActivity.secondaryButton(text: String): Button {
        return Button(this).apply {
            this.text = text
            textSize = 13f
            setTextColor(blue)
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            setAllCaps(false)
            background = rounded(Color.rgb(232, 242, 255), 8, Color.rgb(178, 209, 245), 1)
        }
    }

internal fun MainActivity.outlineButton(text: String): Button {
        return Button(this).apply {
            this.text = text
            textSize = 12f
            setTextColor(red)
            setAllCaps(false)
            background = rounded(Color.WHITE, 8, Color.rgb(252, 190, 190), 1)
        }
    }

internal fun MainActivity.chip(text: String, textColor: Int, backgroundColor: Int): TextView {
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

internal fun MainActivity.rounded(color: Int, radiusDp: Int, strokeColor: Int? = null, strokeDp: Int = 0): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color)
            cornerRadius = dp(radiusDp).toFloat()
            if (strokeColor != null && strokeDp > 0) {
                setStroke(dp(strokeDp), strokeColor)
            }
        }
    }

internal fun MainActivity.addSpacer(parent: LinearLayout, heightDp: Int) {
        parent.addView(View(this), LinearLayout.LayoutParams(match(), dp(heightDp)))
    }

internal fun MainActivity.hcmuteLogoView(sizeDp: Int): ImageView {
        return ImageView(this).apply {
            setImageResource(R.drawable.hcmute_logo)
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true
            setPadding(dp(4), dp(4), dp(4), dp(4))
            background = rounded(Color.WHITE, sizeDp / 2, Color.argb(70, 255, 255, 255), 1)
            contentDescription = "Logo HCMUTE"
        }
    }

internal fun MainActivity.applySystemBars() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.statusBarColor = blueDark
            window.navigationBarColor = page
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        }
    }

internal fun MainActivity.match(): Int = ViewGroup.LayoutParams.MATCH_PARENT

internal fun MainActivity.wrap(): Int = ViewGroup.LayoutParams.WRAP_CONTENT

internal fun MainActivity.dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
