package io.github.inductiveautomation.kindling.thread

import com.formdev.flatlaf.extras.FlatSVGIcon
import io.github.inductiveautomation.kindling.thread.model.Thread
import io.github.inductiveautomation.kindling.utils.FilterListPanel

class SystemPanel : FilterListPanel<Thread?>(
    tabName = "System",
    toStringFn = { it?.toString() ?: "Unassigned" },
) {
    override val icon = FlatSVGIcon("icons/bx-hdd.svg")

    override fun filter(item: Thread?): Boolean = item?.system in filterList.checkBoxListSelectedValues
}
