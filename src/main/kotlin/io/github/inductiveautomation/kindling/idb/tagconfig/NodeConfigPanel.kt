package io.github.inductiveautomation.kindling.idb.tagconfig

import io.github.inductiveautomation.kindling.idb.tagconfig.TagBrowseTree.Companion.toTagPath
import io.github.inductiveautomation.kindling.idb.tagconfig.model.MinimalTagConfigSerializer
import io.github.inductiveautomation.kindling.idb.tagconfig.model.Node
import io.github.inductiveautomation.kindling.utils.FloatableComponent
import io.github.inductiveautomation.kindling.utils.PopupMenuCustomizer
import net.miginfocom.swing.MigLayout
import org.jdesktop.swingx.JXTable
import java.awt.Font
import javax.swing.Icon
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.JScrollPane
import javax.swing.JTextArea
import javax.swing.tree.TreePath

class NodeConfigPanel(
    private val node: Node,
    override val icon: Icon? = null,
    override val tabName: String = node.actualName,
    override val tabTooltip: String = tabName,
) : JPanel(MigLayout("fill, ins 0")),
    FloatableComponent,
    PopupMenuCustomizer {

    private val idbInfo = JPanel(MigLayout("fill, ins 0")).apply {
        with(node) {
            // TODO make a real table model
            val rows = arrayOf(
                arrayOf("id", id),
                arrayOf("folderid", folderId.toString()),
                arrayOf("providerid", providerId.toString()),
                arrayOf("rank", rank.toString()),
                arrayOf("name", name.toString()),
            )

            val columns = arrayOf("Column Name", "Value")

            val table = JXTable(rows, columns)

            add(table, "push, grow, span")
            add(table.tableHeader, "north")
        }
    }

    private val textArea = JTextArea(
        TagConfigView.TagExportJson.encodeToString(MinimalTagConfigSerializer, node.config),
    ).apply {
        font = Font(Font.MONOSPACED, Font.PLAIN, 12)
        isEditable = false
    }

    override fun customizePopupMenu(menu: JPopupMenu) = Unit

    init {
        add(idbInfo, "north")
        add(JScrollPane(textArea), "push, grow, span")
    }

    companion object {
        operator fun invoke(treePath: TreePath): NodeConfigPanel {
            val treeNode = treePath.lastPathComponent as Node
            return NodeConfigPanel(
                treeNode,
                null,
                treeNode.actualName,
                treePath.toTagPath(),
            )
        }
    }
}
