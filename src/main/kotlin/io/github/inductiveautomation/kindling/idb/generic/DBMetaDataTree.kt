package io.github.inductiveautomation.kindling.idb.generic

import com.formdev.flatlaf.extras.FlatSVGIcon
import com.formdev.flatlaf.extras.components.FlatTree
import com.jidesoft.swing.StyledLabelBuilder
import com.jidesoft.swing.TreeSearchable
import com.jidesoft.tree.StyledTreeCellRenderer
import io.github.inductiveautomation.kindling.utils.asActionIcon
import io.github.inductiveautomation.kindling.utils.toFileSizeLabel
import java.awt.Font
import javax.swing.JTree
import javax.swing.UIManager
import javax.swing.tree.TreeModel
import javax.swing.tree.TreePath
import javax.swing.tree.TreeSelectionModel

class DBMetaDataTree(treeModel: TreeModel) : FlatTree() {
    init {
        model = treeModel
        isRootVisible = false
        setShowsRootHandles(true)
        selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION
        setCellRenderer(
            object : StyledTreeCellRenderer() {
                override fun customizeStyledLabel(tree: JTree, value: Any?, sel: Boolean, expanded: Boolean, leaf: Boolean, row: Int, focused: Boolean) {
                    super.customizeStyledLabel(tree, value, sel, expanded, leaf, row, focused)
                    clearStyleRanges()

                    foreground = when {
                        selected && focused -> UIManager.getColor("Tree.selectionForeground")
                        selected -> UIManager.getColor("Tree.selectionInactiveForeground")
                        else -> UIManager.getColor("Tree.textForeground")
                    }

                    when (value) {
                        is Table -> {
                            StyledLabelBuilder().apply {
                                add(value.name)
                                add(" ")
                                add("(${value.size.toFileSizeLabel()})", Font.ITALIC)
                            }.configure(this)
                            icon = TABLE_ICON.asActionIcon(selected || focused)
                        }
                        is Column -> {
                            StyledLabelBuilder().apply {
                                add(value.name)
                                add(" ")
                                add(value.type.takeIf { it.isNotEmpty() } ?: "UNKNOWN", Font.ITALIC)
                            }.configure(this)
                            icon = COLUMN_ICON.asActionIcon(selected || focused)
                        }
                    }
                }
            },
        )

        object : TreeSearchable(this) {
            init {
                isRecursive = true
                isRepeats = true
            }

            override fun convertElementToString(element: Any?): String {
                return when (val node = (element as? TreePath)?.lastPathComponent) {
                    is Table -> node.name
                    is Column -> node.name
                    else -> ""
                }
            }
        }
    }

    companion object {
        private val TABLE_ICON = FlatSVGIcon("icons/bx-table.svg")
        private val COLUMN_ICON = FlatSVGIcon("icons/bx-column.svg")
    }
}
