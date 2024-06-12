
package io.github.inductiveautomation.kindling.utils

import com.jidesoft.comparator.AlphanumComparator
import io.github.inductiveautomation.kindling.core.Tool
import java.nio.file.FileSystem
import java.nio.file.Path
import javax.swing.JTree
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreeModel
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.PathWalkOption.INCLUDE_DIRECTORIES
import kotlin.io.path.div
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.walk

data class PathNode(override val userObject: Path) : TypedTreeNode<Path>()

@OptIn(ExperimentalPathApi::class)
class RootNode(zipFile: FileSystem) : AbstractTreeNode() {
    init {
        val pathComparator = compareBy<Path> { it.isDirectory() }.thenBy(AlphanumComparator()) { it.name }
        val zipFilePaths = zipFile.rootDirectories.asSequence()
            .flatMap { it.walk(INCLUDE_DIRECTORIES) }
            .sortedWith(pathComparator)

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
    }
}

class ZipFileModel(fileSystem: FileSystem) : DefaultTreeModel(RootNode(fileSystem))

class ZipFileTree(fileSystem: FileSystem) : JTree(ZipFileModel(fileSystem)) {
    init {
        isRootVisible = false
        setShowsRootHandles(true)

        setCellRenderer(
            treeCellRenderer { _, value, selected, _, _, _, _ ->
                if (value is PathNode) {
                    val path = value.userObject
                    toolTipText = path.toString()
                    text = path.last().toString()
                    icon = if (path.isRegularFile()) {
                        Tool.find(path)?.icon?.asActionIcon(selected) ?: icon
                    } else {
                        icon
                    }
                }
                this
            },
        )
    }

    override fun getModel(): ZipFileModel? = super.getModel() as ZipFileModel?
    override fun setModel(newModel: TreeModel?) {
        newModel as ZipFileModel
        super.setModel(newModel)
    }
}
