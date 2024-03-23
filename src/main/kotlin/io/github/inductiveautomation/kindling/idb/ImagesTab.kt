package io.github.inductiveautomation.kindling.idb

import com.formdev.flatlaf.extras.FlatSVGIcon
import com.github.weisj.jsvg.parser.SVGLoader
import com.inductiveautomation.ignition.gateway.images.ImageFormat
import io.github.inductiveautomation.kindling.core.ToolPanel
import io.github.inductiveautomation.kindling.utils.AbstractTreeNode
import io.github.inductiveautomation.kindling.utils.Action
import io.github.inductiveautomation.kindling.utils.FlatScrollPane
import io.github.inductiveautomation.kindling.utils.HorizontalSplitPane
import io.github.inductiveautomation.kindling.utils.TypedTreeNode
import io.github.inductiveautomation.kindling.utils.asActionIcon
import io.github.inductiveautomation.kindling.utils.attachPopupMenu
import io.github.inductiveautomation.kindling.utils.get
import io.github.inductiveautomation.kindling.utils.render
import io.github.inductiveautomation.kindling.utils.toFileSizeLabel
import io.github.inductiveautomation.kindling.utils.toList
import io.github.inductiveautomation.kindling.utils.treeCellRenderer
import net.miginfocom.swing.MigLayout
import java.awt.event.MouseEvent
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Path
import java.sql.Connection
import javax.imageio.ImageIO
import javax.swing.Icon
import javax.swing.ImageIcon
import javax.swing.JFileChooser
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.JTree
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreeSelectionModel
import kotlin.io.path.createDirectories
import kotlin.io.path.writeBytes

class ImagesPanel(connection: Connection) : ToolPanel("ins 0, fill, hidemode 3") {
    override val icon: Icon? = null

    private val tree = JTree(DefaultTreeModel(RootImageNode(connection))).apply {
        isRootVisible = false
        showsRootHandles = true
        selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION

        cellRenderer = treeCellRenderer { _, value, selected, _, _, _, _ ->
            when (value) {
                is ImageNode -> {
                    text = value.userObject.path.substringAfterLast('/')
                    toolTipText = value.userObject.description
                    icon = FlatSVGIcon("icons/bx-image.svg").asActionIcon(selected)
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
                            Action("Save Folder") {
                                exportFileChooser.apply {
                                    resetChoosableFileFilters()
                                    selectedFile = File(node.userObject.replace('/', '_') + ".zip")
                                    if (showSaveDialog(this@ImagesPanel) == JFileChooser.APPROVE_OPTION) {
                                        node.toZipFile(selectedFile.toPath())
                                    }
                                }
                            },
                        )
                    }
                }

                else -> null
            }
        }
    }

    private fun ImageFolderNode.toZipFile(destination: Path) {
        FileSystems.newFileSystem(destination, mapOf("create" to "true")).use { zipFile ->
            val root = zipFile.getPath("/")
            fun addChildren(node: ImageFolderNode, parent: Path = root) {
                val newParentPath = parent.resolve(node.userObject.substringAfterLast('/'))
                newParentPath.createDirectories()
                for (child in node.children) {
                    when (child) {
                        is ImageNode -> newParentPath.resolve(child.userObject.path.substringAfterLast('/')).writeBytes(child.userObject.data)
                        is ImageFolderNode -> addChildren(child, newParentPath)
                    }
                }
            }
            addChildren(this)
        }
    }

    private val imageDisplay = JLabel()
    private val imageInfo = JLabel()

    private val header = JPanel(MigLayout("ins 0")).apply {
        add(imageInfo, "pushx, growx")
    }

    init {
        add(
            HorizontalSplitPane(
                FlatScrollPane(tree),
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
                    val imageRow = node.userObject
                    imageDisplay.icon = imageRow.image?.let(::ImageIcon)

                    imageInfo.text = buildString {
                        append(imageRow.path)
                        if (imageRow.description != null) {
                            append(" (").append(imageRow.description).append(")")
                        }
                        append(" [").append(imageRow.data.size.toLong().toFileSizeLabel()).append("]")
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

        tree.attachPopupMenu { event ->
            val path = getClosestPathForLocation(event.x, event.y)
            when (val node = path?.lastPathComponent) {
                is ImageNode -> {
                    JPopupMenu().apply {
                        add(saveAction(node.userObject))
                    }
                }

                is ImageFolderNode -> {
                    // show prompt to save the entire folder as a zip file
                    null
                }

                else -> null
            }
        }
    }

    private fun saveAction(image: ImageRow) = Action(
        name = "Save",
        description = "Save to File",
        icon = FlatSVGIcon("icons/bx-save.svg"),
    ) {
        exportFileChooser.apply {
            resetChoosableFileFilters()
            selectedFile = File(image.path.substringAfterLast('/'))
            if (showSaveDialog(this@ImagesPanel) == JFileChooser.APPROVE_OPTION) {
                selectedFile.writeBytes(image.data)
            }
        }
    }
}

private data class ImageNode(override val userObject: ImageRow) : TypedTreeNode<ImageRow>()

private class ImageRow(
    val path: String,
    val type: ImageFormat,
    val description: String?,
    val connection: Connection,
) {
    override fun toString(): String {
        return "ImageRow(path='$path', type=$type, description=$description)"
    }

    val data: ByteArray by lazy {
        connection.prepareStatement(
            "SELECT data FROM images WHERE path = ?",
        ).apply {
            setString(1, path)
        }.executeQuery().use { rs ->
            rs.next()
            rs.getBytes("data")
        }
    }

    val image: BufferedImage? by lazy {
        if (type == ImageFormat.SVG) {
            return@lazy SVGLoader().load(data.inputStream())?.let { svg ->
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
        }

        val seen = mutableMapOf<List<String>, AbstractTreeNode>()
        for (row in images) {
            var lastSeen: AbstractTreeNode = this
            val currentLeadingPath = mutableListOf<String>()
            for (pathPart in row.path.split('/')) {
                currentLeadingPath.add(pathPart)
                val next = seen.getOrPut(currentLeadingPath.toList()) {
                    val newChild = if (pathPart.contains('.')) {
                        ImageNode(row)
                    } else {
                        ImageFolderNode(currentLeadingPath.joinToString("/"))
                    }
                    lastSeen.children.add(newChild)
                    newChild
                }
                lastSeen = next
            }
        }
    }
}
