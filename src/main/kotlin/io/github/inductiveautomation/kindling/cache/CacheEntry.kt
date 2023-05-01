package io.github.inductiveautomation.kindling.cache

data class CacheEntry(
    val id: Int,
    val schemaId: Int,
    val schemaName: String,
    val timestamp: String,
    val attemptCount: Int,
    val dataCount: Int,
)
