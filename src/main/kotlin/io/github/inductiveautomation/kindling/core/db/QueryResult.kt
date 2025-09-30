package io.github.inductiveautomation.kindling.core.db

import javax.swing.table.AbstractTableModel

sealed interface QueryResult {
    class Success(
        val columnNames: List<String>,
        private val columnTypes: List<Class<*>>,
        val data: List<List<*>>,
    ) : AbstractTableModel(),
        QueryResult {
        constructor() : this(emptyList(), emptyList(), emptyList())

        init {
            require(columnNames.size == columnTypes.size)
        }

        override fun getRowCount(): Int = data.size
        override fun getColumnCount(): Int = columnNames.size
        override fun getColumnName(columnIndex: Int): String = columnNames[columnIndex]
        override fun getColumnClass(columnIndex: Int): Class<*> = columnTypes[columnIndex]
        override fun getValueAt(rowIndex: Int, columnIndex: Int): Any? = data[rowIndex][columnIndex]
    }

    class Error(
        val details: String,
    ) : QueryResult
}
