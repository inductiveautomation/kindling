package io.github.inductiveautomation.kindling.cache

import java.sql.Timestamp

data class CacheEntry(
    val id: Int,
    val schemaId: Int,
    val schemaName: String,
    val timestamp: Timestamp,
    val attemptCount: Int,
    val dataCount: Int,
)
