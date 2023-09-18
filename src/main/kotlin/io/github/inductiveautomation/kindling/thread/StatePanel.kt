package io.github.inductiveautomation.kindling.thread

import io.github.inductiveautomation.kindling.core.FilterChangeListener
import io.github.inductiveautomation.kindling.core.FilterPanel
import io.github.inductiveautomation.kindling.thread.model.ThreadLifespan
import io.github.inductiveautomation.kindling.utils.Column
import io.github.inductiveautomation.kindling.utils.FilterList
import io.github.inductiveautomation.kindling.utils.FlatScrollPane
import io.github.inductiveautomation.kindling.utils.getAll
import javax.swing.JPopupMenu

class StatePanel : FilterPanel<ThreadLifespan>() {
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

    override fun isFilterApplied(): Boolean = stateList.checkBoxListSelectedValues.size != stateList.model.size - 1

    override fun reset() = stateList.selectAll()

    override fun filter(item: ThreadLifespan): Boolean = item.any { thread ->
        thread?.state?.name in stateList.checkBoxListSelectedValues
    }

    override fun customizePopupMenu(menu: JPopupMenu, column: Column<out ThreadLifespan, *>, event: ThreadLifespan) {
        TODO("Not yet implemented")
    }
}
