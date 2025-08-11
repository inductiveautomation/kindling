package io.github.inductiveautomation.kindling.utils

import com.jidesoft.comparator.AlphanumComparator
import com.jidesoft.swing.TreeSearchable
import io.github.inductiveautomation.kindling.core.Tool
import java.nio.file.FileSystem
import java.nio.file.Path
import javax.swing.JTree
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreeModel
import javax.swing.tree.TreeNode
import javax.swing.tree.TreePath
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.PathWalkOption.INCLUDE_DIRECTORIES
import kotlin.io.path.div
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.walk

data class PathNode(override val userObject: Path) : TypedTreeNode<Path>() {
    override fun isLeaf(): Boolean {
        return super.isLeaf() || !userObject.isDirectory()
    }
}

@OptIn(ExperimentalPathApi::class)
class RootNode(zipFile: FileSystem) : AbstractTreeNode() {
    init {
        val zipFilePaths = zipFile.rootDirectories.asSequence()
            .flatMap { it.walk(INCLUDE_DIRECTORIES) }

        val seen = mutableMapOf<Path, PathNode>()
        for (path in zipFilePaths) {
            var lastSeen: AbstractTreeNode = this
            var currentDepth = zipFile.getPath("/")
            for (part in path) {
                currentDepth /= part
                val next = seen.getOrPut(currentDepth) {
                    val newChild = PathNode(currentDepth)
                    lastSeen.children.add(newChild)
                    newChild
                }
                lastSeen = next
            }
        }

        sortWith(comparator, recursive = true)
    }

    companion object {
        private val comparator = compareBy<TreeNode> { node ->
            node as AbstractTreeNode
            val isDir = node.children.isNotEmpty() || (node as? PathNode)?.userObject?.isDirectory() == true
            !isDir
        }.thenBy(AlphanumComparator(false)) { node ->
            val path = (node as? PathNode)?.userObject
            path?.name.orEmpty()
        }
    }
}

class ZipFileModel(fileSystem: FileSystem) : DefaultTreeModel(RootNode(fileSystem))

class ZipFileTree(fileSystem: FileSystem) : JTree(ZipFileModel(fileSystem)) {
    init {
        isRootVisible = false
        setShowsRootHandles(true)

        setCellRenderer(
            treeCellRenderer { _, value, _, _, _, _, _ ->
                if (value is PathNode) {
                    val path = value.userObject
                    toolTipText = path.toString()
                    text = path.name
                    icon = if (path.isRegularFile()) {
                        Tool.find(path)?.icon?.derive(ACTION_ICON_SCALE_FACTOR) ?: icon
                    } else {
                        icon
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

            override fun convertElementToString(element: Any?): String {
                return when (val node = (element as? TreePath)?.lastPathComponent) {
                    is PathNode -> node.userObject.name
                    else -> ""
                }
            }
        }
    }

    override fun getModel(): ZipFileModel? = super.getModel() as ZipFileModel?
    override fun setModel(newModel: TreeModel?) {
        newModel as ZipFileModel
        super.setModel(newModel)
    }
}
