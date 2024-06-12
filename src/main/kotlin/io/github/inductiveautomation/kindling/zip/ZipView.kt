package io.github.inductiveautomation.kindling.zip

import com.formdev.flatlaf.FlatClientProperties.TABBED_PANE_TAB_CLOSABLE
import com.formdev.flatlaf.extras.FlatSVGIcon
import com.formdev.flatlaf.extras.components.FlatPopupMenu
import com.formdev.flatlaf.extras.components.FlatTabbedPane
import io.github.inductiveautomation.kindling.core.Tool
import io.github.inductiveautomation.kindling.core.ToolOpeningException
import io.github.inductiveautomation.kindling.core.ToolPanel
import io.github.inductiveautomation.kindling.utils.Action
import io.github.inductiveautomation.kindling.utils.FileFilter
import io.github.inductiveautomation.kindling.utils.FlatScrollPane
import io.github.inductiveautomation.kindling.utils.HorizontalSplitPane
import io.github.inductiveautomation.kindling.utils.PathNode
import io.github.inductiveautomation.kindling.utils.TabStrip
import io.github.inductiveautomation.kindling.utils.ZipFileTree
import io.github.inductiveautomation.kindling.utils.attachPopupMenu
import io.github.inductiveautomation.kindling.utils.getLogger
import io.github.inductiveautomation.kindling.utils.transferTo
import io.github.inductiveautomation.kindling.zip.ZipViewer.createView
import io.github.inductiveautomation.kindling.zip.views.FileView
import io.github.inductiveautomation.kindling.zip.views.ImageView
import io.github.inductiveautomation.kindling.zip.views.MultiToolView
import io.github.inductiveautomation.kindling.zip.views.PathView
import io.github.inductiveautomation.kindling.zip.views.ProjectView
import io.github.inductiveautomation.kindling.zip.views.ToolView
import io.github.inductiveautomation.kindling.zip.views.gwbk.GwbkStatsView
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.io.File
import java.nio.file.FileSystem
import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.spi.FileSystemProvider
import javax.swing.Icon
import javax.swing.JFileChooser
import javax.swing.tree.TreeSelectionModel.CONTIGUOUS_TREE_SELECTION
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import kotlin.io.path.name

class ZipView(path: Path) : ToolPanel("ins 6, flowy") {
    private val zipFile: FileSystem = FileSystems.newFileSystem(path)
    private val provider: FileSystemProvider = zipFile.provider()

    private val fileTree = ZipFileTree(zipFile).apply {
        selectionModel.selectionMode = CONTIGUOUS_TREE_SELECTION
    }

    private val tabStrip = TabStrip()

    private val FlatTabbedPane.tabs: Sequence<PathView>
        get() = sequence {
            repeat(tabCount) { i ->
                yield(getComponentAt(i) as PathView)
            }
        }

    init {
        name = path.name
        toolTipText = path.toString()

        fileTree.addMouseListener(
            object : MouseAdapter() {
                override fun mousePressed(e: MouseEvent?) {
                    if (e?.clickCount == 2) {
                        val pathNode = fileTree.selectionPath?.lastPathComponent as? PathNode ?: return
                        val actualPath = pathNode.userObject
                        maybeAddNewTab(actualPath)
                    }
                }
            },
        )

        fileTree.attachPopupMenu {
            selectionPaths?.let { selectedPaths ->
                FlatPopupMenu().apply {
                    val openIndividually =
                        Action("Open File") {
                            for (treePath in selectedPaths) {
                                val actualPath = (treePath.lastPathComponent as PathNode).userObject
                                maybeAddNewTab(actualPath)
                            }
                        }
                    if (selectedPaths.size > 1) {
                        add(
                            Action("Open in new aggregate view") {
                                val actualPaths =
                                    Array(selectedPaths.size) {
                                        (selectedPaths[it].lastPathComponent as PathNode).userObject
                                    }
                                maybeAddNewTab(*actualPaths)
                            },
                        )
                        openIndividually.name = "Open ${selectedPaths.size} files individually"
                    }
                    add(openIndividually)

                    val selectedNode = selectedPaths.first().lastPathComponent as PathNode

                    if (selectedPaths.size == 1 && selectedNode.isLeaf) {
                        add(
                            Action("Save As") {
                                exportFileChooser.apply {
                                    resetChoosableFileFilters()
                                    selectedFile = File(selectedNode.userObject.name)
                                    if (showSaveDialog(this@attachPopupMenu) == JFileChooser.APPROVE_OPTION) {
                                        provider.newInputStream(selectedNode.userObject) transferTo selectedFile.outputStream()
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }

        add(
            HorizontalSplitPane(
                FlatScrollPane((fileTree)),
                tabStrip,
                0.1,
            ),
            "push, grow, span",
        )

        if (path.extension.lowercase() == "gwbk") {
            maybeAddNewTab(path)
        }
    }

    override val icon: Icon = ZipViewer.icon

    private fun maybeAddNewTab(vararg paths: Path) {
        val pathList = paths.toList()
        val existingTab = tabStrip.tabs.find { tab -> tab.paths == pathList }
        if (existingTab == null) {
            val pathView = createView(provider, *paths)
            if (pathView != null) {
                pathView.putClientProperty(TABBED_PANE_TAB_CLOSABLE, pathView.closable)
                tabStrip.addTab(component = pathView, select = true)
            }
        } else {
            tabStrip.selectedComponent = existingTab
        }
    }
}

private typealias PathPredicate = (Path) -> Boolean
private typealias PathViewProvider = (FileSystemProvider, Path) -> PathView?

object ZipViewer : Tool {
    override val serialKey = "zip-viewer"
    override val title = "ZIP Archive"
    override val description = "ZIP Archive (.gwbk, .zip, .modl)"
    override val icon = FlatSVGIcon("icons/bx-archive.svg")
    override val filter = FileFilter(description, "gwbk", "zip", "modl", "jar")

    override fun open(path: Path): ToolPanel = ZipView(path)

    private val handlers: Map<PathPredicate, PathViewProvider> = buildMap {
        put(GwbkStatsView::isGatewayBackup, ::GwbkStatsView)
        put(ToolView::maybeToolPath, ToolView::safelyCreate)
        put(ImageView::isImageFile, ::ImageView)
        put(ProjectView::isProjectDirectory, ::ProjectView)
        put(Path::isRegularFile, ::FileView)
    }

    fun createView(
        filesystem: FileSystemProvider,
        vararg paths: Path,
    ): PathView? =
        runCatching {
            if (paths.size > 1) {
                MultiToolView(filesystem, paths.toList())
            } else {
                val path = paths.single()
                handlers.firstNotNullOfOrNull { (predicate, provider) ->
                    try {
                        provider.takeIf { predicate(path) }?.invoke(filesystem, path)
                    } catch (_: ToolOpeningException) {
                        null
                    }
                }
            }
        }.getOrElse { ex ->
            logger.error("Failed to open ${paths.contentToString()}", ex)
            null
        }

    private val logger = getLogger<ZipViewer>()
}
