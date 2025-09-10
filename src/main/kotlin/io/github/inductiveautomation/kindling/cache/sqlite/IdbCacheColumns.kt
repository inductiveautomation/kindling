package io.github.inductiveautomation.kindling.cache.sqlite

import io.github.inductiveautomation.kindling.utils.ColumnList
import io.github.inductiveautomation.kindling.utils.toFileSizeLabel
import java.awt.Component
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.swing.JTable
import javax.swing.table.DefaultTableCellRenderer

@Suppress("unused")
object IdbCacheColumns : ColumnList<IdbCacheEntry>() {
    val ID by column { it.id }
    val Data by column(
        column = {
            cellRenderer = object : DefaultTableCellRenderer() {
                override fun getTableCellRendererComponent(
                    table: JTable?,
                    value: Any?,
                    isSelected: Boolean,
                    hasFocus: Boolean,
                    row: Int,
                    column: Int
                ): Component? {
                    super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)
                    text = (value as Long).toFileSizeLabel()
                    return this
                }
            }
        },
        value = IdbCacheEntry::dataSize
    )
    val Timestamp by column(
        column = {
            cellRenderer = object : DefaultTableCellRenderer() {
                override fun getTableCellRendererComponent(
                    table: JTable?,
                    value: Any?,
                    isSelected: Boolean,
                    hasFocus: Boolean,
                    row: Int,
                    column: Int,
                ): Component {
                    super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)

                    val datetime = ZonedDateTime.ofInstant(value as Instant, ZoneId.systemDefault())
                    text = datetime.format(DateTimeFormatter.RFC_1123_DATE_TIME)

                    return this
                }
            }
        },
        value = {
            Instant.ofEpochMilli(it.timestamp)
        }
    )
    val AttemptCount by column("Attempt Count") { it.attemptCount }
    val DataCount by column("Data Count") { it.dataCount }
    val FlavorId by column("Flavor ID") { it.flavorId }
    val FlavorName by column("Flavor Name") { it.flavorName }
    val QuarantineId by column("Quarantine ID") { it.quarantineId }
    val Reason by column { it.reason }
}