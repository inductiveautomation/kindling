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
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreeNode
import javax.swing.tree.TreePath

data class LogEventNode(
    override val userObject: List<String>,
    var frequency: Int = 0,
) : TypedTreeNode<List<String>>() {
    //constructor(vararg parts: String) : this(parts.toList())

    val name by lazy { userObject.joinToString(".") }
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
            for (part in logger.split('.')) {
                currentLeadingPath.add(part)
                val next = seen.getOrPut(currentLeadingPath.toList()) {
                    val newChild = LogEventNode(currentLeadingPath.drop(1))
                    lastSeen.children.add(newChild)
                    newChild
                }
                lastSeen = next
            }
        }
        val leafNodes = depthFirstChildren().filter { it.isLeaf }
        for (leaf in leafNodes) {
            val logEventLeaf = leaf as LogEventNode
            val frequency = logEventsByLogger[logEventLeaf.name]
            logEventLeaf.frequency = frequency!!
        }
    }
}


class LogTree(logEvents: List<SystemLogEvent>) : CheckBoxTree(DefaultTreeModel(RootNode(logEvents))) {
    init {
        isRootVisible = false
        setShowsRootHandles(true)

        selectAll()

        setCellRenderer(
            treeCellRenderer { _, value, selected, _, _, _, _ ->
                if (value is LogEventNode) {
                    val path = value.userObject
                    text = "${path.lastOrNull()} [${value.frequency}]"
                    toolTipText = value.name

                } else {
                    icon = null
                }
                this
            },
        )
    }

    val selectedLeafNodes: List<LogEventNode>
        get() = checkBoxTreeSelectionModel.selectionPaths //drivers/modbus/etc
            .flatMap {
                println(it)
                (it.lastPathComponent as AbstractTreeNode).depthFirstChildren()
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