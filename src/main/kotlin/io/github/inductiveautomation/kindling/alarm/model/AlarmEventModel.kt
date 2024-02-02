package io.github.inductiveautomation.kindling.alarm.model

import com.inductiveautomation.ignition.common.alarming.AlarmEvent
import io.github.inductiveautomation.kindling.utils.Column
import io.github.inductiveautomation.kindling.utils.ColumnList
import javax.swing.table.AbstractTableModel

class AlarmEventModel(private val alarmData: List<AlarmEvent>) : AbstractTableModel() {
    override fun getRowCount() = alarmData.size

    override fun getColumnCount() = AlarmEventColumnList.size

    override fun getColumnClass(columnIndex: Int): Class<*> = AlarmEventColumnList[columnIndex].clazz

    override fun getColumnName(column: Int): String = AlarmEventColumnList[column].header

    override fun getValueAt(
        rowIndex: Int,
        columnIndex: Int,
    ): Any? = get(rowIndex, AlarmEventColumnList[columnIndex])

    operator fun get(row: Int) = alarmData[row]

    operator fun <T> get(
        row: Int,
        col: Column<AlarmEvent, T>,
    ): T {
        return get(row).let {
            col.getValue(it)
        }
    }

    @Suppress("unused")
    object AlarmEventColumnList : ColumnList<AlarmEvent>() {
        val id by column(
            value = AlarmEvent::getId,
        )

        val source by column(
            value = { it.source.toStringSimple() },
        )

        val name by column(
            value = AlarmEvent::getName,
        )

        val state by column(
            value = AlarmEvent::getState,
        )

        val priority by column(
            value = AlarmEvent::getPriority,
        )

        val shelved by column(
            value = AlarmEvent::isShelved,
        )

        val label by column(
            value = AlarmEvent::getLabel,
        )

        /* This stuff is shown in the details pane. Maybe just get rid of it from the table model.
        val activeData by column<EventData?>(
            column = {
                isVisible = false
            },
            value = AlarmEvent::getActiveData,
        )

        val clearData by column<EventData?>(
            column = {
                isVisible = false
            },
            value = AlarmEvent::getClearedData,
        )

        val ackData by column<EventData?>(
            column = {
                isVisible = false
            },
            value = AlarmEvent::getAckData,
        )
         */
    }
}
