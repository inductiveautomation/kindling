package io.github.inductiveautomation.kindling.log

import com.formdev.flatlaf.extras.FlatSVGIcon
import io.github.inductiveautomation.kindling.core.FilterChangeListener
import io.github.inductiveautomation.kindling.core.FilterPanel
import io.github.inductiveautomation.kindling.core.Kindling.SECONDARY_ACTION_ICON_SCALE
import io.github.inductiveautomation.kindling.log.MDCTableModel.MDCColumns
import io.github.inductiveautomation.kindling.utils.Action
import io.github.inductiveautomation.kindling.utils.Column
import io.github.inductiveautomation.kindling.utils.ColumnList
import io.github.inductiveautomation.kindling.utils.FlatScrollPane
import io.github.inductiveautomation.kindling.utils.ReifiedJXTable
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
import javax.swing.JMenu
import javax.swing.JMenuItem
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.table.AbstractTableModel

internal class MDCPanel(events: List<SystemLogEvent>) : FilterPanel<LogEvent>() {
    override val icon = FlatSVGIcon("icons/bx-key.svg")

    private val allMDCs = events.flatMap(SystemLogEvent::mdc)

    private val countByKey =
        allMDCs
            .groupingBy(MDC::key)
            .eachCount()
            .entries
            .sortedByDescending(Map.Entry<String, Int>::value)
            .associate(Map.Entry<String, Int>::toPair)

    private val countByKeyAndValue =
        allMDCs
            .groupingBy(MDC::toPair)
            .eachCount()

    private val mdcValuesPerKey =
        allMDCs
            .groupBy(MDC::key)
            .mapValues { it.value.distinct() }

    private val tableModel = MDCTableModel()

    private val valueCombo: JComboBox<String?> =
        JComboBox<String?>().apply {
            configureCellRenderer { _, value, _, _, _ ->
                text =
                    if (value == null) {
                        " - value - "
                    } else {
                        val count = countByKeyAndValue[keyCombo.selectedItem as String to value] ?: 0
                        "$value [$count]"
                    }
                toolTipText = text
            }
            selectedIndex = -1
        }

    private val keyCombo: JComboBox<String> =
        JComboBox(countByKey.keys.toTypedArray()).apply {
            configureCellRenderer { _, key, _, _, _ ->
                text =
                    if (key == null) {
                        " - key - "
                    } else {
                        val count = countByKey[key]
                        "$key [$count]"
                    }
                toolTipText = text
            }

            addActionListener {
                if (selectedItem == null) return@addActionListener
                valueCombo.model =
                    mdcValuesPerKey.getValue(selectedItem as String).map { mdc ->
                        mdc.value
                    }.let {
                        DefaultComboBoxModel(Vector(it))
                    }
            }

            selectedIndex = -1
        }

    private val addFilter =
        Action(
            icon = FlatSVGIcon("icons/bx-plus.svg").derive(SECONDARY_ACTION_ICON_SCALE),
        ) {
            val selectedKey = keyCombo.selectedItem as String?
            val selectedValue = valueCombo.selectedItem as String?

            if (selectedKey != null && selectedValue != null) {
                filterTable.model.add(selectedKey, selectedValue)
            }
        }

    private val removeFilter =
        Action(
            name = "Remove Filter",
            icon = FlatSVGIcon("icons/bx-minus.svg").derive(SECONDARY_ACTION_ICON_SCALE),
        ) {
            val rowToRemove = filterTable.selectedRow.takeIf { it != -1 } ?: tableModel.data.lastIndex
            if (rowToRemove in tableModel.data.indices) {
                tableModel.removeAt(filterTable.selectedRow)

                val newSelection = rowToRemove.coerceAtMost(tableModel.data.lastIndex)
                if (newSelection >= 0) {
                    filterTable.setRowSelectionInterval(newSelection, newSelection)
                }
            }
        }

    private val removeAllFilters =
        Action(
            name = "Remove All Filters",
            icon = FlatSVGIcon("icons/bx-x.svg").derive(SECONDARY_ACTION_ICON_SCALE),
        ) {
            reset()
        }

    private val filterTable =
        ReifiedJXTable(tableModel, MDCColumns).apply {
            isColumnControlVisible = false
            tableHeader.apply {
                reorderingAllowed = false
            }

            tableModel.addTableModelListener {
                listeners.getAll<FilterChangeListener>().forEach(FilterChangeListener::filterChanged)
            }
        }

