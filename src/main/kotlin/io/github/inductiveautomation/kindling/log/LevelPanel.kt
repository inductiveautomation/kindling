package io.github.inductiveautomation.kindling.log

import com.formdev.flatlaf.extras.FlatSVGIcon
import io.github.inductiveautomation.kindling.utils.Action
import io.github.inductiveautomation.kindling.utils.Column
import io.github.inductiveautomation.kindling.utils.FileFilterResponsive
import io.github.inductiveautomation.kindling.utils.FilterListPanel
import io.github.inductiveautomation.kindling.utils.FilterModel
import javax.swing.JPopupMenu

internal class LevelPanel<T : LogEvent>(
    rawData: List<T>,
) : FilterListPanel<T>("Levels"), FileFilterResponsive<T> {
    override val icon = FlatSVGIcon("icons/bx-bar-chart-alt.svg")

    override fun setModelData(data: List<T>) {
        filterList.setModel(FilterModel.fromRawData(data, filterList.comparator) { it.level?.name })
    }

    init {
        setModelData(rawData)
        filterList.selectAll()
    }

    override fun filter(item: T): Boolean = item.level?.name in filterList.checkBoxListSelectedValues

    override fun customizePopupMenu(
        menu: JPopupMenu,
        column: Column<out T, *>,
        event: T,
    ) {
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
