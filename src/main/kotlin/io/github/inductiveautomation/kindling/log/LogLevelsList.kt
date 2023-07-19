package io.github.inductiveautomation.kindling.log

import io.github.inductiveautomation.kindling.utils.FilterList
import io.github.inductiveautomation.kindling.utils.FilterModel
import io.github.inductiveautomation.kindling.utils.add
import io.github.inductiveautomation.kindling.utils.getAll
import javax.swing.JComponent

class LogLevelsList(rawData: List<LogEvent>) : FilterList(""), LogFilterPanel {
    override val isFilterApplied: Boolean
        get() = (checkBoxListSelectedValues.size != model.size - 1)

    init {
        model = FilterModel(rawData.groupingBy { it.level?.name }.eachCount())
        selectAll()

        checkBoxListSelectionModel.addListSelectionListener { e ->
            if (!e.valueIsAdjusting) {
                listenerList.getAll<FilterChangeListener>().forEach(FilterChangeListener::filterChanged)
            }
        }
    }

    override val component: JComponent = this

    override val tabName: String = "Level"

    override fun filter(event: LogEvent): Boolean = event.level?.name in checkBoxListSelectedValues

    override fun addFilterChangeListener(listener: FilterChangeListener) {
        listenerList.add(listener)
    }
}
