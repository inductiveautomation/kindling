package io.github.inductiveautomation.kindling.log

import com.formdev.flatlaf.extras.FlatSVGIcon
import com.jidesoft.swing.CheckBoxTree
import io.github.inductiveautomation.kindling.idb.metrics.Metric
import io.github.inductiveautomation.kindling.idb.metrics.MetricNode
import io.github.inductiveautomation.kindling.idb.metrics.MetricTree
import io.github.inductiveautomation.kindling.idb.metrics.RootNode
import io.github.inductiveautomation.kindling.utils.AbstractTreeNode
import io.github.inductiveautomation.kindling.utils.TypedTreeNode
import io.github.inductiveautomation.kindling.utils.asActionIcon
import io.github.inductiveautomation.kindling.utils.treeCellRenderer
import org.apache.commons.math3.stat.Frequency
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreeNode
import javax.swing.tree.TreePath

class LogEventNode(
    override val userObject: List<String>,
    frequency: Int = 0
) : TypedTreeNode<List<String>>() {
    //constructor(vararg parts: String) : this(parts.toList())

    val name by lazy { userObject.joinToString(".") }

    val frequency: Int by lazy {
        if (this.isLeaf) {
            frequency
        } else {
            //sum up all children of this node
            this.children.sumOf {
                (it as LogEventNode).frequency
            }
        }
    }
}

class RootNode(logEvents: List<SystemLogEvent>) : AbstractTreeNode() {
    private val logEventsByLogger : Map<String, Int> = logEvents.groupBy { it.logger }.mapValues { mapEntry ->
        mapEntry.value.size
    }

    init {
        val seen = mutableMapOf<List<String>, LogEventNode>()
        for ((logger, freq) in logEventsByLogger.entries) {
            var lastSeen: AbstractTreeNode = this
            val currentLeadingPath = mutableListOf<String>("")
            val loggerParts = logger.split('.')
            for ((index, part) in loggerParts.withIndex()) {
                currentLeadingPath.add(part)
                val next = seen.getOrPut(currentLeadingPath.toList()) {
                    val path = currentLeadingPath.drop(1)
                    val newChild = if (index == loggerParts.size-1) {
                        val name = path.joinToString(".")
                        LogEventNode(path, logEventsByLogger[name]!!)
                    } else {
                        LogEventNode(path)
                    }
                    lastSeen.children.add(newChild)
                    newChild
                }
                lastSeen = next
            }
        }
    }
}


class LogTree(logEvents: List<SystemLogEvent>) : CheckBoxTree(DefaultTreeModel(RootNode(logEvents))) {
    init {
        setShowsRootHandles(false)

        selectAll()

        setCellRenderer(
            treeCellRenderer { _, value, selected, _, _, _, _ ->
                if (value is LogEventNode) {
                    val path = value.userObject
                    text = "${path.lastOrNull()} [${value.frequency}]"
                    toolTipText = value.name

                } else {
                    icon = null
                    text = "Select All"
                }
                this
            },
        )
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

    fun selectAll() = checkBoxTreeSelectionModel.addSelectionPath(TreePath(model.root))
}