package io.github.inductiveautomation.kindling.log

import com.formdev.flatlaf.extras.FlatSVGIcon
import io.github.inductiveautomation.kindling.core.FilterPanel
import io.github.inductiveautomation.kindling.utils.Column
import net.miginfocom.swing.MigLayout
import javax.swing.JPanel
import javax.swing.JPopupMenu

class NameTreePanel(logEvents: List<SystemLogEvent>) : FilterPanel<SystemLogEvent>() {
    private val tree = LogTree(logEvents)

    override fun filter(item: SystemLogEvent) : Boolean {
        return item.logger in tree.selectedLeafNodes.map { it.name }
    }

    override val tabName: String = "Loggers"

    override fun isFilterApplied(): Boolean = true //TODO: Implement this function

    override val component: JPanel = JPanel(MigLayout("ins 2 0, fill"))

    init {
        component.add(tree, "push, grow")
    }

    override val icon: FlatSVGIcon = FlatSVGIcon("icons/null.svg")

    override fun reset() = tree.selectAll()

    override fun customizePopupMenu(menu: JPopupMenu, column: Column<out SystemLogEvent, *>, event: SystemLogEvent) {
        return //TODO: Implement this function
    }
}