package io.github.inductiveautomation.kindling.idb.tagconfig.model

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class NodeStatistics(private val node: Node) {
    // Tag Type
    val isUdtDefinition: Boolean
        get() = node.config.tagType == "UdtType"
    val isUdtInstance: Boolean
        get() = node.config.tagType == "UdtInstance"
    val isAtomicTag: Boolean
        get() = node.config.tagType == "AtomicTag"
    val isFolder: Boolean
        get() = node.config.tagType == "Folder"

    // Alarms:
    private val alarmStates: MutableList<AlarmState> = node.config.alarms?.map {
        val name = it.jsonObject["name"]?.jsonPrimitive?.content
        val enabled = try {
            it.jsonObject["enabled"]?.jsonPrimitive?.boolean ?: true
        } catch (e: IllegalArgumentException) {
            true
        }

        if (name == null) null else AlarmState(name, enabled)
    }?.filterNotNull()?.toMutableList() ?: mutableListOf()

    val numAlarms: Int
        get() = alarmStates.filter { it.enabled ?: true }.size
    val hasAlarms: Boolean
        get() = numAlarms > 0

    // Scripts:
    private val scriptStates: MutableList<ScriptConfig> = node.config.eventScripts ?: mutableListOf()

    val numScripts: Int
        get() = scriptStates.filter { it.enabled ?: true }.size
    val hasScripts: Boolean
        get() = numScripts > 0

    // Other Tag Properties:
    var historyEnabled: Boolean? = try {
        node.config.historyEnabled?.jsonPrimitive?.boolean
    } catch (e: Exception) {
        true
    }

    var dataType: String? = when (val dType = node.config.dataType) {
        is JsonObject -> "Bound"
        is JsonPrimitive -> dType.content
        is JsonArray -> throw IllegalArgumentException("Datatype cannot be a JSON Array")
        null -> null
    }

    var dataSource: String? = node.config.valueSource

    var isReadOnly: Boolean? = node.config.readOnly

    fun copyToNewNode(newNodeStatistics: NodeStatistics) {
        newNodeStatistics.historyEnabled = historyEnabled
        newNodeStatistics.dataType = node.statistics.dataType
        newNodeStatistics.dataSource = node.statistics.dataSource
        newNodeStatistics.scriptStates.addAll(scriptStates)
        newNodeStatistics.alarmStates.addAll(alarmStates)
        newNodeStatistics.isReadOnly = isReadOnly
    }

    fun copyToOverrideNode(overrideNodeStatistics: NodeStatistics) {
        if (overrideNodeStatistics.historyEnabled == null) {
            overrideNodeStatistics.historyEnabled = historyEnabled
        }

        if (overrideNodeStatistics.dataType == null) {
            overrideNodeStatistics.dataType = dataType
        }

        if (overrideNodeStatistics.dataSource == null) {
            overrideNodeStatistics.dataSource = dataSource
        }

        if (overrideNodeStatistics.isReadOnly == null) {
            overrideNodeStatistics.isReadOnly = isReadOnly
        }

        alarmStates.forEach { parentAlarmState ->
            val matchingChildState = overrideNodeStatistics.alarmStates.find { it.name == parentAlarmState.name }

            if (matchingChildState == null) {
                overrideNodeStatistics.alarmStates.add(parentAlarmState)
            } else {
                matchingChildState.enabled = matchingChildState.enabled ?: parentAlarmState.enabled
            }
        }

        scriptStates.forEach { parentScriptState ->
            val matchingChildState = overrideNodeStatistics.scriptStates.find { it.eventId == parentScriptState.eventId }

            if (matchingChildState == null) {
                overrideNodeStatistics.scriptStates.add(parentScriptState)
            } else {
                matchingChildState.enabled = matchingChildState.enabled ?: parentScriptState.enabled
            }
        }
    }

    data class AlarmState(
        val name: String,
        var enabled: Boolean?,
    )
}
