package io.github.inductiveautomation.kindling.idb.generic

import com.formdev.flatlaf.extras.FlatSVGIcon
import com.formdev.flatlaf.extras.components.FlatTree
import com.jidesoft.swing.TreeSearchable
import io.github.inductiveautomation.kindling.utils.asActionIcon
import io.github.inductiveautomation.kindling.utils.toFileSizeLabel
import io.github.inductiveautomation.kindling.utils.treeCellRenderer
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
            treeCellRenderer { _, value, selected, _, _, _, hasFocus ->
                when (value) {
                    is Table -> {
                        text = buildString {
                            append(value.name)
                            append(" ")
                            append("(${value.size.toFileSizeLabel()})")
                        }
                        icon = TABLE_ICON.asActionIcon(selected && hasFocus)
                    }

                    is Column -> {
                        text = buildString {
                            append(value.name)
                            append(" ")
                            append(value.type.takeIf { it.isNotEmpty() } ?: "UNKNOWN")
                        }
                        icon = COLUMN_ICON.asActionIcon(selected && hasFocus)
                    }
                }
                this
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
