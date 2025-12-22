package io.github.inductiveautomation.kindling.log

import io.github.inductiveautomation.kindling.core.Detail.BodyLine
import io.github.inductiveautomation.kindling.core.DetailsPane
import io.github.inductiveautomation.kindling.core.Filter
import io.github.inductiveautomation.kindling.core.FilterPanel
import io.github.inductiveautomation.kindling.core.Kindling.Preferences.Advanced.HyperlinkStrategy
import io.github.inductiveautomation.kindling.core.Kindling.Preferences.General.ShowFullLoggerNames
import io.github.inductiveautomation.kindling.core.Kindling.Preferences.General.UseHyperlinks
import io.github.inductiveautomation.kindling.core.LinkHandlingStrategy
import io.github.inductiveautomation.kindling.core.Timezone
import io.github.inductiveautomation.kindling.core.ToolOpeningException
import io.github.inductiveautomation.kindling.core.ToolPanel
import io.github.inductiveautomation.kindling.utils.Action
import io.github.inductiveautomation.kindling.utils.ColorHighlighter
import io.github.inductiveautomation.kindling.utils.EDT_SCOPE
import io.github.inductiveautomation.kindling.utils.FilterSidebar
import io.github.inductiveautomation.kindling.utils.FlatActionIcon
import io.github.inductiveautomation.kindling.utils.FlatScrollPane
import io.github.inductiveautomation.kindling.utils.HorizontalSplitPane
import io.github.inductiveautomation.kindling.utils.MajorVersion
import io.github.inductiveautomation.kindling.utils.ReifiedJXTable
import io.github.inductiveautomation.kindling.utils.VerticalSplitPane
import io.github.inductiveautomation.kindling.utils.attachPopupMenu
import io.github.inductiveautomation.kindling.utils.configureCellRenderer
import io.github.inductiveautomation.kindling.utils.debounce
import io.github.inductiveautomation.kindling.utils.maxSelectedIndex
import io.github.inductiveautomation.kindling.utils.minSelectedIndex
import io.github.inductiveautomation.kindling.utils.selectedRowIndices
import io.github.inductiveautomation.kindling.utils.toBodyLine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.miginfocom.swing.MigLayout
import org.jdesktop.swingx.JXSearchField
import org.jdesktop.swingx.table.ColumnControlButton.COLUMN_CONTROL_MARKER
import java.awt.BorderLayout
import java.util.Vector
import javax.swing.BorderFactory
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.JSeparator
import javax.swing.JToggleButton
import javax.swing.ListSelectionModel
import javax.swing.SortOrder
import javax.swing.UIManager
import kotlin.time.Duration.Companion.milliseconds
import io.github.inductiveautomation.kindling.core.Detail as DetailEvent

typealias LogFilter = Filter<LogEvent>

