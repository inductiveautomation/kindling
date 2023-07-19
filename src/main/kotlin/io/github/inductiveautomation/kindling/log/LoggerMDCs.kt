package io.github.inductiveautomation.kindling.log

import io.github.inductiveautomation.kindling.core.Kindling
import io.github.inductiveautomation.kindling.utils.Column
import io.github.inductiveautomation.kindling.utils.ColumnList
import io.github.inductiveautomation.kindling.utils.FlatScrollPane
import io.github.inductiveautomation.kindling.utils.ReifiedJXTable
import io.github.inductiveautomation.kindling.utils.add
import io.github.inductiveautomation.kindling.utils.attachPopupMenu
import net.miginfocom.swing.MigLayout
import java.awt.Component
import java.awt.event.ActionEvent
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.JMenuItem
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.UIManager
import javax.swing.event.EventListenerList
import javax.swing.plaf.basic.BasicComboBoxRenderer
import javax.swing.table.AbstractTableModel
import kotlin.math.min

class LoggerMDCPanel(events: List<SystemLogEvent>) : JPanel(MigLayout("ins 0, fill")), LogFilterPanel {

    private val allMDCs = events.flatMap(SystemLogEvent::mdc)

    private val fullMDCMap = allMDCs.groupingBy { it }.eachCount()

    private val keyCounts = allMDCs
        .groupingBy { it.key }
        .eachCount()
        .entries
        .sortedByDescending(Map.Entry<String, Int>::value)
        .associate(Map.Entry<String, Int>::toPair)

    private val keyValueMap = allMDCs
        .groupBy(MDC::key)
        .mapValues { it.value.toSet() }

    private val keyMenu = JComboBox(keyCounts.keys.toTypedArray()).apply {
        renderer = object : BasicComboBoxRenderer() {
            override fun getListCellRendererComponent(
                list: JList<*>?,
                value: Any?,
                index: Int,
                isSelected: Boolean,
                cellHasFocus: Boolean,
            ): Component {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
                text = if (index == -1 && value == null) {
                    " - key - "
                } else {
                    val key = value as? String
                    val count = keyCounts[key]
                    "$key [$count]"
                }
                return this
            }
        }
        selectedIndex = -1

        addActionListener {
            valueMenu.removeAllItems()
            keyValueMap[selectedItem]?.forEach {
                valueMenu.addItem(it.value)
            }
        }
    }

    private val valueMenu: JComboBox<String> = JComboBox<String>().apply {
        renderer = object : BasicComboBoxRenderer() {
            override fun getListCellRendererComponent(
                list: JList<*>?,
                value: Any?,
                index: Int,
                isSelected: Boolean,
                cellHasFocus: Boolean,
            ): Component {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
                text = if (index == -1 && value == null) {
                    " - value - "
                } else {
                    val count = fullMDCMap.entries.find { (mdc, _) ->
                        val selectedKey: String = keyMenu.selectedItem as String
                        val selectedValue: String = value as String
                        mdc.key == selectedKey && mdc.value == selectedValue
                    }?.value
                    "${value as String} [$count]"
                }
                return this
            }
        }
        selectedIndex = -1
    }

    private val addButton = JButton("+").apply {
        border = BorderFactory.createEmptyBorder()
        isOpaque = false
        isContentAreaFilled = false
        font = UIManager.getFont("h2.regular.font")
        addActionListener {
            val key = keyMenu.selectedItem as String?
            val value = valueMenu.selectedItem as String?

            if (key != null && value != null) {
                val exists = filterTable.model.data.any {
                    it.mdc.key == key && it.mdc.value == value
                }
                if (!exists) {
                    filterTable.model.data.add(MDCEntry(mdc = MDC(key, value)))
                    filterTable.model.fireTableDataChanged()
                }
            }
        }
    }

    private val removeButton = JButton("-").apply {
        border = BorderFactory.createEmptyBorder()
        isOpaque = false
        isContentAreaFilled = false
        font = UIManager.getFont("h2.regular.font")
        addActionListener(removeSelectedFilter())
    }

