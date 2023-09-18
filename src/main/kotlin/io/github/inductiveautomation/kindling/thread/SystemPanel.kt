package io.github.inductiveautomation.kindling.thread

import io.github.inductiveautomation.kindling.thread.model.ThreadLifespan
import io.github.inductiveautomation.kindling.utils.ListFilterPanel

class SystemPanel : ListFilterPanel<ThreadLifespan>(
    tabName = "System",
    toStringFn = { it?.toString() ?: "Unassigned" },
) {
    override fun filter(item: ThreadLifespan): Boolean = item.any { thread ->
        thread?.system in filterList.checkBoxListSelectedValues
    }
}
