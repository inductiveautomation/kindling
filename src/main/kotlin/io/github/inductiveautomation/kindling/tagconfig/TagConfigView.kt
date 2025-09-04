package io.github.inductiveautomation.kindling.tagconfig

import io.github.inductiveautomation.kindling.core.ToolPanel
import io.github.inductiveautomation.kindling.core.ToolPanel.Companion.exportFileChooser
import io.github.inductiveautomation.kindling.tagconfig.model.AbstractTagProvider
import io.github.inductiveautomation.kindling.tagconfig.model.LegacyTagProvider
import io.github.inductiveautomation.kindling.tagconfig.model.Node
import io.github.inductiveautomation.kindling.tagconfig.model.TagProvider
import io.github.inductiveautomation.kindling.utils.Action
import io.github.inductiveautomation.kindling.utils.EDT_SCOPE
import io.github.inductiveautomation.kindling.utils.TabStrip
import io.github.inductiveautomation.kindling.utils.attachPopupMenu
import io.github.inductiveautomation.kindling.utils.configureCellRenderer
import io.github.inductiveautomation.kindling.utils.executeQuery
import io.github.inductiveautomation.kindling.utils.get
import io.github.inductiveautomation.kindling.zip.views.PathView
import io.github.inductiveautomation.kindling.zip.views.SinglePathView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import kotlinx.serialization.json.jsonPrimitive
import net.miginfocom.swing.MigLayout
import java.awt.event.ItemEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.io.File
import java.nio.file.Path
import java.nio.file.spi.FileSystemProvider
import java.sql.Connection
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JFileChooser
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.JScrollPane
import javax.swing.JSplitPane
import javax.swing.filechooser.FileNameExtensionFilter
import javax.swing.tree.TreeNode
import javax.swing.tree.TreePath
import kotlin.io.path.div
import kotlin.io.path.inputStream
import kotlin.io.path.isDirectory
import kotlin.io.path.name
import kotlin.use

