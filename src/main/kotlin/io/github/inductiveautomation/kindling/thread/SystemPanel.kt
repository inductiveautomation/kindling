package io.github.inductiveautomation.kindling.thread

import com.formdev.flatlaf.extras.FlatSVGIcon
import io.github.inductiveautomation.kindling.thread.model.Thread
import io.github.inductiveautomation.kindling.utils.FileFilterResponsive
import io.github.inductiveautomation.kindling.utils.FilterListPanel
import io.github.inductiveautomation.kindling.utils.FilterModel

class SystemPanel :
    FilterListPanel<Thread?>(
        tabName = "System",
        toStringFn = { it?.toString() ?: "Unassigned" },
    ),
    FileFilterResponsive<Thread?> {
    override val icon = FlatSVGIcon("icons/bx-hdd.svg")

    override fun setModelData(data: List<Thread?>) {
        filterList.model = FilterModel.fromRawData(data.filterNotNull(), filterList.comparator) { it.system }
    }

    override fun filter(item: Thread?): Boolean = item?.system in filterList.checkBoxListSelectedValues
}
