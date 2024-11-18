package io.github.inductiveautomation.kindling.utils

import javax.swing.table.AbstractTableModel

open class ReifiedListTableModel<T>(
    open val data: List<T>,
    override val columns: ColumnList<T>,
) : AbstractTableModel(),
    ReifiedTableModel<T> {
    override fun getColumnCount(): Int = columns.size

    override fun getRowCount(): Int = data.size

    override fun getColumnClass(columnIndex: Int) = columns[columnIndex].clazz

    override fun getColumnName(columnIndex: Int) = columns[columnIndex].header

    operator fun <R> get(
        row: Int,
        column: Column<T, R>,
    ): R = data[row].let { datum ->
        column.getValue(datum)
    }

    operator fun get(row: Int): T = data[row]

    override fun getValueAt(
        rowIndex: Int,
        columnIndex: Int,
    ): Any? = columns[columnIndex].getValue(data[rowIndex])

    override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean {
        return columns[columnIndex].setValue != null
    }

    override fun setValueAt(newValue: Any?, rowIndex: Int, columnIndex: Int) {
        val row = this[rowIndex]
        columns[columnIndex].setValue?.invoke(row, newValue)
    }
}

interface ReifiedTableModel<T> {
    val columns: ColumnList<T>
}
