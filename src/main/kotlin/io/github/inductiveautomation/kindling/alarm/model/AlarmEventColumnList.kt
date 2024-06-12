package io.github.inductiveautomation.kindling.alarm.model

import com.inductiveautomation.ignition.common.alarming.AlarmEvent
import io.github.inductiveautomation.kindling.utils.ColumnList

@Suppress("unused")
object AlarmEventColumnList : ColumnList<AlarmEvent>() {
    val Id by column(
        value = AlarmEvent::getId,
    )

    val DisplayPath by column(
        column = {
            title = "Display Path"
            isVisible = false
        },
        value = AlarmEvent::getDisplayPath,
    )

    val Source by column(
        value = { it.source.toStringSimple() },
    )

    val Name by column(
        value = AlarmEvent::getName,
    )

    val State by column(
        value = AlarmEvent::getState,
    )

    val Priority by column(
        value = AlarmEvent::getPriority,
    )

    val Shelved by column(
        value = AlarmEvent::isShelved,
    )

    val Label by column(
        value = AlarmEvent::getLabel,
    )

    val Notes by column(
        column = {
            isVisible = false
        },
        value = AlarmEvent::getNotes,
    )
}
