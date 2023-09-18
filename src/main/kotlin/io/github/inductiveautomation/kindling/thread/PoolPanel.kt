package io.github.inductiveautomation.kindling.thread

import io.github.inductiveautomation.kindling.core.ListFilterPanel
import io.github.inductiveautomation.kindling.thread.model.Thread

class PoolPanel : ListFilterPanel<Thread?>(
    tabName = "Pool",
    toStringFn = { it?.toString() ?: "(No Pool)" },
) {
    override fun filter(item: Thread?) = item?.pool in filterList.checkBoxListSelectedValues
}
