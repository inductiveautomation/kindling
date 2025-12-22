package io.github.inductiveautomation.kindling.core.db

import java.util.Collections
import java.util.Enumeration
import javax.swing.tree.TreeNode

data class Table(
    val name: String,
    val columns: List<Column>,
    val _parent: () -> TreeNode,
    val size: Long,
    val rowCount: Long,
) : TreeNode {
    override fun getChildAt(childIndex: Int): TreeNode = columns[childIndex]
    override fun getChildCount(): Int = columns.size
    override fun getParent(): TreeNode = _parent()
    override fun getIndex(node: TreeNode): Int = columns.indexOf(node)
    override fun getAllowsChildren(): Boolean = true
    override fun isLeaf(): Boolean = false
    override fun children(): Enumeration<out TreeNode> = Collections.enumeration(columns)

    override fun toString(): String = this.name
}
