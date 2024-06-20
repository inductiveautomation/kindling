package io.github.inductiveautomation.kindling.idb.metrics

import com.formdev.flatlaf.extras.FlatSVGIcon
import com.jidesoft.swing.CheckBoxTree
import io.github.inductiveautomation.kindling.utils.AbstractTreeNode
import io.github.inductiveautomation.kindling.utils.TypedTreeNode
import io.github.inductiveautomation.kindling.utils.asActionIcon
import io.github.inductiveautomation.kindling.utils.expandAll
import io.github.inductiveautomation.kindling.utils.selectAll
import io.github.inductiveautomation.kindling.utils.treeCellRenderer
import javax.swing.tree.DefaultTreeModel

data class MetricNode(override val userObject: List<String>) : TypedTreeNode<List<String>>() {
    constructor(vararg parts: String) : this(parts.toList())

    val name by lazy { userObject.joinToString(".") }
}

class RootNode(metrics: List<Metric>) : AbstractTreeNode() {
    init {
        val legacy = MetricNode("Legacy")
        val modern = MetricNode("New")

        val seen = mutableMapOf<List<String>, MetricNode>()
        for (metric in metrics) {
            var lastSeen = if (metric.isLegacy) legacy else modern
            val currentLeadingPath = mutableListOf(lastSeen.name)
            for (part in metric.name.split('.')) {
                currentLeadingPath.add(part)
                val next = seen.getOrPut(currentLeadingPath.toList()) {
                    val newChild = MetricNode(currentLeadingPath.drop(1))
                    lastSeen.children.add(newChild)
                    newChild
                }
                lastSeen = next
            }
        }

        when {
            legacy.childCount == 0 && modern.childCount > 0 -> {
                for (zoomer in modern.children) {
                    children.add(zoomer)
                }
            }

            modern.childCount == 0 && legacy.childCount > 0 -> {
                for (boomer in legacy.children) {
                    children.add(boomer)
                }
            }

            else -> {
                children.add(legacy)
                children.add(modern)
            }
        }
    }

    private val Metric.isLegacy: Boolean
        get() = name.first().isUpperCase()
}

class MetricTree(metrics: List<Metric>) : CheckBoxTree(DefaultTreeModel(RootNode(metrics))) {
    init {
        isRootVisible = false
        setShowsRootHandles(true)

        expandAll()
        selectAll()

        setCellRenderer(
            treeCellRenderer { _, value, selected, _, _, _, _ ->
                if (value is MetricNode) {
                    val path = value.userObject
                    text = path.last()
                    toolTipText = value.name
                    icon = CHART_ICON.asActionIcon(selected)
                } else {
                    icon = null
                }
                this
            },
        )
    }

    val selectedLeafNodes: List<MetricNode>
        get() = checkBoxTreeSelectionModel.selectionPaths.flatMap {
            (it.lastPathComponent as MetricNode).depthFirstChildren()
        }.filterIsInstance<MetricNode>()

    companion object {
        private val CHART_ICON = FlatSVGIcon("icons/bx-line-chart.svg")
    }
}
