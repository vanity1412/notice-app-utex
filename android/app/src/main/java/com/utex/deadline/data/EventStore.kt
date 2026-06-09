package com.utex.deadline

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

object EventStore {
    private const val PREFS = "ute_deadline_secure_prefs"
    private const val LEGACY_PREFS = "ute_deadline_prefs"
    private const val KEY_ICAL_URL = "ical_url"
    private const val KEY_EVENTS = "events_json" // Moodle read-only events
    private const val KEY_PERSONAL_EVENTS = "personal_events_json"
    private const val KEY_KNOWN_IDS = "known_ids"
    private const val KEY_LAST_SYNC = "last_sync"
    private const val KEY_DAILY_SUMMARY_TOUCHED = "daily_summary_touched"
    private const val KEY_DAILY_SUMMARY_ENABLED = "daily_summary_enabled"
    private const val KEY_DAILY_SUMMARY_HOUR = "daily_summary_hour"
    private const val KEY_DAILY_SUMMARY_MINUTE = "daily_summary_minute"
    private const val KEY_DAILY_SUMMARY_DAYS = "daily_summary_days"
    private const val KEY_DONE_IDS = "done_ids"
    private const val KEY_REMINDER_OFFSETS = "reminder_offsets"
    private const val KEY_CUSTOM_REMINDER_OFFSETS = "custom_reminder_offsets"
    private const val KEY_PENDING_NOTIFICATIONS = "pending_notifications_json"
    private const val KEY_SCHEDULED_REMINDER_ALARMS = "scheduled_reminder_alarms"
    private const val KEY_DELIVERED_NOTIFICATION_KEYS = "delivered_notification_keys"
    private const val KEY_USER_EMAIL = "user_email"
    private const val KEY_EMAIL_NOTIFICATION_ENABLED = "email_notification_enabled"
    const val ALL_DAYS_MASK = 0b1111111
    private val DEFAULT_REMINDER_MINUTES = listOf(24L * 60L, 12L * 60L, 60L, 0L)
    private val PRESET_REMINDER_MINUTES = listOf(
        7L * 24L * 60L,      // 7 ngày
        3L * 24L * 60L,      // 3 ngày  
        2L * 24L * 60L,      // 2 ngày
        24L * 60L,           // 1 ngày
        12L * 60L,           // 12 giờ
        6L * 60L,            // 6 giờ
        3L * 60L,            // 3 giờ
        60L,                 // 1 giờ
        30L,                 // 30 phút
        15L,                 // 15 phút
        0L                   // Đúng lúc deadline
    )
    private const val MAX_DELIVERED_NOTIFICATION_KEYS = 600
    @Volatile
    private var cachedPrefs: SharedPreferences? = null
    @Volatile
    private var cachedMoodleEvents: List<DeadlineEvent>? = null
    @Volatile
    private var cachedPersonalEvents: List<DeadlineEvent>? = null
    @Volatile
    private var cachedDoneIds: Set<String>? = null

    fun getIcalUrl(context: Context): String {
        return prefs(context).getString(KEY_ICAL_URL, "").orEmpty()
    }

    fun setIcalUrl(context: Context, url: String) {
        prefs(context).edit().putString(KEY_ICAL_URL, url.trim()).apply()
    }

    fun clearConnection(context: Context) {
        prefs(context).edit()
            .remove(KEY_ICAL_URL)
            .remove(KEY_EVENTS)
            .remove(KEY_KNOWN_IDS)
            .remove(KEY_LAST_SYNC)
            .remove(KEY_PENDING_NOTIFICATIONS)
            .remove(KEY_SCHEDULED_REMINDER_ALARMS)
            .remove(KEY_DELIVERED_NOTIFICATION_KEYS)
            .apply()
        cachedMoodleEvents = emptyList()
        clearLegacyConnection(context)
    }

    fun getLastSync(context: Context): Long {
        return prefs(context).getLong(KEY_LAST_SYNC, 0L)
    }

