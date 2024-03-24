package io.github.inductiveautomation.kindling.utils

import javax.swing.table.AbstractTableModel

class ReifiedListTableModel<T>(
    val data: List<T>,
    override val columns: ColumnList<T>,
) : AbstractTableModel(), ReifiedTableModel<T> {
    override fun getColumnCount(): Int = columns.size

    override fun getRowCount(): Int = data.size

    override fun getColumnClass(columnIndex: Int) = columns[columnIndex].clazz

    override fun getColumnName(columnIndex: Int) = columns[columnIndex].header

    operator fun <R> get(
        row: Int,
        column: Column<T, R>,
    ): R {
        return data[row].let { datum ->
            column.getValue(datum)
        }
    }

    operator fun get(row: Int): T = data[row]

    override fun getValueAt(
        rowIndex: Int,
        columnIndex: Int,
    ): Any? {
        return columns[columnIndex].getValue(data[rowIndex])
    }
}

interface ReifiedTableModel<T> {
    val columns: ColumnList<T>
}
