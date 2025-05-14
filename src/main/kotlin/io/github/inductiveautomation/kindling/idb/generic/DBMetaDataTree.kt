package io.github.inductiveautomation.kindling.idb.generic

import com.formdev.flatlaf.extras.components.FlatTree
import com.jidesoft.swing.TreeSearchable
import io.github.inductiveautomation.kindling.utils.FlatActionIcon
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
                        icon = FlatActionIcon("icons/bx-table.svg")
                    }

                    is Column -> {
                        text = buildString {
                            append(value.name)
                            append(" ")
                            append(value.type.takeIf { it.isNotEmpty() } ?: "UNKNOWN")
                        }
                        icon = FlatActionIcon("icons/bx-column.svg")
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
}
