package io.github.inductiveautomation.kindling.core

import io.github.inductiveautomation.kindling.core.Kindling.Preferences
import java.time.ZoneId
import java.time.temporal.TemporalAccessor
import java.time.format.DateTimeFormatter as JavaFormatter

object Timezone {
    object Default : AbstractDateTimeFormatter() {
        override val zoneId: ZoneId
            get() = Preferences.General.DefaultTimezone.currentValue

        init {
            Preferences.General.DefaultTimezone.addChangeListener { newValue ->
                formatter = createFormatter(newValue)
                listeners.forEach { it.invoke(this) } // notify listeners when timezone changes
            }
        }
    }
}

interface DateTimeFormatter {
    val zoneId: ZoneId

    /**
     * Format [time] using [zoneId] automatically.
     */
    fun format(time: TemporalAccessor): String

    /**
     * Format [date] using [zoneId] automatically.
     *
     * This function is overloaded to also accept [java.util.Date] types, including [java.sql.Date]
     * and [java.sql.Timestamp].
     * - [java.sql.Date] is converted via [java.sql.Date.toLocalDate] at the start of the day
     * in the selected timezone.
     * - [java.sql.Timestamp] and [java.util.Date] preserve full time-of-day precision.
     */
    fun format(date: java.util.Date): String

    fun addChangeListener(listener: (DateTimeFormatter) -> Unit)

    fun removeChangeListener(listener: (DateTimeFormatter) -> Unit)
}

abstract class AbstractDateTimeFormatter : DateTimeFormatter {
    protected var formatter = createFormatter(zoneId)

    protected val listeners = mutableListOf<(DateTimeFormatter) -> Unit>()

    abstract override val zoneId: ZoneId

    protected open fun createFormatter(id: ZoneId): JavaFormatter = JavaFormatter.ofPattern("uuuu-MM-dd HH:mm:ss:SSS").withZone(id)

    override fun addChangeListener(listener: (DateTimeFormatter) -> Unit) {
        listeners += listener
    }

    override fun removeChangeListener(listener: (DateTimeFormatter) -> Unit) {
        listeners -= listener
    }

    override fun format(time: TemporalAccessor): String = formatter.format(time)

    override fun format(date: java.util.Date): String = when (date) {
        is java.sql.Date -> format(
            date.toLocalDate().atStartOfDay(zoneId),
        )

        is java.sql.Timestamp -> format(date.toInstant())
        else -> format(date.toInstant())
    }
}
