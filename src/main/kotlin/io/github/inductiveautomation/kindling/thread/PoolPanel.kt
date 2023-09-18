package io.github.inductiveautomation.kindling.thread

import io.github.inductiveautomation.kindling.thread.model.ThreadLifespan
import io.github.inductiveautomation.kindling.utils.Action
import io.github.inductiveautomation.kindling.utils.Column
import io.github.inductiveautomation.kindling.utils.ListFilterPanel
import javax.swing.JPopupMenu

class PoolPanel : ListFilterPanel<ThreadLifespan>(
    tabName = "Pool",
    toStringFn = { it?.toString() ?: "(No Pool)" },
) {
    override fun filter(item: ThreadLifespan): Boolean = item.any { thread ->
        thread?.pool in filterList.checkBoxListSelectedValues
    }

    override fun customizePopupMenu(menu: JPopupMenu, column: Column<out ThreadLifespan, *>, event: ThreadLifespan) {
        val pool = event.first()?.pool
        val poolIndex = filterList.model.indexOf(pool)
        menu.add(
            Action("Show only $pool events") {
                filterList.checkBoxListSelectedIndex = poolIndex
                filterList.ensureIndexIsVisible(poolIndex)
            },
        )
        menu.add(
            Action("Exclude $pool events") {
                filterList.removeCheckBoxListSelectedIndex(poolIndex)
            },
        )
    }
}
