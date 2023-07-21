package io.github.inductiveautomation.kindling.log

import com.formdev.flatlaf.extras.FlatSVGIcon
import com.jidesoft.comparator.AlphanumComparator
import io.github.inductiveautomation.kindling.core.Kindling.Preferences.General.ShowFullLoggerNames
import io.github.inductiveautomation.kindling.log.LogViewer.TimeStampFormatter
import io.github.inductiveautomation.kindling.utils.Column
import io.github.inductiveautomation.kindling.utils.ColumnList
import io.github.inductiveautomation.kindling.utils.ReifiedLabelProvider
import org.jdesktop.swingx.renderer.DefaultTableRenderer
import org.jdesktop.swingx.renderer.StringValues
import java.time.Instant
import javax.swing.table.AbstractTableModel

class LogsModel<T : LogEvent>(
    val data: List<T>,
    val columns: LogColumnList<T>,
) : AbstractTableModel() {
    override fun getColumnName(column: Int): String = columns[column].header
    override fun getRowCount(): Int = data.size
    override fun getColumnCount(): Int = columns.size
    override fun getValueAt(row: Int, column: Int): Any? = get(row, columns[column])
    override fun getColumnClass(column: Int): Class<*> = columns[column].clazz

    override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean {
        val markIndex = columns[
            when (columns) {
                is SystemLogColumns -> SystemLogColumns.Marked
                is WrapperLogColumns -> WrapperLogColumns.Marked
            },
        ]
        return columnIndex == markIndex
    }

    operator fun get(row: Int): T = data[row]
    operator fun <R> get(row: Int, column: Column<T, R>): R? {
        return data.getOrNull(row)?.let { event ->
            column.getValue(event)
        }
    }

    override fun setValueAt(aValue: Any?, rowIndex: Int, columnIndex: Int) {
        require(isCellEditable(rowIndex, columnIndex))
        data[rowIndex].marked = aValue as Boolean
    }
}

@Suppress("PropertyName")
sealed class LogColumnList<T : LogEvent> : ColumnList<T>() {
    val Marked = Column<T, Boolean>(
        header = "Marked",
        columnCustomization = {
            minWidth = 25
            maxWidth = 25
            toolTipText = "Marked Logs"
            headerRenderer = DefaultTableRenderer(StringValues.EMPTY) {
                FlatSVGIcon("icons/bx-search.svg").derive(0.8F)
            }
        },
        getValue = LogEvent::marked,
    )

    val Level = Column<T, Level?>(
        header = "Level",
        columnCustomization = {
            minWidth = 55
            maxWidth = 55
        },
        getValue = LogEvent::level,
    )

    val Timestamp = Column<T, Instant>(
        header = "Timestamp",
        columnCustomization = {
            minWidth = 155
            maxWidth = 155
            cellRenderer = DefaultTableRenderer {
                (it as? Instant)?.let(TimeStampFormatter::format)
            }
        },
        getValue = LogEvent::timestamp,
    )

    val Logger = Column<T, String>(
        header = "Logger",
        columnCustomization = {
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
        getValue = LogEvent::logger,
    )

    val Message = Column<T, String>(
        header = "Message",
        getValue = LogEvent::message,
    )

    init {
        add(Marked)
        add(Level)
        add(Logger)
        add(Message)
        add(Timestamp)
    }

    abstract val filterableColumns: List<Column<T, out Any?>>
}

data object SystemLogColumns : LogColumnList<SystemLogEvent>() {
    val Thread = Column(
        header = "Thread",
        columnCustomization = {
            minWidth = 50
        },
        getValue = SystemLogEvent::thread,
    )

    init {
        add(Thread)
    }

    override val filterableColumns = listOf(
        Level,
        Thread,
        Logger,
        Message,
    )
}

data object WrapperLogColumns : LogColumnList<WrapperLogEvent>() {
    override val filterableColumns = listOf(
        Level,
        Logger,
        Message,
    )
}
