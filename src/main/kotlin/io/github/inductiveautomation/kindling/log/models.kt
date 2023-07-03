package io.github.inductiveautomation.kindling.log // ktlint-disable filename

import com.jidesoft.comparator.AlphanumComparator
import io.github.inductiveautomation.kindling.core.Kindling.Preferences.General.ShowFullLoggerNames
import io.github.inductiveautomation.kindling.utils.Column
import io.github.inductiveautomation.kindling.utils.ColumnList
import io.github.inductiveautomation.kindling.utils.ReifiedLabelProvider
import org.jdesktop.swingx.renderer.DefaultTableRenderer
import java.time.Instant
import javax.swing.table.AbstractTableModel

class LogsModel<T : LogEvent>(
    val data: List<T>,
    val columns: ColumnList<T>,
) : AbstractTableModel() {
    override fun getColumnName(column: Int): String = columns[column].header
    override fun getRowCount(): Int = data.size
    override fun getColumnCount(): Int = columns.size
    override fun getValueAt(row: Int, column: Int): Any? = get(row, columns[column])
    override fun getColumnClass(column: Int): Class<*> = columns[column].clazz

    operator fun get(row: Int): T = data[row]
    operator fun <R> get(row: Int, column: Column<T, R>): R? {
        return data.getOrNull(row)?.let { event ->
            column.getValue(event)
        }
    }
}

@Suppress("unused", "PropertyName")
class SystemLogsColumns(panel: LogPanel) : ColumnList<SystemLogsEvent>() {
    val Level by column(
        column = {
            minWidth = 55
            maxWidth = 55
        },
        value = { it.level },
    )
    val Timestamp by column(
        column = {
            minWidth = 155
            maxWidth = 155
            cellRenderer = DefaultTableRenderer {
                panel.dateFormatter.format(it as Instant)
            }
        },
        value = SystemLogsEvent::timestamp,
    )
    val Thread by column(
        column = {
            minWidth = 50
        },
        value = { it.thread },
    )
    val Logger by column(
        column = {
            minWidth = 50

            val valueExtractor: (String?) -> String? = {
                if (ShowFullLoggerNames.currentValue) {
                    it
                } else {
                    it?.substringAfterLast('.')
                }
            }

            cellRenderer = DefaultTableRenderer(
                ReifiedLabelProvider(
                    getText = valueExtractor,
                    getTooltip = { it },
                ),
            )
            comparator = compareBy(AlphanumComparator(), valueExtractor)
        },
        value = SystemLogsEvent::logger,
    )

    val Message by column(
        column = {
            isSortable = false
        },
        value = { it.message },
    )
}

@Suppress("unused", "PropertyName")
class WrapperLogColumns(panel: LogPanel) : ColumnList<WrapperLogEvent>() {
    val Level by column(
        column = {
            minWidth = 55
            maxWidth = 55
        },
        value = { it.level },
    )
    val Timestamp by column(
        column = {
            minWidth = 155
            maxWidth = 155
            cellRenderer = DefaultTableRenderer {
                panel.dateFormatter.format(it as Instant)
            }
        },
        value = { it.timestamp },
    )
    val Logger by column(
        column = {
            minWidth = 50

            val valueExtractor: (String?) -> String? = {
                if (ShowFullLoggerNames.currentValue) {
                    it
                } else {
                    it?.substringAfterLast('.')
                }
            }

            cellRenderer = DefaultTableRenderer(
                ReifiedLabelProvider(
                    getText = valueExtractor,
                    getTooltip = { it },
                ),
            )
            comparator = compareBy(AlphanumComparator(), valueExtractor)
        },
        value = { it.logger },
    )
    val Message by column(
        column = {
            isSortable = false
        },
        value = { it.message },
    )
}
