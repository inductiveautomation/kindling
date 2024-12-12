package io.github.inductiveautomation.kindling.log

import io.github.inductiveautomation.kindling.utils.FileFilterableCollection
import io.github.inductiveautomation.kindling.utils.StackTrace
import java.time.Instant

data class LogFile<T : LogEvent>(
    override val items: List<T>,
) : FileFilterableCollection<T>

sealed interface LogEvent {
    var marked: Boolean
    val timestamp: Instant
    val message: String
    val logger: String
    val level: Level?
    val stacktrace: List<String>
}

data class MDC(
    val key: String,
    val value: String?,
) {
    fun toPair() = Pair(key, value)
}

data class WrapperLogEvent(
    override val timestamp: Instant,
    override val message: String,
    override val logger: String = STDOUT,
    override val level: Level? = null,
    override val stacktrace: StackTrace = emptyList(),
    override var marked: Boolean = false,
) : LogEvent {
    companion object {
        const val STDOUT = "STDOUT"
    }
}

data class SystemLogEvent(
    override val timestamp: Instant,
    override val message: String,
    override val logger: String,
    val thread: String,
    override val level: Level,
    val mdc: List<MDC>,
    override val stacktrace: List<String>,
    override var marked: Boolean = false,
) : LogEvent

@Suppress("ktlint:standard:trailing-comma-on-declaration-site")
enum class Level {
    TRACE,
    DEBUG,
    INFO,
    WARN,
    ERROR;

    companion object {
        private val firstChars = entries.associateBy { it.name.first() }

        fun valueOf(char: Char): Level = firstChars.getValue(char)
    }
}
