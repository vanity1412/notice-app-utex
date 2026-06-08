package com.utex.deadline

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject

object EventStore {
    private const val PREFS = "ute_deadline_secure_prefs"
    private const val LEGACY_PREFS = "ute_deadline_prefs"
    private const val KEY_ICAL_URL = "ical_url"
    private const val KEY_EVENTS = "events_json"
    private const val KEY_KNOWN_IDS = "known_ids"
    private const val KEY_LAST_SYNC = "last_sync"
    private const val KEY_DAILY_SUMMARY_TOUCHED = "daily_summary_touched"
    private const val KEY_DAILY_SUMMARY_ENABLED = "daily_summary_enabled"
    private const val KEY_DAILY_SUMMARY_HOUR = "daily_summary_hour"
    private const val KEY_DAILY_SUMMARY_MINUTE = "daily_summary_minute"
    private const val KEY_DAILY_SUMMARY_DAYS = "daily_summary_days"
    private const val KEY_DONE_IDS = "done_ids"
    private const val KEY_REMINDER_OFFSETS = "reminder_offsets"
    private const val KEY_PENDING_NOTIFICATIONS = "pending_notifications_json"
    const val ALL_DAYS_MASK = 0b1111111
    private val DEFAULT_REMINDER_MINUTES = listOf(24L * 60L, 12L * 60L, 60L)
    private val ALLOWED_REMINDER_MINUTES = listOf(2L * 24L * 60L, 24L * 60L, 12L * 60L, 3L * 60L, 60L, 30L)
    @Volatile
    private var cachedPrefs: SharedPreferences? = null
    @Volatile
    private var cachedEvents: List<DeadlineEvent>? = null
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
            .remove(KEY_DAILY_SUMMARY_TOUCHED)
            .remove(KEY_DONE_IDS)
            .remove(KEY_PENDING_NOTIFICATIONS)
            .putBoolean(KEY_DAILY_SUMMARY_ENABLED, false)
            .apply()
        cachedEvents = emptyList()
        cachedDoneIds = emptySet()
        clearLegacyConnection(context)
    }

    fun getLastSync(context: Context): Long {
        return prefs(context).getLong(KEY_LAST_SYNC, 0L)
    }

    fun setLastSync(context: Context, millis: Long) {
        prefs(context).edit().putLong(KEY_LAST_SYNC, millis).apply()
    }

    fun isDailySummaryEnabled(context: Context): Boolean {
        val preferences = prefs(context)
        return preferences.getString(KEY_ICAL_URL, "").orEmpty().isNotBlank() &&
            preferences.getBoolean(KEY_DAILY_SUMMARY_ENABLED, false)
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

    fun loadEvents(context: Context): List<DeadlineEvent> {
        cachedEvents?.let { return it }
        val json = prefs(context).getString(KEY_EVENTS, "[]").orEmpty()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).mapNotNull { i ->
                val obj = arr.optJSONObject(i) ?: return@mapNotNull null
                DeadlineEvent(
                    id = obj.optString("id"),
                    title = obj.optString("title"),
                    startAtMillis = obj.optLong("startAtMillis"),
                    sourceUrl = obj.optString("sourceUrl").takeIf { it.isNotBlank() },
                    rawType = obj.optString("rawType").takeIf { it.isNotBlank() },
                    description = obj.optString("description").takeIf { it.isNotBlank() }
                )
            }.sortedBy { it.startAtMillis }.also {
                cachedEvents = it
            }
        } catch (_: Exception) {
            emptyList<DeadlineEvent>().also {
                cachedEvents = it
            }
        }
    }

    fun saveEvents(context: Context, events: List<DeadlineEvent>) {
        val arr = JSONArray()
        val sortedEvents = events.sortedBy { it.startAtMillis }
        sortedEvents.forEach { event ->
            arr.put(JSONObject().apply {
                put("id", event.id)
                put("title", event.title)
                put("startAtMillis", event.startAtMillis)
                put("sourceUrl", event.sourceUrl ?: "")
                put("rawType", event.rawType ?: "")
                put("description", event.description ?: "")
            })
        }
        prefs(context).edit().putString(KEY_EVENTS, arr.toString()).apply()
        cachedEvents = sortedEvents
        clearDoneForMissingEvents(context, sortedEvents.map { it.id }.toSet())
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
                val type = obj.optString("type").takeIf { it == "new" || it == "changed" } ?: return@mapNotNull null
                val event = obj.optJSONObject("event")?.let { pendingEventFromJson(it) } ?: return@mapNotNull null
                val key = obj.optString("key").takeIf { it.isNotBlank() } ?: "$type-${event.id}"
                PendingDeadlineNotification(key, type, event)
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
            })
        }
        prefs(context).edit().putString(KEY_PENDING_NOTIFICATIONS, arr.toString()).apply()
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

    fun getReminderOffsetOptions(): List<Long> = ALLOWED_REMINDER_MINUTES

    fun getReminderOffsetsMinutes(context: Context): List<Long> {
        val saved = prefs(context).getString(KEY_REMINDER_OFFSETS, null)
        val parsed = saved
            ?.split(',')
            ?.mapNotNull { it.trim().toLongOrNull() }
            ?.filter { it in ALLOWED_REMINDER_MINUTES }
            ?.distinct()
            .orEmpty()
        val selected = parsed.ifEmpty { DEFAULT_REMINDER_MINUTES }
        return ALLOWED_REMINDER_MINUTES.filter { it in selected }
    }

    fun isReminderOffsetEnabled(context: Context, minutes: Long): Boolean {
        return minutes in getReminderOffsetsMinutes(context)
    }

    fun setReminderOffsetEnabled(context: Context, minutes: Long, enabled: Boolean) {
        if (minutes !in ALLOWED_REMINDER_MINUTES) return
        val selected = getReminderOffsetsMinutes(context).toMutableSet()
        if (enabled) {
            selected += minutes
        } else {
            selected -= minutes
        }
        val safeSelected = selected.ifEmpty { setOf(60L) }
        val saved = ALLOWED_REMINDER_MINUTES
            .filter { it in safeSelected }
            .joinToString(",")
        prefs(context).edit().putString(KEY_REMINDER_OFFSETS, saved).apply()
    }

    fun reminderOptionLabel(minutes: Long): String {
        return when (minutes) {
            2L * 24L * 60L -> "2 ngày"
            24L * 60L -> "1 ngày"
            12L * 60L -> "12 giờ"
            3L * 60L -> "3 giờ"
            60L -> "1 giờ"
            30L -> "30 phút"
            else -> "$minutes phút"
        }
    }

    fun reminderLeadLabel(minutes: Long): String {
        return "${reminderOptionLabel(minutes)} trước hạn"
    }

    fun reminderOffsetsText(context: Context): String {
        return getReminderOffsetsMinutes(context)
            .joinToString(", ") { reminderOptionLabel(it) }
            .ifBlank { "1 giờ" }
    }

    private fun prefs(context: Context): SharedPreferences {
        cachedPrefs?.let { return it }
        return synchronized(this) {
            cachedPrefs ?: run {
                val appContext = context.applicationContext
                val securePrefs = securePrefs(appContext)
                val preferences = if (securePrefs != null) {
                    migrateLegacyPrefs(appContext, securePrefs)
                    securePrefs
                } else {
                    legacyPrefs(appContext)
                }
                cachedPrefs = preferences
                preferences
            }
        }
    }

    private fun securePrefs(context: Context): SharedPreferences? {
        return try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                PREFS,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (_: Exception) {
            null
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
            KEY_LAST_SYNC,
            KEY_DAILY_SUMMARY_ENABLED,
            KEY_DAILY_SUMMARY_TOUCHED,
            KEY_DAILY_SUMMARY_HOUR,
            KEY_DAILY_SUMMARY_MINUTE,
            KEY_DAILY_SUMMARY_DAYS,
            KEY_REMINDER_OFFSETS,
            KEY_PENDING_NOTIFICATIONS
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
        }
    }

    private fun pendingEventFromJson(obj: JSONObject): DeadlineEvent? {
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
            description = obj.optString("description").takeIf { it.isNotBlank() }
        )
    }

    private fun clearLegacyConnection(context: Context) {
        legacyPrefs(context).edit()
            .remove(KEY_ICAL_URL)
            .remove(KEY_EVENTS)
            .remove(KEY_KNOWN_IDS)
            .remove(KEY_LAST_SYNC)
            .remove(KEY_DONE_IDS)
            .remove(KEY_PENDING_NOTIFICATIONS)
            .apply()
    }
}
