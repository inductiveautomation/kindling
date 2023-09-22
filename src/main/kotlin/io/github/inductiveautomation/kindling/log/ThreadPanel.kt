package io.github.inductiveautomation.kindling.log

import io.github.inductiveautomation.kindling.utils.Action
import io.github.inductiveautomation.kindling.utils.Column
import io.github.inductiveautomation.kindling.utils.FilterListPanel
import io.github.inductiveautomation.kindling.utils.FilterModel
import javax.swing.JPopupMenu

internal class ThreadPanel(events: List<LogEvent>) : FilterListPanel<LogEvent>("Thread") {
    init {
        filterList.apply {
            setModel(FilterModel(events.groupingBy { (it as SystemLogEvent).thread }.eachCount()))
            selectAll()
        }
    }

    override fun filter(item: LogEvent) = (item as SystemLogEvent).thread in filterList.checkBoxListSelectedValues

    override fun customizePopupMenu(
        menu: JPopupMenu,
        column: Column<out LogEvent, *>,
        event: LogEvent,
    ) {
        if (column == SystemLogColumns.Thread) {
            val threadIndex = filterList.model.indexOf((event as SystemLogEvent).thread)
            menu.add(
                Action("Show only ${event.thread} events") {
                    filterList.checkBoxListSelectedIndex = threadIndex
                    filterList.ensureIndexIsVisible(threadIndex)
                },
            )
            menu.add(
                Action("Exclude ${event.thread} events") {
                    filterList.removeCheckBoxListSelectedIndex(threadIndex)
                },
            )
        }
    }
}
