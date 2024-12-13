package io.github.inductiveautomation.kindling.log

import com.formdev.flatlaf.extras.FlatSVGIcon
import io.github.inductiveautomation.kindling.core.FilterChangeListener
import io.github.inductiveautomation.kindling.core.FilterPanel
import io.github.inductiveautomation.kindling.core.Kindling.Preferences.General.ShowFullLoggerNames
import io.github.inductiveautomation.kindling.core.Kindling.Preferences.General.ShowLogTree
import io.github.inductiveautomation.kindling.utils.AbstractTreeNode
import io.github.inductiveautomation.kindling.utils.Action
import io.github.inductiveautomation.kindling.utils.ButtonPanel
import io.github.inductiveautomation.kindling.utils.Column
import io.github.inductiveautomation.kindling.utils.FileFilterResponsive
import io.github.inductiveautomation.kindling.utils.FilterList
import io.github.inductiveautomation.kindling.utils.FilterModel
import io.github.inductiveautomation.kindling.utils.FlatScrollPane
import io.github.inductiveautomation.kindling.utils.NoSelectionModel
import io.github.inductiveautomation.kindling.utils.PopupMenuCustomizer
import io.github.inductiveautomation.kindling.utils.asActionIcon
import io.github.inductiveautomation.kindling.utils.attachPopupMenu
import io.github.inductiveautomation.kindling.utils.collapseAll
import io.github.inductiveautomation.kindling.utils.expandAll
import io.github.inductiveautomation.kindling.utils.getAll
import io.github.inductiveautomation.kindling.utils.isAllSelected
import io.github.inductiveautomation.kindling.utils.selectAll
import net.miginfocom.swing.MigLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JMenuItem
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.JScrollPane
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath

