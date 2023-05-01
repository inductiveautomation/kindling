package io.github.inductiveautomation.kindling.cache

import com.jidesoft.swing.CheckBoxList
import io.github.inductiveautomation.kindling.utils.listCellRenderer
import java.awt.Font
import java.awt.Font.MONOSPACED
import javax.swing.AbstractListModel
import javax.swing.DefaultListSelectionModel

class SchemaModel(data: List<SchemaRecord>) : AbstractListModel<Any>() {
    private val comparator: Comparator<SchemaRecord> = compareBy(nullsFirst()) { it.id }
    private val values = data.sortedWith(comparator)

    override fun getSize(): Int {
        return values.size + 1
    }

    override fun getElementAt(index: Int): Any {
        return if (index == 0) {
            CheckBoxList.ALL_ENTRY
        } else {
            values[index - 1]
        }
    }
}

class SchemaFilterList(modelData: List<SchemaRecord>) : CheckBoxList(SchemaModel(modelData)) {
    init {
        selectionModel = DefaultListSelectionModel()
        isClickInCheckBoxOnly = true
        visibleRowCount = 0

        val txGroupRegex = """(.*)\{.*}""".toRegex()

        cellRenderer = listCellRenderer<Any?> { _, schemaEntry, _, _, _ ->
            text = when (schemaEntry) {
                is SchemaRecord -> {
                    buildString {
                        append("%4d".format(schemaEntry.id))
                        val name = txGroupRegex.find(schemaEntry.name)?.groups?.get(1)?.value ?: schemaEntry.name
                        append(": ").append(name)

                        when (val size = schemaEntry.errors.size) {
                            0 -> Unit
                            1 -> append(" ($size error. Click to view.)")
                            else -> append(" ($size errors. Click to view.)")
                        }
                    }
                }
                else -> schemaEntry.toString()
            }
            font = Font(MONOSPACED, Font.PLAIN, 14)
        }
        selectAll()
    }

    override fun getModel() = super.getModel() as SchemaModel
}
