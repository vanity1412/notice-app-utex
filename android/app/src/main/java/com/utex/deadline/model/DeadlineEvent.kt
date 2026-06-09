package com.utex.deadline

enum class DeadlineSource {
    MOODLE,
    PERSONAL;

    companion object {
        fun fromStored(value: String?): DeadlineSource {
            return values().firstOrNull { it.name.equals(value.orEmpty(), ignoreCase = true) } ?: MOODLE
        }
    }
}

data class DeadlineEvent(
    val id: String,
    val title: String,
    val startAtMillis: Long,
    val sourceUrl: String? = null,
    val rawType: String? = null,
    val description: String? = null,
    val source: DeadlineSource = DeadlineSource.MOODLE
) {
    val isMoodle: Boolean get() = source == DeadlineSource.MOODLE
    val isPersonal: Boolean get() = source == DeadlineSource.PERSONAL
}

data class SyncResult(
    val ok: Boolean,
    val message: String,
    val totalEvents: Int = 0,
    val newEvents: Int = 0,
    val retryable: Boolean = false
)

data class PendingDeadlineNotification(
    val key: String,
    val type: String,
    val event: DeadlineEvent,
    val timestamp: Long? = null,
    val leadText: String? = null,
    val leadMinutes: Long? = null
)