class LoggerNamePanel<T : LogEvent>(private val rawData: List<T>) :
    FilterPanel<T>(),
    PopupMenuCustomizer,
    FileFilterResponsive<T> {
    private val isTreeAvailable = rawData.first() is SystemLogEvent

    private var isTreeMode: Boolean = ShowLogTree.currentValue && isTreeAvailable
        set(value) {
            isContextChanging = true

            maintainSelection(value)
            field = value

            mainTreeComponent.isVisible = value
            mainListComponent.isVisible = !value

            isContextChanging = false
        }

    private var isContextChanging: Boolean = false

    private val filterList = FilterList(toStringFn = ::getSortKey).apply {
        setModel(FilterModel.fromRawData(rawData, comparator, ::getSortKey, LogEvent::logger))
        selectAll()

        // Right clicking will check and uncheck an item. We don't want that for this list.
        isClickInCheckBoxOnly = true

        ShowFullLoggerNames.addChangeListener {
            model = model.copy(comparator)
        }

        checkBoxListSelectionModel.addListSelectionListener { e ->
            if (!e.valueIsAdjusting && !isContextChanging) {
                listeners.getAll<FilterChangeListener>().forEach(FilterChangeListener::filterChanged)
            }
        }

        attachPopupMenu {
            JPopupMenu().apply {
                val prompt = if (ShowFullLoggerNames.currentValue) "Show Compact Names" else "Show Full Names"
                add(
                    JMenuItem(
                        Action(prompt) {
                            ShowFullLoggerNames.currentValue = !ShowFullLoggerNames.currentValue
                        },
                    ),
                )
            }
        }
    }

    private val sortButtons = filterList.createSortButtons()

    @Suppress("UNCHECKED_CAST")
    private val filterTree: LogTree = LogTree(
        if (isTreeAvailable) {
            rawData as List<SystemLogEvent>
        } else {
            emptyList()
        },
    ).apply {
        selectionModel = NoSelectionModel()
        checkBoxTreeSelectionModel.addTreeSelectionListener {
            if (!isContextChanging) {
                // Build a set containing the subset of all selected nodes, to catch messages on "parent" keys
                // e.g. `Alarming.Notification` and `Alarming.Notification.Voice` which might both have logged messages.
                selectedTreeNodes = selectedNodes.flatMapTo(mutableSetOf()) {
                    it.name.split(".").runningReduce { acc, s ->
                        "$acc.$s"
                    }
                }
                listeners.getAll<FilterChangeListener>().forEach(FilterChangeListener::filterChanged)
            }
        }
    }

    private var selectedTreeNodes: Set<String> = emptySet()

    @Suppress("unchecked_cast")
    override fun setModelData(data: List<T>) {
        if (isTreeAvailable && isTreeMode) {
            val allSelected = filterTree.checkBoxTreeSelectionModel.isRowSelected(0)

            val previousExpandedPaths = (0..<filterTree.rowCount).mapNotNull { i ->
                if (filterTree.isExpanded(i)) {
                    (filterTree.getPathForRow(i).lastPathComponent as? LogEventNode)?.name
                } else {
                    null
                }
            }

            if (allSelected) {
                filterTree.model = DefaultTreeModel(RootNode(data as List<SystemLogEvent>))
                filterTree.selectAll()
            } else {
                val previousSelection = filterTree.checkBoxTreeSelectionModel.selectionPaths.map {
                    (it.lastPathComponent as LogEventNode).name
                }

                filterTree.model = DefaultTreeModel(RootNode(data as List<SystemLogEvent>))

                val newSelection = previousSelection.mapNotNull {
                    filterTree.pathFromLogger(it)
                }.toTypedArray()

                filterTree.checkBoxTreeSelectionModel.selectionPaths = newSelection
            }

            val newExpandedPaths = previousExpandedPaths.map {
                filterTree.pathFromLogger(it)
            }.distinct()

            newExpandedPaths.forEach(filterTree::expandPath)
        }
        filterList.model = FilterModel.fromRawData(data, filterList.comparator) { it.logger }
    }

    override fun filter(item: T): Boolean = if (isTreeMode) {
        item.logger in selectedTreeNodes
    } else {
        item.logger in filterList.checkBoxListSelectedValues
    }

    override val tabName: String = "Loggers"

    override fun isFilterApplied(): Boolean = if (isTreeMode) {
        !filterTree.checkBoxTreeSelectionModel.isRowSelected(0)
    } else {
        !filterList.checkBoxListSelectionModel.isAllSelected()
    }

    private val mainListComponent = ButtonPanel(sortButtons).apply {
        add(FlatScrollPane(filterList), "newline, push, grow, align right")
        isVisible = !isTreeMode
    }

    private val mainTreeComponent = ButtonPanel(
        listOf(
            JButton(
                Action(description = "Expand All", icon = expandIcon) {
                    filterTree.expandAll()
                },
            ),
            JButton(
                Action(description = "Collapse All", icon = collapseIcon) {
                    filterTree.collapseAll()
                },
            ),
        ),
    ).apply {
        add(JScrollPane(filterTree), "newline, push, grow, span")
        isVisible = isTreeMode
    }

    init {
        if (isTreeAvailable) {
            ShowLogTree.addChangeListener {
                isTreeMode = it
            }
        }
    }

    override val component: JComponent = JPanel(MigLayout("ins 0, fill, hidemode 3")).apply {
        add(mainListComponent, "push, grow")
        add(mainTreeComponent, "push, grow")
    }

    override val icon: FlatSVGIcon = FlatSVGIcon("icons/bx-detail.svg")

    override fun reset() {
        if (isTreeMode) {
            filterTree.selectAll()
        } else {
            filterList.selectAll()
        }
    }

    override fun customizePopupMenu(menu: JPopupMenu, column: Column<out T, *>, event: T) {
        if (isTreeMode) {
            menu.add(
                Action("Focus in Tree") {
                    val treePath = filterTree.pathFromLogger(event.logger) ?: return@Action

                    filterTree.apply {
                        selectionModel.clearSelection()
                        requestFocusInWindow()
                        expandPath(treePath)
                        scrollPathToVisible(treePath)
                        selectionModel.selectionPath = treePath
                    }
                },
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

    // Popup menu for the tab itself
    override fun customizePopupMenu(menu: JPopupMenu) {
        if (isTreeAvailable) {
            val switchTo = if (isTreeMode) "List View" else "Tree View"
            menu.add(
                JMenuItem(
                    Action("Switch to $switchTo") {
                        ShowLogTree.currentValue = !isTreeMode
                    },
                ),
            )
        }
    }

    private fun maintainSelection(toTreeMode: Boolean) {
        // Switched from List to Tree
        if (toTreeMode) {
            if (!isFilterApplied()) {
                filterTree.selectAll()
                return
            }

            val currentSelection = filterList.checkBoxListSelectionModel.selectedIndices.map {
                filterList.model.getElementAt(it) as String
            }

            val treePaths = currentSelection.map { filterTree.pathFromLogger(it) }

            filterTree.checkBoxTreeSelectionModel.clearSelection()
            filterTree.checkBoxTreeSelectionModel.selectionPaths = treePaths.toTypedArray()
        } else {
            if (!isFilterApplied()) {
                filterList.selectAll()
                return
            }

            val indices = selectedTreeNodes.map { filterList.model.indexOf(it) }

            filterList.checkBoxListSelectionModel.clearSelection()

            indices.forEach {
                filterList.checkBoxListSelectionModel.addSelectionInterval(it, it)
            }
        }
    }

    companion object {
        private val expandIcon = FlatSVGIcon("icons/bx-expand-vertical.svg").asActionIcon()
        private val collapseIcon = FlatSVGIcon("icons/bx-collapse-vertical.svg").asActionIcon()

        /*
         * return a TreePath given a fully qualified logger name.
         * Returns null if the logger does not exist in the tree model.
         */
        private fun LogTree.pathFromLogger(logger: String): TreePath? {
            val loggerParts = logger.split(".")
            val selectedPath = mutableListOf(model.root as AbstractTreeNode)
            var currentNode = selectedPath.first()

            for (part in loggerParts) {
                var foundChild = false
                for (child in currentNode.children) {
                    child as LogEventNode
                    if (child.userObject.last() == part) {
                        selectedPath.add(child)
                        currentNode = child
                        foundChild = true
                        break
                    }
                }
                if (!foundChild) return null
            }

            return TreePath(selectedPath.toTypedArray())
        }

        private fun getSortKey(key: Any?): String {
            require(key is String)
            return if (ShowFullLoggerNames.currentValue) {
                key
            } else {
                key.substringAfterLast('.')
            }
        }
    }
}
