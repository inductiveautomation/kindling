package io.github.inductiveautomation.kindling.internal

import io.github.inductiveautomation.kindling.utils.ColumnList
import javax.swing.table.AbstractTableModel
import kotlin.properties.Delegates

class DetailsModel(details: List<Map.Entry<String, String>>) : AbstractTableModel() {
    var details: List<Map.Entry<String, String>> by Delegates.observable(details) { _, _, _ ->
        fireTableDataChanged()
    }

    override fun getColumnName(column: Int): String = DetailsColumns[column].header

    override fun getRowCount(): Int = details.size

    override fun getColumnCount(): Int = size

    override fun getValueAt(
        row: Int,
        column: Int,
    ): Any? {
        return details[row].let { entry ->
            DetailsColumns[column].getValue(entry)
        }
    }

    override fun getColumnClass(column: Int): Class<*> = DetailsColumns[column].clazz

    @Suppress("unused")
    companion object DetailsColumns : ColumnList<Map.Entry<String, String>>() {
        val Key by column { it.key }
        val Value by column { it.value }
    }
}
