package io.github.inductiveautomation.kindling.tagconfig

import com.jidesoft.swing.TreeSearchable
import io.github.inductiveautomation.kindling.tagconfig.model.AbstractTagProvider
import io.github.inductiveautomation.kindling.tagconfig.model.Node
import io.github.inductiveautomation.kindling.utils.EDT_SCOPE
import io.github.inductiveautomation.kindling.utils.FlatActionIcon
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
    var provider: AbstractTagProvider? by Delegates.observable(null) { _, _, newValue ->
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
            treeCellRenderer { _, value, _, _, _, _, _ ->
                val actualValue = value as? Node

                text = if (actualValue?.inferred == true) {
                    buildString {
                        tag("html") {
                            tag("i") {
                                append("${actualValue.name}*")
                            }
                        }
                    }
                } else {
                    actualValue?.name
                }

                when (actualValue?.config?.tagType) {
                    "AtomicTag" -> {
                        icon = FlatActionIcon("icons/bx-purchase-tag.svg")
                    }
                    "UdtInstance", "UdtType" -> {
                        icon = FlatActionIcon("icons/bx-vector.svg")
                    }
                }

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
                    (path[it] as Node).name
                }
            }
        }
    }

    companion object {
        private val NO_SELECTION = DefaultTreeModel(DefaultMutableTreeNode("Select a Tag Provider to Browse"))
    }
}
