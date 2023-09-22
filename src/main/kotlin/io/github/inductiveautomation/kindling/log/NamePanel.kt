package io.github.inductiveautomation.kindling.log

import io.github.inductiveautomation.kindling.core.Kindling.Preferences.General.ShowFullLoggerNames
import io.github.inductiveautomation.kindling.utils.Action
import io.github.inductiveautomation.kindling.utils.Column
import io.github.inductiveautomation.kindling.utils.FilterListPanel
import io.github.inductiveautomation.kindling.utils.FilterModel
import javax.swing.JPopupMenu

internal class NamePanel(events: List<LogEvent>) : FilterListPanel<LogEvent>("Logger", ::getSortKey) {
    init {
        filterList.setModel(FilterModel(events.groupingBy(LogEvent::logger).eachCount(), ::getSortKey))
        filterList.selectAll()

        ShowFullLoggerNames.addChangeListener {
            filterList.model = filterList.model.copy(filterList.comparator)
        }
    }

    override fun filter(item: LogEvent) = item.logger in filterList.checkBoxListSelectedValues

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

    companion object {
        private fun getSortKey(key: Any?): String {
            require(key is String)
            return if (ShowFullLoggerNames.currentValue) {
                key
            } else {
                key.substringAfterLast('.')
            }
        }
    }
}
