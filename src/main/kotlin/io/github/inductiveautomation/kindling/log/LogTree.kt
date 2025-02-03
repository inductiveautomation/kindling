package io.github.inductiveautomation.kindling.log

import com.jidesoft.comparator.AlphanumComparator
import com.jidesoft.swing.CheckBoxTree
import com.jidesoft.swing.TreeSearchable
import io.github.inductiveautomation.kindling.utils.AbstractTreeNode
import io.github.inductiveautomation.kindling.utils.TypedTreeNode
import io.github.inductiveautomation.kindling.utils.selectAll
import io.github.inductiveautomation.kindling.utils.treeCellRenderer
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreeNode
import javax.swing.tree.TreePath

private val nodeComparator: Comparator<TreeNode> =
    compareBy(AlphanumComparator(false)) { (it as LogEventNode).userObject.last() }

class LogEventNode(
    override val userObject: List<String>,
    frequency: Int = 0,
) : TypedTreeNode<List<String>>() {
    val name by lazy { userObject.joinToString(".") }

    override val children: MutableList<TreeNode> = object : ArrayList<TreeNode>() {
        override fun add(element: TreeNode): Boolean {
            require(element is LogEventNode)
            element.parent = this@LogEventNode
            return super.add(element).also {
                sortWith(nodeComparator)
            }
        }
    }

    val frequency: Int by lazy {
        if (this.isLeaf) {
            frequency
        } else {
            frequency + children.filterIsInstance<LogEventNode>().sumOf { it.frequency }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (other == null || other !is LogEventNode) {
            return false
        }

        return other.userObject == this.userObject
    }

    override fun hashCode(): Int = userObject.hashCode()
}

class RootNode(logEvents: List<SystemLogEvent>) : AbstractTreeNode() {
    init {
        val logEventsByLogger = logEvents.groupingBy(SystemLogEvent::logger).eachCount()

        val seen = mutableMapOf<List<String>, LogEventNode>()
        for (logger in logEventsByLogger.keys) {
            var lastSeen: AbstractTreeNode = this

            val currentLeadingPath = mutableListOf("")
            val loggerParts = logger.split('.')

            for (part in loggerParts) {
                currentLeadingPath.add(part)
                val next = seen.getOrPut(currentLeadingPath.toList()) {
                    val path = currentLeadingPath.drop(1)

                    // Need to check every path and "sub-path" to see if it is also a full logger.
                    // Thanks to the map, this is not that expensive of a check.
                    val loggerFreq = logEventsByLogger[path.joinToString(".")]

                    val newChild = if (loggerFreq != null) {
                        LogEventNode(path, loggerFreq)
                    } else {
                        LogEventNode(path)
                    }
                    lastSeen.children.add(newChild)
                    newChild
                }
                lastSeen = next
            }
        }

        children.sortWith(nodeComparator)
    }
}

class LogTree(logEvents: List<SystemLogEvent>) : CheckBoxTree(DefaultTreeModel(RootNode(logEvents))) {
    init {
        setShowsRootHandles(false)
        isClickInCheckBoxOnly = false
        selectAll()

        setCellRenderer(
            treeCellRenderer { _, value, _, _, _, _, _ ->
                if (value is LogEventNode) {
                    val path = value.userObject
                    text = "${path.lastOrNull()} [${value.frequency}]"
                    toolTipText = value.name
                } else {
                    text = "(All)"
                }
                icon = null
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
                    is LogEventNode -> node.name
                    else -> ""
                }
            }
        }
    }

    val selectedNodes: List<LogEventNode>
        get() = checkBoxTreeSelectionModel.selectionPaths.flatMap {
            (it.lastPathComponent as AbstractTreeNode).depthFirstChildren().ifEmpty {
                sequenceOf(it.lastPathComponent)
            }
        }.filterIsInstance<LogEventNode>()
}
