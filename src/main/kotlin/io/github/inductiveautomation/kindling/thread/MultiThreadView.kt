package io.github.inductiveautomation.kindling.thread

import com.formdev.flatlaf.extras.FlatSVGIcon
import com.jidesoft.comparator.AlphanumComparator
import io.github.inductiveautomation.kindling.core.ClipboardTool
import io.github.inductiveautomation.kindling.core.Detail
import io.github.inductiveautomation.kindling.core.Detail.BodyLine
import io.github.inductiveautomation.kindling.core.Filter
import io.github.inductiveautomation.kindling.core.Kindling.Preferences.Experimental.enableMachineLearning
import io.github.inductiveautomation.kindling.core.MultiTool
import io.github.inductiveautomation.kindling.core.Preference
import io.github.inductiveautomation.kindling.core.Preference.Companion.PreferenceCheckbox
import io.github.inductiveautomation.kindling.core.Preference.Companion.preference
import io.github.inductiveautomation.kindling.core.PreferenceCategory
import io.github.inductiveautomation.kindling.core.ToolOpeningException
import io.github.inductiveautomation.kindling.core.ToolPanel
import io.github.inductiveautomation.kindling.core.add
import io.github.inductiveautomation.kindling.thread.model.MachineLearningModel
import io.github.inductiveautomation.kindling.thread.model.MachineLearningModel.evaluator
import io.github.inductiveautomation.kindling.thread.model.Thread
import io.github.inductiveautomation.kindling.thread.model.ThreadDump
import io.github.inductiveautomation.kindling.thread.model.ThreadLifespan
import io.github.inductiveautomation.kindling.thread.model.ThreadModel
import io.github.inductiveautomation.kindling.thread.model.ThreadModel.MultiThreadColumns
import io.github.inductiveautomation.kindling.thread.model.ThreadModel.SingleThreadColumns
import io.github.inductiveautomation.kindling.utils.Action
import io.github.inductiveautomation.kindling.utils.Column
import io.github.inductiveautomation.kindling.utils.EDT_SCOPE
import io.github.inductiveautomation.kindling.utils.FileFilter
import io.github.inductiveautomation.kindling.utils.FilterModel
import io.github.inductiveautomation.kindling.utils.FilterSidebar
import io.github.inductiveautomation.kindling.utils.FlatScrollPane
import io.github.inductiveautomation.kindling.utils.HorizontalSplitPane
import io.github.inductiveautomation.kindling.utils.ReifiedJXTable
import io.github.inductiveautomation.kindling.utils.VerticalSplitPane
import io.github.inductiveautomation.kindling.utils.attachPopupMenu
import io.github.inductiveautomation.kindling.utils.escapeHtml
import io.github.inductiveautomation.kindling.utils.rowIndices
import io.github.inductiveautomation.kindling.utils.selectedRowIndices
import io.github.inductiveautomation.kindling.utils.toBodyLine
import io.github.inductiveautomation.kindling.utils.transferTo
import io.github.inductiveautomation.kindling.utils.uploadMultipleToWeb
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jdesktop.swingx.JXSearchField
import org.jdesktop.swingx.decorator.ColorHighlighter
import org.jdesktop.swingx.table.ColumnControlButton.COLUMN_CONTROL_MARKER
import org.jpmml.evaluator.ProbabilityDistribution
import java.awt.Color
import java.awt.Desktop
import java.awt.Rectangle
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.JLabel
import javax.swing.JMenu
import javax.swing.JMenuBar
import javax.swing.JPopupMenu
import javax.swing.ListSelectionModel
import javax.swing.SortOrder
import javax.swing.SwingUtilities
import javax.swing.UIManager
import kotlin.io.path.bufferedReader
import kotlin.io.path.extension
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

    private val poolPanel = PoolPanel()
    private val systemPanel = SystemPanel()
    private val statePanel = StatePanel()
    private val searchField = JXSearchField("Search")

    private var visibleThreadDumps: List<ThreadDump?> = emptyList()
        set(value) {
            field = value
            threadCountLabel.totalThreads = value.sumOf { it?.threads?.size ?: 0 }
            currentLifespanList = value.toLifespanList()
        }

    private var currentLifespanList: List<ThreadLifespan> = emptyList()
        set(value) {
            field = value
            val allThreads = value.flatten().filterNotNull()
            if (allThreads.isNotEmpty()) {
                statePanel.stateList.setModel(FilterModel(allThreads.groupingBy { it.state.name }.eachCount()))
                systemPanel.filterList.setModel(FilterModel(allThreads.groupingBy(Thread::system).eachCount()))
                poolPanel.filterList.setModel(FilterModel(allThreads.groupingBy(Thread::pool).eachCount()))
            }
            if (initialized) {
                updateData()
            }
        }

    private var threadsOfInterest: List<Thread> = emptyList()

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
                    { _, adapter ->
                        threadDumps.any { threadDump ->
                            val rowNum = convertRowIndexToModel(adapter.row)
                            model[rowNum, model.columns.id] in threadDump.deadlockIds
                        }
                    },
                    UIManager.getColor("Actions.Red"),
                    null,
                ),
            )

            addHighlighter(
                ColorHighlighter(
                    { _, adapter ->
                        val rowNum = convertRowIndexToModel(adapter.row)
                        model[rowNum, model.columns.id] in threadsOfInterest.map(Thread::id)
                    },
                    UIManager.getColor("Objects.YellowDark"),
                    Color.BLACK,
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

            actionMap.apply {
                put("$COLUMN_CONTROL_MARKER}.clearAllMarks", clearAllMarks)
                put(
                    "$COLUMN_CONTROL_MARKER.clearAllMarks",
                    Action(name = "Clear All Marks") {
                        for (lifespan in model.threadData) {
                            lifespan.forEach { thread ->
                                thread?.marked = false
                            }
                        }
                    },
                )
                put(
                    "$COLUMN_CONTROL_MARKER.markThreadsOfInterest",
                    Action("Mark Threads of Interest") {
                        markThreadsOfInterest()
                    },
                )
            }

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

    init {
        updateThreadsOfInterest()
    }

    private val sidebar = FilterSidebar(
        statePanel,
        systemPanel,
        poolPanel,
    )

    private var comparison = ThreadComparisonPane(threadDumps.size, threadDumps[0].version)

    private val threadDumpCheckboxList = ThreadDumpCheckboxList(paths).apply {
        isVisible = !mainTable.model.isSingleContext
    }

    private var listModelIsAdjusting = false

    private val exportMenu = run {
        val firstThreadDump = threadDumps.first()
        val fileName = "threaddump_${firstThreadDump.version}_${firstThreadDump.hashCode()}"
        exportMenu(fileName) { mainTable.model }
    }

    private val filters = buildList<Filter<Thread?>> {
        addAll(sidebar.filterPanels)

        add { thread -> thread != null }

        add { thread ->
            val query = if (!searchField.text.isNullOrEmpty()) searchField.text else return@add true

            thread!!.id.toString().contains(query) ||
                thread.name.contains(query, ignoreCase = true) ||
                thread.system != null && thread.system.contains(query, ignoreCase = true) ||
                thread.scope != null && thread.scope.contains(query, ignoreCase = true) ||
                thread.state.name.contains(query, ignoreCase = true) ||
                thread.stacktrace.any { stack -> stack.contains(query, ignoreCase = true) }
        }
    }

    private fun updateData() {
        BACKGROUND.launch {
            val filteredThreadDumps = currentLifespanList.filter { lifespan ->
                lifespan.any {
                    filters.all { threadFilter -> threadFilter.filter(it) }
                }
            }

            EDT_SCOPE.launch {
                val selectedID = if (!mainTable.selectionModel.isSelectionEmpty) {
                    /* Maintain selection when model changes */
                    val previousSelectedIndex = mainTable.convertRowIndexToModel(mainTable.selectedRow)
                    mainTable.model[previousSelectedIndex, mainTable.model.columns.id]
                } else {
                    null
                }

                val sortedColumnIdentifier = mainTable.sortedColumn?.identifier
                val sortOrder = sortedColumnIdentifier?.let(mainTable::getSortOrder)

                val newModel = ThreadModel(filteredThreadDumps).apply {
                    addTableModelListener {
                        comparison.updateData()
                    }
                }
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
                val columnExt = sortedColumnIdentifier?.let(mainTable::getColumnExt)
                if (columnExt != null) {
                    columnExt.isVisible = true
                    if (sortOrder != null) {
                        mainTable.setSortOrder(sortedColumnIdentifier, sortOrder)
                    }
                }

                threadCountLabel.visibleThreads = mainTable.model.threadData.flatten().filterNotNull().size
            }
        }
    }

    private val multiExportMenu = JMenu("Export").apply {
        add(
            Action("Export all to Web") {
                val models = threadDumps.map {
                    val fileName = "threaddump_${it.version}_${it.hashCode()}"
                    val model = ThreadModel(listOf(it).toLifespanList())
                    fileName to model
                }
                uploadMultipleToWeb(models)
            },
        )
    }

    private val singleExportMenu = run {
        val selectedThreadDump = threadDumpCheckboxList.selectedValue as ThreadDump?
        val fileName = "threaddump_${selectedThreadDump?.version}_${selectedThreadDump.hashCode()}"
        exportMenu(fileName) { mainTable.model }
    }

    private val exportButton = JMenuBar().apply {
        if (mainTable.model.isSingleContext) {
            add(singleExportMenu)
        } else {
            add(multiExportMenu)
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

        poolPanel.filterList.selectAll()
        statePanel.stateList.selectAll()
        systemPanel.filterList.selectAll()

        sidebar.filterPanels.forEach { panel ->
            panel.addFilterChangeListener {
                if (!listModelIsAdjusting) updateData()
            }
        }

        searchField.addActionListener {
            updateData()
        }

        threadDumpCheckboxList.checkBoxListSelectionModel.apply {
            addListSelectionListener { event ->
                if (!event.valueIsAdjusting) {
                    listModelIsAdjusting = true

                    val selectedThreadDumps = List(threadDumps.size) { i ->
                        if (isSelectedIndex(i + 1)) {
                            threadDumps[i]
                        } else {
                            null
                        }
                    }
                    visibleThreadDumps = selectedThreadDumps
                    listModelIsAdjusting = false
                    exportButton.run {
                        removeAll()
                        if (selectedThreadDumps.filterNotNull().size == 1) {
                            add(singleExportMenu)
                        } else {
                            add(multiExportMenu)
                        }
                        revalidate()
                    }
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

        mainTable.model.addTableModelListener {
            comparison.updateData()
        }

        comparison.apply {
            addBlockerSelectedListener { selectedID ->
                for (i in 0 until mainTable.model.rowCount) {
                    if (selectedID == mainTable.model[i, mainTable.model.columns.id]) {
                        val rowIndex = mainTable.convertRowIndexToView(i)
                        mainTable.selectionModel.setSelectionInterval(0, rowIndex)
                        mainTable.scrollRectToVisible(Rectangle(mainTable.getCellRect(rowIndex, 0, true)))
                        break
                    }
                }
            }
            addThreadMarkedListener {
                mainTable.repaint()
            }
        }

        add(JLabel("Version: ${threadDumps.first().version}"))
        add(threadCountLabel)
        add(threadDumpCheckboxList, "gapleft 20px, pushx, growx, shpx 200")
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
            ).apply {
                resizeWeight = 0.5
                isOneTouchExpandable = true
            },
            "push, grow, span",
        )

        enableMachineLearning.addChangeListener { enable ->
            if (enable) {
                SwingUtilities.invokeLater {
                    MachineLearningModel.verifyPMML()
                }
            }

            BACKGROUND.launch {
                updateThreadsOfInterest()

                EDT_SCOPE.launch {
                    mainTable.repaint()
                }
            }
        }
    }

    private fun updateThreadsOfInterest() {
        threadsOfInterest = if (enableMachineLearning.currentValue) {
            buildList {
                val threads = mainTable.model.threadData

                threads.flatten().filterNotNull().forEach { thread ->
                    val evaluation = evaluator.evaluate(
                        evaluator.inputFields.associate { field ->
                            field.name to field.prepare(thread.getPmmlProperty(field.name))
                        },
                    )
                    val result = (evaluation["marked"] as? ProbabilityDistribution<*>)?.result as? Int ?: 0
                    if (result == 1) add(thread)
                }
            }
        } else {
            emptyList()
        }
    }

    private fun markThreadsOfInterest() = BACKGROUND.launch {
        val model = mainTable.model.threadData

        model.flatten().filterNotNull().forEach { thread ->
            if (thread in threadsOfInterest) {
                thread.marked = true
            }
        }
        mainTable.model.fireTableDataChanged()
    }

    private fun Thread.getPmmlProperty(prop: String): Any? = when (prop) {
        "thread_state" -> state.toString()
        "system" -> system
        "scope" -> scope
        "stacktrace_depth" -> stacktrace.size
        "cpu_usage" -> cpuUsage
        "daemon" -> isDaemon
        "thread_pool" -> pool
        "version" -> threadDumps.first().version
        "thread_id" -> id
        else -> null
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
        private val BACKGROUND = CoroutineScope(Dispatchers.Default)

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
    override val title = "Thread Viewer"
    override val description = "Thread dump (.json or .txt) files"
    override val icon = FlatSVGIcon("icons/bx-file.svg")
    override val filter = FileFilter(
        description = description,
        predicate = { file ->
            file.extension in listOf("json", "txt") &&
                run {
                    val firstTwoLines = buildString {
                        file.bufferedReader().use { br ->
                            append(br.readLine())
                            append(br.readLine())
                        }
                    }
                    "Ignition" in firstTwoLines || "version" in firstTwoLines
                }
        },
    )

    override val respectsEncoding: Boolean = true
    override fun open(path: Path): ToolPanel = open(listOf(path))
    override fun open(paths: List<Path>): ToolPanel {
        return MultiThreadView(paths.sortedWith(compareBy(AlphanumComparator(), Path::name)))
    }

    override fun open(data: String): ToolPanel {
        val tempFile = Files.createTempFile("kindling", "cb")
        data.byteInputStream() transferTo tempFile.outputStream()
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

    override val displayName: String = "Thread View"
    override val key: String = "threadview"
    override val preferences = listOf(ShowNullThreads, ShowEmptyValues)
}
