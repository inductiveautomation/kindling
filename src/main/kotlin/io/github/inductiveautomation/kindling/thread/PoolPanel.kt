package io.github.inductiveautomation.kindling.thread

import io.github.inductiveautomation.kindling.thread.model.Thread
import io.github.inductiveautomation.kindling.utils.FilterListPanel

class PoolPanel : FilterListPanel<Thread?>(
    tabName = "Pool",
    toStringFn = { it?.toString() ?: "(No Pool)" },
) {
    override fun filter(item: Thread?) = item?.pool in filterList.checkBoxListSelectedValues
}
