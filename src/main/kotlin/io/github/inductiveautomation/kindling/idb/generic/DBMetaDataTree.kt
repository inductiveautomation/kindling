package io.github.inductiveautomation.kindling.idb.generic

import com.formdev.flatlaf.extras.FlatSVGIcon
import com.formdev.flatlaf.extras.components.FlatTree
import com.jidesoft.swing.StyledLabelBuilder
import com.jidesoft.swing.TreeSearchable
import io.github.inductiveautomation.kindling.core.Kindling.SECONDARY_ACTION_ICON_SCALE
import io.github.inductiveautomation.kindling.utils.derive
import io.github.inductiveautomation.kindling.utils.treeCellRenderer
import java.awt.Font
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
            treeCellRenderer { _, value, selected, _, _, _, focused ->
                when (value) {
                    is Table -> {
                        text = value.name
                        icon = if (selected && focused) TABLE_ICON_SELECTED else TABLE_ICON
                        this
                    }

                    is Column -> {
                        StyledLabelBuilder()
                            .add(value.name)
                            .add("   ")
                            .add(value.type.takeIf { it.isNotEmpty() } ?: "UNKNOWN", Font.ITALIC)
                            .createLabel()
                            .apply {
                                icon = if (selected && focused) COLUMN_ICON_SELECTED else COLUMN_ICON
                                foreground = when {
                                    selected && focused -> UIManager.getColor("Tree.selectionForeground")
                                    selected -> UIManager.getColor("Tree.selectionInactiveForeground")
                                    else -> UIManager.getColor("Tree.textForeground")
                                }
                            }
                    }

                    else -> this
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
        private val TABLE_ICON = FlatSVGIcon("icons/bx-table.svg").derive(SECONDARY_ACTION_ICON_SCALE)
        private val TABLE_ICON_SELECTED = TABLE_ICON.derive { UIManager.getColor("Tree.selectionForeground") }

        private val COLUMN_ICON = FlatSVGIcon("icons/bx-column.svg").derive(SECONDARY_ACTION_ICON_SCALE)
        private val COLUMN_ICON_SELECTED = COLUMN_ICON.derive { UIManager.getColor("Tree.selectionForeground") }
    }
}
