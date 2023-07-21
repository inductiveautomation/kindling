package io.github.inductiveautomation.kindling.cache.model

import com.inductiveautomation.ignition.common.alarming.EventData
import com.inductiveautomation.ignition.common.alarming.evaluation.EventPropertyType
import io.github.inductiveautomation.kindling.core.Detail
import java.io.Serializable
import java.util.EnumSet

class AlarmJournalData(
    private val profileName: String?,
    private val tableName: String?,
    private val dataTableName: String?,
    private val source: String?,
    private val dispPath: String?,
    private val uuid: String?,
    private val priority: Int,
    private val eventType: Int,
    private val eventFlags: Int,
    val data: EventData,
    private val storedProps: EnumSet<EventPropertyType>,
) : Serializable {
    val details by lazy {
        mapOf(
            "profile" to profileName.toString(),
            "table" to tableName.toString(),
            "dataTable" to dataTableName.toString(),
            "source" to source.toString(),
            "displayPath" to dispPath.toString(),
            "uuid" to uuid.toString(),
            "priority" to priority.toString(),
            "eventType" to eventType.toString(),
            "eventFlags" to eventFlags.toString(),
            "storedProps" to storedProps.joinToString(),
        )
    }

    val body by lazy {
        data.properties.map { property ->
            "${property.name} (${property.type.simpleName}) = ${data.getOrDefault(property)}"
        }
    }

    fun toDetail() = Detail(
        title = "Alarm Journal Data",
        details = details,
        body = body,
    )

    companion object {
        @JvmStatic
        private val serialVersionUID = 1L
    }
}

class AlarmJournalSFGroup(
    private val groupId: String,
    private val entries: List<AlarmJournalData>,
) : Serializable {
    fun toDetail() = Detail(
        title = "Grouped Alarm Journal Data ($groupId)",
        details = entries.fold(mutableMapOf()) { acc, nextData ->
            acc.putAll(nextData.details)
            acc
        },
        body = entries.flatMap {
            it.data.timestamp
            it.body
        },
    )

    companion object {
        @JvmStatic
        private val serialVersionUID = -1199203578454144713L
    }
}
