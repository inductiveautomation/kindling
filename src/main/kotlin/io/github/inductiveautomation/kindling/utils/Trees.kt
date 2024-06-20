package io.github.inductiveautomation.kindling.utils

import com.jidesoft.swing.CheckBoxTree
import java.util.Collections
import java.util.Enumeration
import javax.swing.JTree
import javax.swing.tree.TreeNode
import javax.swing.tree.TreePath

abstract class AbstractTreeNode : TreeNode {
    open val children: MutableList<TreeNode> = object : ArrayList<TreeNode>() {
        override fun add(element: TreeNode): Boolean {
            element as AbstractTreeNode
            element.parent = this@AbstractTreeNode
            return super.add(element)
        }
    }
    var parent: AbstractTreeNode? = null

    override fun getAllowsChildren(): Boolean = true
    override fun getChildCount(): Int = children.size
    override fun isLeaf(): Boolean = children.isEmpty()
    override fun getChildAt(childIndex: Int): TreeNode = children[childIndex]
    override fun getIndex(node: TreeNode?): Int = children.indexOf(node)
    override fun getParent(): TreeNode? = this.parent
    override fun children(): Enumeration<out TreeNode> = Collections.enumeration(children)

    fun depthFirstChildren(): Sequence<AbstractTreeNode> = sequence {
        for (child in children) {
            yield(child as AbstractTreeNode)
            yieldAll(child.depthFirstChildren())
        }
    }
}

abstract class TypedTreeNode<T> : AbstractTreeNode() {
    abstract val userObject: T
}

fun JTree.expandAll() {
    var i = 0
    while (i < rowCount) {
        expandRow(i)
        i += 1
    }
}

fun JTree.collapseAll() {
    var i = rowCount - 1 // Skip the root node
    while (i > 0) {
        collapseRow(i)
        i -= 1
    }
}

fun CheckBoxTree.selectAll() {
    checkBoxTreeSelectionModel.addSelectionPath(TreePath(model.root))
}

fun CheckBoxTree.unselectAll() {
    checkBoxTreeSelectionModel.clearSelection()
}