sealed class LogPanel<T : LogEvent>(
    /**
     * Pass a **sorted** list of LogEvents, in ascending order.
     */
    rawData: List<T>,
    private val columnList: LogColumnList<T>,
) : ToolPanel("ins 0, fill, hidemode 3") {
    protected val rawData: MutableList<T> = rawData.sortedBy(LogEvent::timestamp).toMutableList()

    protected var selectedData: List<T> = rawData
        set(value) {
            field = value.sortedBy(LogEvent::timestamp)
            footer.totalRows = value.size
            updateData()
        }

    init {
        if (rawData.isEmpty()) {
            throw ToolOpeningException("Opening an empty log file is pointless")
        }
    }

    protected val header = Header()

    private val footer = Footer(selectedData.size)

    val table = run {
        val initialModel = createModel(rawData)
        ReifiedJXTable(initialModel, columnList).apply {
            setSortOrder(initialModel.columns.Timestamp, SortOrder.ASCENDING)
        }
    }

    private val tableScrollPane = FlatScrollPane(table)

    abstract val sidebar: FilterSidebar<T>

    private val sidebarContainer = JPanel(BorderLayout())

    protected fun addSidebar(sidebar: FilterSidebar<T>) {
        sidebarContainer.add(sidebar, BorderLayout.CENTER)

        filters.addAll(sidebar)
    }

    private val details = DetailsPane()

    protected val filters = mutableListOf<Filter<T>>(
        object : Filter<T> {
            override fun filter(item: T): Boolean {
                val behavior = header.markedBehavior.selectedItem as MarkedBehavior
                return when (behavior) {
                    MarkedBehavior.ShowAll -> true
                    MarkedBehavior.OnlyMarked -> item.marked
                    MarkedBehavior.OnlyUnmarked -> !item.marked
                    MarkedBehavior.AlwaysShowMarked -> true
                }
            }
        },
    )

    private val dataUpdater = debounce(50.milliseconds, BACKGROUND) {
        val selectedEvents = table.selectedRowIndices().map { row -> table.model[row].hashCode() }
        val behavior = header.markedBehavior.selectedItem as? MarkedBehavior ?: MarkedBehavior.ShowAll
        val filteredData = selectedData.filter { event ->
            filters.all { it.filter(event) } || (behavior == MarkedBehavior.AlwaysShowMarked && event.marked)
        }

        EDT_SCOPE.launch {
            table.apply {
                model = createModel(filteredData)

                selectionModel.valueIsAdjusting = true
                model.data.forEachIndexed { index, event ->
                    if (event.hashCode() in selectedEvents) {
                        val viewIndex = convertRowIndexToView(index)
                        addRowSelectionInterval(viewIndex, viewIndex)
                    }
                }
                selectionModel.valueIsAdjusting = false
            }
        }
    }

    protected fun updateData() = dataUpdater()

    fun reset() {
        sidebar.forEach(FilterPanel<*>::reset)
        header.search.text = null
    }

    private fun createModel(rawData: List<T>): LogsModel<out T> = LogsModel(rawData, columnList)

    override val icon: Icon = LogViewer.icon

    private fun getNextMarkedIndex(forward: Boolean): Int? {
        val viewRowCount = table.rowSorter.viewRowCount

        val indicesToSearch = if (forward) {
            val startIndex = table.selectionModel.minSelectedIndex?.let { it + 1 } ?: 0
            startIndex until viewRowCount
        } else {
            val startIndex = table.selectionModel.maxSelectedIndex?.let { it - 1 } ?: (viewRowCount - 1)
            startIndex downTo 0
        }

        return indicesToSearch.find { viewIndex ->
            val modelIndex = table.convertRowIndexToModel(viewIndex)
            table.model.data[modelIndex].marked
        }
    }

    private val markHighlighter = ColorHighlighter(
        fgSupplier = { UIManager.getColor("Table.selectionForeground") },
        bgSupplier = { UIManager.getColor("Table.cellFocusColor") },
        predicate = { _, adapter ->
            header.highlightMarked.isSelected &&
                !table.isRowSelected(adapter.row) &&
                table.model[table.convertRowIndexToModel(adapter.row)].marked
        },
    )

    init {
        @Suppress("LeakingThis")
        add(
            VerticalSplitPane(
                HorizontalSplitPane(
                    sidebarContainer,
                    JPanel(MigLayout("ins 0, fill")).apply {
                        add(header, "wrap, growx")
                        add(tableScrollPane, "grow, push")
                    },
                    resizeWeight = 0.1,
                ),
                details,
            ),
            "wrap, push, grow",
        )
        @Suppress("LeakingThis")
        add(footer, "growx, spanx 2")

        table.apply {
            selectionModel.addListSelectionListener { selectionEvent ->
                if (!selectionEvent.valueIsAdjusting) {
                    selectionModel.updateDetails()
                }
                footer.selectedRows = selectionModel.minSelectionIndex + 1..selectionModel.maxSelectionIndex + 1
            }
            addPropertyChangeListener("model") {
                footer.displayedRows = model.rowCount
            }

            val clearAllMarks =
                Action("Clear all marks") {
                    model.markRows { false }
                }
            actionMap.put(
                "$COLUMN_CONTROL_MARKER.clearAllMarks",
                clearAllMarks,
            )
            attachPopupMenu { mouseEvent ->
                val rowAtPoint = rowAtPoint(mouseEvent.point)
                if (rowAtPoint != -1) {
                    addRowSelectionInterval(rowAtPoint, rowAtPoint)
                }
                val colAtPoint = columnAtPoint(mouseEvent.point)
                if (colAtPoint != -1) {
                    JPopupMenu().apply {
                        val column = model.columns[convertColumnIndexToModel(colAtPoint)]
                        val event = model[convertRowIndexToModel(rowAtPoint)]
                        for (filterPanel in sidebar) {
                            filterPanel.customizePopupMenu(this, column, event)
                        }

                        if (colAtPoint == model.markIndex) {
                            add(clearAllMarks)
                        }

                        if (column == SystemLogColumns.Message || column == WrapperLogColumns.Message) {
                            add(
                                Action("Mark all with same message") {
                                    model.markRows { row ->
                                        if (row.marked) {
                                            null // Leave existing marked messages marked
                                        } else {
                                            row.message == event.message
                                        }
                                    }
                                },
                            )
                        }

                        if (event.stacktrace.isNotEmpty()) {
                            add(
                                Action("Mark all with same stacktrace") {
                                    model.markRows { row ->
                                        (row.stacktrace == event.stacktrace).takeIf { it }
                                    }
                                },
                            )
                        }

                        if (column == SystemLogColumns.Thread && event is SystemLogEvent) {
                            add(
                                Action("Mark all ${event.thread} events") {
                                    model.markRows { row ->
                                        ((row as SystemLogEvent).thread == event.thread).takeIf { it }
                                    }
                                },
                            )
                        }
                    }.takeIf { it.componentCount > 0 }
                } else {
                    null
                }
            }

            addHighlighter(markHighlighter)
        }

        header.apply {
            search.addActionListener {
                updateData()
            }
            version.addActionListener {
                table.selectionModel.updateDetails()
            }
            markedBehavior.addActionListener {
                updateData()
            }
            highlightMarked.addActionListener {
                table.repaint()
            }

            fun updateSelection(viewIndex: Int) {
                if (viewIndex < 0 || viewIndex >= table.rowCount) return
                table.selectionModel.setSelectionInterval(viewIndex, viewIndex)
                val cellRect = table.getCellRect(viewIndex, 0, true)
                table.scrollRectToVisible(cellRect)
            }
            clearMarked.addActionListener {
                table.model.markRows { false }
                updateData()
            }
            prevMarked.addActionListener {
                getNextMarkedIndex(forward = false)?.let(::updateSelection)
            }
            nextMarked.addActionListener {
                getNextMarkedIndex(forward = true)?.let(::updateSelection)
            }
        }

        ShowFullLoggerNames.addChangeListener {
            table.model.fireTableDataChanged()
        }

        HyperlinkStrategy.addChangeListener {
            // if the link strategy changes, we need to rebuild all the hyperlinks
            table.selectionModel.updateDetails()
        }

        Timezone.Default.addChangeListener {
            table.model.fireTableDataChanged()
        }
    }

    override fun customizePopupMenu(menu: JPopupMenu) {
        menu.add(
            exportMenu { table.model },
        )
    }

    private fun ListSelectionModel.updateDetails() {
        details.events =
            selectedIndices.filter { isSelectedIndex(it) }
                .map { table.convertRowIndexToModel(it) }
                .map { row -> table.model[row] }
                .map { event ->
                    DetailEvent(
                        title = when (event) {
                            is SystemLogEvent -> "${Timezone.Default.format(event.timestamp)} ${event.thread}"
                            else -> Timezone.Default.format(event.timestamp)
                        },
                        message = event.message,
                        body = event.stacktrace.map { element ->
                            if (UseHyperlinks.currentValue) {
                                element.toBodyLine((header.version.selectedItem as MajorVersion).version + ".0")
                            } else {
                                BodyLine(element)
                            }
                        },
                        details = when (event) {
                            is SystemLogEvent -> event.mdc.associate { (key, value) -> key to value }
                            else -> emptyMap()
                        },
                    )
                }
    }

    protected class Header : JPanel(MigLayout("ins 0, fill, hidemode 3")) {
        val search = JXSearchField("")

        val version: JComboBox<MajorVersion> =
            JComboBox(Vector(MajorVersion.entries)).apply {
                selectedItem = MajorVersion.EightOne
                configureCellRenderer { _, value, _, _, _ ->
                    text = "${value?.version}.*"
                }
            }
        private val versionLabel = JLabel("Version")

        private val versionPanel = JPanel(MigLayout("fill, ins 0 2 0 2")).apply {
            border = BorderFactory.createTitledBorder("Stacktrace Links")
            add(versionLabel)
            add(version, "growy")
        }

        val highlightMarked = JToggleButton(FlatActionIcon("icons/bx-highlight.svg")).apply {
            toolTipText = "Highlight all marked log events"
        }
        val clearMarked = JButton(FlatActionIcon("icons/bxs-eraser.svg")).apply {
            toolTipText = "Clear all visible marks"
        }
        val prevMarked = JButton(FlatActionIcon("icons/bx-arrow-up.svg")).apply {
            toolTipText = "Jump to previous marked event"
        }
        val nextMarked = JButton(FlatActionIcon("icons/bx-arrow-down.svg")).apply {
            toolTipText = "Jump to next marked event"
        }

        @Suppress("EnumValuesSoftDeprecate") // not a performance sensitive enum.values() call
        val markedBehavior = JComboBox(MarkedBehavior.values()).apply {
            selectedItem = MarkedBehavior.ShowAll

            configureCellRenderer { _, value, _, _, _ ->
                text = value?.displayName.orEmpty()
            }
        }

        private val markedPanel = JPanel(MigLayout("fill, ins 0 2 0 2")).apply {
            border = BorderFactory.createTitledBorder("Marking")
            add(prevMarked)
            add(nextMarked)
            add(markedBehavior, "growy")
            add(clearMarked)
            add(highlightMarked)
        }

        private val searchPanel = JPanel(MigLayout("fill, ins 0 2 0 2")).apply {
            border = BorderFactory.createTitledBorder("Search")
            add(search, "grow")
        }

        private fun updateVersionVisibility() {
            val isVisible =
                UseHyperlinks.currentValue && HyperlinkStrategy.currentValue == LinkHandlingStrategy.OpenInBrowser
            versionPanel.isVisible = isVisible
        }

        init {
            add(markedPanel, "cell 0 0, growy")
            add(versionPanel, "cell 0 0, growy")
            add(searchPanel, "cell 0 0, grow, push")
            updateVersionVisibility()
            UseHyperlinks.addChangeListener { updateVersionVisibility() }
            HyperlinkStrategy.addChangeListener { updateVersionVisibility() }
        }
    }

    private class Footer(totalRows: Int) : JPanel(MigLayout("ins 2 4 0 4, fill, gap 10")) {
        var displayedRows = totalRows
            set(value) {
                field = value
                events.text = "Showing $value of $totalRows events"
            }

        var totalRows: Int = totalRows
            set(value) {
                field = value
                events.text = "Showing $displayedRows of $value events"
            }

        var selectedRows: IntRange? = null
            set(value) {
                field = value
                selectedRow.text = "Selected Row(s): $value"
            }

        private val events = JLabel("Showing $displayedRows of $totalRows events")
        private val selectedRow = JLabel(
            "Selected Row(s): ${selectedRows?.joinToString(prefix = "[", postfix = "]") ?: "None"}",
        )

        init {
            add(events, "growx")
            add(JSeparator(JSeparator.VERTICAL), "h 10!")
            add(selectedRow, "growx, pushx")
        }
    }

    companion object {
        private val BACKGROUND = CoroutineScope(Dispatchers.Default)
    }

    protected enum class MarkedBehavior(val displayName: String) {
        ShowAll("Show All Events"),
        OnlyMarked("Only Show Marked"),
        OnlyUnmarked("Only Show Unmarked"),
        AlwaysShowMarked("Always Show Marked"),
    }
}
