package io.github.inductiveautomation.kindling.log

import com.formdev.flatlaf.extras.FlatSVGIcon
import com.jidesoft.swing.CheckBoxList
import io.github.inductiveautomation.kindling.core.Kindling.General.ShowFullLoggerNames
import io.github.inductiveautomation.kindling.utils.Action
import io.github.inductiveautomation.kindling.utils.FlatScrollPane
import io.github.inductiveautomation.kindling.utils.NoSelectionModel
import io.github.inductiveautomation.kindling.utils.installSearchable
import io.github.inductiveautomation.kindling.utils.listCellRenderer
import net.miginfocom.swing.MigLayout
import javax.swing.AbstractListModel
import javax.swing.ButtonGroup
import javax.swing.JPanel
import javax.swing.JToggleButton
import javax.swing.ListModel

data class LoggerName(
    val name: String,
    val eventCount: Int,
)

class LoggerNamesModel(val data: List<LoggerName>) : AbstractListModel<Any>() {
    override fun getSize(): Int = data.size + 1
    override fun getElementAt(index: Int): Any {
        return if (index == 0) {
            CheckBoxList.ALL_ENTRY
        } else {
            data[index - 1]
        }
    }
}

class LoggerNamesList(model: LoggerNamesModel) : CheckBoxList(model) {
    var isShowFullLoggerName = ShowFullLoggerNames.currentValue
        set(value) {
            field = value
            repaint()
        }

    private fun displayValue(value: Any?): String {
        return when (value) {
            is LoggerName -> if (!isShowFullLoggerName) {
                value.name.substringAfterLast('.')
            } else {
                value.name
            }

            else -> value.toString()
        }
    }

    init {
        installSearchable(
            setup = {
                isCaseSensitive = false
                isRepeats = true
                isCountMatch = true
            },
            conversion = ::displayValue,
        )
        selectionModel = NoSelectionModel()
        cellRenderer = listCellRenderer<Any> { _, value, _, _, _ ->
            when (value) {
                is LoggerName -> {
                    text = "${displayValue(value)} - [${value.eventCount}]"
                    toolTipText = value.name
                }

                else -> {
                    text = value.toString()
                }
            }
        }

        selectAll()
    }

    override fun getModel(): LoggerNamesModel = super.getModel() as LoggerNamesModel

    override fun setModel(model: ListModel<*>) {
        require(model is LoggerNamesModel)
        val selection = checkBoxListSelectedValues
        checkBoxListSelectionModel.valueIsAdjusting = true
        super.setModel(model)
        addCheckBoxListSelectedValues(selection)
        checkBoxListSelectionModel.valueIsAdjusting = false
    }
}

class LoggerNamesPanel(events: List<LogEvent>) : JPanel(MigLayout("ins 0, fill")) {
    val list: LoggerNamesList = run {
        val loggerNames: List<LoggerName> = events.groupingBy { it.logger }
            .eachCount()
            .entries
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.key })
            .map { (key, value) -> LoggerName(key, value) }
        LoggerNamesList(LoggerNamesModel(loggerNames))
    }

    init {
        val sortButtons = ButtonGroup()

        fun sortButton(icon: FlatSVGIcon, tooltip: String, comparator: Comparator<LoggerName>): JToggleButton {
            return JToggleButton(
                Action(
                    description = tooltip,
                    icon = icon,
                ) {
                    list.model = LoggerNamesModel(list.model.data.sortedWith(comparator))
                },
            )
        }

        val naturalAsc = sortButton(
            icon = NATURAL_SORT_ASCENDING,
            tooltip = "Sort A-Z",
            comparator = byName,
        )
        listOf(
            naturalAsc,
            sortButton(
                icon = NATURAL_SORT_DESCENDING,
                tooltip = "Sort Z-A",
                comparator = byName.reversed(),
            ),
            sortButton(
                icon = NUMERIC_SORT_DESCENDING,
                tooltip = "Sort by Count",
                comparator = byCount.reversed() then byName,
            ),
            sortButton(
                icon = NUMERIC_SORT_ASCENDING,
                tooltip = "Sort by Count (ascending)",
                comparator = byCount then byName,
            ),
        ).forEach { sortButton ->
            sortButtons.add(sortButton)
            add(sortButton, "cell 0 0")
        }

        sortButtons.setSelected(naturalAsc.model, true)

        add(FlatScrollPane(list), "newline, push, grow")
    }

    companion object {
        private val byName = compareBy(String.CASE_INSENSITIVE_ORDER, LoggerName::name)
        private val byCount = compareBy(LoggerName::eventCount)

        private val NATURAL_SORT_ASCENDING = FlatSVGIcon("icons/bx-sort-a-z.svg")
        private val NATURAL_SORT_DESCENDING = FlatSVGIcon("icons/bx-sort-z-a.svg")
        private val NUMERIC_SORT_ASCENDING = FlatSVGIcon("icons/bx-sort-up.svg")
        private val NUMERIC_SORT_DESCENDING = FlatSVGIcon("icons/bx-sort-down.svg")
    }
}
