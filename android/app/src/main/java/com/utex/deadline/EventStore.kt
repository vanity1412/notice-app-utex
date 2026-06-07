package com.utex.deadline

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject

object EventStore {
    private const val PREFS = "ute_deadline_prefs"
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
    const val ALL_DAYS_MASK = 0b1111111

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
            .putBoolean(KEY_DAILY_SUMMARY_ENABLED, false)
            .apply()
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

    fun enableDailySummaryAfterSetup(context: Context) {
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

    fun loadEvents(context: Context): List<DeadlineEvent> {
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
            }.sortedBy { it.startAtMillis }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveEvents(context: Context, events: List<DeadlineEvent>) {
        val arr = JSONArray()
        events.sortedBy { it.startAtMillis }.forEach { event ->
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

    private fun prefs(context: Context): SharedPreferences {
        val securePrefs = securePrefs(context) ?: return legacyPrefs(context)
        migrateLegacyPrefs(context, securePrefs)
        return securePrefs
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
            KEY_DAILY_SUMMARY_DAYS
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
        editor.apply()
        clearLegacyConnection(context)
    }

    private fun clearLegacyConnection(context: Context) {
        legacyPrefs(context).edit()
            .remove(KEY_ICAL_URL)
            .remove(KEY_EVENTS)
            .remove(KEY_KNOWN_IDS)
            .remove(KEY_LAST_SYNC)
            .apply()
    }
}