    fun setLastSync(context: Context, millis: Long) {
        prefs(context).edit().putLong(KEY_LAST_SYNC, millis).apply()
    }

    fun isDailySummaryEnabled(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_DAILY_SUMMARY_ENABLED, false)
    }

    fun setDailySummaryEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit()
            .putBoolean(KEY_DAILY_SUMMARY_TOUCHED, true)
            .putBoolean(KEY_DAILY_SUMMARY_ENABLED, enabled)
            .apply()
    }

    fun prepareDailySummaryAfterSetup(context: Context) {
        val preferences = prefs(context)
        if (preferences.getBoolean(KEY_DAILY_SUMMARY_TOUCHED, false)) return
        preferences.edit()
            .putBoolean(KEY_DAILY_SUMMARY_ENABLED, true)
            .putInt(KEY_DAILY_SUMMARY_HOUR, getDailySummaryHour(context))
            .putInt(KEY_DAILY_SUMMARY_MINUTE, getDailySummaryMinute(context))
            .putInt(KEY_DAILY_SUMMARY_DAYS, getDailySummaryDaysMask(context))
            .apply()
    }

    fun getDailySummaryHour(context: Context): Int {
        return prefs(context).getInt(KEY_DAILY_SUMMARY_HOUR, 6)
    }

    fun getDailySummaryMinute(context: Context): Int {
        return prefs(context).getInt(KEY_DAILY_SUMMARY_MINUTE, 0)
    }

    fun setDailySummaryTime(context: Context, hour: Int, minute: Int) {
        prefs(context).edit()
            .putBoolean(KEY_DAILY_SUMMARY_TOUCHED, true)
            .putBoolean(KEY_DAILY_SUMMARY_ENABLED, true)
            .putInt(KEY_DAILY_SUMMARY_HOUR, hour.coerceIn(0, 23))
            .putInt(KEY_DAILY_SUMMARY_MINUTE, minute.coerceIn(0, 59))
            .apply()
    }

    fun getDailySummaryDaysMask(context: Context): Int {
        return prefs(context).getInt(KEY_DAILY_SUMMARY_DAYS, ALL_DAYS_MASK).takeIf { it != 0 } ?: ALL_DAYS_MASK
    }

    fun setDailySummaryDaysMask(context: Context, daysMask: Int) {
        val safeMask = daysMask and ALL_DAYS_MASK
        prefs(context).edit()
            .putBoolean(KEY_DAILY_SUMMARY_TOUCHED, true)
            .putBoolean(KEY_DAILY_SUMMARY_ENABLED, safeMask != 0)
            .putInt(KEY_DAILY_SUMMARY_DAYS, safeMask)
            .apply()
    }

    fun isDailySummaryAllowedToday(context: Context): Boolean {
        val dayValue = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).dayOfWeek.value
        val bit = 1 shl (dayValue - 1)
        return getDailySummaryDaysMask(context) and bit != 0
    }

    /**
     * Danh sách dùng cho UI, nhắc lịch, tổng hợp: Moodle read-only + deadline cá nhân editable.
     */
    fun loadEvents(context: Context): List<DeadlineEvent> = loadAllEvents(context)

    fun loadAllEvents(context: Context): List<DeadlineEvent> {
        return (loadMoodleEvents(context) + loadPersonalEvents(context))
            .distinctBy { it.id }
            .sortedBy { it.startAtMillis }
    }

    /**
     * Chỉ deadline lấy từ Moodle. Người dùng không được sửa trực tiếp danh sách này.
     */
    fun loadMoodleEvents(context: Context): List<DeadlineEvent> {
        cachedMoodleEvents?.let { return it }
        val json = prefs(context).getString(KEY_EVENTS, "[]").orEmpty()
        return parseEventsJson(json, DeadlineSource.MOODLE).also {
            cachedMoodleEvents = it
        }
    }

    fun saveMoodleEvents(context: Context, events: List<DeadlineEvent>) {
        val sortedEvents = events
            .map { it.copy(source = DeadlineSource.MOODLE) }
            .sortedBy { it.startAtMillis }
        prefs(context).edit().putString(KEY_EVENTS, eventsToJson(sortedEvents).toString()).apply()
        cachedMoodleEvents = sortedEvents
        clearDoneForMissingEvents(context, (sortedEvents + loadPersonalEvents(context)).map { it.id }.toSet())
    }

    /**
     * Tương thích với code cũ: saveEvents hiện chỉ dùng cho Moodle sync.
     */
    fun saveEvents(context: Context, events: List<DeadlineEvent>) = saveMoodleEvents(context, events)

    fun loadPersonalEvents(context: Context): List<DeadlineEvent> {
        cachedPersonalEvents?.let { return it }
        val json = prefs(context).getString(KEY_PERSONAL_EVENTS, "[]").orEmpty()
        return parseEventsJson(json, DeadlineSource.PERSONAL).also {
            cachedPersonalEvents = it
        }
    }

    fun savePersonalEvents(context: Context, events: List<DeadlineEvent>) {
        val sortedEvents = events
            .map { it.copy(source = DeadlineSource.PERSONAL, sourceUrl = null) }
            .sortedBy { it.startAtMillis }
        prefs(context).edit().putString(KEY_PERSONAL_EVENTS, eventsToJson(sortedEvents).toString()).apply()
        cachedPersonalEvents = sortedEvents
        clearDoneForMissingEvents(context, (loadMoodleEvents(context) + sortedEvents).map { it.id }.toSet())
    }

    fun createPersonalEvent(
        context: Context,
        title: String,
        startAtMillis: Long,
        description: String? = null,
        rawType: String? = "Cá nhân"
    ): DeadlineEvent {
        val event = DeadlineEvent(
            id = "personal-${UUID.randomUUID()}",
            title = title.trim(),
            startAtMillis = startAtMillis,
            sourceUrl = null,
            rawType = rawType?.trim()?.takeIf { it.isNotBlank() } ?: "Cá nhân",
            description = description?.trim()?.takeIf { it.isNotBlank() },
            source = DeadlineSource.PERSONAL
        )
        upsertPersonalEvent(context, event)
        return event
    }

    fun upsertPersonalEvent(context: Context, event: DeadlineEvent) {
        val personalEvent = event.copy(source = DeadlineSource.PERSONAL, sourceUrl = null)
        val next = loadPersonalEvents(context)
            .filterNot { it.id == personalEvent.id }
            .plus(personalEvent)
            .sortedBy { it.startAtMillis }
        savePersonalEvents(context, next)
    }

    fun deletePersonalEvent(context: Context, eventId: String) {
        val next = loadPersonalEvents(context).filterNot { it.id == eventId }
        savePersonalEvents(context, next)
        setDone(context, eventId, false)
    }

    fun isEditablePersonalEvent(context: Context, eventId: String): Boolean {
        return loadPersonalEvents(context).any { it.id == eventId }
    }

    private fun parseEventsJson(json: String, defaultSource: DeadlineSource): List<DeadlineEvent> {
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).mapNotNull { i ->
                val obj = arr.optJSONObject(i) ?: return@mapNotNull null
                eventFromJson(obj, defaultSource)
            }.sortedBy { it.startAtMillis }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun eventsToJson(events: List<DeadlineEvent>): JSONArray {
        val arr = JSONArray()
        events.sortedBy { it.startAtMillis }.forEach { event ->
            arr.put(eventToJson(event))
        }
        return arr
    }

    fun getKnownIds(context: Context): MutableSet<String> {
        return prefs(context).getStringSet(KEY_KNOWN_IDS, emptySet())?.toMutableSet() ?: mutableSetOf()
    }

    fun saveKnownIds(context: Context, ids: Set<String>) {
        prefs(context).edit().putStringSet(KEY_KNOWN_IDS, ids.toSet()).apply()
    }

    fun resetKnownIds(context: Context) {
        prefs(context).edit().remove(KEY_KNOWN_IDS).apply()
    }

    fun loadPendingDeadlineNotifications(context: Context): List<PendingDeadlineNotification> {
        val json = prefs(context).getString(KEY_PENDING_NOTIFICATIONS, "[]").orEmpty()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).mapNotNull { i ->
                val obj = arr.optJSONObject(i) ?: return@mapNotNull null
                val type = obj.optString("type").takeIf {
                    it == "new" || it == "changed" || it == "reminder" || it == "daily-summary" || it == "initial-summary"
                } ?: return@mapNotNull null
                val event = obj.optJSONObject("event")?.let { pendingEventFromJson(it) } ?: return@mapNotNull null
                val key = obj.optString("key").takeIf { it.isNotBlank() } ?: "$type-${event.id}"
                val timestamp = obj.optLong("timestamp", 0L).takeIf { it > 0L }
                val leadText = obj.optString("leadText").takeIf { it.isNotBlank() }
                val leadMinutes = if (obj.has("leadMinutes")) {
                    obj.optLong("leadMinutes", 0L).takeIf { it >= 0L }
                } else {
                    null
                }
                PendingDeadlineNotification(key, type, event, timestamp, leadText, leadMinutes)
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun upsertPendingDeadlineNotifications(context: Context, notifications: List<PendingDeadlineNotification>) {
        if (notifications.isEmpty()) return
        val merged = LinkedHashMap<String, PendingDeadlineNotification>()
        loadPendingDeadlineNotifications(context).forEach { merged[it.key] = it }
        notifications.forEach { merged[it.key] = it }
        savePendingDeadlineNotifications(context, merged.values.toList())
    }

    fun savePendingDeadlineNotifications(context: Context, notifications: List<PendingDeadlineNotification>) {
        val arr = JSONArray()
        notifications.distinctBy { it.key }.forEach { pending ->
            arr.put(JSONObject().apply {
                put("key", pending.key)
                put("type", pending.type)
                put("event", eventToJson(pending.event))
                pending.timestamp?.let { put("timestamp", it) }
                pending.leadText?.let { put("leadText", it) }
                pending.leadMinutes?.let { put("leadMinutes", it) }
            })
        }
        prefs(context).edit().putString(KEY_PENDING_NOTIFICATIONS, arr.toString()).apply()
    }

    fun getScheduledReminderAlarmKeys(context: Context): Set<String> {
        return prefs(context).getStringSet(KEY_SCHEDULED_REMINDER_ALARMS, emptySet())?.toSet() ?: emptySet()
    }

    fun saveScheduledReminderAlarmKeys(context: Context, keys: Set<String>) {
        prefs(context).edit().putStringSet(KEY_SCHEDULED_REMINDER_ALARMS, keys).apply()
    }

    fun clearScheduledReminderAlarmKeys(context: Context) {
        prefs(context).edit().remove(KEY_SCHEDULED_REMINDER_ALARMS).apply()
    }

    /**
     * Tránh bắn lặp cùng một mốc khi cả AlarmManager và WorkManager backup cùng chạy.
     * Trả về true nếu đây là lần đầu xử lý key này, false nếu đã xử lý trước đó.
     */
    fun tryMarkNotificationDelivery(context: Context, key: String): Boolean {
        val safeKey = key.trim().takeIf { it.isNotBlank() } ?: return true
        return synchronized(this) {
            val preferences = prefs(context)
            val current = preferences.getStringSet(KEY_DELIVERED_NOTIFICATION_KEYS, emptySet())
                ?.toMutableSet()
                ?: mutableSetOf()
            if (safeKey in current) {
                return@synchronized false
            }
            if (current.size >= MAX_DELIVERED_NOTIFICATION_KEYS) {
                current.clear()
            }
            current += safeKey
            preferences.edit().putStringSet(KEY_DELIVERED_NOTIFICATION_KEYS, current).commit()
            true
        }
    }

    fun clearNotificationDeliveryHistory(context: Context) {
        prefs(context).edit().remove(KEY_DELIVERED_NOTIFICATION_KEYS).apply()
    }

    fun getDoneIds(context: Context): Set<String> {
        cachedDoneIds?.let { return it }
        return (prefs(context).getStringSet(KEY_DONE_IDS, emptySet())?.toSet() ?: emptySet()).also {
            cachedDoneIds = it
        }
    }

    fun isDone(context: Context, eventId: String): Boolean {
        return eventId in getDoneIds(context)
    }

    fun setDone(context: Context, eventId: String, done: Boolean) {
        val ids = getDoneIds(context).toMutableSet()
        if (done) {
            ids += eventId
        } else {
            ids -= eventId
        }
        val nextIds = ids.toSet()
        prefs(context).edit().putStringSet(KEY_DONE_IDS, nextIds).apply()
        cachedDoneIds = nextIds
    }

    fun getReminderOffsetOptions(): List<Long> = PRESET_REMINDER_MINUTES

    fun getCustomReminderOffsets(context: Context): List<Long> {
        val json = prefs(context).getString(KEY_CUSTOM_REMINDER_OFFSETS, "[]").orEmpty()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).mapNotNull { i ->
                arr.optLong(i).takeIf { it > 0 }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun addCustomReminderOffset(context: Context, minutes: Long) {
        if (minutes <= 0) return
        val current = getCustomReminderOffsets(context).toMutableSet()
        current += minutes
        saveCustomReminderOffsets(context, current.toList())
        
        // Tự động enable custom reminder offset
        val selected = getReminderOffsetsMinutes(context).toMutableSet()
        selected += minutes
        saveReminderOffsetsMinutes(context, selected.toList())
    }

    fun removeCustomReminderOffset(context: Context, minutes: Long) {
        val current = getCustomReminderOffsets(context).toMutableSet()
        current -= minutes
        saveCustomReminderOffsets(context, current.toList())
        
        // Tự động disable nếu không còn trong custom list và không phải preset
        if (minutes !in PRESET_REMINDER_MINUTES) {
            val selected = getReminderOffsetsMinutes(context).toMutableSet()
            selected -= minutes
            if (selected.isEmpty()) selected += 60L // Đảm bảo ít nhất 1 mốc
            saveReminderOffsetsMinutes(context, selected.toList())
        }
    }

    private fun saveCustomReminderOffsets(context: Context, offsets: List<Long>) {
        val arr = JSONArray()
        offsets.sorted().forEach { arr.put(it) }
        prefs(context).edit().putString(KEY_CUSTOM_REMINDER_OFFSETS, arr.toString()).apply()
    }

    fun getAllReminderOffsetOptions(context: Context): List<Long> {
        return (PRESET_REMINDER_MINUTES + getCustomReminderOffsets(context)).distinct().sorted().reversed()
    }

    fun getReminderOffsetsMinutes(context: Context): List<Long> {
        val saved = prefs(context).getString(KEY_REMINDER_OFFSETS, null)
        val parsed = saved
            ?.split(',')
            ?.mapNotNull { it.trim().toLongOrNull() }
            ?.filter { it >= 0 }
            ?.distinct()
            .orEmpty()
        val selected = parsed.ifEmpty { DEFAULT_REMINDER_MINUTES }
        val allOptions = getAllReminderOffsetOptions(context)
        return allOptions.filter { it in selected }.ifEmpty { listOf(60L) }
    }

    private fun saveReminderOffsetsMinutes(context: Context, offsets: List<Long>) {
        val saved = offsets.filter { it >= 0 }.distinct().joinToString(",")
        prefs(context).edit().putString(KEY_REMINDER_OFFSETS, saved).apply()
    }

    fun isReminderOffsetEnabled(context: Context, minutes: Long): Boolean {
        return minutes in getReminderOffsetsMinutes(context)
    }

    fun setReminderOffsetEnabled(context: Context, minutes: Long, enabled: Boolean) {
        val allOptions = getAllReminderOffsetOptions(context)
        if (minutes !in allOptions) return
        val selected = getReminderOffsetsMinutes(context).toMutableSet()
        if (enabled) {
            selected += minutes
        } else {
            selected -= minutes
        }
        val safeSelected = selected.ifEmpty { setOf(60L) }
        saveReminderOffsetsMinutes(context, safeSelected.toList())
    }

    fun reminderOptionLabel(minutes: Long): String {
        return when (minutes) {
            0L -> "Đúng lúc deadline"
            7L * 24L * 60L -> "7 ngày"
            3L * 24L * 60L -> "3 ngày"
            2L * 24L * 60L -> "2 ngày"
            24L * 60L -> "1 ngày"
            12L * 60L -> "12 giờ"
            6L * 60L -> "6 giờ"
            3L * 60L -> "3 giờ"
            60L -> "1 giờ"
            30L -> "30 phút"
            15L -> "15 phút"
            else -> {
                // Format custom reminders
                val days = minutes / (24L * 60L)
                val hours = (minutes % (24L * 60L)) / 60L
                val mins = minutes % 60L
                when {
                    days > 0 && hours == 0L && mins == 0L -> "$days ngày"
                    days > 0 && hours > 0 && mins == 0L -> "$days ngày $hours giờ"
                    days > 0 -> "$days ngày $hours giờ $mins phút"
                    hours > 0 && mins == 0L -> "$hours giờ"
                    hours > 0 -> "$hours giờ $mins phút"
                    else -> "$mins phút"
                }
            }
        }
    }

    fun reminderLeadLabel(minutes: Long): String {
        return if (minutes == 0L) {
            "ĐÃ TỚI HẠN"
        } else {
            "${reminderOptionLabel(minutes)} trước hạn"
        }
    }

    fun reminderOffsetsText(context: Context): String {
        return getReminderOffsetsMinutes(context)
            .joinToString(", ") { reminderOptionLabel(it) }
            .ifBlank { "1 giờ" }
    }

    // Email notification settings
    fun getUserEmail(context: Context): String {
        return prefs(context).getString(KEY_USER_EMAIL, "").orEmpty()
    }

    fun setUserEmail(context: Context, email: String) {
        prefs(context).edit().putString(KEY_USER_EMAIL, email.trim()).apply()
    }

    fun isEmailNotificationEnabled(context: Context): Boolean {
        return getUserEmail(context).isNotBlank() &&
            prefs(context).getBoolean(KEY_EMAIL_NOTIFICATION_ENABLED, false)
    }

    fun setEmailNotificationEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_EMAIL_NOTIFICATION_ENABLED, enabled).apply()
    }

    private fun prefs(context: Context): SharedPreferences {
        cachedPrefs?.let { return it }
        return synchronized(this) {
            cachedPrefs ?: run {
                val appContext = context.applicationContext
                val encryptedPrefs = encryptedPrefsOrThrow(appContext)
                migrateLegacyPrefs(appContext, encryptedPrefs)
                cachedPrefs = encryptedPrefs
                encryptedPrefs
            }
        }
    }

    private fun encryptedPrefsOrThrow(context: Context): SharedPreferences {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            return EncryptedSharedPreferences.create(
                context,
                PREFS,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            throw IllegalStateException(
                "Không thể mở bộ nhớ mã hóa. Không lưu Moodle token bằng SharedPreferences thường.",
                e
            )
        }
    }

    private fun legacyPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
    }

    private fun migrateLegacyPrefs(context: Context, securePrefs: SharedPreferences) {
        val legacyPrefs = legacyPrefs(context)
        if (!legacyPrefs.contains(KEY_ICAL_URL) || securePrefs.contains(KEY_ICAL_URL)) return

        val editor = securePrefs.edit()
        listOf(
            KEY_ICAL_URL,
            KEY_EVENTS,
            KEY_PERSONAL_EVENTS,
            KEY_LAST_SYNC,
            KEY_DAILY_SUMMARY_ENABLED,
            KEY_DAILY_SUMMARY_TOUCHED,
            KEY_DAILY_SUMMARY_HOUR,
            KEY_DAILY_SUMMARY_MINUTE,
            KEY_DAILY_SUMMARY_DAYS,
            KEY_REMINDER_OFFSETS,
            KEY_CUSTOM_REMINDER_OFFSETS,
            KEY_PENDING_NOTIFICATIONS,
            KEY_SCHEDULED_REMINDER_ALARMS
        ).forEach { key ->
            when (val value = legacyPrefs.all[key]) {
                is String -> editor.putString(key, value)
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
            }
        }
        legacyPrefs.getStringSet(KEY_KNOWN_IDS, null)?.let {
            editor.putStringSet(KEY_KNOWN_IDS, it)
        }
        legacyPrefs.getStringSet(KEY_DONE_IDS, null)?.let {
            editor.putStringSet(KEY_DONE_IDS, it)
        }
        editor.apply()
        clearLegacyConnection(context)
    }

    private fun clearDoneForMissingEvents(context: Context, eventIds: Set<String>) {
        val doneIds = getDoneIds(context)
        val keptIds = doneIds.intersect(eventIds)
        if (keptIds.size != doneIds.size) {
            prefs(context).edit().putStringSet(KEY_DONE_IDS, keptIds).apply()
            cachedDoneIds = keptIds
        }
    }

    private fun eventToJson(event: DeadlineEvent): JSONObject {
        return JSONObject().apply {
            put("id", event.id)
            put("title", event.title)
            put("startAtMillis", event.startAtMillis)
            put("sourceUrl", event.sourceUrl ?: "")
            put("rawType", event.rawType ?: "")
            put("description", event.description ?: "")
            put("source", event.source.name)
        }
    }

    private fun pendingEventFromJson(obj: JSONObject): DeadlineEvent? {
        return eventFromJson(obj, DeadlineSource.MOODLE)
    }

    private fun eventFromJson(obj: JSONObject, defaultSource: DeadlineSource): DeadlineEvent? {
        val id = obj.optString("id").takeIf { it.isNotBlank() } ?: return null
        val title = obj.optString("title").takeIf { it.isNotBlank() } ?: return null
        val startAtMillis = obj.optLong("startAtMillis", 0L)
        if (startAtMillis <= 0L) return null
        return DeadlineEvent(
            id = id,
            title = title,
            startAtMillis = startAtMillis,
            sourceUrl = obj.optString("sourceUrl").takeIf { it.isNotBlank() },
            rawType = obj.optString("rawType").takeIf { it.isNotBlank() },
            description = obj.optString("description").takeIf { it.isNotBlank() },
            source = DeadlineSource.fromStored(obj.optString("source").takeIf { it.isNotBlank() } ?: defaultSource.name)
        )
    }

    private fun clearLegacyConnection(context: Context) {
        legacyPrefs(context).edit()
            .remove(KEY_ICAL_URL)
            .remove(KEY_EVENTS)
            .remove(KEY_KNOWN_IDS)
            .remove(KEY_LAST_SYNC)
            .remove(KEY_DONE_IDS)
            .remove(KEY_CUSTOM_REMINDER_OFFSETS)
            .remove(KEY_PENDING_NOTIFICATIONS)
            .remove(KEY_SCHEDULED_REMINDER_ALARMS)
            .remove(KEY_DELIVERED_NOTIFICATION_KEYS)
            .apply()
    }
}
