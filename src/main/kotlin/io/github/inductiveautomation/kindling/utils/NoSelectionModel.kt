package io.github.inductiveautomation.kindling.utils

import javax.swing.DefaultListSelectionModel
import javax.swing.ListSelectionModel
import javax.swing.tree.DefaultTreeSelectionModel
import javax.swing.tree.TreePath
import javax.swing.tree.TreeSelectionModel

/**
 * A simple [ListSelectionModel]/[TreeSelectionModel] implementation that never allows selecting any elements.
 */
class NoSelectionModel :
    ListSelectionModel by DefaultListSelectionModel(),
    TreeSelectionModel by DefaultTreeSelectionModel() {
    override fun setSelectionInterval(index0: Int, index1: Int) = Unit
    override fun addSelectionInterval(index0: Int, index1: Int) = Unit
    override fun removeSelectionInterval(index0: Int, index1: Int) = Unit
    override fun getMinSelectionIndex(): Int = -1
    override fun getMaxSelectionIndex(): Int = -1
    override fun isSelectedIndex(index: Int): Boolean = false
    override fun getAnchorSelectionIndex(): Int = -1
    override fun setAnchorSelectionIndex(index: Int) = Unit
    override fun getLeadSelectionIndex(): Int = -1
    override fun setLeadSelectionIndex(index: Int) = Unit
    override fun clearSelection() = Unit
    override fun isSelectionEmpty(): Boolean = true
    override fun insertIndexInterval(index: Int, length: Int, before: Boolean) = Unit
    override fun removeIndexInterval(index0: Int, index1: Int) = Unit

    override fun getSelectionMode(): Int = 0
    override fun setSelectionMode(selectionMode: Int) = Unit
    override fun getSelectionPath(): TreePath? = null
    override fun getSelectionCount(): Int = 0
    override fun isPathSelected(path: TreePath): Boolean = false
    override fun isRowSelected(row: Int): Boolean = false
    override fun getMinSelectionRow(): Int = -1
    override fun getMaxSelectionRow(): Int = -1
    override fun getLeadSelectionRow(): Int = -1
}
