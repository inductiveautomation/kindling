package io.github.inductiveautomation.kindling.log

import com.formdev.flatlaf.ui.FlatScrollBarUI
import io.github.inductiveautomation.kindling.core.Detail.BodyLine
import io.github.inductiveautomation.kindling.core.DetailsPane
import io.github.inductiveautomation.kindling.core.Kindling.Preferences.Advanced.HyperlinkStrategy
import io.github.inductiveautomation.kindling.core.Kindling.Preferences.General.ShowFullLoggerNames
import io.github.inductiveautomation.kindling.core.Kindling.Preferences.General.UseHyperlinks
import io.github.inductiveautomation.kindling.core.LinkHandlingStrategy
import io.github.inductiveautomation.kindling.core.ToolPanel
import io.github.inductiveautomation.kindling.log.LogViewer.SelectedTimeZone
import io.github.inductiveautomation.kindling.log.LogViewer.ShowDensity
import io.github.inductiveautomation.kindling.utils.EDT_SCOPE
import io.github.inductiveautomation.kindling.utils.FlatScrollPane
import io.github.inductiveautomation.kindling.utils.MajorVersion
import io.github.inductiveautomation.kindling.utils.ReifiedJXTable
import io.github.inductiveautomation.kindling.utils.configureCellRenderer
import io.github.inductiveautomation.kindling.utils.getValue
import io.github.inductiveautomation.kindling.utils.toBodyLine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.miginfocom.swing.MigLayout
import org.jdesktop.swingx.JXSearchField
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.geom.AffineTransform
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.Temporal
import java.time.temporal.TemporalUnit
import javax.swing.Icon
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollBar
import javax.swing.JSplitPane
import javax.swing.ListSelectionModel
import javax.swing.SortOrder
import javax.swing.SwingConstants
import javax.swing.UIManager
import kotlin.math.absoluteValue
import kotlin.properties.Delegates
import io.github.inductiveautomation.kindling.core.Detail as DetailEvent

