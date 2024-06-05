package io.github.inductiveautomation.kindling.log

import com.formdev.flatlaf.extras.FlatSVGIcon
import com.jidesoft.swing.CheckBoxTree
import com.jidesoft.swing.TreeSearchable
import io.github.inductiveautomation.kindling.idb.generic.Column
import io.github.inductiveautomation.kindling.idb.generic.Table
import io.github.inductiveautomation.kindling.idb.metrics.Metric
import io.github.inductiveautomation.kindling.idb.metrics.MetricNode
import io.github.inductiveautomation.kindling.idb.metrics.MetricTree
import io.github.inductiveautomation.kindling.idb.metrics.RootNode
import io.github.inductiveautomation.kindling.utils.AbstractTreeNode
import io.github.inductiveautomation.kindling.utils.Action
import io.github.inductiveautomation.kindling.utils.TypedTreeNode
import io.github.inductiveautomation.kindling.utils.asActionIcon
import io.github.inductiveautomation.kindling.utils.attachPopupMenu
import io.github.inductiveautomation.kindling.utils.treeCellRenderer
import javax.swing.JMenuItem
import javax.swing.JPopupMenu
import org.apache.commons.math3.stat.Frequency
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreeNode
import javax.swing.tree.TreePath

class LogEventNode(
    override val userObject: List<String>,
    frequency: Int = 0
) : TypedTreeNode<List<String>>() {
    val name by lazy { userObject.joinToString(".") }

    override val children: MutableList<TreeNode> = object : ArrayList<TreeNode>() {
        override fun add(element: TreeNode): Boolean {
            element as AbstractTreeNode
            element.parent = this@LogEventNode
            val success = super.add(element)

            sortWith(compareBy { (it as LogEventNode).userObject.last })
            return success
        }
    }

    val frequency: Int by lazy {
        if (this.isLeaf) {
            frequency
        } else {
            children.sumOf {
                (it as LogEventNode).frequency
            }
        }
    }
}

class RootNode(logEvents: List<SystemLogEvent>) : AbstractTreeNode() {

    init {
        val logEventsByLogger = logEvents.groupingBy(SystemLogEvent::logger).eachCount()

        val seen = mutableMapOf<List<String>, LogEventNode>()
        for ((logger, freq) in logEventsByLogger.entries) {
            var lastSeen: AbstractTreeNode = this

            val currentLeadingPath = mutableListOf<String>("")
            val loggerParts = logger.split('.')

            for ((index, part) in loggerParts.withIndex()) {
                currentLeadingPath.add(part)
                val next = seen.getOrPut(currentLeadingPath.toList()) {
                    val path = currentLeadingPath.drop(1)
                    val newChild = if (index == loggerParts.size - 1) {
                        LogEventNode(path, freq)
                    } else {
                        LogEventNode(path)
                    }
                    lastSeen.children.add(newChild)
                    newChild
                }
                lastSeen = next
            }
        }

        children.sortWith(compareBy { (it as LogEventNode).userObject.last.lowercase() })
    }
}


class LogTree(logEvents: List<SystemLogEvent>) : CheckBoxTree(DefaultTreeModel(RootNode(logEvents))) {
    init {
        setShowsRootHandles(false)
        selectAll()

        setCellRenderer(
            treeCellRenderer { _, value, _, _, _, _, _ ->
                if (value is LogEventNode) {
                    val path = value.userObject
                    text = "${path.lastOrNull()} [${value.frequency}]"
                    toolTipText = value.name

                } else {
                    text = "Select All"
                }
                icon = null
                this
            },
        )

        attachPopupMenu {
            JPopupMenu().apply {
                add(
                    JMenuItem(
                        Action("Expand All") {
                            expandAll()
                        }
                    )
                )
                add(
                    JMenuItem(
                        Action("Collapse All") {
                            collapseAll()
                        }
                    )
                )
            }
        }

        object : TreeSearchable(this) {
            init {
                isRecursive = true
                isRepeats = true
            }

            override fun convertElementToString(element: Any?): String {
                return when (val node = (element as? TreePath)?.lastPathComponent) {
                    is LogEventNode -> node.name
                    else -> ""
                }
            }
        }
    }

    val selectedLeafNodes: List<LogEventNode>
        get() = checkBoxTreeSelectionModel.selectionPaths //drivers/modbus/etc
            .flatMap {
                (it.lastPathComponent as AbstractTreeNode).depthFirstChildren().ifEmpty {
                    sequenceOf(it.lastPathComponent)
                }
            }.filterIsInstance<LogEventNode>()

    private fun expandAll() {
        var i = 0
        while (i < rowCount) {
            expandRow(i)
            i += 1
        }
    }

    private fun collapseAll() {
        var i = rowCount - 1 // Skip the root node
        while (i > 0) {
            collapseRow(i)
            i -= 1
        }
    }

    fun selectAll() = checkBoxTreeSelectionModel.addSelectionPath(TreePath(model.root))
}