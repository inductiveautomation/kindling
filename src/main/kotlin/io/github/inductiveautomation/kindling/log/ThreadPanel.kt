package io.github.inductiveautomation.kindling.log

import com.formdev.flatlaf.extras.FlatSVGIcon
import io.github.inductiveautomation.kindling.utils.Action
import io.github.inductiveautomation.kindling.utils.Column
import io.github.inductiveautomation.kindling.utils.FileFilterResponsive
import io.github.inductiveautomation.kindling.utils.FilterListPanel
import io.github.inductiveautomation.kindling.utils.FilterModel
import javax.swing.JPopupMenu

internal class ThreadPanel(
    events: List<SystemLogEvent>,
) : FilterListPanel<SystemLogEvent>("Threads"), FileFilterResponsive<SystemLogEvent> {
    override val icon = FlatSVGIcon("icons/bx-chip.svg")

    init {
        filterList.apply {
            setModel(FilterModel.fromRawData(events, filterList.comparator) { it.thread })
            selectAll()
        }
    }

    override fun setModelData(data: List<SystemLogEvent>) {
        filterList.model = FilterModel.fromRawData(data, filterList.comparator) { it.thread }
    }

    override fun filter(item: SystemLogEvent) = item.thread in filterList.checkBoxListSelectedValues

    override fun customizePopupMenu(
        menu: JPopupMenu,
        column: Column<out SystemLogEvent, *>,
        event: SystemLogEvent,
    ) {
        if (column == SystemLogColumns.Thread) {
            val threadIndex = filterList.model.indexOf(event.thread)
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