    override val component = JPanel(MigLayout("ins 2 0, fill"))

    init {
        component.apply {
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
        }

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

    override fun isFilterApplied(): Boolean = tableModel.data.isNotEmpty()

    override val tabName: String = "MDC"

    override fun filter(item: LogEvent): Boolean = tableModel.filter(item)

    override fun customizePopupMenu(
        menu: JPopupMenu,
        column: Column<out LogEvent, *>,
        event: LogEvent,
    ) {
        if (column == SystemLogColumns.Message && (event as SystemLogEvent).mdc.isNotEmpty()) {
            for ((key, values) in event.mdc.groupBy { it.key }) {
                menu.add(
                    JMenu("MDC: '$key'").apply {
                        for (mdc in values) {
                            add(
                                Action("Include '${mdc.value}'") {
                                    tableModel.add(key, mdc.value, true)
                                },
                            )
                            add(
                                Action("Exclude '${mdc.value}'") {
                                    tableModel.add(key, mdc.value, false)
                                },
                            )
                        }
                    },
                )
            }
        }
    }

    override fun reset() {
        tableModel.clear()
    }
}

data class MDCTableRow(
    val key: String,
    val value: String?,
    var inclusive: Boolean = true,
) : LogFilter {
    override fun filter(item: LogEvent): Boolean {
        check(item is SystemLogEvent)
        val any =
            item.mdc.any { (key, value) ->
                this.key == key && this.value?.equals(value) == true
            }
        return if (inclusive) any else !any
    }
}

class MDCTableModel : AbstractTableModel(), LogFilter {
    private val _data: MutableList<MDCTableRow> = mutableListOf()
    val data: List<MDCTableRow> = _data

    override fun getRowCount(): Int = _data.size

    @Suppress("RedundantCompanionReference")
    override fun getColumnCount(): Int = MDCColumns.size

    override fun getValueAt(
        rowIndex: Int,
        columnIndex: Int,
    ): Any? = get(rowIndex, MDCColumns[columnIndex])

    override fun getColumnName(column: Int): String = get(column).header

    override fun getColumnClass(columnIndex: Int): Class<*> = get(columnIndex).clazz

    override fun isCellEditable(
        rowIndex: Int,
        columnIndex: Int,
    ): Boolean {
        return columnIndex == MDCColumns[Inclusive]
    }

    override fun setValueAt(
        aValue: Any?,
        rowIndex: Int,
        columnIndex: Int,
    ) {
        if (aValue !is Boolean) return

        if (MDCColumns[columnIndex] == Inclusive) {
            _data[rowIndex].inclusive = aValue
        }

        fireTableCellUpdated(rowIndex, columnIndex)
    }

    operator fun <T> get(
        row: Int,
        column: Column<MDCTableRow, T>,
    ): T {
        return _data[row].let { info ->
            column.getValue(info)
        }
    }

    override fun filter(item: LogEvent): Boolean {
        return when (item) {
            is WrapperLogEvent -> true
            is SystemLogEvent ->
                _data.isEmpty() ||
                    _data.any { row ->
                        row.filter(item)
                    }
        }
    }

    fun removeAt(index: Int) {
        _data.removeAt(index)
        fireTableRowsDeleted(index, index)
    }

    fun add(
        key: String,
        value: String?,
        inclusive: Boolean = true,
    ) {
        val exists =
            data.any { (existingKey, existingValue) ->
                key == existingKey && value == existingValue
            }
        if (!exists) {
            _data += MDCTableRow(key, value, inclusive)
            fireTableRowsInserted(_data.lastIndex, _data.lastIndex)
        }
    }

    fun clear() {
        _data.clear()
        fireTableDataChanged()
    }

    companion object MDCColumns : ColumnList<MDCTableRow>() {
        val Key by column(
            value = MDCTableRow::key,
        )
        val Value by column(
            value = MDCTableRow::value,
        )
        val Inclusive by column(
            column = {
                cellRenderer =
                    DefaultTableRenderer(
                        CheckBoxProvider {
                            if (it as Boolean) "Inclusive" else "Exclusive"
                        },
                    )
            },
            value = MDCTableRow::inclusive,
        )
    }
}
