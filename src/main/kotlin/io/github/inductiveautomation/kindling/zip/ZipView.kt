package io.github.inductiveautomation.kindling.zip

import com.formdev.flatlaf.extras.FlatSVGIcon
import com.formdev.flatlaf.extras.components.FlatPopupMenu
import com.formdev.flatlaf.extras.components.FlatTabbedPane
import io.github.inductiveautomation.kindling.core.Tool
import io.github.inductiveautomation.kindling.core.ToolOpeningException
import io.github.inductiveautomation.kindling.core.ToolPanel
import io.github.inductiveautomation.kindling.utils.Action
import io.github.inductiveautomation.kindling.utils.FileFilter
import io.github.inductiveautomation.kindling.utils.FlatScrollPane
import io.github.inductiveautomation.kindling.utils.PathNode
import io.github.inductiveautomation.kindling.utils.TabStrip
import io.github.inductiveautomation.kindling.utils.ZipFileTree
import io.github.inductiveautomation.kindling.utils.attachPopupMenu
import io.github.inductiveautomation.kindling.utils.getLogger
import io.github.inductiveautomation.kindling.utils.toFileSizeLabel
import io.github.inductiveautomation.kindling.zip.ZipViewer.createView
import io.github.inductiveautomation.kindling.zip.views.GenericFileView
import io.github.inductiveautomation.kindling.zip.views.ImageView
import io.github.inductiveautomation.kindling.zip.views.MultiToolView
import io.github.inductiveautomation.kindling.zip.views.PathView
import io.github.inductiveautomation.kindling.zip.views.ProjectView
import io.github.inductiveautomation.kindling.zip.views.TextFileView
import io.github.inductiveautomation.kindling.zip.views.ToolView
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.io.File
import java.nio.file.FileSystem
import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.spi.FileSystemProvider
import javax.swing.Icon
import javax.swing.JFileChooser
import javax.swing.JLabel
import javax.swing.JSplitPane
import javax.swing.tree.TreeSelectionModel
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.io.path.extension
import kotlin.io.path.fileSize
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.outputStream

class ZipView(path: Path) : ToolPanel("ins 6, flowy") {
    private val zipFile: FileSystem = FileSystems.newFileSystem(path)
    private val provider: FileSystemProvider = zipFile.provider()

    private val fileTree = ZipFileTree(zipFile).apply {
        selectionModel.selectionMode = TreeSelectionModel.CONTIGUOUS_TREE_SELECTION
    }

    private val bundleInfo = JLabel(
        when (path.extension.lowercase()) {
            "gwbk" -> {
                val backupInfo = provider.newInputStream(zipFile.getPath(BACKUP_INFO))
                val document = XML_FACTORY.newDocumentBuilder().parse(backupInfo).apply {
                    normalizeDocument()
                }
                val version = document.getElementsByTagName("version").item(0).textContent
                val edition = document.getElementsByTagName("edition").item(0)?.textContent

                buildString {
                    append(version)
                    if (!edition.isNullOrEmpty()) {
                        append(" - ")
                        append(edition)
                    }
                }
            }

            "modl" -> {
                val moduleInfo = provider.newInputStream(zipFile.getPath(MODULE_INFO))
                val document = XML_FACTORY.newDocumentBuilder().parse(moduleInfo).apply {
                    normalizeDocument()
                }
                val name = document.getElementsByTagName("name").item(0).textContent
                val version = document.getElementsByTagName("version").item(0).textContent

                "$name - $version"
            }

            else -> "ZIP Archive"
        } + " - " + path.fileSize().toFileSizeLabel(),
    )

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
                    val openIndividually = Action("Open File") {
                        for (treePath in selectedPaths) {
                            val actualPath = (treePath.lastPathComponent as PathNode).userObject
                            maybeAddNewTab(actualPath)
                        }
                    }
                    if (selectedPaths.size > 1) {
                        add(
                            Action("Open in new aggregate view") {
                                val actualPaths = Array(selectedPaths.size) {
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
                                exportFileChooser.selectedFile = File(selectedNode.userObject.name)
                                if (exportFileChooser.showSaveDialog(this@attachPopupMenu) == JFileChooser.APPROVE_OPTION) {
                                    provider.newInputStream(selectedNode.userObject).use { file ->
                                        exportFileChooser.selectedFile.toPath().outputStream().use(file::copyTo)
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }

        add(bundleInfo, "split 2, height 32!")
        add(
            JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT,
                FlatScrollPane((fileTree)),
                tabStrip,
            ).apply {
                dividerSize += 2
                isOneTouchExpandable = true
                dividerLocation = 250
            },
            "push, grow, span",
        )
    }

    override val icon: Icon = ZipViewer.icon

    private fun maybeAddNewTab(vararg paths: Path) {
        val pathList = paths.toList()
        val existingTab = tabStrip.tabs.find { tab -> tab.paths == pathList }
        if (existingTab == null) {
            val pathView = createView(provider, *paths)
            if (pathView != null) {
                tabStrip.addTab(component = pathView, select = true)
            }
        } else {
            tabStrip.selectedComponent = existingTab
        }
    }

    companion object Constants {
        const val BACKUP_INFO = "backupinfo.xml"
        const val MODULE_INFO = "module.xml"

        private val XML_FACTORY = DocumentBuilderFactory.newDefaultInstance().apply {
            setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            isXIncludeAware = false
            isExpandEntityReferences = false
        }
    }
}

private typealias PathPredicate = (Path) -> Boolean
private typealias PathViewProvider = (FileSystemProvider, Path) -> PathView?

object ZipViewer : Tool {
    override val title = "Ignition Archive"
    override val description = "Archives (.gwbk, .zip, .modl)"
    override val icon = FlatSVGIcon("icons/bx-archive.svg")
    override val filter = FileFilter(description, listOf("gwbk", "zip", "modl"))

    override fun open(path: Path): ToolPanel = ZipView(path)

    private val handlers: Map<PathPredicate, PathViewProvider> = buildMap {
        put(ToolView::maybeToolPath, ToolView::safelyCreate)
        put(TextFileView::isTextFile, ::TextFileView)
        put(ImageView::isImageFile, ::ImageView)
        put(ProjectView::isProjectDirectory, ::ProjectView)
        put(Path::isRegularFile, ::GenericFileView)
    }

    fun createView(filesystem: FileSystemProvider, vararg paths: Path): PathView? = runCatching {
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