@OptIn(ExperimentalSerializationApi::class)
class TagConfigView(
    private val systemName: String,
    tagProviderData: List<AbstractTagProvider>,
) : JPanel(MigLayout("ins 6, fill, hidemode 3")) {
    private val exportButton = JButton("Export Tags").apply {
        isEnabled = false

        addActionListener {
            exportFileChooser.apply {
                resetChoosableFileFilters()
                fileFilter = FileNameExtensionFilter("JSON Files", "json")
                val provider = providerDropdown.selectedItem as AbstractTagProvider
                selectedFile = File("${this@TagConfigView.systemName}_${provider.name}_tags.json")

                if (showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                    // if they hit the 'export tags' button too fast, we'll just quietly do nothing
                    // they can hit the button again if they want to export
                    if (provider.isInitialized) {
                        EDT_SCOPE.launch {
                            withContext(Dispatchers.IO) {
                                provider.exportToJson(selectedFile.toPath())
                            }

                            JOptionPane.showMessageDialog(this@TagConfigView, "Tag Export Finished.")
                        }
                    }
                }
            }
        }
    }

    private val providerDropdown = JComboBox(tagProviderData.toTypedArray()).apply {
        val defaultPrompt = "Select a Tag Provider..."
        selectedIndex = -1

        configureCellRenderer { _, value, _, _, _ ->
            text = value?.name ?: defaultPrompt
        }
    }

    private val tagProviderTree = TagBrowseTree().apply {
        addMouseListener(
            object : MouseAdapter() {
                override fun mousePressed(e: MouseEvent?) {
                    if (e?.clickCount == 2) {
                        selectionPath?.let {
                            val node = it.lastPathComponent as TreeNode
                            if (node.isLeaf) {
                                addOrFocusTab(node as Node)
                            }
                        }
                    }
                }
            },
        )

        attachPopupMenu { mouseEvent ->
            val configAction = Action("Open Config") {
                val pathAtPoint = getClosestPathForLocation(mouseEvent.x, mouseEvent.y)
                addOrFocusTab(pathAtPoint.lastPathComponent as Node)
            }

            val exportAction = Action("Export Selection") {
                val selection = this@attachPopupMenu.selectionPaths

                if (selection == null) {
                    JOptionPane.showMessageDialog(
                        this@TagConfigView,
                        "You must selected at least one item to export.",
                        "Export Error",
                        JOptionPane.WARNING_MESSAGE,
                    )
                    return@Action
                }

                val allSameParent = selection.all { path -> path.parentPath == selection.first().parentPath }

                if (allSameParent) {
                    if (exportFileChooser.showSaveDialog(this@TagConfigView) == JFileChooser.APPROVE_OPTION) {
                        val nodeList = selection.map {
                            it.lastPathComponent as Node
                        }

                        exportFileChooser.selectedFile.outputStream().use { output ->
                            if (nodeList.size == 1) {
                                TagExportJson.encodeToStream(nodeList.single(), output)
                            } else {
                                TagExportJson.encodeToStream(mapOf("tags" to nodeList), output)
                            }
                        }
                    }
                } else {
                    JOptionPane.showMessageDialog(
                        this@TagConfigView,
                        "All selected items must have the same parent folder.",
                        "Export Error",
                        JOptionPane.WARNING_MESSAGE,
                    )
                    return@Action
                }
            }

            JPopupMenu().apply {
                add(configAction)
                add(exportAction)
            }
        }
    }

    private val providerTab = ProviderStatisticsPanel()

    private val tabs = TabStrip().apply {
        addTab("Tag Provider Statistics", providerTab)
        setTabClosable(0, false)
    }

    init {
        providerDropdown.addItemListener { itemEvent ->
            if (itemEvent.stateChange != ItemEvent.SELECTED) return@addItemListener

            val selectedTagProvider = itemEvent.item as AbstractTagProvider

            tabs.setTitleAt(0, selectedTagProvider.name)
            providerTab.provider = selectedTagProvider
            tagProviderTree.provider = selectedTagProvider
            exportButton.isEnabled = true
        }

        val leftPane = JPanel(MigLayout("fill, ins 5")).apply {
            add(providerDropdown, "pushx, growx, wmin 200")
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

    private fun TagBrowseTree.addOrFocusTab(node: Node) {
        val tips = tabs.indices.map(tabs::getToolTipTextAt)

        provider?.run {
            val tPath = node.tagPath
            val index = tips.indexOf(tPath)
            if (index == -1) {
                tabs.addTab(
                    NodeConfigPanel(node, null, node.name, tPath).apply {
                        if (node.inferred) {
                            addNodeSelectListener {
                                val nodes = mutableListOf<Node>()
                                var currNode: Node? = it

                                while (currNode != null) {
                                    nodes.add(0, currNode)
                                    currNode = currNode.parent
                                }

                                selectionPath = TreePath(nodes.toTypedArray())
                            }
                        }
                    },
                )
            } else {
                tabs.selectedIndex = index
            }
        }
    }

    companion object {
        internal val TagExportJson = Json {
            prettyPrint = true
            prettyPrintIndent = "  "
        }

        fun fromZip(provider: FileSystemProvider, configDir: Path): PathView {
            val tagProviderData = TagProvider.loadProviders(configDir)
            val systemPropsPath = configDir / "resources/core/ignition/system-properties/config.json"

            val systemName = systemPropsPath.inputStream().use {
                val json = Json.decodeFromStream<JsonObject>(it)
                json["systemName"]?.jsonPrimitive?.content ?: "Gateway"
            }

            val view = TagConfigView(systemName, tagProviderData)

            return object : SinglePathView("fill, ins 0") {
                override val path: Path = configDir
                override val provider: FileSystemProvider = provider
                override val icon = null
                override val closable = false
                override val tabName = "Tag Config"

                init {
                    add(view, "push, grow")
                }
            }
        }

        fun fromIdb(connection: Connection): ToolPanel {
            val systemName = connection.executeQuery("SELECT systemname FROM sysprops").use { rs ->
                rs.next()
                rs.get<String>("systemname")
            }

            val tagProviderData = LegacyTagProvider.loadProviders(connection)

            val view = TagConfigView(systemName, tagProviderData)

            return object : ToolPanel("ins 0, fill") {
                override val icon = null

                init {
                    name = "Tag Config"
                    add(view, "push, grow")
                }
            }
        }

        fun isConfigDirectory(dir: Path): Boolean {
            return dir.isDirectory() && dir.name == "config"
        }
    }
}
