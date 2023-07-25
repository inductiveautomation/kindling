package io.github.inductiveautomation.kindling.log

import io.github.inductiveautomation.kindling.core.Kindling.Preferences.General.ShowFullLoggerNames
import io.github.inductiveautomation.kindling.utils.Action
import io.github.inductiveautomation.kindling.utils.Column
import io.github.inductiveautomation.kindling.utils.FilterList
import io.github.inductiveautomation.kindling.utils.FilterModel
import io.github.inductiveautomation.kindling.utils.FlatScrollPane
import io.github.inductiveautomation.kindling.utils.add
import io.github.inductiveautomation.kindling.utils.getAll
import net.miginfocom.swing.MigLayout
import javax.swing.ButtonGroup
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.JToggleButton

internal class NamePanel(events: List<LogEvent>) : JPanel(MigLayout("ins 0, fill")), LogFilterPanel {
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
        tooltipToStringFn = Any?::toString
    ).apply {
        setModel(FilterModel(countByLogger, ::getSortKey))
    }

    init {
        ShowFullLoggerNames.addChangeListener {
            filterList.model = filterList.model.copy(filterList.comparator)
        }

        val bg = ButtonGroup()
        for (sortAction in filterList.sortActions) {
            val sortToggle = JToggleButton(sortAction)
            bg.add(sortToggle)
            add(sortToggle, "split, gapx 2")
        }

        add(FlatScrollPane(filterList), "newline, push, grow")

        filterList.selectAll()
        filterList.checkBoxListSelectionModel.addListSelectionListener { event ->
            if (!event.valueIsAdjusting) {
                listenerList.getAll<FilterChangeListener>().forEach(FilterChangeListener::filterChanged)
            }
        }
    }

    override val component: JComponent = this
    override val tabName: String = "Logger"

    override fun isFilterApplied(): Boolean = filterList.checkBoxListSelectedIndices.size < filterList.model.size - 1

    override fun addFilterChangeListener(listener: FilterChangeListener) {
        listenerList.add(listener)
    }

    override fun filter(event: LogEvent): Boolean {
        return event.logger in filterList.checkBoxListSelectedValues
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
