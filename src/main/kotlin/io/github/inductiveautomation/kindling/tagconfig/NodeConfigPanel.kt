package io.github.inductiveautomation.kindling.tagconfig

import io.github.inductiveautomation.kindling.tagconfig.model.IdbNode
import io.github.inductiveautomation.kindling.tagconfig.model.MinimalTagConfigSerializer
import io.github.inductiveautomation.kindling.tagconfig.model.Node
import io.github.inductiveautomation.kindling.utils.Action
import io.github.inductiveautomation.kindling.utils.FloatableComponent
import io.github.inductiveautomation.kindling.utils.PopupMenuCustomizer
import io.github.inductiveautomation.kindling.utils.add
import io.github.inductiveautomation.kindling.utils.getAll
import io.github.inductiveautomation.kindling.utils.remove
import net.miginfocom.swing.MigLayout
import org.jdesktop.swingx.JXTable
import java.awt.Font
import java.util.EventListener
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.JScrollPane
import javax.swing.JTextArea
import kotlin.toString

class NodeConfigPanel(
    private val node: Node,
    override val icon: Icon? = null,
    override val tabName: String = node.name,
    override val tabTooltip: String = tabName,
) : JPanel(MigLayout("fill, ins 0")),
    FloatableComponent,
    PopupMenuCustomizer {

    private val idbInfo = if (node is IdbNode) {
        JPanel(MigLayout("fill, ins 0")).apply {
            with(node) {
                val rows = arrayOf(
                    arrayOf("id", id),
                    arrayOf("folderid", folderId.toString()),
                    arrayOf("providerid", providerId.toString()),
                    arrayOf("rank", rank.toString()),
                    arrayOf("name", idbName),
                )

                val columns = arrayOf("Column Name", "Value")

                val table = JXTable(rows, columns)

                add(table, "push, grow, span")
                add(table.tableHeader, "north")
            }
        }
    } else {
        null
    }

    private val textArea = JTextArea(
        TagConfigView.TagExportJson.encodeToString(MinimalTagConfigSerializer, node.config),
    ).apply {
        font = Font(Font.MONOSPACED, Font.PLAIN, 12)
        isEditable = false
    }

    override fun customizePopupMenu(menu: JPopupMenu) = Unit

    private val jumpToDefinition = Action(
        name = "Jump to definition",
        description = "Jump to the tag from which this tag gets its config.",
        action = {
            fireNodeSelectEvent(node.inferredFrom!!)
        },
    )

    init {
        if (idbInfo != null) {
            add(idbInfo, "north")
        }

        if (node.inferred) {
            add(
                JLabel().apply {
                    text = "This tag is inferred from UDT inheritance. it does not have a config entry."
                    font = font.deriveFont(Font.BOLD, 14F)
                    horizontalAlignment = JLabel.LEFT
                },
                "pushx, growx, gapbottom 6, gaptop 6",
            )
            add(JButton(jumpToDefinition), "gaptop 6, gapbottom 6, wrap")
        }
        add(JScrollPane(textArea), "push, grow, span")
    }

    fun addNodeSelectListener(l: NodeSelectListener) {
        listenerList.add(l)
    }

    @Suppress("unused")
    fun removeNodeSelectListener(l: NodeSelectListener) {
        listenerList.remove(l)
    }

    private fun fireNodeSelectEvent(node: Node) {
        listenerList.getAll<NodeSelectListener>().forEach {
            it.onNodeSelect(node)
        }
    }

    fun interface NodeSelectListener : EventListener {
        fun onNodeSelect(node: Node)
    }
}
