package io.github.inductiveautomation.kindling.thread

import com.formdev.flatlaf.extras.FlatSVGIcon
import com.jidesoft.comparator.AlphanumComparator
import io.github.inductiveautomation.kindling.core.ClipboardTool
import io.github.inductiveautomation.kindling.core.Detail
import io.github.inductiveautomation.kindling.core.Detail.BodyLine
import io.github.inductiveautomation.kindling.core.Filter
import io.github.inductiveautomation.kindling.core.MultiTool
import io.github.inductiveautomation.kindling.core.Preference
import io.github.inductiveautomation.kindling.core.Preference.Companion.PreferenceCheckbox
import io.github.inductiveautomation.kindling.core.Preference.Companion.preference
import io.github.inductiveautomation.kindling.core.PreferenceCategory
import io.github.inductiveautomation.kindling.core.ToolOpeningException
import io.github.inductiveautomation.kindling.core.ToolPanel
import io.github.inductiveautomation.kindling.core.add
import io.github.inductiveautomation.kindling.thread.comparison.ThreadComparisonPane
import io.github.inductiveautomation.kindling.thread.model.Thread
import io.github.inductiveautomation.kindling.thread.model.ThreadDump
import io.github.inductiveautomation.kindling.thread.model.ThreadLifespan
import io.github.inductiveautomation.kindling.thread.model.ThreadModel
import io.github.inductiveautomation.kindling.thread.model.ThreadModel.MultiThreadColumns
import io.github.inductiveautomation.kindling.thread.model.ThreadModel.SingleThreadColumns
import io.github.inductiveautomation.kindling.utils.Action
import io.github.inductiveautomation.kindling.utils.ColorHighlighter
import io.github.inductiveautomation.kindling.utils.Column
import io.github.inductiveautomation.kindling.utils.EDT_SCOPE
import io.github.inductiveautomation.kindling.utils.FileFilter
import io.github.inductiveautomation.kindling.utils.FileFilterSidebar
import io.github.inductiveautomation.kindling.utils.FlatScrollPane
import io.github.inductiveautomation.kindling.utils.HorizontalSplitPane
import io.github.inductiveautomation.kindling.utils.ReifiedJXTable
import io.github.inductiveautomation.kindling.utils.VerticalSplitPane
import io.github.inductiveautomation.kindling.utils.attachPopupMenu
import io.github.inductiveautomation.kindling.utils.escapeHtml
import io.github.inductiveautomation.kindling.utils.rowIndices
import io.github.inductiveautomation.kindling.utils.selectedRowIndices
import io.github.inductiveautomation.kindling.utils.toBodyLine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.miginfocom.swing.MigLayout
import org.jdesktop.swingx.JXSearchField
import org.jdesktop.swingx.table.ColumnControlButton.COLUMN_CONTROL_MARKER
import java.awt.Desktop
import java.nio.file.Path
import javax.swing.ButtonGroup
import javax.swing.JLabel
import javax.swing.JMenu
import javax.swing.JMenuBar
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.JRadioButton
import javax.swing.ListSelectionModel
import javax.swing.SortOrder
import javax.swing.UIManager
import kotlin.io.path.createTempFile
import kotlin.io.path.inputStream
import kotlin.io.path.name
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.writeText

