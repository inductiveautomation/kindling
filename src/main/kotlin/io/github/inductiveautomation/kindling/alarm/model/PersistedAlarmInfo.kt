package io.github.inductiveautomation.kindling.alarm.model

import com.inductiveautomation.ignition.common.alarming.AlarmEvent
import com.inductiveautomation.ignition.common.alarming.AlarmState

class PersistedAlarmInfo(
    @JvmField
    val data: Map<AlarmState, Array<AlarmEvent>>,
) : java.io.Serializable {
    companion object {
        @JvmStatic
        private val serialVersionUID = -7560255562233237831L
    }
}
