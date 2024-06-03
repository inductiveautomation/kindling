package io.github.inductiveautomation.kindling.idb.tagconfig

import io.github.inductiveautomation.kindling.core.ToolPanel
import io.github.inductiveautomation.kindling.idb.tagconfig.TagBrowseTree.Companion.toTagPath
import io.github.inductiveautomation.kindling.idb.tagconfig.model.Node
import io.github.inductiveautomation.kindling.idb.tagconfig.model.TagProviderRecord
import io.github.inductiveautomation.kindling.utils.Action
import io.github.inductiveautomation.kindling.utils.EDT_SCOPE
import io.github.inductiveautomation.kindling.utils.TabStrip
import io.github.inductiveautomation.kindling.utils.attachPopupMenu
import io.github.inductiveautomation.kindling.utils.configureCellRenderer
import java.awt.event.ItemEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.sql.Connection
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JFileChooser
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.JScrollPane
import javax.swing.JSplitPane
import javax.swing.tree.TreeNode
import kotlin.io.path.Path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToStream
import net.miginfocom.swing.MigLayout

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

    private val providerDropdown = JComboBox(tagProviderData.toTypedArray()).apply {
        val defaultPrompt = "Select a Tag Provider..."
        selectedIndex = -1

        addItemListener { itemEvent ->
            if (itemEvent.stateChange != ItemEvent.SELECTED) return@addItemListener

            val selectedTagProvider = itemEvent.item as TagProviderRecord

            EDT_SCOPE.launch {
                tabs.setTitleAt(0, selectedTagProvider.name)
                providerTab.provider = selectedTagProvider
                tagProviderTree.provider = selectedTagProvider
            }
        }

        // Dummy Tag Provider Record for preferred size
        prototypeDisplayValue = (
            tagProviderData + TagProviderRecord(
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

    private val tagProviderTree = TagBrowseTree().apply {
        addMouseListener(
            object : MouseAdapter() {
                override fun mousePressed(e: MouseEvent?) {
                    if (e?.clickCount == 2) {
                        selectionPath?.let {
                            if ((it.lastPathComponent as TreeNode).childCount == 0) {
                                if (it.toTagPath() !in tabs.indices.map(tabs::getToolTipTextAt)) {
                                    tabs.addTab(NodeConfigPanel(it))
                                }
                            }
                        }
                    }
                }
            },
        )

        attachPopupMenu {mouseEvent ->
            val configAction = Action("Open Config") {
                val pathAtPoint = getClosestPathForLocation(mouseEvent.x, mouseEvent.y)

                if (pathAtPoint.toTagPath() !in tabs.indices.map(tabs::getToolTipTextAt)) {
                    tabs.addTab(NodeConfigPanel(pathAtPoint))
                }
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
    }
}