class MultiThreadView(
    val paths: List<Path>,
) : ToolPanel() {
    private val threadDumps = paths.map { path ->
        ThreadDump.fromStream(path.inputStream()) ?: throw ToolOpeningException("Failed to open $path as a thread dump")
    }

    private val poolPanel = PoolPanel()
    private val systemPanel = SystemPanel()
    private val statePanel = StatePanel()
    private val searchField = JXSearchField("Search")

    private val sidebar = FileFilterSidebar(
        listOf(
            statePanel,
            systemPanel,
            poolPanel,
        ),
        fileData = paths.zip(threadDumps).toMap(),
    )

    private var visibleThreadDumps: List<ThreadDump?> = emptyList()
        set(value) {
            field = value
            threadCountLabel.totalThreads = value.sumOf { it?.threads?.size ?: 0 }
            currentLifespanList = value.toLifespanList()
        }

    private var currentLifespanList: List<ThreadLifespan> = emptyList()
        set(value) {
            field = value
            if (initialized) updateData()
        }

    private val threadCountLabel = object : JLabel() {
        var totalThreads = threadDumps.sumOf { it.threads.size }
            set(value) {
                field = value
                update()
            }
        var visibleThreads = totalThreads
            set(value) {
                field = value
                update()
            }

        init {
            update()
        }

        private fun update() = setText("Showing $visibleThreads of $totalThreads threads")
    }

    private val mainTable: ReifiedJXTable<ThreadModel> = run {
        // populate initial state of all the filter lists
        visibleThreadDumps = threadDumps
        val initialModel = ThreadModel(currentLifespanList)

        ReifiedJXTable(initialModel).apply {
            columnFactory = initialModel.columns.toColumnFactory()
            createDefaultColumnsFromModel()
            setSortOrder(initialModel.columns.id, SortOrder.ASCENDING)

            selectionMode = ListSelectionModel.SINGLE_SELECTION

            addHighlighter(
                ColorHighlighter(
                    UIManager.getColor("Actions.Red"),
                    null,
                ) { _, adapter ->
                    threadDumps.any { threadDump ->
                        model[adapter.row, model.columns.id] in threadDump.deadlockIds
                    }
                },
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
                        statePanel.stateList.select(state.name)
                    }

                    SingleThreadColumns.system, MultiThreadColumns.system -> {
                        val system = model[selectedRowIndex, model.columns.system]
                        if (system != null) {
                            systemPanel.filterList.select(system)
                        }
                    }

                    SingleThreadColumns.pool, MultiThreadColumns.pool -> {
                        val pool = model[selectedRowIndex, model.columns.pool]
                        if (pool != null) {
                            poolPanel.filterList.select(pool)
                        }
                    }
                }
            }

            val clearAllMarks = Action(name = "Clear all marks") {
                for (lifespan in model.threadData) {
                    lifespan.forEach { thread ->
                        thread?.marked = false
                    }
                }
            }
            actionMap.put("$COLUMN_CONTROL_MARKER.clearAllMarks", clearAllMarks)

            attachPopupMenu table@{ event ->
                val rowAtPoint = rowAtPoint(event.point)
                selectionModel.setSelectionInterval(rowAtPoint, rowAtPoint)

                JPopupMenu().apply {
                    val colAtPoint = columnAtPoint(event.point)

                    if (colAtPoint == model.markIndex) {
                        add(clearAllMarks)
                    }

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

    private val exportMenu = run {
        val firstThreadDump = threadDumps.first()
        val fileName = "threaddump_${firstThreadDump.version}_${firstThreadDump.hashCode()}"
        exportMenu(fileName) { mainTable.model }
    }

    private val exportButton = JMenuBar().apply {
        add(exportMenu)
        exportMenu.isEnabled = mainTable.model.isSingleContext
    }

    private val filters = buildList<Filter<Thread?>> {
        addAll(sidebar)

        add { thread -> thread != null }

        add { thread ->
            val query = if (!searchField.text.isNullOrEmpty()) searchField.text else return@add true

            thread!!.id.toString().contains(query) ||
                thread.name.contains(query, ignoreCase = true) ||
                (thread.system != null && thread.system.contains(query, ignoreCase = true)) ||
                (thread.scope != null && thread.scope.contains(query, ignoreCase = true)) ||
                thread.state.name.contains(query, ignoreCase = true) ||
                thread.stacktrace.any { stack -> stack.contains(query, ignoreCase = true) }
        }
    }

    // Setting the model will fire a selection event. This gets around that.
    private var tableModelIsAdjusting = false

    private fun updateData() = EDT_SCOPE.launch {
        val filteredThreadDumps = withContext(Dispatchers.Default) {
            currentLifespanList.filter { lifespan ->
                lifespan.any {
                    filters.all { threadFilter -> threadFilter.filter(it) }
                }
            }
        }

        val selectedID = if (!mainTable.selectionModel.isSelectionEmpty) {
            /* Maintain selection when model changes */
            val previousSelectedIndex = mainTable.convertRowIndexToModel(mainTable.selectedRow)
            mainTable.model[previousSelectedIndex, mainTable.model.columns.id]
        } else {
            null
        }

        val sortedColumnIdentifier = mainTable.sortedColumn?.identifier
        val sortOrder = sortedColumnIdentifier?.let(mainTable::getSortOrder)

        val newModel = ThreadModel(filteredThreadDumps)
        mainTable.columnFactory = newModel.columns.toColumnFactory()

        tableModelIsAdjusting = true
        mainTable.model = newModel
        tableModelIsAdjusting = false

        mainTable.createDefaultColumnsFromModel()
        exportMenu.isEnabled = newModel.isSingleContext

        if (selectedID != null) {
            val newSelectedIndex = mainTable.model.threadData.indexOfFirst { lifespan ->
                selectedID in lifespan.mapNotNull { thread -> thread?.id }
            }
            if (newSelectedIndex > -1) {
                val newSelectedViewIndex = mainTable.convertRowIndexToView(newSelectedIndex)
                mainTable.selectionModel.setSelectionInterval(0, newSelectedViewIndex)
                mainTable.scrollRectToVisible(mainTable.getCellRect(newSelectedViewIndex, 0, true))
            }
        }

        // Set visible and/or sort by previously sorted column
        val columnExt = sortedColumnIdentifier?.let(mainTable::getColumnExt)
        if (columnExt != null) {
            columnExt.isVisible = true
            if (sortOrder != null) {
                mainTable.setSortOrder(sortedColumnIdentifier, sortOrder)
            }
        }

        threadCountLabel.visibleThreads = mainTable.model.threadData.flatten().filterNotNull().size
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

        poolPanel.filterList.selectAll()
        statePanel.stateList.selectAll()
        systemPanel.filterList.selectAll()

        sidebar.forEach { panel ->
            panel.addFilterChangeListener {
                if (!sidebar.filterModelsAreAdjusting) updateData()
            }
        }

        searchField.addActionListener {
            updateData()
        }

        sidebar.addFileFilterChangeListener {
            val selectedFiles = sidebar.selectedFiles
            visibleThreadDumps = threadDumps.map {
                if (it in selectedFiles) it else null
            }
        }

        mainTable.selectionModel.apply {
            addListSelectionListener {
                if (!it.valueIsAdjusting && !tableModelIsAdjusting) {
                    val selectedRowIndices = mainTable.selectedRowIndices()
                    if (selectedRowIndices.isNotEmpty()) {
                        val newThreads = mainTable.model.threadData[selectedRowIndices.first()]
                        if (comparison.threads !== newThreads) {
                            comparison.threads = newThreads
                        }
                    } else {
                        comparison.threads = List(threadDumps.size) { null }
                    }
                }
            }
        }

        comparison.addBlockerSelectedListener { selectedID ->
            for (i in mainTable.model.rowIndices) {
                if (selectedID == mainTable.model[i, mainTable.model.columns.id]) {
                    val rowIndex = mainTable.convertRowIndexToView(i)
                    mainTable.selectionModel.setSelectionInterval(0, rowIndex)
                    mainTable.scrollRectToVisible(mainTable.getCellRect(rowIndex, 0, true))
                    break
                }
            }
        }

        add(JLabel("Version: ${threadDumps.first().version}"))
        add(threadCountLabel)
        add(exportButton, "gapright 8")
        add(searchField, "wmin 300, wrap")
        add(
            VerticalSplitPane(
                HorizontalSplitPane(
                    sidebar,
                    FlatScrollPane(mainTable),
                    resizeWeight = 0.1,
                ),
                comparison,
            ),
            "push, grow, span, wmax 100%",
        )

        comparison.threads = mainTable.model.threadData.first()

        sidebar.selectedIndex = 0
    }

    private val initialized = true

    override val icon = MultiThreadViewer.icon

    override fun customizePopupMenu(menu: JPopupMenu) {
        menu.addSeparator()
        menu.add(
            Action(name = "Open in External Editor") {
                val desktop = Desktop.getDesktop()
                paths.forEach { desktop.open(it.toFile()) }
            },
        )
    }

    companion object {
        private fun List<ThreadDump?>.toLifespanList(): List<ThreadLifespan> {
            val idsToLifespans = mutableMapOf<Long, Array<Thread?>>()
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
                    addAll(stacktrace.map { it.toBodyLine(version) })
                }
            },
        )
    }
}

