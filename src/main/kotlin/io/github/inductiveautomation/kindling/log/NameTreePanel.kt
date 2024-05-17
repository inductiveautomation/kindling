package io.github.inductiveautomation.kindling.log

import com.formdev.flatlaf.extras.FlatSVGIcon
import io.github.inductiveautomation.kindling.core.FilterChangeListener
import io.github.inductiveautomation.kindling.core.FilterPanel
import io.github.inductiveautomation.kindling.utils.Column
import io.github.inductiveautomation.kindling.utils.getAll
import net.miginfocom.swing.MigLayout
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.JScrollPane

class NameTreePanel(logEvents: List<SystemLogEvent>) : FilterPanel<LogEvent>() {
    private val tree = LogTree(logEvents)

    private var currentSelectedLeafNodes = tree.selectedLeafNodes.map { it.name }

    override fun filter(item: LogEvent) : Boolean {
        val results = item.logger in currentSelectedLeafNodes
        return results
    }

    override val tabName: String = "Loggers"

    override fun isFilterApplied(): Boolean = true //TODO: Implement this function

    override val component: JPanel = JPanel(MigLayout("ins 2 0, fill"))

    init {
        component.add(JScrollPane(tree), "push, grow")

        tree.checkBoxTreeSelectionModel.addTreeSelectionListener {
            currentSelectedLeafNodes = tree.selectedLeafNodes.map { it.name }
            listeners.getAll<FilterChangeListener>().forEach(FilterChangeListener::filterChanged)
        }
    }

    override val icon: FlatSVGIcon = FlatSVGIcon("icons/bx-network-chart.svg")

    override fun reset() = tree.selectAll()

    override fun customizePopupMenu(menu: JPopupMenu, column: Column<out LogEvent, *>, event: LogEvent) {
        return //TODO: Implement this function
    }
}