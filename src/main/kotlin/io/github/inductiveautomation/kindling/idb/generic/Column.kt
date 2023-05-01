package io.github.inductiveautomation.kindling.idb.generic

import java.util.Collections
import java.util.Enumeration
import javax.swing.tree.TreeNode

data class Column(
    val name: String,
    val type: String,
    val notNull: Boolean,
    val defaultValue: String?,
    val primaryKey: Boolean,
    val hidden: Boolean,
    val _parent: () -> TreeNode,
) : TreeNode {
    override fun getChildAt(childIndex: Int): TreeNode? = null
    override fun getChildCount(): Int = 0
    override fun getParent(): TreeNode = _parent()
    override fun getIndex(node: TreeNode?): Int = -1
    override fun getAllowsChildren(): Boolean = false
    override fun isLeaf(): Boolean = true
    override fun children(): Enumeration<out TreeNode> = Collections.emptyEnumeration()
}