data object MultiThreadViewer : MultiTool, ClipboardTool, PreferenceCategory {
    override val serialKey = "threadview"
    override val title = "Thread Dump"
    override val description = "Thread Dump (.json, .txt)"
    override val icon = FlatSVGIcon("icons/bx-chip.svg")
    override val extensions: Array<String> = arrayOf("json", "txt")

    override val respectsEncoding = true
    override fun open(path: Path) = open(listOf(path))
    override fun open(paths: List<Path>): ToolPanel = MultiThreadView(paths.sortedWith(compareBy(AlphanumComparator(), Path::name)))

    override fun open(data: String): ToolPanel {
        val tempFile = createTempFile(prefix = "kindling", suffix = "cb")
        tempFile.writeText(data)
        return open(tempFile)
    }

    val ShowNullThreads: Preference<Boolean> = preference(
        name = "Show Null Threads",
        default = false,
        editor = {
            PreferenceCheckbox("Show columns for missing threads")
        },
    )

    val ShowEmptyValues: Preference<Boolean> = preference(
        name = "Show Empty Values",
        default = false,
        editor = {
            PreferenceCheckbox("Show empty values (stacktrace, blockers) as collapsible sections per column")
        },
    )

    val DefaultDiffView: Preference<DiffViewPreference> = preference(
        name = "Default Diff View",
        default = DiffViewPreference.UNIFIED,
        editor = {
            JPanel(MigLayout("ins 0")).apply {
                background = null

                val unifiedOption = JRadioButton(
                    Action("Unified", selected = currentValue == DiffViewPreference.UNIFIED) {
                        currentValue = DiffViewPreference.UNIFIED
                    },
                )

                val sideBySideOption = JRadioButton(
                    Action("Side-by-side", selected = currentValue == DiffViewPreference.SIDE_BY_SIDE) {
                        currentValue = DiffViewPreference.SIDE_BY_SIDE
                    },
                )

                ButtonGroup().apply {
                    add(unifiedOption)
                    add(sideBySideOption)
                }

                add(unifiedOption)
                add(sideBySideOption)
            }
        },
    )

    enum class DiffViewPreference {
        UNIFIED,
        SIDE_BY_SIDE,
    }

    override val displayName = "Thread View"
    override val preferences = listOf(ShowNullThreads, ShowEmptyValues, DefaultDiffView)
}
