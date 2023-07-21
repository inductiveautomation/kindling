package io.github.inductiveautomation.kindling.log

import com.formdev.flatlaf.extras.FlatSVGIcon
import com.jidesoft.swing.CheckBoxList
import io.github.inductiveautomation.kindling.core.Kindling.Preferences.General.ShowFullLoggerNames
import io.github.inductiveautomation.kindling.core.Kindling.SECONDARY_ACTION_ICON_SCALE
import io.github.inductiveautomation.kindling.utils.Action
import io.github.inductiveautomation.kindling.utils.Column
import io.github.inductiveautomation.kindling.utils.FlatScrollPane
import io.github.inductiveautomation.kindling.utils.NoSelectionModel
import io.github.inductiveautomation.kindling.utils.add
import io.github.inductiveautomation.kindling.utils.getAll
import io.github.inductiveautomation.kindling.utils.installSearchable
import io.github.inductiveautomation.kindling.utils.listCellRenderer
import net.miginfocom.swing.MigLayout
import javax.swing.AbstractListModel
import javax.swing.ButtonGroup
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JPopupMenu
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

    fun indexOf(value: String): Int {
        val indexOf = data.indexOfFirst { it.name == value }
        return if (indexOf >= 0) {
            indexOf + 1
        } else {
            -1
        }
    }
}

class LoggerNamesList(model: LoggerNamesModel) : CheckBoxList(model) {
    private fun displayValue(value: Any?): String = when (value) {
        is LoggerName -> if (ShowFullLoggerNames.currentValue) {
            value.name
        } else {
            value.name.substringAfterLast('.')
        }

        else -> value.toString()
    }

    private var cachedLoggerNames: Set<String> = emptySet()
    private var lastCacheKey: Int = 0
    val loggerNames: Set<String>
        get() {
            val currentCacheKey = checkBoxListSelectionModel.selectedIndices.contentHashCode()
            if (currentCacheKey != lastCacheKey) {
                val checkedBoxes = checkBoxListSelectedIndices
                cachedLoggerNames = buildSet {
                    for (selectedIndex in checkedBoxes) {
                        add(model.data[selectedIndex - 1].name)
                    }
                }
                lastCacheKey = currentCacheKey
            }
            return cachedLoggerNames
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
        isClickInCheckBoxOnly = false
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

internal class NamePanel(events: List<LogEvent>) : JPanel(MigLayout("ins 0, fill")), LogFilterPanel {
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
            ).apply {
                isFocusable = false
            }
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
            add(sortButton, "split, gapx 2")
        }

        sortButtons.setSelected(naturalAsc.model, true)

        add(FlatScrollPane(list), "newline, push, grow")

        list.checkBoxListSelectionModel.addListSelectionListener { event ->
            if (!event.valueIsAdjusting) {
                listenerList.getAll<FilterChangeListener>().forEach(FilterChangeListener::filterChanged)
            }
        }
    }

    override val component: JComponent = this
    override val tabName: String = "Logger"

    override fun isFilterApplied(): Boolean = list.checkBoxListSelectedIndices.size < list.model.size - 1

    override fun addFilterChangeListener(listener: FilterChangeListener) {
        listenerList.add(listener)
    }

    override fun filter(event: LogEvent): Boolean {
        return event.logger in list.loggerNames
    }

    override fun customizePopupMenu(
        menu: JPopupMenu,
        column: Column<out LogEvent, *>,
        event: LogEvent,
    ) {
        if (column == WrapperLogColumns.Logger || column == SystemLogColumns.Logger) {
            val loggerIndex = list.model.indexOf(event.logger)
            menu.add(
                Action("Show only ${event.logger} events") {
                    list.checkBoxListSelectedIndex = loggerIndex
                    list.ensureIndexIsVisible(loggerIndex)
                },
            )
            menu.add(
                Action("Exclude ${event.logger} events") {
                    list.removeCheckBoxListSelectedIndex(loggerIndex)
                },
            )
        }
    }

    override fun reset() = list.selectAll()

    companion object {
        private val byName = compareBy(String.CASE_INSENSITIVE_ORDER, LoggerName::name)
        private val byCount = compareBy(LoggerName::eventCount)

        private val NATURAL_SORT_ASCENDING = FlatSVGIcon("icons/bx-sort-a-z.svg").derive(SECONDARY_ACTION_ICON_SCALE)
        private val NATURAL_SORT_DESCENDING = FlatSVGIcon("icons/bx-sort-z-a.svg").derive(SECONDARY_ACTION_ICON_SCALE)
        private val NUMERIC_SORT_ASCENDING = FlatSVGIcon("icons/bx-sort-up.svg").derive(SECONDARY_ACTION_ICON_SCALE)
        private val NUMERIC_SORT_DESCENDING = FlatSVGIcon("icons/bx-sort-down.svg").derive(SECONDARY_ACTION_ICON_SCALE)
    }
}
