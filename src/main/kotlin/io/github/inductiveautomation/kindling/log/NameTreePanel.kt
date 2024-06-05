package io.github.inductiveautomation.kindling.log

import com.formdev.flatlaf.extras.FlatSVGIcon
import io.github.inductiveautomation.kindling.core.FilterChangeListener
import io.github.inductiveautomation.kindling.core.FilterPanel
import io.github.inductiveautomation.kindling.utils.AbstractTreeNode
import io.github.inductiveautomation.kindling.utils.Action
import io.github.inductiveautomation.kindling.utils.Column
import io.github.inductiveautomation.kindling.utils.getAll
import net.miginfocom.swing.MigLayout
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.JScrollPane
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreePath

class NameTreePanel(logEvents: List<SystemLogEvent>) : FilterPanel<LogEvent>() {
    private val tree = LogTree(logEvents)

    private var currentSelectedLeafNodes = tree.selectedLeafNodes.map { it.name }

    override fun filter(item: LogEvent) : Boolean = item.logger in currentSelectedLeafNodes

    override val tabName: String = "Loggers"

    override fun isFilterApplied() = !tree.checkBoxTreeSelectionModel.isRowSelected(0)

    override val component: JPanel = JPanel(MigLayout("ins 2 0, fill"))

    init {
        component.add(JScrollPane(tree), "push, grow")

        tree.checkBoxTreeSelectionModel.addTreeSelectionListener {
            currentSelectedLeafNodes = tree.selectedLeafNodes.map { it.name }
            listeners.getAll<FilterChangeListener>().forEach(FilterChangeListener::filterChanged)
        }

        tree.expandsSelectedPaths = true
    }

    override val icon = FlatSVGIcon("icons/bx-detail.svg")

    override fun reset() = tree.selectAll()

    override fun customizePopupMenu(menu: JPopupMenu, column: Column<out LogEvent, *>, event: LogEvent) {
        menu.add(
            Action("Focus in Tree") {
                val loggerParts = event.logger.split(".")
                val selectedPath = mutableListOf(tree.model.root as AbstractTreeNode)
                var currentNode = selectedPath.first()

                for (part in loggerParts) {
                    for (child in currentNode.children) {
                        child as LogEventNode
                        if (child.userObject.last == part) {
                            selectedPath.add(child)
                            currentNode = child
                            break
                        }
                    }
                }

                val treePath = TreePath(selectedPath.toTypedArray())

                tree.apply {
                    selectionModel.clearSelection()
                    grabFocus()
                    expandPath(treePath)
                    selectionModel.selectionPath = treePath
                }
            }
        )
    }
}