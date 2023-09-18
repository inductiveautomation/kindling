package io.github.inductiveautomation.kindling.core

import io.github.inductiveautomation.kindling.utils.Column
import io.github.inductiveautomation.kindling.utils.FilterList
import io.github.inductiveautomation.kindling.utils.FlatScrollPane
import io.github.inductiveautomation.kindling.utils.Stringifier
import io.github.inductiveautomation.kindling.utils.getAll
import net.miginfocom.swing.MigLayout
import javax.swing.JPanel
import javax.swing.JPopupMenu

abstract class ListFilterPanel<T>(
    override val tabName: String,
    toStringFn: Stringifier = { it?.toString() },
) : FilterPanel<T>() {
    val filterList = FilterList(toStringFn = toStringFn)

    private val sortButtons = filterList.createSortButtons()

    override val component = JPanel(MigLayout("fill, gap 5")).apply {
        val sortGroupEnumeration = sortButtons.elements
        add(sortGroupEnumeration.nextElement(), "split ${sortButtons.buttonCount}, flowx")
        for (element in sortGroupEnumeration) {
            add(element, "gapx 2")
        }
        add(FlatScrollPane(filterList), "newline, push, grow")
    }

    init {
        filterList.checkBoxListSelectionModel.addListSelectionListener { e ->
            if (!e.valueIsAdjusting) {
                listeners.getAll<FilterChangeListener>().forEach(FilterChangeListener::filterChanged)
            }
        }
    }

    override fun isFilterApplied() = filterList.checkBoxListSelectedValues.size != filterList.model.size - 1

    override fun reset() = filterList.selectAll()

    override fun customizePopupMenu(menu: JPopupMenu, column: Column<out T, *>, event: T) = Unit
}
