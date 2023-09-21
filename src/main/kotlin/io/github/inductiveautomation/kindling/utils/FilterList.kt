package io.github.inductiveautomation.kindling.utils

import com.formdev.flatlaf.extras.FlatSVGIcon
import com.jidesoft.comparator.AlphanumComparator
import com.jidesoft.swing.CheckBoxList
import com.jidesoft.swing.ListSearchable
import io.github.inductiveautomation.kindling.core.Kindling.SECONDARY_ACTION_ICON_SCALE
import io.github.inductiveautomation.kindling.utils.FilterComparator.ByCountDescending
import java.text.DecimalFormat
import javax.swing.AbstractListModel
import javax.swing.ButtonGroup
import javax.swing.JToggleButton
import javax.swing.ListModel

data class FilterModelEntry(
    val key: String?,
    val count: Int,
)

enum class FilterComparator(
    val tooltip: String,
    val icon: FlatSVGIcon,
    val comparator: Comparator<FilterModelEntry>,
) : Comparator<FilterModelEntry> by comparator {
    ByNameAscending(
        tooltip = "Sort A-Z",
        icon = FlatSVGIcon("icons/bx-sort-a-z.svg").derive(SECONDARY_ACTION_ICON_SCALE),
        comparator = compareBy(nullsFirst(AlphanumComparator(false))) { it.key },
    ),
    ByNameDescending(
        tooltip = "Sort Z-A",
        icon = FlatSVGIcon("icons/bx-sort-z-a.svg").derive(SECONDARY_ACTION_ICON_SCALE),
        comparator = ByNameAscending.reversed(),
    ),
    ByCountAscending(
        tooltip = "Sort by Count",
        icon = FlatSVGIcon("icons/bx-sort-up.svg").derive(SECONDARY_ACTION_ICON_SCALE),
        comparator = compareBy(FilterModelEntry::count),
    ),
    ByCountDescending(
        tooltip = "Sort by Count (descending)",
        icon = FlatSVGIcon("icons/bx-sort-down.svg").derive(SECONDARY_ACTION_ICON_SCALE),
        comparator = ByCountAscending.reversed(),
    ),
}

fun FilterModel(data: Map<String?, Int>) = FilterModel(data) { it }

class FilterModel<T>(
    val data: Map<T, Int>,
    val sortKey: (T) -> String?,
) : AbstractListModel<Any>() {
    private val total = data.values.sum()
    val percentages = data.mapValues { (_, count) ->
        val percentage = count.toFloat() / total
        percentFormat.format(percentage)
    }

    internal var values: List<*> = data.keys.toList()

    override fun getSize(): Int = values.size + 1
    override fun getElementAt(index: Int): Any? {
        return if (index == 0) {
            CheckBoxList.ALL_ENTRY
        } else {
            values[index - 1]
        }
    }

    fun indexOf(value: Any?): Int {
        val indexOf: Int = values.indexOf(value)
        return if (indexOf >= 0) {
            indexOf + 1
        } else {
            -1
        }
    }

    fun copy(comparator: FilterComparator): FilterModel<T> = FilterModel(
        data.entries
            .sortedWith(
                compareBy(comparator) { (key, value) ->
                    FilterModelEntry(sortKey(key), value)
                },
            )
            .associate(Map.Entry<T, Int>::toPair),
        sortKey,
    )

    companion object {
        private val percentFormat = DecimalFormat.getPercentInstance()
    }
}

typealias Stringifier = (Any?) -> String?

class FilterList(
    initialComparator: FilterComparator = ByCountDescending,
    private val tooltipToStringFn: Stringifier? = null,
    private val toStringFn: Stringifier = { it?.toString() },
) : CheckBoxList(FilterModel(emptyMap())) {
    private var lastSelection = arrayOf<Any>()

    init {
        selectionModel = NoSelectionModel()
        isClickInCheckBoxOnly = false

        cellRenderer = listCellRenderer<Any?> { _, value, _, _, _ ->
            when (value) {
                ALL_ENTRY -> {
                    text = value.toString()
                }

                else -> {
                    text = "${toStringFn(value)} [${model.data[value]}] (${model.percentages[value]})"
                    toolTipText = tooltipToStringFn?.let { stringifier ->
                        "${stringifier(value)} [${model.data[value]}] (${model.percentages[value]})"
                    } ?: text
                }
            }
        }

        object : ListSearchable(this) {
            init {
                isCaseSensitive = false
                isRepeats = true
                isCountMatch = true
            }

            override fun convertElementToString(element: Any?): String = element.toString()

            override fun setSelectedIndex(index: Int, incremental: Boolean) {
                checkBoxListSelectedIndex = index
            }
        }
    }

    fun select(value: Any?) {
        val rowToSelect = model.indexOf(value)
        checkBoxListSelectionModel.setSelectionInterval(rowToSelect, rowToSelect)
    }

    var comparator: FilterComparator = initialComparator
        set(newComparator) {
            model = model.copy(newComparator)
            field = newComparator
        }

    @Suppress("UNCHECKED_CAST")
    override fun getModel(): FilterModel<in Any?> = super.getModel() as FilterModel<in Any?>

    override fun setModel(model: ListModel<*>) {
        require(model is FilterModel<*>)
        val currentSelection = checkBoxListSelectedValues
        lastSelection = if (currentSelection.isEmpty()) {
            lastSelection
        } else {
            currentSelection
        }

        super.setModel(model)

        for (sortAction in sortActions) {
            sortAction.selected = comparator == sortAction.comparator
        }
        addCheckBoxListSelectedValues(lastSelection)
    }

    private val sortActions: List<SortAction> = FilterComparator.entries.map { filterComparator ->
        SortAction(filterComparator)
    }

    inner class SortAction(comparator: FilterComparator) : Action(
        description = comparator.tooltip,
        icon = comparator.icon,
        selected = this@FilterList.comparator == comparator,
        action = {
            this@FilterList.comparator = comparator
            selected = true
        },
    ) {
        var comparator: FilterComparator by actionValue("filterComparator", comparator)
    }

    fun createSortButtons(): ButtonGroup = ButtonGroup().apply {
        for (sortAction in sortActions) {
            add(
                JToggleButton(
                    Action(
                        description = sortAction.description,
                        icon = sortAction.icon,
                        selected = sortAction.selected,
                    ) { e ->
                        sortAction.actionPerformed(e)
                    },
                ),
            )
        }
    }
}
