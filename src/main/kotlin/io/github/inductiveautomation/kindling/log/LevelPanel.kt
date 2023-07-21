package io.github.inductiveautomation.kindling.log

import io.github.inductiveautomation.kindling.utils.Action
import io.github.inductiveautomation.kindling.utils.Column
import io.github.inductiveautomation.kindling.utils.FilterList
import io.github.inductiveautomation.kindling.utils.FilterModel
import io.github.inductiveautomation.kindling.utils.add
import io.github.inductiveautomation.kindling.utils.getAll
import javax.swing.JPopupMenu
import javax.swing.event.EventListenerList

internal class LevelPanel(rawData: List<LogEvent>) : LogFilterPanel {
    override val component: FilterList = FilterList("")

    private val listenerList = EventListenerList()

    init {
        component.model = FilterModel(rawData.groupingBy { it.level?.name }.eachCount())
        component.selectAll()

        component.checkBoxListSelectionModel.addListSelectionListener { e ->
            if (!e.valueIsAdjusting) {
                listenerList.getAll<FilterChangeListener>().forEach(FilterChangeListener::filterChanged)
            }
        }
    }

    override val tabName: String = "Level"
    override fun isFilterApplied() = component.checkBoxListSelectedValues.size != component.model.size - 1
    override fun filter(event: LogEvent): Boolean = event.level?.name in component.checkBoxListSelectedValues
    override fun addFilterChangeListener(listener: FilterChangeListener) {
        listenerList.add(listener)
    }

    override fun customizePopupMenu(menu: JPopupMenu, column: Column<out LogEvent, *>, event: LogEvent) {
        val level = event.level
        if ((column == WrapperLogColumns.Level || column == SystemLogColumns.Level) && level != null) {
            val levelIndex = component.model.indexOf(level.name)
            menu.add(
                Action("Show only $level events") {
                    component.checkBoxListSelectedIndex = levelIndex
                    component.ensureIndexIsVisible(levelIndex)
                },
            )
            menu.add(
                Action("Exclude $level events") {
                    component.removeCheckBoxListSelectedIndex(levelIndex)
                },
            )
        }
    }

    override fun reset() = component.selectAll()
}
