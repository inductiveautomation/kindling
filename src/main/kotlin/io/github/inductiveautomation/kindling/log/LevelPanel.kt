package io.github.inductiveautomation.kindling.log

import io.github.inductiveautomation.kindling.utils.Action
import io.github.inductiveautomation.kindling.utils.Column
import io.github.inductiveautomation.kindling.utils.FilterListPanel
import io.github.inductiveautomation.kindling.utils.FilterModel
import javax.swing.JPopupMenu

internal class LevelPanel(rawData: List<LogEvent>) : FilterListPanel<LogEvent>("Level") {
    init {
        filterList.setModel(FilterModel(rawData.groupingBy { it.level?.name }.eachCount()))
        filterList.selectAll()
    }

    override fun filter(item: LogEvent): Boolean = item.level?.name in filterList.checkBoxListSelectedValues

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
}
