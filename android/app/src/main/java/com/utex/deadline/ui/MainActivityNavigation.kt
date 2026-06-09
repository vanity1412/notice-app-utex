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

internal fun MainActivity.openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: Exception) {
            setStatus("Không mở được liên kết trên máy này.", StatusType.ERROR)
        }
    }

internal fun MainActivity.openEventMoodle(event: DeadlineEvent) {
        openUrl(event.sourceUrl?.takeIf { it.isNotBlank() } ?: "https://utexlms.hcmute.edu.vn/calendar/view.php?view=month")
    }

internal fun MainActivity.copyEventInfo(event: DeadlineEvent) {
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
