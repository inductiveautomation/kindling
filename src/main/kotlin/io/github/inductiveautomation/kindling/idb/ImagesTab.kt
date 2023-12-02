package io.github.inductiveautomation.kindling.idb

import com.inductiveautomation.ignition.gateway.images.ImageFormat
import io.github.inductiveautomation.kindling.core.ToolPanel
import io.github.inductiveautomation.kindling.utils.AbstractTreeNode
import io.github.inductiveautomation.kindling.utils.FlatScrollPane
import io.github.inductiveautomation.kindling.utils.TypedTreeNode
import io.github.inductiveautomation.kindling.utils.toList
import io.github.inductiveautomation.kindling.utils.treeCellRenderer
import java.awt.Dimension
import java.sql.Connection
import javax.imageio.ImageIO
import javax.swing.Icon
import javax.swing.ImageIcon
import javax.swing.JLabel
import javax.swing.JTree
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreeSelectionModel

class ImagesPanel(connection: Connection) : ToolPanel("ins 0, fill, hidemode 3") {
    override val icon: Icon? = null

    init {
        val tree = JTree(DefaultTreeModel(RootImageNode(connection)))
        tree.isRootVisible = false
        tree.cellRenderer =
            treeCellRenderer { _, value, _, _, _, _, _ ->
                when (value) {
                    is ImageNode -> {
                        text = value.userObject.path
                        toolTipText = value.userObject.description
                    }

                    is ImageFolderNode -> {
                        text = value.userObject
                    }
                }
                this
            }

        add(FlatScrollPane(tree), "push, grow, w 30%!")
        val imageDisplay = JLabel()
        add(FlatScrollPane(imageDisplay), "push, grow")

        tree.selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION
        tree.addTreeSelectionListener {
            val node = it.newLeadSelectionPath?.lastPathComponent as? AbstractTreeNode
            imageDisplay.icon =
                if (node is ImageNode) {
                    runCatching {
                        val data = node.userObject.data
                        val readers = ImageIO.getImageReadersByFormatName(node.userObject.type.name)
                        val image =
                            readers.asSequence().firstNotNullOfOrNull { reader ->
                                ImageIO.createImageInputStream(data.inputStream())?.use { iis ->
                                    reader.input = iis
                                    reader.read(
                                        0,
                                        reader.defaultReadParam.apply {
                                            sourceRenderSize = Dimension(200, 200)
                                        },
                                    )
                                }
                            }
                        image?.let(::ImageIcon)
                    }.getOrNull()
                } else {
                    null
                }
        }
    }
}

private data class ImageNode(override val userObject: ImageRow) : TypedTreeNode<ImageRow>()

private data class ImageRow(
    val path: String,
    val type: ImageFormat,
    val description: String?,
    val data: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ImageRow

        if (path != other.path) return false
        if (type != other.type) return false
        if (description != other.description) return false
        if (!data.contentEquals(other.data)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = path.hashCode()
        result = 31 * result + type.hashCode()
        result = 31 * result + (description?.hashCode() ?: 0)
        result = 31 * result + data.contentHashCode()
        return result
    }

    override fun toString(): String {
        return "ImageRow(path='$path', type=$type, description=$description)"
    }
}

private data class ImageFolderNode(override val userObject: String) : TypedTreeNode<String>()

class RootImageNode(connection: Connection) : AbstractTreeNode() {
    private val listAll =
        connection.prepareStatement(
            """
            SELECT path, type, description, data
            FROM images
            WHERE type IS NOT NULL
            ORDER BY path
            """.trimIndent(),
        )

    init {
        val images =
            listAll.use {
                it.executeQuery().toList { rs ->
                    ImageRow(
                        rs.getString("path"),
                        rs.getString("type").let(ImageFormat::valueOf),
                        rs.getString("description"),
                        rs.getBytes("data"),
                    )
                }
            }

        val seen = mutableMapOf<List<String>, AbstractTreeNode>()
        for (row in images) {
            var lastSeen: AbstractTreeNode = this
            val currentLeadingPath = mutableListOf<String>()
            for (pathPart in row.path.split('/')) {
                currentLeadingPath.add(pathPart)
                val next =
                    seen.getOrPut(currentLeadingPath.toList()) {
                        val newChild =
                            if (pathPart.contains('.')) {
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
