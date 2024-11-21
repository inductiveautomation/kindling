package io.github.inductiveautomation.kindling.thread

import com.formdev.flatlaf.extras.FlatSVGIcon
import io.github.inductiveautomation.kindling.thread.model.Thread
import io.github.inductiveautomation.kindling.utils.FileFilterResponsive
import io.github.inductiveautomation.kindling.utils.FilterListPanel
import io.github.inductiveautomation.kindling.utils.FilterModel

class PoolPanel :
    FilterListPanel<Thread?>(
        tabName = "Pool",
        toStringFn = { it?.toString() ?: "(No Pool)" },
    ),
    FileFilterResponsive<Thread?> {
    override val icon = FlatSVGIcon("icons/bx-chip.svg")

    override fun setModelData(data: List<Thread?>) {
        filterList.model = FilterModel.fromRawData(data.filterNotNull(), filterList.comparator) { it.pool }
    }

    override fun filter(item: Thread?) = item?.pool in filterList.checkBoxListSelectedValues
}
