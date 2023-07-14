package io.github.inductiveautomation.kindling.log

import com.formdev.flatlaf.ui.FlatScrollBarUI
import io.github.inductiveautomation.kindling.core.ToolPanel
import io.github.inductiveautomation.kindling.utils.EDT_SCOPE
import io.github.inductiveautomation.kindling.utils.FlatScrollPane
import io.github.inductiveautomation.kindling.utils.ReifiedJXTable
import io.github.inductiveautomation.kindling.utils.attachPopupMenu
import io.github.inductiveautomation.kindling.utils.getValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.miginfocom.swing.MigLayout
import org.jdesktop.swingx.action.AbstractActionExt
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.event.ActionEvent
import java.awt.geom.AffineTransform
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.Temporal
import java.time.temporal.TemporalUnit
import javax.swing.Icon
import javax.swing.JCheckBoxMenuItem
import javax.swing.JComponent
import javax.swing.JMenu
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.JScrollBar
import javax.swing.JSplitPane
import javax.swing.JTabbedPane
import javax.swing.SortOrder
import javax.swing.SwingConstants
import javax.swing.UIManager
import kotlin.math.absoluteValue
import kotlin.reflect.KProperty
import io.github.inductiveautomation.kindling.core.Detail as DetailEvent

class LogPanel(
    private val rawData: List<LogEvent>,
) : ToolPanel("ins 0, fill, hidemode 3") {
    private val totalRows: Int = rawData.size

    var dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss:SSS")
        .withZone(ZoneId.systemDefault())

    private val densityDisplay = GroupingScrollBar()
    private var showDensityDisplay: Boolean = true

    val header = Header(totalRows)
    private val startTime = rawData.minBy { it.timestamp }.timestamp.toEpochMilli()
    private val endTime = rawData.maxBy { it.timestamp }.timestamp.toEpochMilli()
    private var messageFilter = ""
    private var stackTraceFilter = object {
        var enabled = false
        var completeMatch = false
        var stacktrace = emptyList<String>()

        fun enableFilter(completeMatch: Boolean, stacktrace: List<String>) {
            enabled = true
            this.completeMatch = completeMatch
            this.stacktrace = stacktrace
        }
    }
    private var threadFilter = ""

    val table = run {
        val initialModel = createModel(rawData)
        ReifiedJXTable(initialModel, initialModel.columns).apply {
            setSortOrder("Timestamp", SortOrder.ASCENDING)
            val densityDisplayAction = object : AbstractActionExt("Display Density") {
                init {
                    isSelected = showDensityDisplay
                    isStateAction = true
                }

                override fun actionPerformed(e: ActionEvent) {
                    showDensityDisplay = !showDensityDisplay
                    densityDisplay.repaint()
                }
            }
            actionMap.put("column.showLogDensity", densityDisplayAction)

            attachPopupMenu { event ->
                val viewRow = rowAtPoint(event.point)
                val modelRow = convertRowIndexToModel(viewRow)

                JPopupMenu().apply {
                    val model = this@attachPopupMenu.model
                    val selectedLogEvent = model.data[modelRow]

                    add(
                        JMenu("Filter all with same...").apply {
                            model.columns.filterableColumns.forEach { column ->
                                when (column) {
                                    model.columns.Level -> {
                                        logMenuItem(
                                            label = column.header,
                                            condition = !loggerLevelsSidebar.isOnlySelected(selectedLogEvent.level!!.name),
                                            onTrue = { loggerLevelsSidebar.select(selectedLogEvent.level!!.name) },
                                            onFalse = { loggerLevelsSidebar.checkBoxListSelectionModel.setSelectionInterval(0, 0) },
                                        )
                                    }

                                    model.columns.Logger -> {
                                        logMenuItem(
                                            label = column.header,
                                            condition = !loggerNamesSidebar.loggerNamesList.isOnlySelected(selectedLogEvent.logger),
                                            onTrue = { loggerNamesSidebar.loggerNamesList.select(selectedLogEvent.logger) },
                                            onFalse = { loggerNamesSidebar.loggerNamesList.checkBoxListSelectionModel.setSelectionInterval(0, 0) },
                                        )
                                    }

                                    model.columns.Message -> {
                                        logMenuItem(
                                            label = column.header,
                                            condition = messageFilter.isEmpty(),
                                            onTrue = { messageFilter = selectedLogEvent.message },
                                            onFalse = { messageFilter = "" },
                                        )
                                    }

                                    (model.columns as? SystemLogColumns)?.Thread -> {
                                        logMenuItem(
                                            label = column.header,
                                            condition = threadFilter.isEmpty(),
                                            onTrue = { threadFilter = (selectedLogEvent as SystemLogEvent).thread },
                                            onFalse = { threadFilter = "" },
                                        )
                                    }
                                    else -> null
                                }?.let(this::add)
                            }
                            add(
                                JMenu("StackTrace").apply {
                                    val eventStacktrace = selectedLogEvent.stacktrace
                                    if (eventStacktrace.isNotEmpty()) {
                                        add(
                                            logMenuItem(
                                                label = "Complete Match",
                                                condition = !stackTraceFilter.enabled,
                                                onTrue = { stackTraceFilter.enableFilter(true, eventStacktrace) },
                                                onFalse = { stackTraceFilter.enabled = false },
                                            ),
                                        )
                                        add(
                                            logMenuItem(
                                                label = "Partial Match",
                                                condition = !stackTraceFilter.enabled,
                                                onTrue = { stackTraceFilter.enableFilter(false, eventStacktrace) },
                                                onFalse = { stackTraceFilter.enabled = false },
                                            ),
                                        )
                                    } else {
                                        isEnabled = false
                                    }
                                },
                            )
                        },
                    )

                    add(
                        JMenu("Mark/Unmark all with same...").apply {
                            model.columns.filterableColumns.forEach { column ->
                                when(column) {
                                    model.columns.Level -> {
                                        val condition = isMarked(LogEvent::level, selectedLogEvent.level!!.name)
                                        logMenuItem(
                                            label = column.header,
                                            condition = condition,
                                            action = { event ->
                                                if (event.level?.name == selectedLogEvent.level!!.name) {
                                                    event.marked = !condition
                                                }
                                            },
                                        )
                                    }
                                    model.columns.Logger -> {
                                        val condition = isMarked(LogEvent::logger, selectedLogEvent.logger)
                                        logMenuItem(
                                            label = column.header,
                                            condition = condition,
                                            action = { event ->
                                                if (event.logger == selectedLogEvent.logger) {
                                                    event.marked = !condition
                                                }
                                            },
                                        )

                                    }
                                    model.columns.Message -> {
                                        val condition = isMarked(LogEvent::message, selectedLogEvent.message)
                                        logMenuItem(
                                            label = column.header,
                                            condition = condition,
                                            action = { event ->
                                                if (event.message == selectedLogEvent.message) {
                                                    event.marked = !condition
                                                }
                                            },
                                        )
                                    }

                                    (model.columns as SystemLogColumns).Thread -> {
                                        val condition = isMarked(SystemLogEvent::thread, (selectedLogEvent as SystemLogEvent).thread)
                                        logMenuItem(
                                            label = column.header,
                                            condition = condition,
                                            action = { event ->
                                                if ((event as SystemLogEvent).thread == selectedLogEvent.thread) {
                                                    event.marked = !condition
                                                }
                                            },
                                        )
                                    }

                                    else -> null
                                }?.let(this::add)
                            }

                            val eventStacktrace = selectedLogEvent.stacktrace
                            add(
                                JMenu("StackTrace").apply {
                                    if (eventStacktrace.isNotEmpty()) {
                                        val isFullStackMarked = isMarked(LogEvent::stacktrace, eventStacktrace.joinToString("\n"))
                                        add(
                                            logMenuItem(
                                                label = "Complete Match",
                                                condition = isFullStackMarked,
                                                action = {
                                                    if (it.stacktrace == eventStacktrace) {
                                                        it.marked = !isFullStackMarked
                                                    }
                                                },
                                            ),
                                        )
                                        val isPartialStackMarked = isMarked(
                                            LogEvent::stacktrace,
                                            if (eventStacktrace.isNotEmpty()) {
                                                eventStacktrace.first()
                                            } else {
                                                ""
                                            },
                                        )
                                        add(
                                            logMenuItem(
                                                label = "Partial Match",
                                                condition = isPartialStackMarked,
                                                action = {
                                                    if (it.stacktrace.isNotEmpty() && it.stacktrace.first() == selectedLogEvent.stacktrace.first()) {
                                                        it.marked = !isPartialStackMarked
                                                    }
                                                },
                                            ),
                                        )
                                    } else {
                                        isEnabled = false
                                    }
                                },
                            )
                        },
                    )
                }
            }
        }
    }

    private fun logMenuItem(
        label: String,
        condition: Boolean,
        onTrue: () -> Unit,
        onFalse: () -> Unit,
    ): JCheckBoxMenuItem {
        return JCheckBoxMenuItem(label, !condition).apply {
            addActionListener {
                if (condition) onTrue() else onFalse()
                updateData()
            }
        }
    }

    private fun logMenuItem(
        label: String,
        condition: Boolean,
        action: (LogEvent) -> Unit,
    ): JCheckBoxMenuItem {
        return JCheckBoxMenuItem(label, condition).apply {
            addActionListener {
                table.model.data.forEach {
                    action(it)
                }
                updateData()
            }
        }
    }

    private fun isMarked(type: KProperty<*>, selectedValue: String): Boolean {
        table.model.data.forEach {
            if (!it.marked) {
                val eventValue = when (type) {
                    LogEvent::level -> it.level?.name ?: ""
                    LogEvent::logger -> it.logger
                    SystemLogEvent::thread -> (it as SystemLogEvent).thread
                    LogEvent::message -> it.message
                    LogEvent::stacktrace -> {
                        if (selectedValue.contains("\n")) {
                            it.stacktrace.joinToString("\n")
                        } else if (it.stacktrace.isNotEmpty()) {
                            it.stacktrace.first()
                        } else {
                            "empty stacktrace"
                        }
                    }
                    else -> ""
                }
                if (eventValue == selectedValue) return false
            }
        }
        return true
    }

    private val tableScrollPane = FlatScrollPane(table) {
        verticalScrollBar = densityDisplay
    }

    private val details = LogDetailsPane()
    private val loggerNamesSidebar = LoggerNamesPanel(rawData)

    private val loggerLevelsSidebar = LogLevelsList(rawData)
    @Suppress("UNCHECKED_CAST")
    private val loggerMDCsSidebar = if (rawData.first() is SystemLogEvent) {
        LoggerMDCPanel(rawData as List<SystemLogEvent>)
    } else {
        null
    }
    private val loggerTimesSidebar = LoggerTimesPanel(startTime, endTime)
    private val filterPane = JTabbedPane().apply {
        addTab("Loggers", loggerNamesSidebar)
        addTab("Levels", FlatScrollPane(loggerLevelsSidebar))
        addTab("Time", loggerTimesSidebar)
        if (loggerMDCsSidebar != null) {
            addTab("MDC", loggerMDCsSidebar)
        }
    }
    private val filters: List<(LogEvent) -> Boolean> = buildList {
        add { event ->
            event.logger in loggerNamesSidebar.loggerNamesList.checkBoxListSelectedValues
        }
        add { event ->
            loggerTimesSidebar.isValidLogEvent(event)
        }
        add { event ->
            event.level!!.name in loggerLevelsSidebar.checkBoxListSelectedValues
        }
        add { event ->
            loggerMDCsSidebar?.filterTable?.model?.isValidLogEvent(event, false) ?: true
        }
        add { event ->
            loggerMDCsSidebar?.filterTable?.model?.isValidLogEvent(event, true) ?: true
        }
        add { event ->
            event.marked || !header.isOnlyShowMarkedLogs
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
                            event.stacktrace.any { stacktrace -> stacktrace.contains(text, ignoreCase = true) }
                    }

                    is WrapperLogEvent -> {
                        text in event.message ||
                            event.logger.contains(text, ignoreCase = true) ||
                            event.stacktrace.any { stacktrace -> stacktrace.contains(text, ignoreCase = true) }
                    }
                }
            }
        }
        add { event ->
            messageFilter.isEmpty() || messageFilter in event.message
        }
        add { event ->
            !stackTraceFilter.enabled ||
                (
                    event.stacktrace.isNotEmpty() &&
                        (
                            (stackTraceFilter.completeMatch && stackTraceFilter.stacktrace == event.stacktrace) ||
                                (!stackTraceFilter.completeMatch && stackTraceFilter.stacktrace.first() == event.stacktrace.first())
                            )
                    )
        }
        add { event ->
            event is WrapperLogEvent || threadFilter.isEmpty() || threadFilter == (event as SystemLogEvent).thread
        }
    }

    private fun updateData() {
        BACKGROUND.launch {
            val filteredData = rawData.filter { event ->
                filters.all { filter -> filter(event) }
            }
            EDT_SCOPE.launch {
                table.model = createModel(filteredData)
            }
            updateFilterPaneUI()
        }
    }

    private fun updateFilterPaneUI() {
        filterPane.run {
            listOf(
                loggerNamesSidebar,
                loggerLevelsSidebar,
                loggerTimesSidebar,
                loggerMDCsSidebar,
            ).forEachIndexed { index, sidebar ->
                sidebar?.let {
                    updateBackgroundAt(it.isFilterApplied, index)
                }
            }
        }
    }
    private fun JTabbedPane.updateBackgroundAt(condition: Boolean, index: Int) {
        if(condition) {
            setBackgroundAt(index, UIManager.getColor("TabbedPane.focusColor"))
        } else {
            setBackgroundAt(index, background)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun createModel(rawData: List<LogEvent>) = when (rawData.firstOrNull()) {
        is WrapperLogEvent -> LogsModel(rawData as List<WrapperLogEvent>, WrapperLogColumns(this))
        is SystemLogEvent -> LogsModel(rawData as List<SystemLogEvent>, SystemLogColumns(this))
        else -> LogsModel(rawData as List<WrapperLogEvent>, WrapperLogColumns(this))
    }

    init {
        add(header, "wrap, growx, spanx 2")
        add(
            JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT,
                JPanel(MigLayout("ins 0, fill")).apply {
                    add(filterPane, "grow")
                },
                JSplitPane(
                    JSplitPane.VERTICAL_SPLIT,
                    tableScrollPane,
                    details,
                ).apply {
                    resizeWeight = 0.6
                },
            ).apply {
                isOneTouchExpandable = true
                resizeWeight = 0.1
            },
            "push, grow",
        )

        table.selectionModel.apply {
            addListSelectionListener { selectionEvent ->
                if (!selectionEvent.valueIsAdjusting) {
                    details.events = selectedIndices
                        .filter { isSelectedIndex(it) }
                        .map { table.convertRowIndexToModel(it) }
                        .map { row -> table.model[row] }
                        .map { event ->
                            when (event) {
                                is SystemLogEvent -> DetailEvent(
                                    title = "${dateFormatter.format(event.timestamp)} ${event.thread}",
                                    message = event.message,
                                    body = event.stacktrace,
                                    details = event.mdc.associate { it.key to it.value },
                                )

                                is WrapperLogEvent -> DetailEvent(
                                    title = event.level.toString() + "   -   " + dateFormatter.format(event.timestamp) + "   -   " + event.logger.substringAfterLast("."),
                                    message = event.message,
                                    body = event.stacktrace,
                                )
                            }
                        }
                }
            }
        }

        table.addPropertyChangeListener("model") {
            header.displayedRows = table.model.rowCount
        }

        loggerNamesSidebar.loggerNamesList.checkBoxListSelectionModel.addListSelectionListener {
            if (!it.valueIsAdjusting) {
                updateData()
            }
        }

        loggerLevelsSidebar.checkBoxListSelectionModel.addListSelectionListener {
            if (!it.valueIsAdjusting) {
                updateData()
            }
        }

        loggerMDCsSidebar?.filterTable?.model?.addTableModelListener {
            updateData()
        }

        loggerTimesSidebar.addTimeUpdateEventListener {
            updateData()
        }

        header.addPropertyChangeListener("minimumLevel") {
            updateData()
        }
        header.search.addActionListener { updateData() }

        header.addPropertyChangeListener("selectedTimeZone") {
            dateFormatter = dateFormatter.withZone(ZoneId.of(it.newValue as String))
            table.model.fireTableDataChanged()
        }

        header.addPropertyChangeListener("isShowFullLoggerName") {
            loggerNamesSidebar.loggerNamesList.isShowFullLoggerNames = !loggerNamesSidebar.loggerNamesList.isShowFullLoggerNames
            table.model.fireTableDataChanged()
        }

        header.addPropertyChangeListener("isOnlyShowMarkedLogs") {
            table.model.fireTableDataChanged()
            updateData()
        }
    }

    inner class GroupingScrollBar : JScrollBar() {
        private val density: Map<Instant, Int>
        private val rangex: Int

        init {
            val delta = Duration.between(rawData.first().timestamp, rawData.last().timestamp)
            val slice = delta.dividedBy((rawData.size.toLong() / 60).coerceAtLeast(1))
            val insertionPoint = DURATIONS.binarySearch { it.compareTo(slice) }
            val aggregate = DURATIONS[insertionPoint.absoluteValue - 1]

            toolTipText = aggregate.toString()

            density = rawData.groupingBy {
                it.timestamp.truncatedTo(DurationUnit(aggregate))
            }.eachCount()
            rangex = density.values.maxOf { it }
        }

        override fun getUnitIncrement(direction: Int): Int {
            return table.getScrollableUnitIncrement(tableScrollPane.viewport.viewRect, SwingConstants.VERTICAL, direction)
        }

        override fun getBlockIncrement(direction: Int): Int {
            return table.getScrollableBlockIncrement(tableScrollPane.viewport.viewRect, SwingConstants.VERTICAL, direction)
        }

        private val customUI = object : FlatScrollBarUI() {
            override fun paintTrack(g: Graphics, c: JComponent, trackBounds: Rectangle) {
                super.paintTrack(g, c, trackBounds)
                if (showDensityDisplay) {
                    g as Graphics2D
                    g.color = UIManager.getColor("Actions.Red")

                    val old = g.transform
                    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                    g.transform(
                        AffineTransform.getScaleInstance(
                            trackBounds.width / rangex.toDouble(),
                            trackBounds.height / density.size.toDouble(),
                        ),
                    )
                    density.values.forEachIndexed { index, count ->
                        g.drawLine(
                            trackBounds.x,
                            trackBounds.y + index,
                            trackBounds.x + count,
                            trackBounds.y + index,
                        )
                    }
                    g.transform = old
                }
            }
        }

        init {
            preferredSize = Dimension(30, 100)
            setUI(customUI)
        }

        override fun updateUI() {
            setUI(customUI)
            updateFilterPaneUI()
        }
    }

    override val icon: Icon? = null

    companion object {
        private val BACKGROUND = CoroutineScope(Dispatchers.Default)

        private val DURATIONS = listOf(
            Duration.ofMillis(100),
            Duration.ofMillis(500),
            Duration.ofSeconds(1),
            Duration.ofSeconds(5),
            Duration.ofSeconds(10),
            Duration.ofSeconds(30),
            Duration.ofMinutes(1),
            Duration.ofMinutes(2),
            Duration.ofMinutes(5),
            Duration.ofMinutes(10),
            Duration.ofMinutes(15),
            Duration.ofMinutes(30),
            Duration.ofHours(1),
            Duration.ofHours(2),
            Duration.ofHours(6),
            Duration.ofHours(12),
            Duration.ofDays(1),
        )

        private val DEFAULT_WRAPPER_LOG_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss")
            .withZone(ZoneId.systemDefault())
        private val DEFAULT_WRAPPER_MESSAGE_FORMAT =
            "^[^|]+\\|(?<jvm>[^|]+)\\|(?<timestamp>[^|]+)\\|(?: (?<level>[TDIWE]) \\[(?<logger>[^]]++)] \\[(?<time>[^]]++)]: (?<message>.*)| (?<stack>.*))\$".toRegex()

        fun parseLogs(lines: Sequence<String>): List<WrapperLogEvent> {
            val events = mutableListOf<WrapperLogEvent>()
            val currentStack = mutableListOf<String>()
            var partialEvent: WrapperLogEvent? = null
            var lastEventTimestamp: Instant? = null

            fun WrapperLogEvent?.flush() {
                if (this != null) {
                    // flush our previously built event
                    events += this.copy(stacktrace = currentStack.toList())
                    currentStack.clear()
                    partialEvent = null
                }
            }

            for ((index, line) in lines.withIndex()) {
                if (line.isBlank()) {
                    continue
                }
                val match = DEFAULT_WRAPPER_MESSAGE_FORMAT.matchEntire(line)
                if (match != null) {
                    val timestamp by match.groups
                    val time = DEFAULT_WRAPPER_LOG_TIME_FORMAT.parse(timestamp.value.trim(), Instant::from)

                    // we hit an actual logged event
                    if (match.groups["level"] != null) {
                        partialEvent.flush()

                        // now build up a new partial (the next line(s) may have stacktrace)
                        val level by match.groups
                        val logger by match.groups
                        val message by match.groups
                        lastEventTimestamp = time
                        partialEvent = WrapperLogEvent(
                            timestamp = time,
                            message = message.value.trim(),
                            logger = logger.value.trim(),
                            level = Level.valueOf(level.value.single()),
                            marked = false,
                        )
                    } else {
                        val stack by match.groups

                        if (lastEventTimestamp == time) {
                            // same timestamp - must be attached stacktrace
                            currentStack += stack.value
                        } else {
                            partialEvent.flush()
                            // different timestamp, but doesn't match our regex - just try to display it in a useful way
                            events += WrapperLogEvent(
                                timestamp = time,
                                message = stack.value,
                                level = Level.INFO,
                                marked = false,
                            )
                        }
                    }
                } else {
                    throw IllegalArgumentException("Error parsing line $index, unparseable value: $line")
                }
            }
            partialEvent.flush()
            return events
        }
    }
}

