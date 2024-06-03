package io.github.inductiveautomation.kindling.idb.tagconfig

import com.formdev.flatlaf.extras.FlatSVGIcon
import com.jidesoft.swing.TreeSearchable
import io.github.inductiveautomation.kindling.idb.tagconfig.model.Node
import io.github.inductiveautomation.kindling.idb.tagconfig.model.TagProviderRecord
import io.github.inductiveautomation.kindling.utils.AbstractTreeNode
import io.github.inductiveautomation.kindling.utils.tag
import io.github.inductiveautomation.kindling.utils.treeCellRenderer
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreeNode
import javax.swing.tree.TreePath

@Suppress("unused")
class SortedLazyTagTreeNode(
    val name: String,
    val tagType: String?,
    originalNode: Node,
) : AbstractTreeNode() {
    val inferred = originalNode.inferredNode
    private val isProvider = originalNode.config.tagType == "Provider"
    val originalNode by lazy { originalNode }


    override val children: MutableList<TreeNode> = object : ArrayList<TreeNode>() {
        override fun add(element: TreeNode): Boolean {
            element as AbstractTreeNode
            element.parent = this@SortedLazyTagTreeNode
            return super.add(element)
        }
    }

    companion object {
        fun createRootNode(provider: TagProviderRecord): SortedLazyTagTreeNode {
            val rootNode = fromNode(provider.providerNode.value)

            if (provider.providerStatistics.totalOrphanedTags.value > 0) {
                val orphanedParentNode = fromNode(provider.orphanedParentNode)
                rootNode.children.add(1, orphanedParentNode)
            }

            return rootNode
        }

        private fun fromNode(node: Node): SortedLazyTagTreeNode {
            return SortedLazyTagTreeNode(
                name = if (node.config.name.isNullOrEmpty()) {
                    node.name.toString()
                } else {
                    node.config.name
                },
                tagType = node.config.tagType,
                originalNode = node,
            ).apply {
                children.addAll(node.config.tags.map(::fromNode).sortedBy { it.name })
            }
        }
    }
}

class TagBrowseTree : JTree(NO_SELECTION) {
    var provider: TagProviderRecord? = null
        set(value) {
            field = value
            model = if (value == null) {
                DefaultTreeModel(NO_SELECTION)
            } else {
                DefaultTreeModel(SortedLazyTagTreeNode.createRootNode(value))
            }
        }

    init {
        isRootVisible = false
        setShowsRootHandles(true)

        setCellRenderer(
            treeCellRenderer { _, value, _, expanded, _, _, _ ->
                val actualValue = value as? SortedLazyTagTreeNode

                text = if (actualValue?.inferred == true) {
                    buildString {
                        tag("html") {
                            tag("i") {
                                append("${actualValue.name}*")
                            }
                        }
                    }
                } else {
                    actualValue?.name.toString()
                }

                icon = when (actualValue?.tagType) {
                    "AtomicTag" -> TAG_ICON
                    "UdtInstance", "UdtType" -> UDT_ICON
                    else -> {
                        if (expanded) FOLDER_OPEN_ICON else FOLDER_CLOSED_ICON
                    }
                }

                this
            }
        )

        object : TreeSearchable(this) {
            init {
                isRecursive = true
                isRepeats = true
            }

            // Returns full tag path without provider name. (path/to/tag)
            override fun convertElementToString(element: Any?): String {
                val path = (element as? TreePath)?.path ?: return ""
                return path.asList().subList(1, path.size).joinToString("/") {
                    (it as SortedLazyTagTreeNode).name
                }
            }
        }
    }

    companion object {
        private val NO_SELECTION = DefaultMutableTreeNode("Select a Tag Provider to Browse")

        private const val ICON_SIZE = 18

        val UDT_ICON: FlatSVGIcon = FlatSVGIcon("icons/bx-vector.svg").derive(ICON_SIZE, ICON_SIZE)
        val TAG_ICON: FlatSVGIcon = FlatSVGIcon("icons/bx-purchase-tag.svg").derive(ICON_SIZE, ICON_SIZE)
        val FOLDER_CLOSED_ICON: FlatSVGIcon = FlatSVGIcon("icons/bx-folder.svg").derive(ICON_SIZE, ICON_SIZE)
        val FOLDER_OPEN_ICON: FlatSVGIcon = FlatSVGIcon("icons/bx-folder-open.svg").derive(ICON_SIZE, ICON_SIZE)

        fun TreePath.toTagPath(): String {
            val provider = "[${(path.first() as SortedLazyTagTreeNode).name}]"
            val tagPath = path.asList().subList(1, path.size).joinToString("/") {
                (it as SortedLazyTagTreeNode).name
            }
            return "$provider$tagPath"
        }
    }
}
