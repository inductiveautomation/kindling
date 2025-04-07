package io.github.inductiveautomation.kindling.thread

import com.formdev.flatlaf.extras.FlatSVGIcon
import io.github.inductiveautomation.kindling.core.FilterChangeListener
import io.github.inductiveautomation.kindling.core.FilterPanel
import io.github.inductiveautomation.kindling.thread.model.Thread
import io.github.inductiveautomation.kindling.utils.Column
import io.github.inductiveautomation.kindling.utils.FileFilterResponsive
import io.github.inductiveautomation.kindling.utils.FilterList
import io.github.inductiveautomation.kindling.utils.FilterModel
import io.github.inductiveautomation.kindling.utils.FlatScrollPane
import io.github.inductiveautomation.kindling.utils.getAll
import javax.swing.JPopupMenu

class StatePanel : FilterPanel<Thread?>(), FileFilterResponsive<Thread?> {
    override val icon = FlatSVGIcon("icons/bx-check-circle.svg")

    val stateList = FilterList()
    override val tabName = "State"

    override val component = FlatScrollPane(stateList)

    init {
        stateList.selectAll()

        stateList.checkBoxListSelectionModel.addListSelectionListener { e ->
            if (!e.valueIsAdjusting) {
                listeners.getAll<FilterChangeListener>().forEach(FilterChangeListener::filterChanged)
            }
        }
    }

    override fun setModelData(data: List<Thread?>) {
        stateList.model = FilterModel.fromRawData(data.filterNotNull(), stateList.comparator) { it.state.name }
    }

    override fun isFilterApplied(): Boolean = stateList.checkBoxListSelectedValues.size != stateList.model.size - 1

    override fun reset() = stateList.selectAll()

    override fun filter(item: Thread?): Boolean = item?.state?.name in stateList.checkBoxListSelectedValues

    override fun customizePopupMenu(
        menu: JPopupMenu,
        column: Column<out Thread?, *>,
        event: Thread?,
    ) = Unit
}
