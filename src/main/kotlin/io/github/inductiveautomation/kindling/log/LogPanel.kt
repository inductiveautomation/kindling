package io.github.inductiveautomation.kindling.log

import com.formdev.flatlaf.extras.FlatSVGIcon
import io.github.inductiveautomation.kindling.core.Detail.BodyLine
import io.github.inductiveautomation.kindling.core.DetailsPane
import io.github.inductiveautomation.kindling.core.Filter
import io.github.inductiveautomation.kindling.core.FilterPanel
import io.github.inductiveautomation.kindling.core.Kindling.Preferences.Advanced.HyperlinkStrategy
import io.github.inductiveautomation.kindling.core.Kindling.Preferences.General.ShowFullLoggerNames
import io.github.inductiveautomation.kindling.core.Kindling.Preferences.General.UseHyperlinks
import io.github.inductiveautomation.kindling.core.LinkHandlingStrategy
import io.github.inductiveautomation.kindling.core.ToolOpeningException
import io.github.inductiveautomation.kindling.core.ToolPanel
import io.github.inductiveautomation.kindling.log.LogViewer.TimeStampFormatter
import io.github.inductiveautomation.kindling.utils.Action
import io.github.inductiveautomation.kindling.utils.EDT_SCOPE
import io.github.inductiveautomation.kindling.utils.FilterSidebar
import io.github.inductiveautomation.kindling.utils.FlatScrollPane
import io.github.inductiveautomation.kindling.utils.HorizontalSplitPane
import io.github.inductiveautomation.kindling.utils.MajorVersion
import io.github.inductiveautomation.kindling.utils.ReifiedJXTable
import io.github.inductiveautomation.kindling.utils.VerticalSplitPane
import io.github.inductiveautomation.kindling.utils.asActionIcon
import io.github.inductiveautomation.kindling.utils.attachPopupMenu
import io.github.inductiveautomation.kindling.utils.configureCellRenderer
import io.github.inductiveautomation.kindling.utils.debounce
import io.github.inductiveautomation.kindling.utils.isSortedBy
import io.github.inductiveautomation.kindling.utils.selectedRowIndices
import io.github.inductiveautomation.kindling.utils.toBodyLine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.miginfocom.swing.MigLayout
import org.jdesktop.swingx.JXSearchField
import org.jdesktop.swingx.table.ColumnControlButton.COLUMN_CONTROL_MARKER
import java.util.Vector
import javax.swing.BorderFactory
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.JSeparator
import javax.swing.ListSelectionModel
import javax.swing.SortOrder
import kotlin.time.Duration.Companion.milliseconds
import io.github.inductiveautomation.kindling.core.Detail as DetailEvent

typealias LogFilter = Filter<LogEvent>

