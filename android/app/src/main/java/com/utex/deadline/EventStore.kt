package com.utex.deadline

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object EventStore {
    private const val PREFS = "ute_deadline_prefs"
    private const val KEY_ICAL_URL = "ical_url"
    private const val KEY_EVENTS = "events_json"
    private const val KEY_KNOWN_IDS = "known_ids"
    private const val KEY_LAST_SYNC = "last_sync"
    private const val KEY_DAILY_SUMMARY_ENABLED = "daily_summary_enabled"
    private const val KEY_DAILY_SUMMARY_HOUR = "daily_summary_hour"

    fun getIcalUrl(context: Context): String {
        return prefs(context).getString(KEY_ICAL_URL, "").orEmpty()
    }

    fun setIcalUrl(context: Context, url: String) {
        prefs(context).edit().putString(KEY_ICAL_URL, url.trim()).apply()
    }

    fun getLastSync(context: Context): Long {
        return prefs(context).getLong(KEY_LAST_SYNC, 0L)
    }

    fun setLastSync(context: Context, millis: Long) {
        prefs(context).edit().putLong(KEY_LAST_SYNC, millis).apply()
    }

    fun isDailySummaryEnabled(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_DAILY_SUMMARY_ENABLED, true)
    }

    fun setDailySummaryEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_DAILY_SUMMARY_ENABLED, enabled).apply()
    }

    fun getDailySummaryHour(context: Context): Int {
        return prefs(context).getInt(KEY_DAILY_SUMMARY_HOUR, 7)
    }

    fun setDailySummaryHour(context: Context, hour: Int) {
        prefs(context).edit()
            .putBoolean(KEY_DAILY_SUMMARY_ENABLED, true)
            .putInt(KEY_DAILY_SUMMARY_HOUR, hour.coerceIn(0, 23))
            .apply()
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
                    rawType = obj.optString("rawType").takeIf { it.isNotBlank() }
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

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
