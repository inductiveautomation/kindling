package io.github.inductiveautomation.kindling.thread

import com.formdev.flatlaf.extras.FlatSVGIcon
import io.github.inductiveautomation.kindling.thread.model.Thread
import io.github.inductiveautomation.kindling.utils.FilterListPanel

class PoolPanel : FilterListPanel<Thread?>(
    tabName = "Pool",
    toStringFn = { it?.toString() ?: "(No Pool)" },
) {
    override val icon = FlatSVGIcon("icons/bx-chip.svg")

    override fun filter(item: Thread?) = item?.pool in filterList.checkBoxListSelectedValues
}
