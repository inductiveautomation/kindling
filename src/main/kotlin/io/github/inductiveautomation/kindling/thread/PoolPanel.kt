package io.github.inductiveautomation.kindling.thread

import io.github.inductiveautomation.kindling.core.LIstFilterPanel
import io.github.inductiveautomation.kindling.thread.model.Thread

class PoolPanel : LIstFilterPanel<Thread?>(
    tabName = "Pool",
    toStringFn = { it?.toString() ?: "(No Pool)" },
) {
    override fun filter(item: Thread?) = item?.pool in filterList.checkBoxListSelectedValues
}
