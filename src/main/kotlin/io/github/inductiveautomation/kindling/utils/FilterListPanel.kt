package io.github.inductiveautomation.kindling.utils

import io.github.inductiveautomation.kindling.core.FilterChangeListener
import io.github.inductiveautomation.kindling.core.FilterPanel
import javax.swing.JPopupMenu

abstract class FilterListPanel<T>(
    override val tabName: String,
    toStringFn: Stringifier = { it?.toString() },
) : FilterPanel<T>() {
    val filterList = FilterList(toStringFn = toStringFn)

    private val sortButtons = filterList.createSortButtons()

    override val component = ButtonPanel(sortButtons).apply {
        add(FlatScrollPane(filterList), "newline, push, grow, align right")
    }

    init {
        filterList.checkBoxListSelectionModel.addListSelectionListener { e ->
            if (!e.valueIsAdjusting && !filterList.comparatorIsAdjusting) {
                listeners.getAll<FilterChangeListener>().forEach(FilterChangeListener::filterChanged)
            }
        }
    }

    override fun isFilterApplied() = filterList.checkBoxListSelectedValues.size != filterList.model.size - 1

    override fun reset() = filterList.selectAll()

    override fun customizePopupMenu(
        menu: JPopupMenu,
        column: Column<out T, *>,
        event: T,
    ) = Unit
}
