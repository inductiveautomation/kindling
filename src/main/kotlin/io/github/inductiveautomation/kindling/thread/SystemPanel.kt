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

class SystemPanel : FilterPanel<Thread?>() {
    override val tabName = "System"

    val systemList = FilterList { it?.toString() ?: "Unassigned" }

    private val sortButtons = systemList.createSortButtons()

    override val component = JPanel(MigLayout("fill, gap 5")).apply {
        val sortGroupEnumeration = sortButtons.elements
        add(sortGroupEnumeration.nextElement(), "split ${sortButtons.buttonCount}, flowx")
        for (element in sortGroupEnumeration) {
            add(element, "gapx 2")
        }
        add(FlatScrollPane(systemList), "newline, push, grow")
    }

    init {
        systemList.selectAll()

        systemList.checkBoxListSelectionModel.addListSelectionListener { e ->
            if (!e.valueIsAdjusting) {
                listeners.getAll<FilterChangeListener>().forEach(FilterChangeListener::filterChanged)
            }
        }
    }

    override fun isFilterApplied(): Boolean = systemList.checkBoxListSelectedValues.size != systemList.model.size - 1

    override fun reset() = systemList.selectAll()

    override fun filter(item: Thread?): Boolean = item?.system in systemList.checkBoxListSelectedValues

    override fun customizePopupMenu(menu: JPopupMenu, column: Column<out Thread?, *>, event: Thread?) = Unit
}
