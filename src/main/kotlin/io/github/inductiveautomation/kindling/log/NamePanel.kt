package io.github.inductiveautomation.kindling.log

import com.formdev.flatlaf.extras.FlatSVGIcon
import io.github.inductiveautomation.kindling.core.Kindling.Preferences.General.ShowFullLoggerNames
import io.github.inductiveautomation.kindling.utils.Action
import io.github.inductiveautomation.kindling.utils.Column
import io.github.inductiveautomation.kindling.utils.FilterListPanel
import io.github.inductiveautomation.kindling.utils.FilterModel
import io.github.inductiveautomation.kindling.utils.PopupMenuCustomizer
import java.awt.event.ItemEvent
import javax.swing.JCheckBoxMenuItem
import javax.swing.JPopupMenu

internal class NamePanel(
    events: List<LogEvent>,
) : FilterListPanel<LogEvent>("Loggers", ::getSortKey), PopupMenuCustomizer {
    override val icon = FlatSVGIcon("icons/bx-detail.svg")

    init {
        filterList.setModel(FilterModel(events.groupingBy(LogEvent::logger).eachCount(), ::getSortKey))
        filterList.selectAll()

        ShowFullLoggerNames.addChangeListener {
            filterList.model = filterList.model.copy(filterList.comparator)
        }
    }

    override fun filter(item: LogEvent) = item.logger in filterList.checkBoxListSelectedValues

    // customize the popup menu on the primary log panel table
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

    // customize the popup menu above our own entry in the tab strip
    override fun customizePopupMenu(menu: JPopupMenu) {
        menu.add(
            JCheckBoxMenuItem("Show full logger names", ShowFullLoggerNames.currentValue).apply {
                addItemListener { e ->
                    ShowFullLoggerNames.currentValue = e.stateChange == ItemEvent.SELECTED
                }
            },
        )
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
