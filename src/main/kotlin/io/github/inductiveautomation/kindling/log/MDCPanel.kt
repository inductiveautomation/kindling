package io.github.inductiveautomation.kindling.log

import com.formdev.flatlaf.extras.FlatSVGIcon
import io.github.inductiveautomation.kindling.core.Kindling.SECONDARY_ACTION_ICON_SCALE
import io.github.inductiveautomation.kindling.utils.Action
import io.github.inductiveautomation.kindling.utils.Column
import io.github.inductiveautomation.kindling.utils.ColumnList
import io.github.inductiveautomation.kindling.utils.FlatScrollPane
import io.github.inductiveautomation.kindling.utils.ReifiedJXTable
import io.github.inductiveautomation.kindling.utils.add
import io.github.inductiveautomation.kindling.utils.attachPopupMenu
import io.github.inductiveautomation.kindling.utils.configureCellRenderer
import io.github.inductiveautomation.kindling.utils.getAll
import net.miginfocom.swing.MigLayout
import org.jdesktop.swingx.renderer.CheckBoxProvider
import org.jdesktop.swingx.renderer.DefaultTableRenderer
import java.util.Vector
import javax.swing.DefaultComboBoxModel
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JMenuItem
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.table.AbstractTableModel


internal class MDCPanel(events: List<SystemLogEvent>) : JPanel(MigLayout("ins 0, fill")), LogFilterPanel {
    private val allMDCs = events.flatMap(SystemLogEvent::mdc)

    private val countByKey = allMDCs
        .groupingBy(MDC::key)
        .eachCount()
        .entries
        .sortedByDescending(Map.Entry<String, Int>::value)
        .associate(Map.Entry<String, Int>::toPair)

    private val countByKeyAndValue = allMDCs
        .groupingBy(MDC::toPair)
        .eachCount()

    private val valuesByKey = allMDCs
        .groupBy(MDC::key)
        .mapValues { it.value.distinct() }

    private val tableModel = MDCTableModel()

    private val valueCombo: JComboBox<String?> = JComboBox<String?>().apply {
        configureCellRenderer { _, value, _, _, _ ->
            text = if (value == null) {
                " - value - "
            } else {
                val count = countByKeyAndValue[keyCombo.selectedItem as String to value] ?: 0
                "$value [$count]"
            }
            toolTipText = text
        }
        selectedIndex = -1
    }

    private val keyCombo: JComboBox<String> = JComboBox(countByKey.keys.toTypedArray()).apply {
        configureCellRenderer { _, key, _, _, _ ->
            text = if (key == null) {
                " - key - "
            } else {
                val count = countByKey[key]
                "$key [$count]"
            }
            toolTipText = text
        }

        addActionListener {
            valueCombo.model = valuesByKey.getValue(selectedItem as String).map { it.value }.let { DefaultComboBoxModel(Vector(it)) }
        }

        selectedIndex = 0
    }

    private val addFilter = Action(
        icon = FlatSVGIcon("icons/bx-plus.svg").derive(SECONDARY_ACTION_ICON_SCALE),
    ) {
        val selectedKey = keyCombo.selectedItem as String?
        val selectedValue = valueCombo.selectedItem as String?

        if (selectedKey != null && selectedValue != null) {
            val exists = filterTable.model.data.any { (mdc) ->
                mdc.key == selectedKey && mdc.value == selectedValue
            }
            if (!exists) {
                filterTable.model += MDC(selectedKey, selectedValue)
            }
        }
    }

    private val removeFilter = Action(
        name = "Remove Filter",
        icon = FlatSVGIcon("icons/bx-minus.svg").derive(SECONDARY_ACTION_ICON_SCALE),
    ) {
        if (filterTable.selectedRow in filterTable.model.data.indices) {
            val previousSelection = filterTable.selectedRow
            filterTable.model.removeAt(filterTable.selectedRow)

            val currentSelection = previousSelection.coerceAtMost(filterTable.model.data.size - 1)
            if (currentSelection >= 0) {
                filterTable.setRowSelectionInterval(currentSelection, currentSelection)
            }
        }
    }

    private val removeAllFilters = Action(
        name = "Remove All Filters",
        icon = FlatSVGIcon("icons/bx-x.svg").derive(SECONDARY_ACTION_ICON_SCALE),
    ) {
        reset()
    }

