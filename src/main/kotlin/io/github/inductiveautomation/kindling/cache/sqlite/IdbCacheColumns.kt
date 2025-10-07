package io.github.inductiveautomation.kindling.cache.sqlite

import io.github.inductiveautomation.kindling.utils.ColumnList
import io.github.inductiveautomation.kindling.utils.toFileSizeLabel
import org.jdesktop.swingx.renderer.DefaultTableRenderer
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

@Suppress("unused")
object IdbCacheColumns : ColumnList<IdbCacheEntry>() {
    val ID by column { it.id }
    val Data by column(
        column = {
            cellRenderer = DefaultTableRenderer {
                (it as Long).toFileSizeLabel()
            }
        },
        value = IdbCacheEntry::dataSize,
    )
    val Timestamp by column(
        column = {
            cellRenderer = DefaultTableRenderer {
                val datetime = ZonedDateTime.ofInstant(it as Instant, ZoneId.systemDefault())
                datetime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z"))
            }
        },
        value = {
            Instant.ofEpochMilli(it.timestamp)
        },
    )
    val AttemptCount by column("Attempt Count") { it.attemptCount }
    val DataCount by column("Data Count") { it.dataCount }
    val FlavorId by column("Flavor ID") { it.flavorId }
    val FlavorName by column("Flavor Name") { it.flavorName }
    val QuarantineId by column("Quarantine ID") { it.quarantineId }
    val Reason by column { it.reason }
}
