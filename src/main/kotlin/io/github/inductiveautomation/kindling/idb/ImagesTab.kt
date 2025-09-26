package io.github.inductiveautomation.kindling.idb

import com.formdev.flatlaf.extras.FlatSVGIcon
import com.github.weisj.jsvg.parser.LoaderContext
import com.github.weisj.jsvg.parser.SVGLoader
import com.inductiveautomation.ignition.gateway.images.ImageFormat
import com.jidesoft.comparator.AlphanumComparator
import io.github.inductiveautomation.kindling.core.ToolPanel
import io.github.inductiveautomation.kindling.core.ToolPanel.Companion.exportFileChooser
import io.github.inductiveautomation.kindling.utils.AbstractTreeNode
import io.github.inductiveautomation.kindling.utils.Action
import io.github.inductiveautomation.kindling.utils.EDT_SCOPE
import io.github.inductiveautomation.kindling.utils.FileFilter
import io.github.inductiveautomation.kindling.utils.FlatActionIcon
import io.github.inductiveautomation.kindling.utils.FlatScrollPane
import io.github.inductiveautomation.kindling.utils.HorizontalSplitPane
import io.github.inductiveautomation.kindling.utils.TypedTreeNode
import io.github.inductiveautomation.kindling.utils.attachPopupMenu
import io.github.inductiveautomation.kindling.utils.get
import io.github.inductiveautomation.kindling.utils.render
import io.github.inductiveautomation.kindling.utils.toFileSizeLabel
import io.github.inductiveautomation.kindling.utils.toList
import io.github.inductiveautomation.kindling.utils.transferTo
import io.github.inductiveautomation.kindling.utils.treeCellRenderer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.miginfocom.swing.MigLayout
import java.awt.event.MouseEvent
import java.awt.image.BufferedImage
import java.io.File
import java.io.InputStream
import java.nio.file.FileSystems
import java.nio.file.Path
import java.sql.Connection
import javax.imageio.ImageIO
import javax.swing.Icon
import javax.swing.ImageIcon
import javax.swing.JButton
import javax.swing.JFileChooser
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.JTree
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreeSelectionModel
import kotlin.io.path.createDirectories
import kotlin.io.path.outputStream

class ImagesPanel(connection: IdbConnection) : ToolPanel("ins 0, fill, hidemode 3") {
    override val icon: Icon? = null

    private val tree = JTree(DefaultTreeModel(RootImageNode(connection.connection))).apply {
        isRootVisible = false
        showsRootHandles = true
        selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION

        cellRenderer = treeCellRenderer { _, value, _, _, _, _, _ ->
            when (value) {
                is ImageNode -> {
                    text = value.userObject.path.substringAfterLast('/')
                    toolTipText = value.userObject.description
                    icon = FlatActionIcon("icons/bx-image.svg")
                }

                is ImageFolderNode -> {
                    text = value.userObject.substringAfterLast('/')
                }
            }
            this
        }

        attachPopupMenu { event: MouseEvent ->
            when (val node = getPathForLocation(event.x, event.y)?.lastPathComponent) {
                is ImageNode -> {
                    JPopupMenu().apply {
                        add(saveAction(node.userObject))
                    }
                }

                is ImageFolderNode -> {
                    JPopupMenu().apply {
                        add(
                            saveFolderAction(node),
                        )
                    }
                }

                else -> null
            }
        }
    }

    private val imageDisplay = JLabel()
    private val imageInfo = JLabel()

    private val exportPrefix = connection.systemName?.plus("_").orEmpty()

    private val exportAllAction = Action(
        name = "Save All Images",
        icon = FlatActionIcon("icons/bx-download.svg"),
    ) {
        showSaveDialog(
            "ZIP files",
            "zip",
            "${exportPrefix}images.zip",
        ) { destination ->
            (tree.model.root as RootImageNode).exportToZip(destination)
        }
    }

    private val header = JPanel(MigLayout("ins 0")).apply {
        add(imageInfo, "pushx, growx")
    }

    init {
        add(
            HorizontalSplitPane(
                JPanel(MigLayout("ins 0, fill")).apply {
                    add(FlatScrollPane(tree), "push, grow, wrap")
                    add(JButton(exportAllAction))
                },
                JPanel(MigLayout("ins 0, fill")).apply {
                    add(header, "pushx, growx, wrap, gapleft 6")
                    add(FlatScrollPane(imageDisplay), "push, grow")
                },
                resizeWeight = 0.2,
            ),
            "push, grow",
        )

        tree.addTreeSelectionListener { event ->
            when (val node = event.newLeadSelectionPath?.lastPathComponent) {
                is ImageNode -> {
                    EDT_SCOPE.async {
                        val imageRow = node.userObject

                        imageDisplay.icon = throbber
                        imageInfo.text = "${imageRow.path} [...]"

                        val icon = withContext(Dispatchers.IO) {
                            imageRow.image?.let(::ImageIcon)
                        }

                        val fileSize = withContext(Dispatchers.IO) {
                            imageRow.data.size.toLong().toFileSizeLabel()
                        }

                        imageDisplay.icon = icon
                        imageInfo.text = buildString {
                            append(imageRow.path)
                            imageRow.description?.let { append(" ($it)") }
                            append(" [").append(fileSize).append("]")
                        }
                    }.invokeOnCompletion {
                        if (it != null) {
                            imageDisplay.icon = null
                            imageInfo.text = "Error loading image: ${it.message}"
                            it.printStackTrace(System.err)
                        }
                    }
                }

                is ImageFolderNode -> {
                    imageDisplay.icon = null
                    imageInfo.text = buildString {
                        append(node.userObject)
                        append(" [").append(node.depthFirstChildren().count { it is ImageNode }).append(" images]")
                    }
                }
            }
        }
    }

