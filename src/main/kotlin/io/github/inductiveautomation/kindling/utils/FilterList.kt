package io.github.inductiveautomation.kindling.utils

import com.jidesoft.swing.CheckBoxList
import com.jidesoft.swing.ListSearchable
import java.text.DecimalFormat
import javax.swing.AbstractListModel
import javax.swing.ListModel

typealias FilterComparator = Comparator<Map.Entry<String?, Int>>

class FilterModel(val rawData: Map<String?, Int>) : AbstractListModel<Any>() {
    var comparator: FilterComparator = byCountDesc
        set(value) {
            values = rawData.entries.sortedWith(value).map { it.key }
            fireContentsChanged(this, 0, size)
            field = value
        }

    private var values = rawData.entries.sortedWith(comparator).map { it.key }

    override fun getSize(): Int = values.size + 1
    override fun getElementAt(index: Int): Any? {
        return if (index == 0) {
            CheckBoxList.ALL_ENTRY
        } else {
            values[index - 1]
        }
    }

    fun indexOf(value: String): Int {
        val indexOf = values.indexOf(value)
        return if (indexOf >= 0) {
            indexOf + 1
        } else {
            -1
        }
    }

    companion object {
        val byNameAsc: FilterComparator = compareBy(nullsFirst(String.CASE_INSENSITIVE_ORDER)) { it.key }
        val byNameDesc: FilterComparator = byNameAsc.reversed()
        val byCountAsc: FilterComparator = compareBy { it.value }
        val byCountDesc: FilterComparator = byCountAsc.reversed()
    }
}

open class FilterList(private val emptyLabel: String) : CheckBoxList(FilterModel(emptyMap())) {
    protected var total = 0
    protected var percentages = emptyMap<String?, String>()

    protected var lastSelection = arrayOf<Any>()

    init {
        selectionModel = NoSelectionModel()
        isClickInCheckBoxOnly = false

        cellRenderer = listCellRenderer<Any?> { _, value, _, _, _ ->
            text = when (value) {
                is String -> "$value - ${model.rawData[value]} (${percentages.getValue(value)})"
                null -> "$emptyLabel - ${model.rawData[null]} (${percentages.getValue(null)})"
                else -> value.toString()
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

    fun updateComparator(comparator: FilterComparator) {
        checkBoxListSelectionModel.valueIsAdjusting = true
        val currentSelection = checkBoxListSelectedValues
        lastSelection = if (currentSelection.isEmpty()) {
            lastSelection
        } else {
            currentSelection
        }
        model.comparator = comparator
        selectNone()
        addCheckBoxListSelectedValues(lastSelection)
        checkBoxListSelectionModel.valueIsAdjusting = false
    }

    fun isOnlySelected(value: String): Boolean = checkBoxListSelectedValues.size == 1 && value == checkBoxListSelectedValues.first()

    fun select(value: String) {
        val rowToSelect = model.indexOf(value)
        checkBoxListSelectionModel.setSelectionInterval(rowToSelect, rowToSelect)
    }

    override fun getModel(): FilterModel = super.getModel() as FilterModel

    override fun setModel(model: ListModel<*>) {
        require(model is FilterModel)
        val currentSelection = checkBoxListSelectedValues
        lastSelection = if (currentSelection.isEmpty()) {
            lastSelection
        } else {
            currentSelection
        }

        try {
            checkBoxListSelectionModel.valueIsAdjusting = true

            super.setModel(model)

            total = model.rawData.values.sum()
            percentages = model.rawData.mapValues { (_, count) ->
                val percentage = count.toFloat() / total
                DecimalFormat.getPercentInstance().format(percentage)
            }
            addCheckBoxListSelectedValues(lastSelection)
        } finally {
            checkBoxListSelectionModel.valueIsAdjusting = false
        }
    }
}
