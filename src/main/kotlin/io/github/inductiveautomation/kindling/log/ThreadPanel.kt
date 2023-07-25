package io.github.inductiveautomation.kindling.log

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

internal class ThreadPanel(events: List<LogEvent>) : JPanel(MigLayout("ins 0, fill")), LogFilterPanel {
    private val filterList = FilterList().apply {
        setModel(FilterModel(events.groupingBy { (it as SystemLogEvent).thread }.eachCount()))
    }

    init {
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
    override val tabName: String = "Thread"

    override fun isFilterApplied(): Boolean = filterList.checkBoxListSelectedIndices.size < filterList.model.size - 1

    override fun addFilterChangeListener(listener: FilterChangeListener) {
        listenerList.add(listener)
    }

    override fun filter(event: LogEvent): Boolean {
        return (event as SystemLogEvent).thread in filterList.checkBoxListSelectedValues
    }

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

    override fun reset() = filterList.selectAll()
}