    private fun saveAction(image: ImageRow) = Action(
        name = "Save",
        description = "Save to File",
        icon = FlatActionIcon("icons/bx-save.svg"),
    ) {
        val imageName = image.path.substringAfterLast('/')
        val extension = imageName.substringAfterLast('.')

        showSaveDialog(
            "$extension images",
            extension,
            "$exportPrefix$imageName",
        ) { destination ->
            image.inputStream() transferTo destination.outputStream()
        }
    }

    private fun saveFolderAction(node: ImageFolderNode): Action = Action("Save Folder", icon = FlatActionIcon("icons/bx-download.svg")) {
        showSaveDialog(
            "ZIP files",
            "zip",
            "$exportPrefix${node.userObject.replace('/', '_')}.zip",
        ) { destination ->
            node.exportToZip(destination)
        }
    }

    companion object {
        private val throbber = FlatSVGIcon("icons/bx-loader-circle.svg").derive(50, 50)
    }
}

private data class ImageNode(override val userObject: ImageRow) : TypedTreeNode<ImageRow>()

private class ImageRow(
    val path: String,
    val type: ImageFormat,
    val description: String?,
    val connection: Connection,
) {
    override fun toString(): String = "ImageRow(path='$path', type=$type, description=$description)"

    fun inputStream(): InputStream = connection.prepareStatement(
        "SELECT data FROM images WHERE path = ?",
    ).apply {
        setString(1, path)
    }.executeQuery().use { rs ->
        rs.next()
        rs.getBinaryStream("data")
    }

    val data: ByteArray by lazy {
        inputStream().use { it.readBytes() }
    }

    val image: BufferedImage? by lazy {
        if (type == ImageFormat.SVG) {
            return@lazy SVGLoader().load(data.inputStream(), null, LoaderContext.createDefault())?.let { svg ->
                val size = svg.size()
                svg.render(size.width.toInt().coerceAtLeast(20), size.height.toInt().coerceAtLeast(20))
            }
        }
        val readers = ImageIO.getImageReadersByFormatName(type.name)
        readers.forEach { reader ->
            ImageIO.createImageInputStream(data.inputStream()).use { iis ->
                reader.input = iis
                return@lazy reader.read(0)
            }
        }
        null
    }
}

private data class ImageFolderNode(override val userObject: String) : TypedTreeNode<String>()

class RootImageNode(connection: Connection) : AbstractTreeNode() {
    private val listAll = connection.prepareStatement(
        """
            SELECT path, type, description
            FROM images
            WHERE type IS NOT NULL
            ORDER BY path
        """.trimIndent(),
    )

    init {
        val images = listAll.use {
            it.executeQuery().toList { rs ->
                ImageRow(
                    rs["path"],
                    rs.get<String>("type").let(ImageFormat::valueOf),
                    rs.get<String?>("description")?.takeIf(String::isNotEmpty),
                    connection,
                )
            }
        }.sortedWith(compareBy(AlphanumComparator(false)) { it.path })

        val seenFolders = mutableMapOf<String, AbstractTreeNode>()

        for (row in images) {
            val pathParts = row.path.split('/')
            var currentPath = ""
            var parentNode: AbstractTreeNode = this

            for (i in 0 until pathParts.size - 1) {
                val part = pathParts[i]
                currentPath = if (currentPath.isEmpty()) part else "$currentPath/$part"
                parentNode = seenFolders.getOrPut(currentPath) {
                    val folderNode = ImageFolderNode(currentPath)
                    parentNode.children.add(folderNode)
                    folderNode
                }
            }
            parentNode.children.add(ImageNode(row))
        }
    }
}

private fun AbstractTreeNode.exportToZip(destination: Path) {
    val basePath = (this as? ImageFolderNode)?.userObject?.let { "$it/" }.orEmpty()

    FileSystems.newFileSystem(
        destination,
        mapOf("create" to "true"),
    ).use { zipFs ->
        val rootInZip = zipFs.getPath("/")

        val imageNodes = depthFirstChildren().filterIsInstance<ImageNode>()

        for (imageNode in imageNodes) {
            val imageRow = imageNode.userObject
            val relativePath = imageRow.path.removePrefix(basePath)
            val pathInZip = rootInZip.resolve(relativePath)

            pathInZip.parent?.createDirectories()

            imageRow.inputStream() transferTo pathInZip.outputStream()
        }
    }
}

private val BACKGROUND = CoroutineScope(Dispatchers.Default)

private fun ToolPanel.showSaveDialog(
    description: String,
    extension: String,
    suggestedName: String,
    handler: suspend (Path) -> Unit,
) {
    val chooser = exportFileChooser.apply {
        resetChoosableFileFilters()
        fileFilter = FileFilter(description, extension)
        selectedFile = File(suggestedName)
    }

    if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
        val selectedPath = chooser.selectedFile.toPath()
        BACKGROUND.launch {
            handler(selectedPath)
        }
    }
}
