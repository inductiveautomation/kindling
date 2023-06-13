package io.github.inductiveautomation.kindling.thread

import com.formdev.flatlaf.extras.FlatSVGIcon
import com.jidesoft.comparator.AlphanumComparator
import com.jidesoft.swing.CheckBoxListSelectionModel
import io.github.inductiveautomation.kindling.core.ClipboardTool
import io.github.inductiveautomation.kindling.core.Detail
import io.github.inductiveautomation.kindling.core.Detail.BodyLine
import io.github.inductiveautomation.kindling.core.MultiTool
import io.github.inductiveautomation.kindling.core.ToolOpeningException
import io.github.inductiveautomation.kindling.core.ToolPanel
import io.github.inductiveautomation.kindling.core.add
import io.github.inductiveautomation.kindling.thread.FilterModel.Companion.byCountAsc
import io.github.inductiveautomation.kindling.thread.FilterModel.Companion.byCountDesc
import io.github.inductiveautomation.kindling.thread.FilterModel.Companion.byNameAsc
import io.github.inductiveautomation.kindling.thread.FilterModel.Companion.byNameDesc
import io.github.inductiveautomation.kindling.thread.model.Stacktrace
import io.github.inductiveautomation.kindling.thread.model.Thread
import io.github.inductiveautomation.kindling.thread.model.ThreadDump
import io.github.inductiveautomation.kindling.thread.model.ThreadLifespan
import io.github.inductiveautomation.kindling.thread.model.ThreadModel
import io.github.inductiveautomation.kindling.thread.model.ThreadModel.MultiThreadColumns
import io.github.inductiveautomation.kindling.thread.model.ThreadModel.SingleThreadColumns
import io.github.inductiveautomation.kindling.utils.Action
import io.github.inductiveautomation.kindling.utils.Column
import io.github.inductiveautomation.kindling.utils.EDT_SCOPE
import io.github.inductiveautomation.kindling.utils.FlatScrollPane
import io.github.inductiveautomation.kindling.utils.ReifiedJXTable
import io.github.inductiveautomation.kindling.utils.attachPopupMenu
import io.github.inductiveautomation.kindling.utils.escapeHtml
import io.github.inductiveautomation.kindling.utils.getValue
import io.github.inductiveautomation.kindling.utils.selectedRowIndices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.miginfocom.swing.MigLayout
import org.jdesktop.swingx.JXSearchField
import org.jdesktop.swingx.decorator.ColorHighlighter
import org.jdesktop.swingx.table.ColumnControlButton
import org.jdesktop.swingx.table.TableColumnExt
import java.awt.Desktop
import java.awt.Rectangle
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.ButtonGroup
import javax.swing.Icon
import javax.swing.JLabel
import javax.swing.JMenu
import javax.swing.JMenuBar
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.JSplitPane
import javax.swing.JToggleButton
import javax.swing.ListSelectionModel
import javax.swing.SortOrder
import javax.swing.UIManager
import kotlin.io.path.inputStream
import kotlin.io.path.name
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.outputStream

