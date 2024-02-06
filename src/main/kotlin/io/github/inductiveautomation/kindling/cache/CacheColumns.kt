package io.github.inductiveautomation.kindling.cache

import io.github.inductiveautomation.kindling.utils.ColumnList
import org.jdesktop.swingx.renderer.DefaultTableRenderer

@Suppress("unused")
object CacheColumns : ColumnList<CacheEntry>() {
    val Id by column(
        column = {
            cellRenderer = DefaultTableRenderer(Any?::toString)
        },
        value = CacheEntry::id,
    )
    val SchemaId by column { it.schemaId }
    val Timestamp by column { it.timestamp }
    val AttemptCount by column(name = "Attempt Count") { it.attemptCount }
    val DataCount by column(name = "Data Count") { it.dataCount }
    val SchemaName by column(
        column = {
            isVisible = false
        },
        value = CacheEntry::schemaName,
    )
}
