package io.github.inductiveautomation.kindling.cache.model

import com.inductiveautomation.ignition.common.alarming.AlarmPriority
import com.inductiveautomation.ignition.common.alarming.AlarmStateTransition
import com.inductiveautomation.ignition.common.alarming.EventData
import com.inductiveautomation.ignition.gateway.alarming.journal.EventFlags.FROM_ENABLE_CHANGE_FLAG
import com.inductiveautomation.ignition.gateway.alarming.journal.EventFlags.IS_ACKED_FLAG
import com.inductiveautomation.ignition.gateway.alarming.journal.EventFlags.IS_CLEAR_FLAG
import com.inductiveautomation.ignition.gateway.alarming.journal.EventFlags.SHELVED_EVENT_FLAG
import com.inductiveautomation.ignition.gateway.alarming.journal.EventFlags.SYSTEM_ACK_FLAG
import com.inductiveautomation.ignition.gateway.alarming.journal.EventFlags.SYSTEM_EVENT_FLAG
import com.inductiveautomation.ignition.gateway.history.DatasourceData
import com.inductiveautomation.ignition.gateway.history.HistoricalData
import com.inductiveautomation.ignition.gateway.history.HistoryFlavor
import io.github.inductiveautomation.kindling.core.Detail
import java.io.Serial

class RemoteEvent(
    val target: String? = null,
    val source: String? = null,
    val dispPath: String? = null,
    val uuid: String? = null,
    val priority: Int = 0,
    val eventType: Int = 0,
    val eventFlags: Int = 0,
    val data: EventData? = null,
) : HistoricalData {
    override fun getFlavor(): HistoryFlavor = DatasourceData.FLAVOR
    override fun getSignature(): String = "Remote Alarm Journal Event"
    override fun getDataCount(): Int = 1
    override fun getLoggerName(): String? = null

    fun toDetail(): Detail {
        val details = mapOf(
            "uuid" to uuid.toString(),
        )

        val body = buildMap {
            put("Target", target)
            put("Source", source)
            put("Display Path", dispPath)
            put("Event Type", AlarmStateTransition.fromIntValue(eventType))
            put("Priority", AlarmPriority.fromIntValue(priority))

            put(
                "Event Flags",
                buildString {
                    append(eventFlags)
                    append(" (")
                    val isSystem = eventFlags and SYSTEM_EVENT_FLAG == SYSTEM_EVENT_FLAG
                    append("System? ").append(isSystem).append(", ")
                    val isShelved = eventFlags and SHELVED_EVENT_FLAG == SHELVED_EVENT_FLAG
                    append("Shelved? ").append(isShelved).append(", ")
                    val isSystemAck = eventFlags and SYSTEM_ACK_FLAG == SYSTEM_ACK_FLAG
                    append("System Ack? ").append(isSystemAck).append(", ")
                    val isAcked = eventFlags and IS_ACKED_FLAG == IS_ACKED_FLAG
                    append("Acked? ").append(isAcked).append(", ")
                    val isCleared = eventFlags and IS_CLEAR_FLAG == IS_CLEAR_FLAG
                    append("Cleared? ").append(isCleared).append(", ")
                    val fromEnableChange = eventFlags and FROM_ENABLE_CHANGE_FLAG == FROM_ENABLE_CHANGE_FLAG
                    append("From Enabled Change? ").append(fromEnableChange)
                    append(")")
                },
            )

            put("Event Data", "")

            data?.properties?.forEach { property ->
                put(
                    "${property.name.replaceFirstChar(Char::uppercase)} (${property.type.simpleName})",
                    data.getOrDefault(property),
                )
            }
        }

        return Detail(
            title = "Remote Alarm Event",
            details = details,
            body = body.entries.map { (key, value) -> "$key: $value" },
        )
    }

    companion object {
        @Serial
        @JvmStatic
        private val serialVersionUID = -8691470692255228480L
    }
}
