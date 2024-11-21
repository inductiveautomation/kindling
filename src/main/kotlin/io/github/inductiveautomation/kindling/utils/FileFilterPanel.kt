package io.github.inductiveautomation.kindling.utils

import com.formdev.flatlaf.extras.FlatSVGIcon
import com.jidesoft.swing.CheckBoxList
import io.github.inductiveautomation.kindling.core.FilterChangeListener
import io.github.inductiveautomation.kindling.core.FilterPanel
import java.nio.file.Path
import javax.swing.AbstractListModel
import javax.swing.JComponent
import javax.swing.JPopupMenu
import kotlin.io.path.absolutePathString
import kotlin.io.path.name

// A FilterList for files and their respected collections of data.

interface FileFilterResponsive<T> {
    fun setModelData(data: List<T>)
}

class FileFilterPanel<T>(data: Map<Path, Collection<T>>) : FilterPanel<Path>() {

    val fileList = FileFilterList(data)

    override val tabName: String = "Files"
    override val icon = FlatSVGIcon("icons/bx-file.svg")
    override val component: JComponent = FlatScrollPane(fileList)

    init {
        fileList.checkBoxListSelectionModel.addListSelectionListener {
            if (!it.valueIsAdjusting) {
                listeners.getAll<FilterChangeListener>().forEach(FilterChangeListener::filterChanged)
            }
        }
    }

    override fun filter(item: Path): Boolean = item in fileList.checkBoxListSelectedValues
    override fun reset() = fileList.selectAll()
    override fun isFilterApplied(): Boolean = !fileList.checkBoxListSelectionModel.isAllSelected()
    override fun customizePopupMenu(menu: JPopupMenu, column: Column<out Path, *>, event: Path) = Unit
}

class FileFilterList<T>(data: Map<Path, Collection<T>>) : CheckBoxList(FileFilterListModel(data)) {
    init {
        selectionModel = NoSelectionModel()
        isClickInCheckBoxOnly = true

        cellRenderer = listCellRenderer<Any> { _, value, _, _, _ ->
            when (value) {
                ALL_ENTRY -> text = "All Files"
                else -> {
                    value as Path
                    text = value.name
                    toolTipText = value.absolutePathString()
                }
            }
        }
    }

    @Suppress("unchecked_cast")
    override fun getModel(): FileFilterListModel<T> {
        return super.getModel() as FileFilterListModel<T>
    }
}

class FileFilterListModel<T>(val data: Map<Path, Collection<T>>) : AbstractListModel<Any>() {
    override fun getSize(): Int = data.size + 1

    val paths = data.keys.toList()

    val entries = data.entries.map { it.key to it.value }

    override fun getElementAt(index: Int): Any {
        return if (index == 0) {
            CheckBoxList.ALL_ENTRY
        } else {
            paths[index - 1]
        }
    }
}