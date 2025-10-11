package io.github.inductiveautomation.kindling.core

import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAccessor
import java.util.Date

object TimePreferences {
    val SelectedTimeZone = Kindling.Preferences.General.SelectedTimeZone

    private var formatter = createFormatter(SelectedTimeZone.currentValue)

    init {
        Kindling.Preferences.General.SelectedTimeZone.addChangeListener { newValue ->
            formatter = createFormatter(newValue)
        }
    }
    private val listeners = mutableListOf<() -> Unit>()

    fun addChangeListener(listener: () -> Unit) {
        listeners += listener
    }

    init {
        Kindling.Preferences.General.SelectedTimeZone.addChangeListener { newValue ->
            formatter = createFormatter(newValue)
            listeners.forEach { it() } // notify listeners when timezone changes
        }
    }

    private fun createFormatter(id: ZoneId): DateTimeFormatter = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss:SSS")
        .withZone(id)

    /**
     * Format [time] using an internal [DateTimeFormatter] that is in [SelectedTimeZone] automatically.
     *
     * This function is overloaded to also accept [Date] types, including [java.sql.Date] and [java.sql.Timestamp].
     * - [java.sql.Date] is converted via [toLocalDate] at the start of the day in the selected timezone.
     * - [java.sql.Timestamp] and [java.util.Date] preserve full time-of-day precision.
     */

    fun format(time: TemporalAccessor): String = formatter.format(time)

    fun format(date: Date): String = when (date) {
        is java.sql.Date -> formatter.format(date.toLocalDate().atStartOfDay(SelectedTimeZone.currentValue))
        is java.sql.Timestamp -> formatter.format(date.toInstant())
        else -> formatter.format(date.toInstant())
    }
}
