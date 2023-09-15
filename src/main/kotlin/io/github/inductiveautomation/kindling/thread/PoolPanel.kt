package io.github.inductiveautomation.kindling.thread

import io.github.inductiveautomation.kindling.core.FilterChangeListener
import io.github.inductiveautomation.kindling.core.FilterPanel
import io.github.inductiveautomation.kindling.thread.model.Thread
import io.github.inductiveautomation.kindling.utils.Column
import io.github.inductiveautomation.kindling.utils.FilterList
import io.github.inductiveautomation.kindling.utils.FlatScrollPane
import io.github.inductiveautomation.kindling.utils.getAll
import net.miginfocom.swing.MigLayout
import javax.swing.JPanel
import javax.swing.JPopupMenu

class PoolPanel : FilterPanel<Thread?>() {
    override val tabName = "Pool"

    val poolList = FilterList { it?.toString() ?: "(No Pool)" }

    private val sortButtons = poolList.createSortButtons()

    override val component = JPanel(MigLayout("fill, gap 5")).apply {
        val sortGroupEnumeration = sortButtons.elements
        add(sortGroupEnumeration.nextElement(), "split ${sortButtons.buttonCount}, flowx")
        for (element in sortGroupEnumeration) {
            add(element, "gapx 2")
        }
        add(FlatScrollPane(poolList), "newline, push, grow")
    }

    init {
        poolList.checkBoxListSelectionModel.addListSelectionListener { e ->
            if (!e.valueIsAdjusting) {
                listeners.getAll<FilterChangeListener>().forEach(FilterChangeListener::filterChanged)
            }
        }
    }

    override fun isFilterApplied(): Boolean = poolList.checkBoxListSelectedValues.size != poolList.model.size - 1

    override fun reset() = poolList.selectAll()

    override fun filter(item: Thread?): Boolean = item?.pool in poolList.checkBoxListSelectedValues

    override fun customizePopupMenu(menu: JPopupMenu, column: Column<out Thread?, *>, event: Thread?) = Unit
}
