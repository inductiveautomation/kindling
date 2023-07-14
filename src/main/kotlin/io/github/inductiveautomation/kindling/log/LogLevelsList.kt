package io.github.inductiveautomation.kindling.log

import io.github.inductiveautomation.kindling.utils.FilterList
import io.github.inductiveautomation.kindling.utils.FilterModel

class LogLevelsList(rawData: List<LogEvent>) : FilterList(""), LogFilterPanel {

    override val isFilterApplied: Boolean
        get() = checkBoxListSelectedValues.size != model.size - 1

    init {
        model = FilterModel(rawData.groupingBy { it.level?.name }.eachCount())
        selectAll()
    }
}
