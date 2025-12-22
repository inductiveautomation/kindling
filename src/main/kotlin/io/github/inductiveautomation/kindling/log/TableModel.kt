package io.github.inductiveautomation.kindling.log

import com.jidesoft.comparator.AlphanumComparator
import io.github.inductiveautomation.kindling.core.Kindling.Preferences.General.ShowFullLoggerNames
import io.github.inductiveautomation.kindling.core.Timezone
import io.github.inductiveautomation.kindling.utils.Column
import io.github.inductiveautomation.kindling.utils.ColumnList
import io.github.inductiveautomation.kindling.utils.FlatActionIcon
import io.github.inductiveautomation.kindling.utils.ReifiedLabelProvider
import io.github.inductiveautomation.kindling.utils.ReifiedListTableModel
import io.github.inductiveautomation.kindling.utils.StringProvider
import org.jdesktop.swingx.renderer.DefaultTableRenderer
import org.jdesktop.swingx.renderer.StringValues
import java.time.Instant

class LogsModel<T : LogEvent>(
    data: List<T>,
    override val columns: LogColumnList<T>,
) : ReifiedListTableModel<T>(data, columns) {

    override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean = columnIndex == markIndex

    val markIndex = columns[
        when (columns) {
            is SystemLogColumns -> SystemLogColumns.Marked
            is WrapperLogColumns -> WrapperLogColumns.Marked
        },
    ]

    override fun setValueAt(aValue: Any?, rowIndex: Int, columnIndex: Int) {
        require(isCellEditable(rowIndex, columnIndex))
        data[rowIndex].marked = aValue as Boolean
    }

    /**
     * Update marks in the model, efficiently.
     * Return true to set marked, false to clear a mark, or null to bypass the row.
     */
    fun markRows(predicate: (T) -> Boolean?) {
        var firstIndex = -1
        var lastIndex = -1

        for ((rowIndex, event) in data.withIndex()) {
            val shouldMark = predicate(event) ?: continue
            if (firstIndex == -1) {
                firstIndex = rowIndex
            }
            lastIndex = rowIndex
            event.marked = shouldMark
        }
        fireTableRowsUpdated(firstIndex, lastIndex)
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
                FlatActionIcon("icons/bx-star.svg")
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
                (it as? Instant)?.let(Timezone.Default::format)
            }
        },
        getValue = LogEvent::timestamp,
    )

    val Logger = Column<T, String>(
        header = "Logger",
        columnCustomization = {
            minWidth = 50

            val valueExtractor: StringProvider<String> = {
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
        columnCustomization = {
            isSortable = false
        },
        getValue = LogEvent::message,
    )

    init {
        add(Marked)
        add(Level)
        add(Logger)
        add(Message)
        add(Timestamp)
    }
}

data object SystemLogColumns : LogColumnList<SystemLogEvent>() {
    val Thread by column(
        column = {
            minWidth = 50
            isSortable = false
        },
        value = SystemLogEvent::thread,
    )
}

data object WrapperLogColumns : LogColumnList<WrapperLogEvent>()
