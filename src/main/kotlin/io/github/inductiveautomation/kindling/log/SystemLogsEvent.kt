package io.github.inductiveautomation.kindling.log

import io.github.inductiveautomation.kindling.utils.StackTrace
import java.time.Instant

sealed interface LogEvent {
    val timestamp: Instant
    val message: String
    val logger: String
}

data class WrapperLogEvent(
    override val timestamp: Instant,
    override val message: String,
    override val logger: String = STDOUT,
    val level: Level? = null,
    val stacktrace: StackTrace = emptyList(),
) : LogEvent {
    companion object {
        const val STDOUT = "STDOUT"
    }
}

data class SystemLogsEvent(
    override val timestamp: Instant,
    override val message: String,
    override val logger: String,
    val thread: String,
    val level: Level,
    val mdc: Map<String, String>,
    val stacktrace: StackTrace,
) : LogEvent

@Suppress("ktlint:trailing-comma-on-declaration-site")
enum class Level {
    TRACE,
    DEBUG,
    INFO,
    WARN,
    ERROR;

    companion object {
        private val firstChars = values().associateBy { it.name.first() }

        fun valueOf(char: Char): Level = firstChars.getValue(char)
    }
}