class LogPanel(
    /**
     * Pass a **sorted** list of LogEvents, in ascending order.
     */
    private val rawData: List<LogEvent>,
) : ToolPanel("ins 0, fill, hidemode 3") {
    init {
        if (rawData.isEmpty()) {
            throw ToolOpeningException("Opening an empty log file is pointless")
        }
        if (!rawData.isSortedBy(LogEvent::timestamp)) {
            throw ToolOpeningException("Input data must be sorted by timestamp, ascending")
        }
    }

    private val totalRows: Int = rawData.size

    private val header = Header()

    private val footer = Footer(totalRows)

    private val columnList = if (rawData.first() is SystemLogEvent) {
        SystemLogColumns
    } else {
        WrapperLogColumns
    }

    val table = run {
        val initialModel = createModel(rawData)
        ReifiedJXTable(initialModel, columnList).apply {
            setSortOrder(initialModel.columns.Timestamp, SortOrder.ASCENDING)
        }
    }

    private val tableScrollPane = FlatScrollPane(table)

    private val sidebar = FilterSidebar(
        LoggerNamePanel(rawData),
        LevelPanel(rawData),
        if (rawData.first() is SystemLogEvent) {
            @Suppress("UNCHECKED_CAST")
            MDCPanel(rawData as List<SystemLogEvent>)
        } else {
            null
        },
        if (rawData.first() is SystemLogEvent) {
            @Suppress("UNCHECKED_CAST")
            ThreadPanel(rawData as List<SystemLogEvent>)
        } else {
            null
        },
        TimePanel(
            rawData,
        ),
    )

    private val details = DetailsPane()

    private val filters: List<LogFilter> = buildList {
        for (panel in sidebar.filterPanels) {
            add { event ->
                panel.filter(event) ||
                    (header.markedBehavior.selectedItem == "Always Show Marked" && event.marked)
            }
        }
        add { event ->
            header.markedBehavior.selectedItem != "Only Show Marked" || event.marked
        }
        add { event ->
            val text = header.search.text
            if (text.isNullOrEmpty()) {
                true
            } else {
                when (event) {
                    is SystemLogEvent -> {
                        text in event.message ||
                            event.logger.contains(text, ignoreCase = true) ||
                            event.thread.contains(text, ignoreCase = true) ||
                            event.stacktrace.any { stacktrace ->
                                stacktrace.contains(text, ignoreCase = true)
                            } ||
                            (header.markedBehavior.selectedItem == "Always Show Marked" && event.marked)
                    }

                    is WrapperLogEvent -> {
                        text in event.message ||
                            event.logger.contains(text, ignoreCase = true) ||
                            event.stacktrace.any { stacktrace -> stacktrace.contains(text, ignoreCase = true) } ||
                            (header.markedBehavior.selectedItem == "Always Show Marked" && event.marked)
                    }
                }
            }
        }
    }

    private val dataUpdater = debounce(50.milliseconds, BACKGROUND) {
        val selectedEvents = table.selectedRowIndices().map { row -> table.model[row].hashCode() }
        val filteredData = rawData.filter { event ->
            filters.all { filter -> filter.filter(event) }
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

    private fun updateData() = dataUpdater()

    fun reset() {
        sidebar.filterPanels.forEach(FilterPanel<LogEvent>::reset)
        header.search.text = null
    }

    @Suppress("UNCHECKED_CAST")
    private fun createModel(rawData: List<LogEvent>): LogsModel<out LogEvent> = when (columnList) {
        is WrapperLogColumns -> LogsModel(rawData as List<WrapperLogEvent>, columnList)
        is SystemLogColumns -> LogsModel(rawData as List<SystemLogEvent>, columnList)
    }

    override val icon: Icon? = null

    init {
        add(
            VerticalSplitPane(
                HorizontalSplitPane(
                    sidebar,
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
                        for (filterPanel in sidebar.filterPanels) {
                            filterPanel.customizePopupMenu(this, column, event)
                        }

                        if (colAtPoint == model.markIndex) {
                            add(clearAllMarks)
                        }

                        if (column == SystemLogColumns.Message || column == WrapperLogColumns.Message) {
                            add(
                                Action("Mark all with same message") {
                                    model.markRows { row ->
                                        (row.message == event.message).takeIf { it }
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
        }

        fun getNextMarkedIndex(): Int {
            val currentSelectionIndex = table.selectionModel.selectedIndices?.lastOrNull() ?: 0
            val markedEvents = table.model.data
                .filter { it.marked }
                .sortedBy { table.convertRowIndexToView(table.model.data.indexOf(it)) }
            val rowIndex = when (markedEvents.size) {
                0 -> -1
                1 -> table.model.data.indexOf(markedEvents.first())
                else -> {
                    val nextMarkedEvent =
                        markedEvents.firstOrNull { event ->
                            table.convertRowIndexToView(table.model.data.indexOf(event)) > currentSelectionIndex
                        }
                    if (nextMarkedEvent == null) {
                        table.model.data.indexOf(markedEvents.first())
                    } else {
                        table.model.data.indexOf(nextMarkedEvent)
                    }
                }
            }
            return if (rowIndex != -1) table.convertRowIndexToView(rowIndex) else -1
        }

        fun getPrevMarkedIndex(): Int {
            val currentSelectionIndex = table.selectionModel.selectedIndices?.firstOrNull() ?: 0
            val markedEvents = table.model.data
                .filter { it.marked }
                .sortedBy { table.convertRowIndexToView(table.model.data.indexOf(it)) }
            val rowIndex = when (markedEvents.size) {
                0 -> -1
                1 -> table.model.data.indexOf(markedEvents.first())
                else -> {
                    val prevMarkedEvent =
                        markedEvents.lastOrNull { event ->
                            table.convertRowIndexToView(table.model.data.indexOf(event)) < currentSelectionIndex
                        }
                    if (prevMarkedEvent == null) {
                        table.model.data.indexOf(markedEvents.last())
                    } else {
                        table.model.data.indexOf(prevMarkedEvent)
                    }
                }
            }
            return if (rowIndex != -1) table.convertRowIndexToView(rowIndex) else -1
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

            fun updateSelection(index: Int) {
                table.selectionModel.setSelectionInterval(index, index)
                val rect = table.bounds
                rect.y = index * table.rowHeight
                rect.height = tableScrollPane.height - table.tableHeader.height - 2
                table.scrollRectToVisible(rect)
                table.updateUI()
            }
            clearMarked.addActionListener {
                table.model.markRows { false }
                updateData()
            }
            prevMarked.addActionListener {
                val prevMarkedIndex = getPrevMarkedIndex()
                if (prevMarkedIndex != -1) updateSelection(prevMarkedIndex)
            }
            nextMarked.addActionListener {
                val nextMarkedIndex = getNextMarkedIndex()
                if (nextMarkedIndex != -1) updateSelection(nextMarkedIndex)
            }
        }

        sidebar.filterPanels.forEach { filterPanel ->
            filterPanel.addFilterChangeListener(::updateData)
        }

        ShowFullLoggerNames.addChangeListener {
            table.model.fireTableDataChanged()
        }

        HyperlinkStrategy.addChangeListener {
            // if the link strategy changes, we need to rebuild all the hyperlinks
            table.selectionModel.updateDetails()
        }

        LogViewer.SelectedTimeZone.addChangeListener {
            table.model.fireTableDataChanged()
        }
    }

    private fun ListSelectionModel.updateDetails() {
        details.events =
            selectedIndices.filter { isSelectedIndex(it) }
                .map { table.convertRowIndexToModel(it) }
                .map { row -> table.model[row] }
                .map { event ->
                    DetailEvent(
                        title = when (event) {
                            is SystemLogEvent -> "${TimeStampFormatter.format(event.timestamp)} ${event.thread}"
                            else -> TimeStampFormatter.format(event.timestamp)
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
                            is WrapperLogEvent -> emptyMap()
                        },
                    )
                }
    }

    private class Header : JPanel(MigLayout("ins 0, fill, hidemode 3")) {
        val search = JXSearchField("")

        val version: JComboBox<MajorVersion> =
            JComboBox(Vector(MajorVersion.entries)).apply {
                selectedItem = MajorVersion.EightOne
                configureCellRenderer { _, value, _, _, _ ->
                    text = "${value?.version}.*"
                }
            }
        private val versionLabel = JLabel("Version")

        val versionPanel = JPanel(MigLayout("fill, ins 0 2 0 2")).apply {
            border = BorderFactory.createTitledBorder("Stacktrace Links")
            add(versionLabel)
            add(version, "growy")
        }

        val clearMarked = JButton(FlatSVGIcon("icons/bxs-eraser.svg").asActionIcon()).apply {
            toolTipText = "Clear all visible marks"
        }
        val prevMarked = JButton(FlatSVGIcon("icons/bx-arrow-up.svg").asActionIcon()).apply {
            toolTipText = "Jump to previous marked log event"
        }
        val nextMarked = JButton(FlatSVGIcon("icons/bx-arrow-down.svg").asActionIcon()).apply {
            toolTipText = "Jump to next marked log event"
        }
        val markedBehavior = JComboBox(arrayOf("Show All Events", "Only Show Marked", "Always Show Marked"))

        val markedPanel = JPanel(MigLayout("fill, ins 0 2 0 2")).apply {
            border = BorderFactory.createTitledBorder("Marking")
            add(prevMarked)
            add(nextMarked)
            add(markedBehavior, "growy")
            add(clearMarked)
        }

        val searchPanel = JPanel(MigLayout("fill, ins 0 2 0 2")).apply {
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

    private class Footer(val totalRows: Int) : JPanel(MigLayout("ins 2 4 0 4, fill, gap 10")) {
        var displayedRows = totalRows
            set(value) {
                field = value
                events.text = "Showing $value of $totalRows events"
            }
        var selectedRows: IntRange? = null
            set(value) {
                field = value
                selectedRow.text = "Selected Row(s): $value"
            }

        private val events = JLabel("Showing $displayedRows of $totalRows events")
        private val selectedRow = JLabel("Selected Row(s): ${selectedRows?.toString().orEmpty()}")

        init {
            add(events, "growx")
            add(JSeparator(JSeparator.VERTICAL), "h 10!")
            add(selectedRow, "growx, pushx")
        }
    }

    companion object {
        private val BACKGROUND = CoroutineScope(Dispatchers.Default)
    }
}
