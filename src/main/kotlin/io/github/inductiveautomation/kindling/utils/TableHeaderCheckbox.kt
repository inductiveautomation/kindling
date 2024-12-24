package io.github.inductiveautomation.kindling.utils

import java.awt.Component
import java.awt.EventQueue
import java.awt.event.MouseEvent
import java.awt.event.MouseListener
import javax.swing.JCheckBox
import javax.swing.JTable
import javax.swing.event.TableModelEvent
import javax.swing.event.TableModelListener
import javax.swing.table.JTableHeader
import javax.swing.table.TableCellRenderer

class TableHeaderCheckbox(
    selected: Boolean = true,
) : JCheckBox(), TableCellRenderer, MouseListener, TableModelListener {
    private lateinit var table: JTable
    private var targetColumn: Int? = null
    private var valueIsAdjusting = false

    init {
        isSelected = selected
        toolTipText = "Select All"

        addActionListener { handleClick() }
    }

    private val isAllDataSelected: Boolean
        get() {
            if (!this::table.isInitialized || targetColumn == null) return false

            val columnModelIndex = table.convertColumnIndexToModel(targetColumn!!)
            val columnClass = table.getColumnClass(targetColumn!!)

            println("Column class: $columnClass")

            if (columnClass != java.lang.Boolean::class.java) return false

            val data = (0..<table.rowCount).map {
                table.model.getValueAt(it, columnModelIndex) as? Boolean ?: return false
            }

            return data.all { it }
        }

    private fun handleClick() {
        valueIsAdjusting = true

        if (!this::table.isInitialized || targetColumn == null) return

        val columnModelIndex = table.convertColumnIndexToModel(targetColumn!!)
        val columnClass = table.getColumnClass(targetColumn!!)

        if (columnClass != java.lang.Boolean::class.java) return

        for (row in 0..<table.rowCount) {
            table.model.setValueAt(isSelected, row, columnModelIndex)
        }

        valueIsAdjusting = false
    }

    override fun tableChanged(e: TableModelEvent?) {
        if (e == null || !::table.isInitialized) return

        val viewColumn = table.convertColumnIndexToView(e.column)

        if ((viewColumn == targetColumn || e.column == TableModelEvent.ALL_COLUMNS) && !valueIsAdjusting) {
            isSelected = isAllDataSelected

            EventQueue.invokeLater {
                table.tableHeader.repaint()
            }
        }
    }

    override fun getTableCellRendererComponent(
        table: JTable?,
        value: Any?,
        isSelected: Boolean,
        hasFocus: Boolean,
        row: Int,
        column: Int,
    ): Component {
        if (!this::table.isInitialized && table != null) {
            this.table = table

            this.table.model.addTableModelListener(this)
            this.table.tableHeader.addMouseListener(this)
        }

        targetColumn = column
        return this
    }

    override fun mouseClicked(e: MouseEvent?) {
        val tableHeader = e?.source as? JTableHeader ?: return

        val viewColumn = tableHeader.columnModel.getColumnIndexAtX(e.x)
        val modelColumn = tableHeader.table.convertColumnIndexToModel(viewColumn)

        if (viewColumn == targetColumn && modelColumn != -1) {
            doClick()
        }

        tableHeader.repaint()
    }

    override fun mousePressed(e: MouseEvent?) = Unit
    override fun mouseReleased(e: MouseEvent?) = Unit
    override fun mouseEntered(e: MouseEvent?) = Unit
    override fun mouseExited(e: MouseEvent?) = Unit
}
