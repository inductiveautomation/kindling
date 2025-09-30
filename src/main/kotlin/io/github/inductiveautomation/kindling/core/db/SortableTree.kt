package io.github.inductiveautomation.kindling.core.db

import com.formdev.flatlaf.extras.FlatSVGIcon
import com.jidesoft.comparator.AlphanumComparator
import io.github.inductiveautomation.kindling.utils.Action
import io.github.inductiveautomation.kindling.utils.ButtonPanel
import io.github.inductiveautomation.kindling.utils.FlatActionIcon
import io.github.inductiveautomation.kindling.utils.FlatScrollPane
import java.util.Collections
import java.util.Enumeration
import javax.swing.ButtonGroup
import javax.swing.JToggleButton
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreeNode
import kotlin.collections.indexOf

enum class TableComparator(
    val tooltip: String,
    val icon: FlatSVGIcon,
    val comparator: Comparator<Table>,
) : Comparator<Table> by comparator {
    ByNameAscending(
        tooltip = "Sort A-Z",
        icon = FlatActionIcon("icons/bx-sort-a-z.svg"),
        comparator = compareBy(nullsFirst(AlphanumComparator(false))) { it.name },
    ),
    ByNameDescending(
        tooltip = "Sort Z-A",
        icon = FlatActionIcon("icons/bx-sort-z-a.svg"),
        comparator = ByNameAscending.reversed(),
    ),
    BySizeAscending(
        tooltip = "Sort by Size",
        icon = FlatActionIcon("icons/bx-sort-up.svg"),
        comparator = compareBy(Table::size),
    ),
    BySizeDescending(
        tooltip = "Sort by Size (descending)",
        icon = FlatActionIcon("icons/bx-sort-down.svg"),
        comparator = BySizeAscending.reversed(),
    ),
}

class SortableTree(val tables: List<Table>) {
    var comparator = TableComparator.BySizeDescending
        set(value) {
            field = value
            root = sortedTreeNode()
            tree.model = DefaultTreeModel(root)
        }

    private fun sortedTreeNode() = object : TreeNode {
        private val sortedTables = tables.sortedWith(comparator)

        override fun getChildAt(childIndex: Int): TreeNode = sortedTables[childIndex]
        override fun getChildCount(): Int = sortedTables.size
        override fun getIndex(node: TreeNode): Int = sortedTables.indexOf(node)
        override fun children(): Enumeration<out TreeNode> = Collections.enumeration(sortedTables)
        override fun getParent(): TreeNode? = null
        override fun getAllowsChildren(): Boolean = true
        override fun isLeaf(): Boolean = false
    }

    var root: TreeNode = sortedTreeNode()

    val tree = DBMetaDataTree(DefaultTreeModel(root))

    private val sortActions: List<SortAction> = TableComparator.entries.map { tableComparator ->
        SortAction(tableComparator)
    }

    inner class SortAction(comparator: TableComparator) :
        Action(
            description = comparator.tooltip,
            icon = comparator.icon,
            selected = this@SortableTree.comparator == comparator,
            action = {
                this@SortableTree.comparator = comparator
                selected = true
            },
        ) {
        var comparator: TableComparator by actionValue("tableComparator", comparator)
    }

    private fun createSortButtons(): ButtonGroup = ButtonGroup().apply {
        for (sortAction in sortActions) {
            add(
                JToggleButton(
                    Action(
                        description = sortAction.description,
                        icon = sortAction.icon,
                        selected = sortAction.selected,
                    ) { e ->
                        sortAction.actionPerformed(e)
                    },
                ),
            )
        }
    }

    private val sortButtons = createSortButtons()

    val component = ButtonPanel(sortButtons).apply {
        add(FlatScrollPane(tree), "newline, push, grow")
    }
}