class MultiThreadView(
    val paths: List<Path>,
) : ToolPanel() {
    private val threadDumps = paths.map { path ->
        ThreadDump.fromStream(path.inputStream()) ?: throw ToolOpeningException("Failed to open $path as a thread dump")
    }

    private val poolList = FilterList("(No Pool)")
    private val systemList = FilterList("Unassigned")
    private val stateList = FilterList("")
    private val searchField = JXSearchField("Search")

    private var visibleThreadDumps: List<ThreadDump?> = emptyList()
        set(value) {
            field = value
            currentLifespanList = value.toLifespanList()
        }

    private var currentLifespanList: List<ThreadLifespan> = emptyList()
        set(value) {
            field = value
            val allThreads = value.flatten().filterNotNull()
            if (allThreads.isNotEmpty()) {
                stateList.model = FilterModel(allThreads.groupingBy { it.state.toString() }.eachCount())
                systemList.model = FilterModel(allThreads.groupingBy(Thread::system).eachCount())
                poolList.model = FilterModel(allThreads.groupingBy(Thread::pool).eachCount())
            }
            if (initialized) {
                updateData()
            }
        }

    private val mainTable: ReifiedJXTable<ThreadModel> = run {
        // populate initial state of all the filter lists
        visibleThreadDumps = threadDumps
        val initialModel = ThreadModel(currentLifespanList)

        ReifiedJXTable(initialModel).apply {
            columnFactory = initialModel.columns.toColumnFactory()
            createDefaultColumnsFromModel()
            setSortOrder(initialModel.columns[initialModel.columns.id], SortOrder.ASCENDING)
            selectionMode = ListSelectionModel.SINGLE_SELECTION

            addHighlighter(
                ColorHighlighter(
                    { _, adapter ->
                        threadDumps.any { threadDump ->
                            model[adapter.row, model.columns.id] in threadDump.deadlockIds
                        }
                    },
                    UIManager.getColor("Actions.Red"),
                    null,
                ),
            )

            fun toggleMarkAllWithSameValue(property: Column<ThreadLifespan, *>) {
                val selectedRowIndex = selectedRowIndices().first()
                val selectedPropertyValue = model[selectedRowIndex, property]
                val selectedThreadMarked = model[selectedRowIndex, model.columns.mark]
                for (lifespan in model.threadData) {
                    if (property.getValue(lifespan) == selectedPropertyValue) {
                        for (thread in lifespan) {
                            thread?.marked = !selectedThreadMarked
                        }
                    }
                }

                model.fireTableDataChanged()
            }

            fun filterAllWithSameValue(property: Column<ThreadLifespan, *>) {
                val selectedRowIndex = selectedRowIndices().first()
                when (property) {
                    SingleThreadColumns.state -> {
                        val state = model[selectedRowIndex, SingleThreadColumns.state]
                        stateList.select(state.name)
                    }

                    SingleThreadColumns.system, MultiThreadColumns.system -> {
                        val system = model[selectedRowIndex, model.columns.system]
                        if (system != null) {
                            systemList.select(system)
                        }
                    }

                    SingleThreadColumns.pool, MultiThreadColumns.pool -> {
                        val pool = model[selectedRowIndex, model.columns.pool]
                        if (pool != null) {
                            poolList.select(pool)
                        }
                    }
                }
            }

            actionMap.put(
                "${ColumnControlButton.COLUMN_CONTROL_MARKER}.clearAllMarks",
                Action(name = "Clear All Marks") {
                    for (lifespan in model.threadData) {
                        lifespan.forEach { thread ->
                            thread?.marked = false
                        }
                    }
                },
            )

            attachPopupMenu table@{ event ->
                val rowAtPoint = rowAtPoint(event.point)
                selectionModel.setSelectionInterval(rowAtPoint, rowAtPoint)

                JPopupMenu().apply {
                    add(
                        JMenu("Filter all with same...").apply {
                            for (column in this@table.model.columns.filterableColumns) {
                                add(
                                    Action(column.header) {
                                        filterAllWithSameValue(column)
                                    },
                                )
                            }
                        },
                    )
                    add(
                        JMenu("Mark/Unmark all with same...").apply {
                            for (column in this@table.model.columns.markableColumns) {
                                add(
                                    Action(column.header) {
                                        toggleMarkAllWithSameValue(column)
                                    },
                                )
                            }
                        },
                    )
                }
            }
        }
    }

    private var comparison = ThreadComparisonPane(threadDumps.size, threadDumps[0].version)

    private val threadDumpCheckboxList = ThreadDumpCheckboxList(paths).apply {
        isVisible = !mainTable.model.isSingleContext
    }

    private var listModelsAdjusting = false

    private val exportMenu = run {
        val firstThreadDump = threadDumps.first()
        val fileName = "threaddump_${firstThreadDump.version}_${firstThreadDump.hashCode()}"
        exportMenu(fileName) { mainTable.model }
    }

    private val exportButton = JMenuBar().apply {
        add(exportMenu)
        exportMenu.isEnabled = mainTable.model.isSingleContext
    }

    private fun filter(thread: Thread?): Boolean {
        if (thread == null) {
            return false
        }

        if (thread.state.name !in stateList.checkBoxListSelectedValues ||
            thread.system !in systemList.checkBoxListSelectedValues ||
            thread.pool !in poolList.checkBoxListSelectedValues
        ) {
            return false
        }

        val query = searchField.text ?: return true

        return thread.id.toString().contains(query) ||
            thread.name.contains(query, ignoreCase = true) ||
            thread.system != null && thread.system.contains(query, ignoreCase = true) ||
            thread.scope != null && thread.scope.contains(query, ignoreCase = true) ||
            thread.state.name.contains(query, ignoreCase = true) ||
            thread.stacktrace.any { stack -> stack.contains(query, ignoreCase = true) }
    }

    private fun updateData() {
        BACKGROUND.launch {
            val filteredThreadDumps = currentLifespanList.filter { lifespan ->
                lifespan.any(::filter)
            }

            EDT_SCOPE.launch {
                val selectedID = if (!mainTable.selectionModel.isSelectionEmpty) {
                    /* Maintain selection when model changes */
                    val previousSelectedIndex = mainTable.convertRowIndexToModel(mainTable.selectedRow)
                    mainTable.model[previousSelectedIndex, mainTable.model.columns.id]
                } else {
                    null
                }

                val sortedColumnIdentifier = mainTable.sortedColumn.identifier
                val sortOrder = mainTable.getSortOrder(sortedColumnIdentifier)

                val newModel = ThreadModel(filteredThreadDumps)
                mainTable.columnFactory = newModel.columns.toColumnFactory()
                mainTable.model = newModel
                mainTable.createDefaultColumnsFromModel()
                exportMenu.isEnabled = newModel.isSingleContext

                if (selectedID != null) {
                    val newSelectedIndex = mainTable.model.threadData.indexOfFirst { lifespan ->
                        selectedID in lifespan.mapNotNull { thread -> thread?.id }
                    }
                    if (newSelectedIndex > -1) {
                        val newSelectedViewIndex = mainTable.convertRowIndexToView(newSelectedIndex)
                        mainTable.selectionModel.setSelectionInterval(0, newSelectedViewIndex)
                        mainTable.scrollRectToVisible(Rectangle(mainTable.getCellRect(newSelectedViewIndex, 0, true)))
                    }
                }

                // Set visible and/or sort by previously sorted column
                val columnExt: TableColumnExt? = mainTable.getColumnExt(sortedColumnIdentifier)
                if (columnExt != null) {
                    columnExt.isVisible = true
                    mainTable.setSortOrder(sortedColumnIdentifier, sortOrder)
                }
            }
        }
    }

    init {
        name = if (mainTable.model.isSingleContext) {
            paths.first().name
        } else {
            "[${paths.size}] " + paths.fold(paths.first().nameWithoutExtension) { acc, next ->
                acc.commonPrefixWith(next.nameWithoutExtension)
            }
        }

        toolTipText = paths.joinToString("\n", transform = Path::name)

        poolList.selectAll()
        poolList.checkBoxListSelectionModel.bind()
        stateList.selectAll()
        stateList.checkBoxListSelectionModel.bind()
        systemList.selectAll()
        systemList.checkBoxListSelectionModel.bind()

        searchField.addActionListener {
            updateData()
        }

        threadDumpCheckboxList.checkBoxListSelectionModel.apply {
            addListSelectionListener { event ->
                if (!event.valueIsAdjusting) {
                    listModelsAdjusting = true

                    val selectedThreadDumps = List(threadDumps.size) { i ->
                        if (isSelectedIndex(i + 1)) {
                            threadDumps[i]
                        } else {
                            null
                        }
                    }
                    visibleThreadDumps = selectedThreadDumps
                    listModelsAdjusting = false
                }
            }
        }

        mainTable.selectionModel.apply {
            addListSelectionListener {
                if (!it.valueIsAdjusting) {
                    val selectedRowIndices = mainTable.selectedRowIndices()
                    if (selectedRowIndices.isNotEmpty()) {
                        comparison.threads = mainTable.model.threadData[selectedRowIndices.first()]
                    } else {
                        comparison.threads = List(threadDumps.size) { null }
                    }
                }
            }
        }

        comparison.addBlockerSelectedListener { selectedID ->
            for (i in 0 until mainTable.model.rowCount) {
                if (selectedID == mainTable.model[i, mainTable.model.columns.id]) {
                    val rowIndex = mainTable.convertRowIndexToView(i)
                    mainTable.selectionModel.setSelectionInterval(0, rowIndex)
                    mainTable.scrollRectToVisible(Rectangle(mainTable.getCellRect(rowIndex, 0, true)))
                    break
                }
            }
        }

        val sortButtons = ButtonGroup().apply {
            val countDescButton = sortButton(NUMERIC_SORT_DESCENDING, "Sort by count (descending)", byCountDesc)
            add(countDescButton)
            setSelected(countDescButton.model, true)

            add(sortButton(NUMERIC_SORT_ASCENDING, "Sort by count (ascending)", byCountAsc))
            add(sortButton(NATURAL_SORT_ASCENDING, "Sort A-Z", byNameAsc))
            add(sortButton(NATURAL_SORT_DESCENDING, "Sort Z-A", byNameDesc))
        }

        add(JLabel("Version: ${threadDumps.first().version}"))
        add(threadDumpCheckboxList, "gapleft 20px, pushx, growx, shpx 200")
        add(exportButton, "gapright 8")
        add(searchField, "wmin 300, wrap")
        add(
            JSplitPane(
                JSplitPane.VERTICAL_SPLIT,
                JPanel(MigLayout("ins 0, fill, flowy")).apply {
                    val sortGroupEnumeration = sortButtons.elements
                    add(sortGroupEnumeration.nextElement(), "split ${sortButtons.buttonCount}, flowx")
                    for (element in sortGroupEnumeration) {
                        add(element)
                    }
                    add(FlatScrollPane(stateList), "w 220, h 100!")
                    // if all the thread dumps are "unassigned", no need to add the system selector
                    if (systemList.model.size > 2) {
                        add(FlatScrollPane(systemList), "w 220, growy")
                    }
                    add(FlatScrollPane(poolList), "w 220, pushy 300, growy")
                    add(FlatScrollPane(mainTable), "newline, spany, pushx, grow")
                },
                comparison,
            ).apply {
                resizeWeight = 0.5
                isOneTouchExpandable = true
            },
            "push, grow, span",
        )
    }

    private val initialized = true

    override val icon = MultiThreadViewer.icon

    private fun sortButton(icon: Icon, description: String, comparator: FilterComparator): JToggleButton {
        return JToggleButton(
            Action(
                description = description,
                icon = icon,
            ) {
                stateList.model.comparator = comparator
                poolList.model.comparator = comparator
                systemList.model.comparator = comparator
            },
        )
    }

    override fun customizePopupMenu(menu: JPopupMenu) {
        menu.addSeparator()
        menu.add(
            Action(name = "Open in External Editor") {
                val desktop = Desktop.getDesktop()
                paths.forEach { desktop.open(it.toFile()) }
            },
        )
    }

    private fun CheckBoxListSelectionModel.bind() {
        addListSelectionListener { event ->
            if (!event.valueIsAdjusting && !listModelsAdjusting) {
                updateData()
            }
        }
    }

    companion object {
        private val BACKGROUND = CoroutineScope(Dispatchers.Default)

        private val NATURAL_SORT_ASCENDING = FlatSVGIcon("icons/bx-sort-a-z.svg")
        private val NATURAL_SORT_DESCENDING = FlatSVGIcon("icons/bx-sort-z-a.svg")
        private val NUMERIC_SORT_ASCENDING = FlatSVGIcon("icons/bx-sort-up.svg")
        private val NUMERIC_SORT_DESCENDING = FlatSVGIcon("icons/bx-sort-down.svg")

        private fun List<ThreadDump?>.toLifespanList(): List<ThreadLifespan> {
            val idsToLifespans = mutableMapOf<Int, Array<Thread?>>()
            forEachIndexed { i, threadDump ->
                for (thread in threadDump?.threads.orEmpty()) {
                    val array = idsToLifespans.getOrPut(thread.id) { arrayOfNulls(size) }
                    array[i] = thread
                    val distinctNames = array.mapNotNull { it?.name }.distinct()
                    require(distinctNames.size == 1) {
                        """Thread dumps must be from the same gateway and runtime instance.
                           Thread dump number ${i + 1} caused this issue.
                           ID ${thread.id} differs.
                        """.trimMargin()
                    }
                }
            }
            return idsToLifespans.map { it.value.toList() }
        }

        private val classnameRegex = """(.*/)?(?<path>[^\s\d$]*)[.$].*\(.*\)""".toRegex()

        fun Stacktrace.linkify(version: String): List<BodyLine> {
            val (_, classmap) = classMapsByVersion.entries.find { (classMapVersion, _) ->
                classMapVersion in version
            } ?: return map(Detail::BodyLine)

            return map { line ->
                val escapedLine = line.escapeHtml()
                val matchResult = classnameRegex.find(line)

                if (matchResult != null) {
                    val path by matchResult.groups
                    val url = classmap[path.value] as String?
                    BodyLine(escapedLine, url)
                } else {
                    BodyLine(escapedLine)
                }
            }
        }

        fun Thread.toDetail(version: String): Detail = Detail(
            title = name,
            details = mapOf(
                "id" to id.toString(),
            ),
            body = buildList {
                if (blocker != null) {
                    add("waiting for:")
                    add(blocker.toString())
                }

                if (lockedMonitors.isNotEmpty()) {
                    add("locked monitors:")
                    lockedMonitors.forEach { monitor ->
                        if (monitor.frame != null) {
                            add(monitor.frame)
                        }
                        add(monitor.lock.escapeHtml())
                    }
                }

                if (lockedSynchronizers.isNotEmpty()) {
                    add("locked synchronizers:")
                    addAll(lockedSynchronizers.map { BodyLine(it.escapeHtml()) })
                }

                if (stacktrace.isNotEmpty()) {
                    add("stacktrace:")
                    addAll(stacktrace.linkify(version))
                }
            },
        )
    }
}

object MultiThreadViewer : MultiTool, ClipboardTool {
    override val title = "Thread Viewer"
    override val description = "Thread dump (.json or .txt) files"
    override val icon = FlatSVGIcon("icons/bx-file.svg")
    override val extensions = listOf("json", "txt")
    override fun open(path: Path): ToolPanel = open(listOf(path))
    override fun open(paths: List<Path>): ToolPanel {
        return MultiThreadView(paths.sortedWith(compareBy(AlphanumComparator(), Path::name)))
    }

    override fun open(data: String): ToolPanel {
        val tempFile = Files.createTempFile("kindling", "cb")
        data.byteInputStream().use { threadDump ->
            tempFile.outputStream().use(threadDump::copyTo)
        }
        return open(tempFile)
    }
}
