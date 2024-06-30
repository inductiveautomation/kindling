package io.github.inductiveautomation.kindling.idb.tagconfig

import com.formdev.flatlaf.extras.FlatSVGIcon
import com.jidesoft.swing.TreeSearchable
import io.github.inductiveautomation.kindling.idb.tagconfig.model.Node
import io.github.inductiveautomation.kindling.idb.tagconfig.model.TagProviderRecord
import io.github.inductiveautomation.kindling.utils.EDT_SCOPE
import io.github.inductiveautomation.kindling.utils.asActionIcon
import io.github.inductiveautomation.kindling.utils.tag
import io.github.inductiveautomation.kindling.utils.treeCellRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath
import kotlin.properties.Delegates

class TagBrowseTree : JTree(NO_SELECTION) {
    var provider: TagProviderRecord? by Delegates.observable(null) { _, _, newValue ->
        if (newValue == null) {
            model = NO_SELECTION
        } else {
            EDT_SCOPE.launch {
                model = NO_SELECTION
                val providerNode = withContext(Dispatchers.Default) {
                    newValue.getProviderNode().await()
                }
                model = DefaultTreeModel(providerNode)
            }
        }
    }

    init {
        isRootVisible = false
        setShowsRootHandles(true)

        setCellRenderer(
            treeCellRenderer { _, value, selected, expanded, _, _, _ ->
                val actualValue = value as? Node

                text = if (actualValue?.inferredNode == true) {
                    buildString {
                        tag("html") {
                            tag("i") {
                                append("${actualValue.actualName}*")
                            }
                        }
                    }
                } else {
                    actualValue?.actualName
                }

                icon = when (actualValue?.config?.tagType) {
                    "AtomicTag" -> TAG_ICON
                    "UdtInstance", "UdtType" -> UDT_ICON
                    else -> if (expanded) {
                        FOLDER_OPEN_ICON
                    } else {
                        FOLDER_CLOSED_ICON
                    }
                }.asActionIcon(selected)

                this
            },
        )

        object : TreeSearchable(this) {
            init {
                isRecursive = true
                isRepeats = true
            }

            // Returns full tag path without provider name. (path/to/tag)
            override fun convertElementToString(element: Any?): String {
                val path = (element as? TreePath)?.path ?: return ""
                return (1..path.lastIndex).joinToString("/") {
                    (path[it] as Node).actualName
                }
            }
        }
    }

    companion object {
        private val NO_SELECTION = DefaultTreeModel(DefaultMutableTreeNode("Select a Tag Provider to Browse"))

        private val UDT_ICON: FlatSVGIcon = FlatSVGIcon("icons/bx-vector.svg")
        private val TAG_ICON: FlatSVGIcon = FlatSVGIcon("icons/bx-purchase-tag.svg")
        private val FOLDER_CLOSED_ICON: FlatSVGIcon = FlatSVGIcon("icons/bx-folder.svg")
        private val FOLDER_OPEN_ICON: FlatSVGIcon = FlatSVGIcon("icons/bx-folder-open.svg")

        fun TreePath.toTagPath(): String {
            val provider = "[${(path.first() as Node).name}]"
            val tagPath = (1 until path.size).joinToString("/") {
                (path[it] as Node).actualName
            }
            return "$provider$tagPath"
        }
    }
}
