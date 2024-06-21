package io.github.inductiveautomation.kindling.thread

import com.jidesoft.swing.CheckBoxList
import io.github.inductiveautomation.kindling.thread.model.ThreadDump
import io.github.inductiveautomation.kindling.utils.NoSelectionModel
import io.github.inductiveautomation.kindling.utils.listCellRenderer
import javax.swing.AbstractListModel
import javax.swing.JList
import javax.swing.ListModel

class ThreadDumpListModel(private val values: List<ThreadDump>) : AbstractListModel<Any>() {
    override fun getSize(): Int = values.size + 1
    override fun getElementAt(index: Int): Any? = when (index) {
        0 -> CheckBoxList.ALL_ENTRY
        else -> values[index - 1]
    }
}

class ThreadDumpCheckboxList(data: List<ThreadDump>) : CheckBoxList(ThreadDumpListModel(data)) {
    init {
        layoutOrientation = JList.HORIZONTAL_WRAP
        visibleRowCount = 0
        isClickInCheckBoxOnly = false
        selectionModel = NoSelectionModel()

        cellRenderer = listCellRenderer { _, value: Any, index, _, _ ->
            val textToDisplay = when(value) {
                ALL_ENTRY -> "All"
                else -> {
                    val valueAsThreadDump = value as ThreadDump
                    val cpuUsage = valueAsThreadDump.threads.sumOf { it.cpuUsage ?: 0.0 }
                    "%d (%.2f%%)".format(index, cpuUsage)
                }
            }
            text = textToDisplay
        }
        selectAll()
    }

    override fun getModel() = super.getModel() as ThreadDumpListModel

    override fun setModel(model: ListModel<*>) {
        require(model is ThreadDumpListModel)
        val selection = checkBoxListSelectedValues
        checkBoxListSelectionModel.valueIsAdjusting = true
        super.setModel(model)
        addCheckBoxListSelectedValues(selection)
        checkBoxListSelectionModel.valueIsAdjusting = false
    }
}
