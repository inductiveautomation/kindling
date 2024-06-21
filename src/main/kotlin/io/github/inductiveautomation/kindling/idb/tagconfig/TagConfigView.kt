package io.github.inductiveautomation.kindling.idb.tagconfig

import com.formdev.flatlaf.extras.FlatSVGIcon
import io.github.inductiveautomation.kindling.core.ToolPanel
import io.github.inductiveautomation.kindling.idb.tagconfig.LazyTreeNode.Companion.createLazyTreeStructure
import io.github.inductiveautomation.kindling.idb.tagconfig.model.Node
import io.github.inductiveautomation.kindling.idb.tagconfig.model.TagProviderRecord
import io.github.inductiveautomation.kindling.utils.AbstractTreeNode
import io.github.inductiveautomation.kindling.utils.EDT_SCOPE
import io.github.inductiveautomation.kindling.utils.TabStrip
import io.github.inductiveautomation.kindling.utils.configureCellRenderer
import io.github.inductiveautomation.kindling.utils.tag
import io.github.inductiveautomation.kindling.utils.treeCellRenderer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import net.miginfocom.swing.MigLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.sql.Connection
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JFileChooser
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JSplitPane
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath
import kotlin.io.path.Path

@OptIn(ExperimentalSerializationApi::class)
class TagConfigView(connection: Connection) : ToolPanel() {
    private val tagProviderData: List<TagProviderRecord> = TagProviderRecord.getProvidersFromDB(connection)

    override val icon = null

    private val exportButton = JButton("Export Tags").apply {
        addActionListener {
            if (exportFileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                val selectedFilePath = Path(exportFileChooser.selectedFile.absolutePath)
                val provider = providerDropdown.selectedItem as? TagProviderRecord

                if (provider == null) {
                    JOptionPane.showMessageDialog(
                        this@TagConfigView,
                        "You must first select a Tag Provider",
                        "Cannot Export Tags",
                        JOptionPane.WARNING_MESSAGE,
                    )
                } else {
                    provider.exportToJson(selectedFilePath)
                    JOptionPane.showMessageDialog(this, "Tag Export Finished.")
                }
            }
        }
    }

    private val providerDropdown =
        JComboBox(tagProviderData.toTypedArray()).apply {
            val defaultPrompt = "Select a Tag Provider..."
            selectedIndex = -1

            addItemListener { itemEvent ->
                val selectedTagProvider = itemEvent.item as TagProviderRecord

                CoroutineScope(Dispatchers.Default).launch {
                    selectedTagProvider.initProviderNode()

                    val lazyTreeNode = createLazyTreeStructure(selectedTagProvider)

                    EDT_SCOPE.launch {
                        tabs.setTitleAt(0, selectedTagProvider.name)
                        providerTab.provider = selectedTagProvider
                        tagProviderTree.model = DefaultTreeModel(lazyTreeNode)
                    }
                }
            }

            // Dummy Tag Provider Record for preferred size
            prototypeDisplayValue = (tagProviderData + TagProviderRecord(
                    allowBackFill = false,
                    dbConnection = connection,
                    description = "",
                    name = "Select a Tag Provider...",
                    enabled = false,
                    id = 0,
                    typeId = "",
                    uuid = "",
                )
                ).maxBy {
                it.name.length
            }

            configureCellRenderer { _, value, _, _, _ ->
                text = value?.name ?: defaultPrompt
            }
        }

    private val tagProviderTree =
        JTree(DefaultMutableTreeNode("Select a Tag Provider to Browse")).apply {
            isRootVisible = false
            addMouseListener(
                object : MouseAdapter() {
                    override fun mousePressed(e: MouseEvent?) {
                        if (e?.clickCount == 2) {
                            selectionPath?.let {
                                if (it.toTagPath() !in tabs.indices.map(tabs::getToolTipTextAt)) {
                                    tabs.addTab(NodeConfigPanel(it))
                                }
                            }
                        }
                    }
                },
            )

            cellRenderer = treeCellRenderer { _, value, _, expanded, _, _, _ ->
                val actualValue = value as? LazyTreeNode

                text = if (actualValue?.inferred == true) {
                    buildString {
                        tag("html") {
                            tag("i") {
                                append("${actualValue.name}*")
                            }
                        }
                    }
                } else {
                    actualValue?.name.toString()
                }

                icon = when (actualValue?.tagType) {
                    "AtomicTag" -> TAG_ICON
                    "UdtInstance", "UdtType" -> UDT_ICON
                    else -> {
                        if (expanded) FOLDER_OPEN_ICON else FOLDER_CLOSED_ICON
                    }
                }

                this
            }
        }

    private val providerTab = ProviderStatisticsPanel()

    private val tabs = TabStrip().apply {
        addTab("Tag Provider Statistics", providerTab)
        setTabClosable(0, false)
    }

    init {
        val leftPane = JPanel(MigLayout("fill, ins 5")).apply {
            add(providerDropdown, "pushx, growx")
            add(exportButton, "wrap")
            add(JScrollPane(tagProviderTree), "push, grow, span")
        }

        add(
            JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT,
                leftPane,
                tabs,
            ).apply { resizeWeight = 0.0 },
            "push, grow, span",
        )
    }

    companion object {
        internal val TagExportJson = Json {
            prettyPrint = true
            prettyPrintIndent = "  "
        }

        private const val ICON_SIZE = 18

        private val UDT_ICON = FlatSVGIcon("icons/bx-vector.svg").derive(ICON_SIZE, ICON_SIZE)
        private val TAG_ICON = FlatSVGIcon("icons/bx-purchase-tag.svg").derive(ICON_SIZE, ICON_SIZE)
        private val FOLDER_CLOSED_ICON = FlatSVGIcon("icons/bx-folder.svg").derive(ICON_SIZE, ICON_SIZE)
        private val FOLDER_OPEN_ICON = FlatSVGIcon("icons/bx-folder-open.svg").derive(ICON_SIZE, ICON_SIZE)

        fun TreePath.toTagPath(): String {
            val provider = "[${(path.first() as LazyTreeNode).name}]"
            val tagPath = path.asList().subList(1, path.size).joinToString("/") {
                (it as LazyTreeNode).name
            }
            return "$provider$tagPath"
        }
    }
}

@Suppress("unused")
class LazyTreeNode(
    val name: String,
    val tagType: String?,
    originalNode: Node,
) : AbstractTreeNode() {
    val originalNode by lazy { originalNode }
    val inferred = originalNode.inferredNode

    companion object {
        fun createLazyTreeStructure(provider: TagProviderRecord): LazyTreeNode {
            val rootNode = fromNode(provider.providerNode.value)

            if (provider.providerStatistics.totalOrphanedTags.value > 0) {
                val orphanedParentNode = fromNode(provider.orphanedParentNode)
                rootNode.children.add(orphanedParentNode)
            }

            return rootNode
        }

        private fun fromNode(node: Node): LazyTreeNode {
            return LazyTreeNode(
                name = if (node.config.name.isNullOrEmpty()) {
                    node.name.toString()
                } else {
                    node.config.name
                },
                tagType = node.config.tagType,
                originalNode = node,
            ).apply {
                for (childNode in node.config.tags) {
                    children.add(fromNode(childNode))
                }
            }
        }
    }
}
