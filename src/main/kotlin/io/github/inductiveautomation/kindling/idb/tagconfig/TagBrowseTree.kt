package io.github.inductiveautomation.kindling.idb.tagconfig

import com.formdev.flatlaf.extras.FlatSVGIcon
import com.jidesoft.swing.TreeSearchable
import io.github.inductiveautomation.kindling.idb.tagconfig.model.Node
import io.github.inductiveautomation.kindling.idb.tagconfig.model.TagProviderRecord
import io.github.inductiveautomation.kindling.utils.EDT_SCOPE
import io.github.inductiveautomation.kindling.utils.tag
import io.github.inductiveautomation.kindling.utils.treeCellRenderer
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TagBrowseTree : JTree(NO_SELECTION) {
    var provider: TagProviderRecord? = null
        set(value) {
            field = value

            if (value == null) {
                model = DefaultTreeModel(NO_SELECTION)
                return
            }

            EDT_SCOPE.launch {
                model = DefaultTreeModel(NO_SELECTION)
                val providerNode = withContext(Dispatchers.Default) { value.getProviderNode().await() }
                model = DefaultTreeModel(providerNode)
            }
        }

    init {
        isRootVisible = false
        setShowsRootHandles(true)

        setCellRenderer(
            treeCellRenderer { _, value, _, expanded, _, _, _ ->
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
                    (it as Node).actualName
                }
            }
        }
    }

    companion object {
        private val NO_SELECTION = DefaultMutableTreeNode("Select a Tag Provider to Browse")

        private const val ICON_SIZE = 18

        private val UDT_ICON: FlatSVGIcon = FlatSVGIcon("icons/bx-vector.svg").derive(ICON_SIZE, ICON_SIZE)
        private val TAG_ICON: FlatSVGIcon = FlatSVGIcon("icons/bx-purchase-tag.svg").derive(ICON_SIZE, ICON_SIZE)
        private val FOLDER_CLOSED_ICON: FlatSVGIcon = FlatSVGIcon("icons/bx-folder.svg").derive(ICON_SIZE, ICON_SIZE)
        private val FOLDER_OPEN_ICON: FlatSVGIcon = FlatSVGIcon("icons/bx-folder-open.svg").derive(ICON_SIZE, ICON_SIZE)

        fun TreePath.toTagPath(): String {
            val provider = "[${(path.first() as Node).name}]"
            val tagPath = path.asList().subList(1, path.size).joinToString("/") {
                (it as Node).actualName
            }
            return "$provider$tagPath"
        }
    }
}
