package com.utex.deadline

internal enum class StatusType {
        INFO,
        SUCCESS,
        ERROR
    }

internal enum class ScreenTab {
        CALENDAR,
        GUIDE,
        NOTIFICATION_SETTINGS
    }

internal enum class EventViewMode {
        LIST,
        MONTH
    }

internal enum class EventFilter(val label: String) {
        ALL("Tất cả"),
        SUBMISSION("Bài nộp"),
        TEST("Kiểm tra"),
        EXAM("Thi"),
        PERSONAL("Cá nhân")
    }
