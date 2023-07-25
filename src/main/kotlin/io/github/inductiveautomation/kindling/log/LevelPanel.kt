package io.github.inductiveautomation.kindling.log

import io.github.inductiveautomation.kindling.utils.Action
import io.github.inductiveautomation.kindling.utils.Column
import io.github.inductiveautomation.kindling.utils.FilterList
import io.github.inductiveautomation.kindling.utils.FilterModel
import io.github.inductiveautomation.kindling.utils.FlatScrollPane
import io.github.inductiveautomation.kindling.utils.add
import io.github.inductiveautomation.kindling.utils.getAll
import javax.swing.JComponent
import javax.swing.JPopupMenu
import javax.swing.event.EventListenerList

internal class LevelPanel(rawData: List<LogEvent>) : LogFilterPanel {
    private val filterList: FilterList = FilterList()
    override val component: JComponent = FlatScrollPane(filterList)

    private val listenerList = EventListenerList()

    init {
        filterList.setModel(FilterModel(rawData.groupingBy { it.level?.name }.eachCount()))
        filterList.selectAll()

        filterList.checkBoxListSelectionModel.addListSelectionListener { e ->
            if (!e.valueIsAdjusting) {
                listenerList.getAll<FilterChangeListener>().forEach(FilterChangeListener::filterChanged)
            }
        }
    }

    override val tabName: String = "Level"
    override fun isFilterApplied() = filterList.checkBoxListSelectedValues.size != filterList.model.size - 1
    override fun filter(event: LogEvent): Boolean = event.level?.name in filterList.checkBoxListSelectedValues
    override fun addFilterChangeListener(listener: FilterChangeListener) {
        listenerList.add(listener)
    }

    override fun customizePopupMenu(menu: JPopupMenu, column: Column<out LogEvent, *>, event: LogEvent) {
        val level = event.level
        if ((column == WrapperLogColumns.Level || column == SystemLogColumns.Level) && level != null) {
            val levelIndex = filterList.model.indexOf(level.name)
            menu.add(
                Action("Show only $level events") {
                    filterList.checkBoxListSelectedIndex = levelIndex
                    filterList.ensureIndexIsVisible(levelIndex)
                },
            )
            menu.add(
                Action("Exclude $level events") {
                    filterList.removeCheckBoxListSelectedIndex(levelIndex)
                },
            )
        }
    }

    override fun reset() = filterList.selectAll()
}