    private val filterTable = ReifiedJXTable(tableModel).apply filterTable@{
        isColumnControlVisible = false

        model.addTableModelListener {
            listenerList.getAll<FilterChangeListener>().forEach(FilterChangeListener::filterChanged)
        }
    }

    init {
        add(keyCombo, "growx, wrap, wmax 100%")
        add(valueCombo, "growx, wrap, wmax 100%")
        add(
            JButton(removeFilter).apply {
                hideActionText = true
            },
            "align right, split",
        )
        add(JButton(addFilter), "gapx 2")
        add(
            JButton(removeAllFilters).apply {
                hideActionText = true
            },
            "gapx 2",
        )
        add(FlatScrollPane(filterTable), "newline, pushy, grow")

        filterTable.attachPopupMenu { mouseEvent ->
            val rowAtPoint = rowAtPoint(mouseEvent.point)
            if (rowAtPoint != -1) {
                filterTable.addRowSelectionInterval(rowAtPoint, rowAtPoint)
            }
            JPopupMenu().apply {
                add(JMenuItem(removeFilter))
                add(JMenuItem(removeAllFilters))
            }
        }
    }

    override fun isFilterApplied(): Boolean = filterTable.rowCount > 0

    override val component: JComponent = this

    override val tabName: String = "MDC"

    override fun filter(event: LogEvent): Boolean = filterTable.model.filter(event)

    override fun addFilterChangeListener(listener: FilterChangeListener) {
        listenerList.add(listener)
    }

    override fun reset() {
        filterTable.model.clear()
    }
}

data class MDCTableRow(
    val mdc: MDC,
    var inclusive: Boolean = true,
) : LogFilter {

    override fun filter(event: LogEvent): Boolean {
        check(event is SystemLogEvent)
        return if (inclusive) {
            event.mdc.any { (key, value) ->
                val keyMatches = mdc.key == key
                val valueMatches = mdc.value?.equals(value)
                keyMatches && valueMatches == true
            }
        } else {
            event.mdc.none { (key, value) ->
                val keyMatches = mdc.key == key
                val valueMatches = mdc.value?.equals(value)
                val valueEquals = valueMatches == true
                keyMatches && valueEquals
            }
        }
    }
}

class MDCTableModel : AbstractTableModel(), LogFilter {
    val data: MutableList<MDCTableRow> = mutableListOf()

    override fun getRowCount(): Int = data.size

    @Suppress("RedundantCompanionReference")
    override fun getColumnCount(): Int = MDCColumns.size
    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any? = get(rowIndex, MDCColumns[columnIndex])
    override fun getColumnName(column: Int): String = get(column).header
    override fun getColumnClass(columnIndex: Int): Class<*> = get(columnIndex).clazz
    override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean {
        return columnIndex == MDCColumns[Inclusive]
    }

    override fun setValueAt(aValue: Any?, rowIndex: Int, columnIndex: Int) {
        if (aValue !is Boolean) return

        if (MDCColumns[columnIndex] == Inclusive) {
            data[rowIndex].inclusive = aValue
        }

        fireTableCellUpdated(rowIndex, columnIndex)
    }

    operator fun <T> get(row: Int, column: Column<MDCTableRow, T>): T {
        return data[row].let { info ->
            column.getValue(info)
        }
    }

    override fun filter(event: LogEvent): Boolean {
        return when (event) {
            is WrapperLogEvent -> true
            is SystemLogEvent -> data.isEmpty() || data.any { row ->
                row.filter(event)
            }
        }
    }

    fun removeAt(index: Int) {
        data.removeAt(index)
        fireTableRowsDeleted(index, index)
    }

    operator fun plusAssign(mdc: MDC) {
        data += MDCTableRow(mdc)
        fireTableRowsInserted(data.lastIndex, data.lastIndex)
    }

    fun clear() {
        data.clear()
        fireTableDataChanged()
    }

    companion object MDCColumns : ColumnList<MDCTableRow>() {
        val Key by column(
            value = { it.mdc.key },
        )
        val Value by column(
            value = { it.mdc.value },
        )
        val Inclusive by column(
            column = {
                cellRenderer = DefaultTableRenderer(
                    CheckBoxProvider {
                        if (it as Boolean) "Inclusive" else "Exclusive"
                    },
                )
            },
            value = MDCTableRow::inclusive,
        )
    }
}