// Credit to https://stackoverflow.com/a/66203968
class DurationUnit(private val duration: Duration) : TemporalUnit {
    init {
        require(!(duration.isZero || duration.isNegative)) { "Duration may not be zero or negative" }
    }

    override fun getDuration(): Duration = duration
    override fun isDurationEstimated(): Boolean = duration.seconds >= SECONDS_PER_DAY
    override fun isDateBased(): Boolean = duration.nano == 0 && duration.seconds % SECONDS_PER_DAY == 0L
    override fun isTimeBased(): Boolean = duration.seconds < SECONDS_PER_DAY && NANOS_PER_DAY % duration.toNanos() == 0L

    @Suppress("UNCHECKED_CAST")
    override fun <R : Temporal?> addTo(temporal: R, amount: Long): R =
        duration.multipliedBy(amount).addTo(temporal) as R

    override fun between(temporal1Inclusive: Temporal, temporal2Exclusive: Temporal): Long {
        return Duration.between(temporal1Inclusive, temporal2Exclusive).dividedBy(duration)
    }

    override fun toString(): String = duration.toString()

    companion object {
        private const val SECONDS_PER_DAY = 86400
        private const val NANOS_PER_SECOND = 1000000000L
        private const val NANOS_PER_DAY = NANOS_PER_SECOND * SECONDS_PER_DAY
    }
}
