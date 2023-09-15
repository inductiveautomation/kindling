package io.github.inductiveautomation.kindling.log

import io.github.inductiveautomation.kindling.core.FilterChangeListener
import io.github.inductiveautomation.kindling.core.FilterPanel
import io.github.inductiveautomation.kindling.core.Kindling.Preferences.General.ShowFullLoggerNames
import io.github.inductiveautomation.kindling.utils.Action
import io.github.inductiveautomation.kindling.utils.Column
import io.github.inductiveautomation.kindling.utils.FilterList
import io.github.inductiveautomation.kindling.utils.FilterModel
import io.github.inductiveautomation.kindling.utils.FlatScrollPane
import io.github.inductiveautomation.kindling.utils.getAll
import net.miginfocom.swing.MigLayout
import javax.swing.ButtonGroup
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.JToggleButton

internal class NamePanel(events: List<LogEvent>) : FilterPanel<LogEvent>() {
    private val countByLogger = events.groupingBy(LogEvent::logger).eachCount()

    private fun getSortKey(key: Any?): String {
        require(key is String)
        return if (ShowFullLoggerNames.currentValue) {
            key
        } else {
            key.substringAfterLast('.')
        }
    }

    private val filterList = FilterList(
        toStringFn = ::getSortKey,
        tooltipToStringFn = Any?::toString,
    ).apply {
        setModel(FilterModel(countByLogger, ::getSortKey))
    }

    override val component = JPanel(MigLayout("ins 0, fill"))

    init {
        ShowFullLoggerNames.addChangeListener {
            filterList.model = filterList.model.copy(filterList.comparator)
        }

        val bg = ButtonGroup()
        for (sortAction in filterList.sortActions) {
            val sortToggle = JToggleButton(sortAction)
            bg.add(sortToggle)
            component.add(sortToggle, "split, gapx 2")
        }

        component.add(FlatScrollPane(filterList), "newline, push, grow")

        filterList.selectAll()
        filterList.checkBoxListSelectionModel.addListSelectionListener { event ->
            if (!event.valueIsAdjusting) {
                listeners.getAll<FilterChangeListener>().forEach(FilterChangeListener::filterChanged)
            }
        }
    }

    override val tabName: String = "Logger"

    override fun isFilterApplied(): Boolean = filterList.checkBoxListSelectedIndices.size < filterList.model.size - 1

    override fun filter(item: LogEvent): Boolean {
        return item.logger in filterList.checkBoxListSelectedValues
    }

    override fun customizePopupMenu(
        menu: JPopupMenu,
        column: Column<out LogEvent, *>,
        event: LogEvent,
    ) {
        if (column == WrapperLogColumns.Logger || column == SystemLogColumns.Logger) {
            val loggerIndex = filterList.model.indexOf(event.logger)
            menu.add(
                Action("Show only ${event.logger} events") {
                    filterList.checkBoxListSelectedIndex = loggerIndex
                    filterList.ensureIndexIsVisible(loggerIndex)
                },
            )
            menu.add(
                Action("Exclude ${event.logger} events") {
                    filterList.removeCheckBoxListSelectedIndex(loggerIndex)
                },
            )
        }
    }

    override fun reset() = filterList.selectAll()
}
