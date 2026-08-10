package io.github.inductiveautomation.kindling.log

import io.github.inductiveautomation.kindling.core.DEFAULT_TIMESTAMP_PATTERN
import io.github.inductiveautomation.kindling.core.Kindling.Preferences.General.DefaultEncoding
import io.github.inductiveautomation.kindling.core.Kindling.Preferences.General.TimestampPattern
import java.nio.file.Path
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.io.path.useLines

private val DEFAULT_LOG_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern(DEFAULT_TIMESTAMP_PATTERN)
    .withZone(ZoneId.systemDefault())

internal val configuredTimestampFormat: DateTimeFormatter?
    get() = TimestampPattern.currentValue
        .takeIf { it != DEFAULT_TIMESTAMP_PATTERN }
        ?.let { pattern ->
            runCatching {
                DateTimeFormatter.ofPattern(pattern).withZone(ZoneId.systemDefault())
            }.getOrNull()
        }

private val NUMERIC_TIMESTAMP =
    """(\d{4})\D(\d{1,2})\D(\d{1,2})\D+(\d{1,2})\D(\d{1,2})\D(\d{1,2})(?:\D(\d{1,3}))?""".toRegex()

internal fun parseLogTimestamp(
    value: String,
    configured: DateTimeFormatter?,
    numericFallback: Boolean = false,
): Instant? {
    if (configured != null) {
        runCatching { configured.parse(value, Instant::from) }.getOrNull()?.let { return it }
    }
    runCatching { DEFAULT_LOG_TIME_FORMAT.parse(value, Instant::from) }.getOrNull()?.let { return it }

    if (numericFallback) {
        val match = NUMERIC_TIMESTAMP.matchEntire(value) ?: return null
        val (year, month, day, hour, minute, second, millis) = match.destructured
        return runCatching {
            LocalDateTime.of(
                year.toInt(),
                month.toInt(),
                day.toInt(),
                hour.toInt(),
                minute.toInt(),
                second.toInt(),
                millis.ifEmpty { "0" }.padEnd(3, '0').toInt() * 1_000_000,
            ).atZone(ZoneId.systemDefault()).toInstant()
        }.getOrNull()
    }
    return null
}

object LogbackLogParser {

    val EVENT_LINE =
        """^(?<level>[TDIWE]) \[(?<logger>[^]]++)] \[(?<timestamp>[^]]++)]: (?:\{(?<thread>[^}]*+)} )?(?<message>.*)$"""
            .toRegex()

    private val MDC_SUFFIX =
        """(?:^|(?<= ))([A-Za-z_][\w.-]*=[^,]*(?:, [A-Za-z_][\w.-]*=[^,]*)*)$""".toRegex()

    fun matches(path: Path): Boolean = path.useLines(DefaultEncoding.currentValue) { lines ->
        matches(lines)
    }

    fun matches(lines: Sequence<String>): Boolean =
        lines.firstOrNull(String::isNotBlank)?.let(EVENT_LINE::matches) == true

    fun parseFile(path: Path): LogFile<SystemLogEvent> = path.useLines(DefaultEncoding.currentValue) { lines ->
        LogFile(parse(lines))
    }

    fun parse(lines: Sequence<String>): List<SystemLogEvent> {
        // resolved once so that every line in a file is parsed consistently
        val configuredFormat = configuredTimestampFormat
        val events = mutableListOf<SystemLogEvent>()
        val currentStack = mutableListOf<String>()
        var partialEvent: SystemLogEvent? = null

        fun flush() {
            partialEvent?.let { events += it.copy(stacktrace = currentStack.toList()) }
            currentStack.clear()
            partialEvent = null
        }

        for (line in lines) {
            if (line.isBlank()) {
                continue
            }

            val match = EVENT_LINE.matchEntire(line)
            if (match == null) {
                if (partialEvent == null && events.isEmpty()) {
                    throw IllegalArgumentException(
                        "Error parsing log file; unexpected content format on first line:\n$line",
                    )
                }
                currentStack += line
                continue
            }

            val timestamp = match.groups["timestamp"]!!.value.trim()
            val time = parseLogTimestamp(timestamp, configuredFormat, numericFallback = true)
            if (time == null) {
                if (events.isEmpty()) {
                    throw IllegalArgumentException(
                        "Error parsing log file; unable to read the timestamp \"$timestamp\". " +
                            "Check the Timestamp Pattern preference (currently \"${TimestampPattern.currentValue}\").",
                    )
                }
                continue
            }

            flush()

            val (message, mdc) = extractMdc(match.groups["message"]!!.value.trim())
            partialEvent = SystemLogEvent(
                timestamp = time,
                message = message,
                logger = match.groups["logger"]!!.value.trim(),
                thread = match.groups["thread"]?.value?.trim().orEmpty(),
                level = Level.valueOf(match.groups["level"]!!.value.single()),
                mdc = mdc,
                stacktrace = emptyList(),
            )
        }
        flush()
        return events
    }

    private fun extractMdc(message: String): Pair<String, List<MDC>> {
        val match = MDC_SUFFIX.find(message) ?: return message to emptyList()
        val mdc = match.groupValues[1].split(", ").map { pair ->
            val (key, value) = pair.split('=', limit = 2)
            MDC(key, value)
        }
        return message.substring(0, match.range.first).trim() to mdc
    }
}