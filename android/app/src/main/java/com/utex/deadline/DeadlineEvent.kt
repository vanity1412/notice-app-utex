package com.utex.deadline

data class DeadlineEvent(
    val id: String,
    val title: String,
    val startAtMillis: Long,
    val sourceUrl: String? = null,
    val rawType: String? = null,
    val description: String? = null
)

data class SyncResult(
    val ok: Boolean,
    val message: String,
    val totalEvents: Int = 0,
    val newEvents: Int = 0
)
