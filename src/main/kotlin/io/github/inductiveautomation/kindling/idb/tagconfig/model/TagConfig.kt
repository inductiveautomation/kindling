package io.github.inductiveautomation.kindling.idb.tagconfig.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonTransformingSerializer
import kotlinx.serialization.json.jsonObject

/**
 * All possible elements of a node (of any type) in the tag hierarchy.
 * This is 'technically' future-proof in the sense that if more tag config entries are added to ignition,
 * they will be bundled with the customProperties property instead of causing an error.
 *
 * Values marked as JsonElement or any of its subclasses are marked as such for two reasons. Either:
 * 1. We don't care about their data types, and the serializers will represent them correctly for display/export purposes.
 * 2. The value can be multiple types, depending on the export. For example, a tag's value can be either a Json Primitive or an Object,
 *      if the tag's value is bound.
 */
@Serializable
data class TagConfig(
    // Basic Properties:
    val name: String? = null,
    val tagGroup: JsonElement? = null, // String
    val enabled: JsonElement? = null, // String

    // Value Properties:
    val tagType: String? = null, // Unlisted
    val typeId: String? = null, // Unlisted
    val valueSource: String? = null,
    val dataType: JsonElement? = null, // String
    val value: JsonElement? = null, // JsonPrimitive
    val opcServer: JsonElement? = null, // OPC, String
    val opcItemPath: JsonElement? = null, // OPC // JsonElement
    val sourceTagPath: JsonElement? = null, // Derived, Reference, String
    val executionMode: JsonElement? = null, // String
    val executionRate: JsonElement? = null, // Int
    val expression: JsonElement? = null, // Expression, String
    @SerialName("deriveExpressionGetter")
    val readExpression: JsonElement? = null, // Derived, String
    @SerialName("deriveExpressionSetter")
    val writeExpression: JsonElement? = null, // Derived, String
    val query: JsonElement? = null, // Query, String
    val queryType: JsonElement? = null, // Query, String
    val datasource: JsonElement? = null, // Query, String

    // Numeric Properties:
    val deadband: JsonElement? = null, // Double
    val deadbandMode: JsonElement? = null, // String
    val scaleMode: JsonElement? = null, // String
    val rawLow: JsonElement? = null, // Double
    val rawHigh: JsonElement? = null, // Double
    val scaledLow: JsonElement? = null, // Double
    val scaledHigh: JsonElement? = null, // Double
    val clampMode: JsonElement? = null, // String
    val scaleFactor: JsonElement? = null, // Double
    val engUnit: JsonElement? = null, // String
    val engLow: JsonElement? = null, // Double
    val engHigh: JsonElement? = null, // Double
    val engLimitMode: JsonElement? = null, // String
    val formatString: JsonElement? = null, // String
    // Metadata Properties:
    val tooltip: JsonElement? = null, // String
    val documentation: JsonElement? = null, // String
    val typeColor: JsonPrimitive? = null, // UDT Definitions

    // Security Properties
    val readPermissions: JsonObject? = null,
    val readOnly: Boolean? = null,
    val writePermissions: JsonObject? = null,

    // Scripting Properties
    val eventScripts: MutableList<ScriptConfig>? = null,

    // Alarm Properties
    val alarms: JsonArray? = null,
    val alarmEvalEnabled: JsonElement? = null, // Boolean

    // Historical Properties
    val historyEnabled: JsonElement? = null, // Boolean
    val historyProvider: JsonElement? = null, // String
    val historicalDeadbandStyle: JsonElement? = null, // String
    val historicalDeadbandMode: JsonElement? = null, // String
    val historicalDeadband: JsonElement? = null, // Double
    val sampleMode: JsonElement? = null, // String
    val historySampleRate: JsonElement? = null, // Int
    val historySampleRateUnits: JsonElement? = null, // String
    val historyTagGroup: JsonElement? = null, // String
    val historyTimeDeadband: JsonElement? = null, // Int
    val historyTimeDeadbandUnits: JsonElement? = null, // String
    val historyMaxAge: JsonElement? = null, // Int
    val historyMaxAgeUnits: JsonElement? = null, // String
    val tags: NodeGroup = NodeGroup(),

    // UDT
    val parameters: JsonObject? = null,

    // Custom Properties:
    val customProperties: JsonObject? = null,
)

@Serializable
data class ScriptConfig(
    @SerialName("eventid")
    val eventId: String,
    val script: String? = null,
    var enabled: Boolean? = null,
)

data class AlarmState(
    val name: String,
    var enabled: Boolean?,
)

/**
 * Deserialize the json into a TagConfig object,
 * grouping all custom properties into a separate `customProperties` property.
 */
object TagConfigSerializer : JsonTransformingSerializer<TagConfig>(TagConfig.serializer()) {
    @OptIn(ExperimentalSerializationApi::class)
    override fun transformDeserialize(element: JsonElement): JsonElement {
        val elementNames = List(TagConfig.serializer().descriptor.elementsCount) {
            TagConfig.serializer().descriptor.getElementName(it)
        }.filter { it != "customProperties" }

        val elementMap = element.jsonObject.toMutableMap()

        val customPropertiesMap = elementMap.filter { it.key !in elementNames }
            .onEach { (key, value) ->
                elementMap.remove(key, value)
            }

        elementMap["customProperties"] = if (customPropertiesMap.isEmpty()) {
            JsonNull
        } else {
            JsonObject(customPropertiesMap)
        }

        return JsonObject(elementMap)
    }

    /*
        "Spread" all the values inside the "customProperties" property back into their own json key/value pair for serialization.
     */
    override fun transformSerialize(element: JsonElement): JsonElement {
        val tagConfig = element.jsonObject.toMutableMap()

        val customProperties = tagConfig.remove("customProperties")?.let {
            if (it is JsonNull) {
                return JsonObject(tagConfig)
            } else {
                it.jsonObject
            }
        }

        customProperties?.entries?.forEach { (key, value) ->
            tagConfig[key] = value
        }

        return JsonObject(tagConfig)
    }
}

/**
 * Serialize a node without recursively serializing the tags within it.
 * This is used to display a single nodes config when browsing from the Tree in the UI.
 */
object MinimalTagConfigSerializer : JsonTransformingSerializer<TagConfig>(TagConfig.serializer()) {
    override fun transformDeserialize(element: JsonElement): JsonElement {
        throw UnsupportedOperationException("This serializer is for serialization only. Use TagConfigSerializer instead.")
    }

    override fun transformSerialize(element: JsonElement): JsonElement {
        val tagConfig = element.jsonObject.toMutableMap()
        tagConfig.remove("tags")

        val customProperties = tagConfig.remove("customProperties")?.let {
            if (it is JsonNull) {
                return JsonObject(tagConfig)
            } else {
                it.jsonObject
            }
        }

        customProperties?.entries?.forEach { (key, value) ->
            tagConfig[key] = value
        }

        return JsonObject(tagConfig)
    }
}