class LogPanel(
    private val rawData: List<LogEvent>,
) : ToolPanel("ins 0, fill, hidemode 3") {
    private val totalRows: Int = rawData.size

    var dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss:SSS")
        .withZone(ZoneId.systemDefault())

    private val densityDisplay = GroupingScrollBar()

    val header = Header(totalRows)

    val table = run {
        val initialModel = createModel(rawData)
        ReifiedJXTable(initialModel, initialModel.columns).apply {
            setSortOrder("Timestamp", SortOrder.ASCENDING)
        }
    }

    private val tableScrollPane = FlatScrollPane(table) {
        verticalScrollBar = densityDisplay
    }

    private val details = DetailsPane()
    private val sidebar = LoggerNamesPanel(rawData)

    private val filters: List<(LogEvent) -> Boolean> = buildList {
        add { event ->
            event.logger in sidebar.list.checkBoxListSelectedIndices
                .map { sidebar.list.model.getElementAt(it) }
                .filterIsInstance<LoggerName>()
                .mapTo(mutableSetOf()) { it.name }
        }
        add { event ->
            val text = header.search.text
            if (text.isNullOrEmpty()) {
                true
            } else {
                when (event) {
                    is SystemLogsEvent -> {
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
    }

    private fun updateData() {
        BACKGROUND.launch {
            val filteredData = rawData.filter { event ->
                filters.all { filter -> filter(event) }
            }
            EDT_SCOPE.launch {
                table.model = createModel(filteredData)
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun createModel(rawData: List<LogEvent>) = when (rawData.firstOrNull()) {
        is WrapperLogEvent -> LogsModel(rawData as List<WrapperLogEvent>, WrapperLogColumns(this))
        is SystemLogsEvent -> LogsModel(rawData as List<SystemLogsEvent>, SystemLogsColumns(this))
        else -> LogsModel(rawData as List<WrapperLogEvent>, WrapperLogColumns(this))
    }

    init {
        add(header, "wrap, growx, spanx 2")
        add(
            JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT,
                sidebar,
                JSplitPane(
                    JSplitPane.VERTICAL_SPLIT,
                    tableScrollPane,
                    details,
                ).apply {
                    resizeWeight = 0.6
                },
            ).apply {
                resizeWeight = 0.1
            },
            "push, grow",
        )

        table.selectionModel.addListSelectionListener { selectionEvent ->
            if (!selectionEvent.valueIsAdjusting) {
                table.selectionModel.updateDetails()
            }
        }

        table.addPropertyChangeListener("model") {
            header.displayedRows = table.model.rowCount
        }

        sidebar.list.checkBoxListSelectionModel.addListSelectionListener {
            if (!it.valueIsAdjusting) {
                updateData()
            }
        }

        header.search.addActionListener { updateData() }

        header.version.addActionListener {
            table.selectionModel.updateDetails()
        }

        SelectedTimeZone.addChangeListener { zoneId ->
            dateFormatter = dateFormatter.withZone(zoneId)
            table.model.fireTableDataChanged()
        }

        ShowFullLoggerNames.addChangeListener { newValue ->
            table.model.fireTableDataChanged()
            sidebar.list.isShowFullLoggerName = newValue
        }

        HyperlinkStrategy.addChangeListener {
            // if the link strategy changes, we need to rebuild all the hyperlinks
            table.selectionModel.updateDetails()
        }
    }

    private fun ListSelectionModel.updateDetails() {
        details.events = selectedIndices
            .filter { isSelectedIndex(it) }
            .map { table.convertRowIndexToModel(it) }
            .map { row -> table.model[row] }
            .map { event ->
                when (event) {
                    is SystemLogsEvent -> DetailEvent(
                        title = "${dateFormatter.format(event.timestamp)} ${event.thread}",
                        message = event.message,
                        body = event.stacktrace.map { element ->
                            if (UseHyperlinks.currentValue) {
                                element.toBodyLine((header.version.selectedItem as MajorVersion).version + ".0")
                            } else {
                                BodyLine(element)
                            }
                        },
                        details = event.mdc,
                    )

                    is WrapperLogEvent -> DetailEvent(
                        title = dateFormatter.format(event.timestamp),
                        message = event.message,
                        body = event.stacktrace.map { element ->
                            if (UseHyperlinks.currentValue) {
                                element.toBodyLine((header.version.selectedItem as MajorVersion).version + ".0")
                            } else {
                                BodyLine(element)
                            }
                        },
                    )
                }
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
                if (ShowDensity.currentValue) {
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
        }
    }

    override val icon: Icon? = null

    class Header(private val totalRows: Int) : JPanel(MigLayout("ins 0, fill, hidemode 3")) {
        private val events = JLabel("$totalRows (of $totalRows) events")

        val search = JXSearchField("Search")

        val version: JComboBox<MajorVersion> = JComboBox(MajorVersion.values()).apply {
            selectedItem = MajorVersion.EightOne
            configureCellRenderer { _, value, _, _, _ ->
                text = "${value?.version}.*"
            }
        }
        private val versionLabel = JLabel("Version")

        private fun updateVersionVisibility() {
            val isVisible = UseHyperlinks.currentValue && HyperlinkStrategy.currentValue == LinkHandlingStrategy.OpenInBrowser
            version.isVisible = isVisible
            versionLabel.isVisible = isVisible
        }

        init {
            add(events, "pushx")

            add(versionLabel)
            add(version)
            updateVersionVisibility()
            UseHyperlinks.addChangeListener { updateVersionVisibility() }
            HyperlinkStrategy.addChangeListener { updateVersionVisibility() }

            add(search, "width 300, gap unrelated")
        }

        var displayedRows by Delegates.observable(totalRows) { _, _, newValue ->
            events.text = "$newValue (of $totalRows) events"
        }
    }

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
