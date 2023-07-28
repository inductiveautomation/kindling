package io.github.inductiveautomation.kindling.log

import com.formdev.flatlaf.extras.FlatSVGIcon
import com.jidesoft.comparator.AlphanumComparator
import io.github.inductiveautomation.kindling.core.ClipboardTool
import io.github.inductiveautomation.kindling.core.Kindling.Preferences.General.DefaultEncoding
import io.github.inductiveautomation.kindling.core.MultiTool
import io.github.inductiveautomation.kindling.core.Preference
import io.github.inductiveautomation.kindling.core.Preference.Companion.PreferenceCheckbox
import io.github.inductiveautomation.kindling.core.Preference.Companion.preference
import io.github.inductiveautomation.kindling.core.PreferenceCategory
import io.github.inductiveautomation.kindling.core.ToolPanel
import io.github.inductiveautomation.kindling.utils.Action
import io.github.inductiveautomation.kindling.utils.FileFilter
import io.github.inductiveautomation.kindling.utils.ZoneIdSerializer
import io.github.inductiveautomation.kindling.utils.getValue
import java.awt.Desktop
import java.io.File
import java.nio.file.Path
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.zone.ZoneRulesProvider
import java.util.Vector
import javax.swing.Icon
import javax.swing.JComboBox
import javax.swing.JPopupMenu
import kotlin.io.path.name
import kotlin.io.path.useLines

class WrapperLogView(
    events: List<WrapperLogEvent>,
    tabName: String,
    private val fromFile: Boolean,
) : ToolPanel() {
    private val logPanel = LogPanel(events)

    init {
        name = tabName
        toolTipText = tabName

        add(logPanel, "push, grow")
    }

    override val icon: Icon = LogViewer.icon

    override fun customizePopupMenu(menu: JPopupMenu) {
        menu.add(
            exportMenu { logPanel.table.model },
        )
        if (fromFile) {
            menu.addSeparator()
            menu.add(
                Action(name = "Open in External Editor") {
                    Desktop.getDesktop().open(File(tabName))
                },
            )
        }
    }

    companion object {
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

data object LogViewer : MultiTool, ClipboardTool, PreferenceCategory {
    override val title = "Wrapper Log"
    override val description = "wrapper.log(.n) files"
    override val icon = FlatSVGIcon("icons/bx-file.svg")
    override val respectsEncoding: Boolean = true

    override val filter = FileFilter(description) { file ->
        file.name.endsWith("log") || file.name.substringAfterLast('.').toIntOrNull() != null
    }

    override fun open(paths: List<Path>): ToolPanel {
        require(paths.isNotEmpty()) { "Must provide at least one path" }
        // flip the paths, so the .5, .4, .3, .2, .1 - this hopefully helps with the per-event sort below
        val reverseOrder = paths.sortedWith(compareBy(AlphanumComparator(), Path::name).reversed())
        val events = reverseOrder.flatMap { path ->
            path.useLines(DefaultEncoding.currentValue) { lines -> WrapperLogView.parseLogs(lines) }
        }
        return WrapperLogView(
            events = events.sortedBy { it.timestamp },
            tabName = paths.first().name,
            fromFile = true,
        )
    }

    override fun open(data: String): ToolPanel {
        return WrapperLogView(
            events = WrapperLogView.parseLogs(data.lineSequence()),
            tabName = "Paste at ${LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))}",
            fromFile = false,
        )
    }

    val SelectedTimeZone = preference(
        name = "Timezone",
        description = "Timezone to use when displaying logs",
        default = ZoneId.systemDefault(),
        serializer = ZoneIdSerializer,
        editor = {
            JComboBox(Vector(ZoneRulesProvider.getAvailableZoneIds().sorted())).apply {
                selectedItem = currentValue.id
                addActionListener {
                    currentValue = ZoneId.of(selectedItem as String)
                }
            }
        },
    )

    private lateinit var _formatter: DateTimeFormatter
    val TimeStampFormatter: DateTimeFormatter
        get() {
            if (!this::_formatter.isInitialized) {
                _formatter = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss:SSS").withZone(SelectedTimeZone.currentValue)
            }
            if (_formatter.zone != SelectedTimeZone.currentValue) {
                _formatter = _formatter.withZone(SelectedTimeZone.currentValue)
            }
            return _formatter
        }

    val ShowDensity = preference(
        name = "Density Display",
        default = true,
        editor = {
            PreferenceCheckbox("Show 'minimap' of log events in scrollbar")
        },
    )

    override val displayName: String = "Log View"
    override val key: String = "logview"
    override val preferences: List<Preference<*>> = listOf(SelectedTimeZone, ShowDensity)
}
