package io.github.inductiveautomation.kindling.thread

import io.github.inductiveautomation.kindling.core.LIstFilterPanel
import io.github.inductiveautomation.kindling.thread.model.Thread

class SystemPanel : LIstFilterPanel<Thread?>(
    tabName = "System",
    toStringFn = { it?.toString() ?: "Unassigned" },
) {
    override fun filter(item: Thread?): Boolean = item?.system in filterList.checkBoxListSelectedValues
}
