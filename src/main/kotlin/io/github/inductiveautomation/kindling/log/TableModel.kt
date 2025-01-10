package io.github.inductiveautomation.kindling.log

import com.formdev.flatlaf.extras.FlatSVGIcon
import com.jidesoft.comparator.AlphanumComparator
import io.github.inductiveautomation.kindling.core.Kindling.Preferences.General.ShowFullLoggerNames
import io.github.inductiveautomation.kindling.log.LogViewer.TimeStampFormatter
import io.github.inductiveautomation.kindling.utils.Column
import io.github.inductiveautomation.kindling.utils.ColumnList
import io.github.inductiveautomation.kindling.utils.ReifiedLabelProvider
import io.github.inductiveautomation.kindling.utils.ReifiedListTableModel
import io.github.inductiveautomation.kindling.utils.StringProvider
import io.github.inductiveautomation.kindling.utils.asActionIcon
import org.jdesktop.swingx.renderer.DefaultTableRenderer
import org.jdesktop.swingx.renderer.StringValues
import java.time.Instant

class LogsModel(
    data: List<LogEvent>,
    columns: LogColumnList,
) : ReifiedListTableModel<LogEvent>(data, columns) {
    /**
     * Update marks in the model, efficiently.
     * Return true to set marked, false to clear a mark, or null to bypass the row.
     */
    fun markRows(predicate: (LogEvent) -> Boolean?) {
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
sealed class LogColumnList : ColumnList<LogEvent>() {
    val Marked = Column<LogEvent, Boolean>(
        header = "Marked",
        columnCustomization = {
            minWidth = 25
            maxWidth = 25
            toolTipText = "Marked Logs"
            headerRenderer = DefaultTableRenderer(StringValues.EMPTY) {
                FlatSVGIcon("icons/bx-star.svg").asActionIcon()
            }
        },
        setValue = { newValue ->
            marked = newValue ?: false
        },
        getValue = LogEvent::marked,
    )

    val Level = Column<LogEvent, Level?>(
        header = "Level",
        columnCustomization = {
            minWidth = 55
            maxWidth = 55
        },
        getValue = LogEvent::level,
    )

    val Timestamp = Column<LogEvent, Instant>(
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

    val Logger = Column<LogEvent, String>(
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

    val Message = Column<LogEvent, String>(
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

data object SystemLogColumns : LogColumnList() {
    val Thread by column(
        column = {
            minWidth = 50
            isSortable = false
        },
        value = { (it as SystemLogEvent).thread },
    )
}

data object WrapperLogColumns : LogColumnList()