    private fun removeSelectedFilter(): (e: ActionEvent) -> Unit = {
        if (filterTable.selectedRow < filterTable.model.data.size && filterTable.model.data.size > 0 && filterTable.selectedRow > -1) {
            val previousSelection = filterTable.selectedRow
            filterTable.model.data.removeAt(filterTable.selectedRow)
            filterTable.model.fireTableDataChanged()
            val currentSelection = min(previousSelection, filterTable.model.data.size - 1)
            if (currentSelection >= 0) {
                filterTable.setRowSelectionInterval(currentSelection, currentSelection)
            }
        }
    }

    private val filterTable = ReifiedJXTable(MDCTableModel()).apply {
        attachPopupMenu {
            JPopupMenu().apply {
                add(
                    JMenuItem("Remove Filter").apply {
                        addActionListener(removeSelectedFilter())
                    },
                )
            }
        }
    }

    init {
        add(keyMenu, "spanx 2, pushx, growx, wrap")
        add(valueMenu, "spanx 2, pushx, growx, wrap")
        add(removeButton, "cell 0 3, align right")
        add(addButton, "cell 0 3, align right, wrap, pushx")
        add(FlatScrollPane(filterTable), "spanx 2, pushy, grow")

        Kindling.Preferences.UI.Theme.addChangeListener {
            addButton.font = UIManager.getFont("h2.regular.font")
            removeButton.font = UIManager.getFont("h2.regular.font")
        }
    }

    override val isFilterApplied: Boolean
        get() = filterTable.model.data.any(MDCEntry::enabled)

    override val component: JComponent = this

    override val tabName: String = "MDC"

    override fun filter(event: LogEvent): Boolean {
        return filterTable.model.filter(event)
    }

    private val listeners = EventListenerList()

    override fun addFilterChangeListener(listener: FilterChangeListener) {
        listeners.add(listener)
    }
}

data class MDC(
    override val key: String,
    override val value: String?,
) : Map.Entry<String, String?>

data class MDCEntry(
    val mdc: MDC,
    var enabled: Boolean = true,
    var inverted: Boolean = false,
)

class MDCTableModel(
    val data: MutableList<MDCEntry> = mutableListOf(),
) : AbstractTableModel(), LogFilter {
    override fun getRowCount(): Int = data.size

    @Suppress("RedundantCompanionReference")
    override fun getColumnCount(): Int = MDCColumns.size

    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any? {
        return get(rowIndex, MDCColumns[columnIndex])
    }

    override fun getColumnName(column: Int): String {
        return get(column).header
    }

    override fun getColumnClass(columnIndex: Int): Class<*> {
        return get(columnIndex).clazz
    }

    override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean {
        return columnIndex == MDCColumns[Enable] || columnIndex == MDCColumns[Invert]
    }

    override fun setValueAt(aValue: Any?, rowIndex: Int, columnIndex: Int) {
        if (aValue !is Boolean) return

        when (MDCColumns[columnIndex]) {
            Enable -> {
                data[rowIndex].enabled = aValue
            }

            Invert -> {
                data[rowIndex].inverted = aValue
            }
        }

        fireTableCellUpdated(rowIndex, columnIndex)
    }

    operator fun <T> get(row: Int, column: Column<MDCEntry, T>): T {
        return data[row].let { info ->
            column.getValue(info)
        }
    }

    fun isValidLogEvent(logEvent: LogEvent, inverted: Boolean): Boolean = when (logEvent) {
        is WrapperLogEvent -> true
        is SystemLogEvent -> {
            val mdcList = data.filter {
                it.enabled && it.inverted == inverted
            }

            if (mdcList.isEmpty()) {
                true
            } else {
                if (mdcList.any { it.mdc in logEvent.mdc }) {
                    !inverted
                } else {
                    inverted
                }
            }
        }
    }

    override fun filter(event: LogEvent): Boolean {
        return true
//            val mdcList = data.filter {
//                it.enabled || it.inverted
//            }
//
//            mdcList.isEmpty()
    }

    companion object MDCColumns : ColumnList<MDCEntry>() {
        val Enable by column(
            value = MDCEntry::enabled,
        )
        val Key by column(
            value = { it.mdc.key },
        )
        val Value by column(
            value = { it.mdc.value },
        )
        val Invert by column(
            value = MDCEntry::inverted,
        )
    }
}
