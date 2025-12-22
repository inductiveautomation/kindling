package io.github.inductiveautomation.kindling.log

import com.formdev.flatlaf.extras.FlatSVGIcon
import com.jidesoft.comparator.AlphanumComparator
import io.github.inductiveautomation.kindling.core.ClipboardTool
import io.github.inductiveautomation.kindling.core.Kindling.Preferences.General.DefaultEncoding
import io.github.inductiveautomation.kindling.core.MultiTool
import io.github.inductiveautomation.kindling.core.ToolPanel
import io.github.inductiveautomation.kindling.log.WrapperLogEvent.Companion.STDOUT
import io.github.inductiveautomation.kindling.utils.FileFilter
import io.github.inductiveautomation.kindling.utils.FileFilterSidebar
import io.github.inductiveautomation.kindling.utils.TabStrip
import io.github.inductiveautomation.kindling.utils.getValue
import io.github.inductiveautomation.kindling.utils.transferTo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import javax.swing.SwingUtilities
import kotlin.io.path.absolutePathString
import kotlin.io.path.name
import kotlin.io.path.outputStream
import kotlin.io.path.useLines

class WrapperLogPanel(
    paths: List<Path>,
    fileData: List<LogFile<WrapperLogEvent>>,
) : LogPanel<WrapperLogEvent>(fileData.flatMap { it.items }, WrapperLogColumns) {

    override val sidebar = FileFilterSidebar(
        listOf(
            LoggerNamePanel(rawData),
            LevelPanel(rawData),
            TimePanel(rawData),
        ),
        fileData = paths.zip(fileData).toMap(),
    )

    init {
        name = "Wrapper Logs [${fileData.size}]"
        toolTipText = paths.joinToString("\n") { it.absolutePathString() }

        filters.add { event ->
            val text = header.search.text
            if (text.isNullOrEmpty()) {
                true
            } else {
                text in event.message ||
                    event.logger.contains(text, ignoreCase = true) ||
                    event.stacktrace.any { stacktrace -> stacktrace.contains(text, ignoreCase = true) }
            }
        }

        addSidebar(sidebar)

        sidebar.forEach { filterPanel ->
            filterPanel.addFilterChangeListener {
                if (!sidebar.filterModelsAreAdjusting) updateData()
            }
        }

        if (paths.size > 1) {
            sidebar.addFileFilterChangeListener {
                selectedData = sidebar.selectedFiles.flatMap { it.items }

                val mainTabbedPane = SwingUtilities.getAncestorNamed("MainTabStrip", this) as? TabStrip

                if (mainTabbedPane != null) {
                    val index = mainTabbedPane.indexOfComponent(this)

                    mainTabbedPane.setTitleAt(index, "System Logs [${sidebar.allData.size}]")
                    mainTabbedPane.setToolTipTextAt(index, sidebar.allData.keys.joinToString("\n"))
                }
            }

            sidebar.registerHighlighters(table)
        }

        sidebar.configureFileDrop { files ->
            val newFileData = runBlocking {
                files.map { path ->
                    async(Dispatchers.IO) {
                        val logFile = path.useLines {
                            LogFile(parseLogs(it))
                        }
                        path to logFile
                    }
                }.awaitAll()
            }

            rawData.addAll(
                newFileData.flatMap { it.second.items },
            )

            newFileData.toMap()
        }
    }

    companion object {
        private val DEFAULT_WRAPPER_LOG_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss")
            .withZone(ZoneId.systemDefault())
        private val DEFAULT_WRAPPER_MESSAGE_FORMAT =
            "(?:^[^|]+\\|)?(?<prefix>[^|]+)\\|(?<timestamp>[^|]+)\\|(?: (?<level>[TDIWE]) \\[(?<logger>[^]]++)] \\[(?<time>[^]]++)]: (?<message>.*)| (?<stack>.*))$".toRegex()

        fun parseLogs(lines: Sequence<String>): List<WrapperLogEvent> {
            val events = mutableListOf<WrapperLogEvent>()
            val currentStack = mutableListOf<String>()
            var partialEvent: WrapperLogEvent? = null
            var lastEventTimestamp: Instant? = null

            fun WrapperLogEvent.flush() {
                // flush our previously built event
                events += this.copy(stacktrace = currentStack.toList())
                currentStack.clear()
                partialEvent = null
            }

            for (line in lines) {
                if (line.isBlank()) {
                    continue
                }

                val match = DEFAULT_WRAPPER_MESSAGE_FORMAT.matchEntire(line)
                if (match != null) {
                    val timestamp by match.groups
                    val time = try {
                        DEFAULT_WRAPPER_LOG_TIME_FORMAT.parse(timestamp.value.trim(), Instant::from)
                    } catch (_: DateTimeParseException) {
                        if (events.isEmpty()) {
                            throw IllegalArgumentException("Error parsing wrapper log file; unexpected content format on first line:\n$line")
                        }
                        continue
                    }

                    // we hit an actual logged event
                    if (match.groups["level"] != null) {
                        partialEvent?.flush()

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
                            partialEvent?.flush()
                            // different timestamp, but doesn't match our regex - just try to display it in a useful way
                            events += WrapperLogEvent(
                                timestamp = time,
                                message = stack.value,
                                logger = match.groups["prefix"]?.value?.trim()?.takeIf { it == "wrapper" } ?: STDOUT,
                                level = Level.INFO,
                            )
                        }
                    }
                } else {
                    if (events.isEmpty()) {
                        throw IllegalArgumentException("Error parsing wrapper log file; unexpected content format on first line:\n$line")
                    }
                    continue
                }
            }
            partialEvent?.flush()
            return events
        }
    }
}

data object LogViewer : MultiTool, ClipboardTool {
    override val serialKey = "logview"
    override val title = "Wrapper Log"
    override val description = "Wrapper Log(s) (wrapper.log, wrapper.log.1, wrapper.log...)"
    override val icon = FlatSVGIcon("icons/bx-file.svg")
    override val respectsEncoding = true
    override val extensions: Array<String> = arrayOf("log")

    override val filter = FileFilter(description) { file ->
        file.name.endsWith("log") || file.name.substringAfterLast('.').toIntOrNull() != null
    }

    override fun open(paths: List<Path>): ToolPanel {
        require(paths.isNotEmpty()) { "Must provide at least one path" }
        // flip the paths, so the .5, .4, .3, .2, .1 - this hopefully helps with the per-event sort below
        val reverseOrder = paths.sortedWith(compareBy(AlphanumComparator(), Path::name).reversed())
        val fileData = reverseOrder.map { path ->
            path.useLines(DefaultEncoding.currentValue) { lines ->
                LogFile(WrapperLogPanel.parseLogs(lines))
            }
        }
        return WrapperLogPanel(
            reverseOrder,
            fileData,
        )
    }

    override fun open(data: String): ToolPanel {
        val tempFile = Files.createTempFile("paste", "kindl")
        data.byteInputStream() transferTo tempFile.outputStream()

        val fileData = LogFile(
            tempFile.useLines(DefaultEncoding.currentValue) { lines ->
                WrapperLogPanel.parseLogs(lines)
            },
        )
        return WrapperLogPanel(listOf(tempFile), listOf(fileData))
    }
}
