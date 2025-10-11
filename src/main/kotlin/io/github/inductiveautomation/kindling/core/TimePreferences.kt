package io.github.inductiveautomation.kindling.core

import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAccessor

object TimePreferences {
    val SelectedTimeZone = Kindling.Preferences.General.SelectedTimeZone

    private var formatter = createFormatter(SelectedTimeZone.currentValue)

    init {
        Kindling.Preferences.General.SelectedTimeZone.addChangeListener { newValue ->
            formatter = createFormatter(newValue)
        }
    }

    private fun createFormatter(id: ZoneId): DateTimeFormatter = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss:SSS")
        .withZone(id)

    /**
     * Format [time] using an internal [DateTimeFormatter] that is in [SelectedTimeZone] automatically.
     */
    fun format(time: TemporalAccessor): String = formatter.format(time)

    fun currentZone(): ZoneId = SelectedTimeZone.currentValue



}