package io.github.inductiveautomation.kindling.log

import com.formdev.flatlaf.extras.FlatSVGIcon
import io.github.inductiveautomation.kindling.core.FilterChangeListener
import io.github.inductiveautomation.kindling.core.FilterPanel
import io.github.inductiveautomation.kindling.core.Kindling
import io.github.inductiveautomation.kindling.core.Kindling.Preferences.General.ShowFullLoggerNames
import io.github.inductiveautomation.kindling.core.Kindling.Preferences.General.ShowLogTree
import io.github.inductiveautomation.kindling.utils.AbstractTreeNode
import io.github.inductiveautomation.kindling.utils.Action
import io.github.inductiveautomation.kindling.utils.ButtonPanel
import io.github.inductiveautomation.kindling.utils.Column
import io.github.inductiveautomation.kindling.utils.FilterList
import io.github.inductiveautomation.kindling.utils.FilterModel
import io.github.inductiveautomation.kindling.utils.FlatScrollPane
import io.github.inductiveautomation.kindling.utils.getAll
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.JScrollPane
import javax.swing.tree.TreePath
import net.miginfocom.swing.MigLayout

class NewNamePanel(private val rawData: List<LogEvent>) : FilterPanel<LogEvent>() {
    private val isTreeAvailable = rawData.first() is SystemLogEvent

    private val isTreeMode: Boolean
        get() = isTreeAvailable && ShowLogTree.currentValue

    private val filterList = FilterList(toStringFn = ::getSortKey).apply {
        setModel(
            FilterModel(
                rawData.groupingBy(LogEvent::logger).eachCount(),
                ::getSortKey,
            )
        )
        selectAll()

        ShowFullLoggerNames.addChangeListener {
            model = model.copy(comparator)
        }
    }

    private val sortButtons = filterList.createSortButtons()

    private val filterTree = (if (isTreeAvailable) LogTree(rawData as List<SystemLogEvent>) else null)?.apply {
        checkBoxTreeSelectionModel.addTreeSelectionListener {
            currentSelectedLeafNodes = selectedLeafNodes.map { it.name }
            listeners.getAll<FilterChangeListener>().forEach(FilterChangeListener::filterChanged)
        }
    }

    private var currentSelectedLeafNodes: List<String>? = filterTree?.selectedLeafNodes?.map { it.name }

    override fun filter(item: LogEvent): Boolean {
        return if (isTreeMode) {
            item.logger in currentSelectedLeafNodes!!
        } else {
            item.logger in filterList.checkBoxListSelectedValues
        }
    }

    override val tabName: String = "Loggers"

    override fun isFilterApplied(): Boolean {
        return if (isTreeMode) {
            !filterTree!!.checkBoxTreeSelectionModel.isRowSelected(0)
        } else {
            filterList.checkBoxListSelectedValues.size != filterList.model.size - 1
        }
    }

    private val mainListComponent = ButtonPanel(sortButtons).apply {
        add(FlatScrollPane(filterList), "newline, push, grow, align right")
    }

    private val mainTreeComponent = JPanel(MigLayout("ins 2 0, fill")).apply {
        add(JScrollPane(filterTree), "push, grow")
    }

    override val component: JComponent
        get() = if (isTreeMode) mainTreeComponent else mainListComponent

    override val icon: FlatSVGIcon = FlatSVGIcon("icons/bx-detail.svg")

    override fun reset() {
        if (isTreeMode) filterTree!!.selectAll() else filterList.selectAll()
    }

    override fun customizePopupMenu(menu: JPopupMenu, column: Column<out LogEvent, *>, event: LogEvent) {
        if (isTreeMode) {
            menu.add(
                Action("Focus in Tree") {
                    val loggerParts = event.logger.split(".")
                    val selectedPath = mutableListOf(filterTree!!.model.root as AbstractTreeNode)
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

                    filterTree!!.apply {
                        selectionModel.clearSelection()
                        grabFocus()
                        expandPath(treePath)
                        selectionModel.selectionPath = treePath
                    }
                }
            )
        } else {
            if (column == WrapperLogColumns.Logger || column == SystemLogColumns.Logger) {
                val loggerIndex = filterList.model.indexOf(event.logger)
                menu.add(
                    Action("Show only ${event.logger} events") {
                        filterList.checkBoxListSelectedIndex = loggerIndex
                        filterList.ensureIndexIsVisible(loggerIndex)
                    },
                )
                menu.add(
                    Action("Exclude ${event.logger} events") {
                        filterList.removeCheckBoxListSelectedIndex(loggerIndex)
                    },
                )
            }
        }
    }

    companion object {
        private fun getSortKey(key: Any?): String {
            require(key is String)
            return if (Kindling.Preferences.General.ShowFullLoggerNames.currentValue) {
                key
            } else {
                key.substringAfterLast('.')
            }
        }
    }
}