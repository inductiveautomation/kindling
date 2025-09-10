package io.github.inductiveautomation.kindling.cache.sqlite

data class IdbCacheEntry(
    val id: Int,
    val dataSize: Long,
    val timestamp: Long,
    val attemptCount: Int,
    val dataCount: Int,
    val flavorId: Int,
    val flavorName: String,
    val quarantineId: Int?,
    val reason: String?,
    val quarantineFlavorId: Int?,
    val getData: () -> ByteArray,
) {
    val data by lazy {
        println("Getting data")
        getData()
    }
}