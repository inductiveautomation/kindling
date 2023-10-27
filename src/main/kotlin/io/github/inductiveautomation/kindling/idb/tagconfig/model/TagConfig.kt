package io.github.inductiveautomation.kindling.idb.tagconfig.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable(with = TagConfigSerializer::class)
data class TagConfig(
    // Basic Properties:
    val name: String?,
    val tagGroup: String?,
    val enabled: Boolean?,

    // Value Properties:
    val tagType: String, // Unlisted
    val typeId: String?, // Unlisted
    val valueSource: String?,
    val dataType: String?,
    val value: JsonPrimitive?,
    val opcServer: String?, // OPC
    val opcItemPath: JsonElement?, // OPC
    val sourceTagPath: String?, // Derived, Reference
    val executionMode: String?,
    val executionRate: Int?,
    val expression: String?, // Expression
    @SerialName("deriveExpressionGetter")
    val readExpression: String?, // Derived
    @SerialName("deriveExpressionSetter")
    val writeExpression: String?, // Derived
    val query: String?, // Query
    val queryType: String?, // Query
    val datasource: String?, // Query

    // Numeric Properties:
    val deadband: Double?,
    val deadbandMode: String?,
    val scaleMode: String?,
    val rawLow: Double?,
    val rawHigh: Double?,
    val scaledLow: Double?,
    val scaledHigh: Double?,
    val clampMode: String?,
    val scaleFactor: Double?,
    val engUnit: String?,
    val engLow: Double?,
    val engHigh: Double?,
    val engLimitMode: String?,
    val formatString: String?,

    // Metadata Properties:
    val tooltip: String?,
    val documentation: String?,
    val typeColor: JsonPrimitive?, // UDT Definitions

    // Security Properties
    val readPermissions: JsonObject?,
    val readOnly: Boolean?,
    val writePermissions: JsonObject?,

    // Scripting Properties
    val eventScripts: JsonArray?,

    // Alarm Properties
    val alarms: JsonArray?,
    val alarmEvalEnabled: Boolean?,

    // Historical Properties
    val historyEnabled: Boolean?,
    val historyProvider: String?,
    val historicalDeadbandStyle: String?,
    val historicalDeadbandMode: String?,
    val historicalDeadband: Double?,
    val sampleMode: String?,
    val historySampleRate: Int?,
    val historySampleRateUnits: String?,
    val historyTagGroup: String?,
    val historyTimeDeadband: Int?,
    val historyTimeDeadbandUnits: String?,
    val historyMaxAge: Int?,
    val historyMaxAgeUnits: String?,

    // UDT
    val parameters: JsonObject?,

    // Custom Properties:
    val customProperties: JsonObject,
)

object TagConfigSerializer : KSerializer<TagConfig> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("TagConfig") {
        element<String?>("name")
        element<String?>("tagGroup")
        element<Boolean?>("enabled")
        element<String>("tagType")
        element<String?>("typeId")
        element<String?>("valueSource")
        element<String?>("dataType")
        element<JsonPrimitive?>("value")
        element<String?>("opcServer")
        element<JsonElement?>("opcItemPath")
        element<String?>("sourceTagPath")
        element<String?>("executionMode")
        element<Int?>("executionRate")
        element<String?>("expression")
        element<String?>("deriveExpressionGetter")
        element<String?>("deriveExpressionSetter")
        element<String?>("query")
        element<String?>("queryType")
        element<String?>("datasource")
        element<Double?>("deadband")
        element<String?>("deadbandMode")
        element<String?>("scaleMode")
        element<Double?>("rawLow")
        element<Double?>("rawHigh")
        element<Double?>("scaledLow")
        element<Double?>("scaledHigh")
        element<String?>("clampMode")
        element<Double?>("scaleFactor")
        element<String?>("engUnit")
        element<Double?>("engLow")
        element<Double?>("engHigh")
        element<String?>("engLimitMode")
        element<String?>("formatString")
        element<String?>("tooltip")
        element<String?>("documentation")
        element<JsonPrimitive?>("typeColor")
        element<JsonObject?>("readPermissions")
        element<Boolean?>("readOnly")
        element<JsonObject?>("writePermissions")
        element<JsonArray?>("eventScripts")
        element<JsonArray?>("alarms")
        element<Boolean?>("alarmEvalEnabled")
        element<Boolean?>("historyEnabled")
        element<String?>("historyProvider")
        element<String?>("historicalDeadbandStyle")
        element<String?>("historicalDeadbandMode")
        element<Double?>("historicalDeadband")
        element<String?>("sampleMode")
        element<Int?>("historySampleRate")
        element<String?>("historySampleRateUnits")
        element<String?>("historyTagGroup")
        element<Int?>("historyTimeDeadband")
        element<String?>("historyTimeDeadbandUnits")
        element<Int?>("historyMaxAge")
        element<String?>("historyMaxAgeUnits")
        element<JsonObject?>("parameters")
        element<JsonArray?>("customProperties")
    }

    override fun deserialize(decoder: Decoder): TagConfig {
        // Cast to JSON-specific interface
        val jsonInput = decoder as? JsonDecoder ?: error("Can be deserialized only by JSON")

        // Read the whole content as JSON
        val json = jsonInput.decodeJsonElement().jsonObject.toMutableMap()

        // Extract and remove name property

        return TagConfig(
            name = json["name"]?.jsonPrimitive?.content.also { json.remove("name") },
            tagGroup = json["tagGroup"]?.jsonPrimitive?.content.also { json.remove("tagGroup") },
            enabled = json["enabled"]?.jsonPrimitive?.content?.toBoolean().also { json.remove("enabled") },
            tagType = json["tagType"]?.jsonPrimitive?.content.also { json.remove("tagType") } ?: "NoType",
            typeId = json["typeId"]?.jsonPrimitive?.content.also { json.remove("typeId") },
            valueSource = json["valueSource"]?.jsonPrimitive?.content.also { json.remove("valueSource") },
            dataType = json["dataType"]?.jsonPrimitive?.content.also { json.remove("dataType") },
            value = json["value"]?.jsonPrimitive.also { json.remove("value") },
            opcServer = json["opcServer"]?.jsonPrimitive?.content.also { json.remove("opcServer") },
            opcItemPath = json["opcItemPath"].also { json.remove("opcItemPath") },
            sourceTagPath = json["sourceTagPath"]?.jsonPrimitive?.content.also { json.remove("sourceTagPath") },
            executionMode = json["executionMode"]?.jsonPrimitive?.content.also { json.remove("executionMode") },
            executionRate = json["executionRate"]?.jsonPrimitive?.content?.toInt()
                .also { json.remove("executionRate") },
            expression = json["expression"]?.jsonPrimitive?.content.also { json.remove("expression") },
            readExpression = json["deriveExpressionGetter"]?.jsonPrimitive?.content.also { json.remove("deriveExpressionGetter") },
            writeExpression = json["deriveExpressionSetter"]?.jsonPrimitive?.content.also { json.remove("deriveExpressionSetter") },
            query = json["query"]?.jsonPrimitive?.content.also { json.remove("query") },
            queryType = json["queryType"]?.jsonPrimitive?.content.also { json.remove("queryType") },
            datasource = json["datasource"]?.jsonPrimitive?.content.also { json.remove("datasource") },
            deadband = json["deadband"]?.jsonPrimitive?.content?.toDouble().also { json.remove("deadband") },
            deadbandMode = json["deadbandMode"]?.jsonPrimitive?.content.also { json.remove("deadbandMode") },
            scaleMode = json["scaleMode"]?.jsonPrimitive?.content.also { json.remove("scaleMode") },
            rawLow = json["rawLow"]?.jsonPrimitive?.content?.toDouble().also { json.remove("rawLow") },
            rawHigh = json["rawHigh"]?.jsonPrimitive?.content?.toDouble().also { json.remove("rawHigh") },
            scaledLow = json["scaledLow"]?.jsonPrimitive?.content?.toDouble().also { json.remove("scaledLow") },
            scaledHigh = json["scaledHigh"]?.jsonPrimitive?.content?.toDouble().also { json.remove("scaledHigh") },
            clampMode = json["clampMode"]?.jsonPrimitive?.content.also { json.remove("clampMode") },
            scaleFactor = json["scaleFactor"]?.jsonPrimitive?.content?.toDouble().also { json.remove("scaleFactor") },
            engUnit = json["engUnit"]?.jsonPrimitive?.content.also { json.remove("engUnit") },
            engLow = json["engLow"]?.jsonPrimitive?.content?.toDouble().also { json.remove("engLow") },
            engHigh = json["engHigh"]?.jsonPrimitive?.content?.toDouble().also { json.remove("engHigh") },
            engLimitMode = json["engLimitMode"]?.jsonPrimitive?.content.also { json.remove("engLimitMode") },
            formatString = json["formatString"]?.jsonPrimitive?.content.also { json.remove("formatString") },
            tooltip = json["tooltip"]?.jsonPrimitive?.content.also { json.remove("tooltip") },
            documentation = json["documentation"]?.jsonPrimitive?.content.also { json.remove("documentation") },
            typeColor = json["typeColor"]?.jsonPrimitive.also { json.remove("typeColor") },
            readPermissions = json["readPermissions"]?.jsonObject.also { json.remove("readPermissions") },
            readOnly = json["readOnly"]?.jsonPrimitive?.content?.toBoolean().also { json.remove("readOnly") },
            writePermissions = json["writePermissions"]?.jsonObject.also { json.remove("writePermissions") },
            eventScripts = json["eventScripts"]?.jsonArray.also { json.remove("eventScripts") },
            alarms = json["alarms"]?.jsonArray.also { json.remove("alarms") },
            alarmEvalEnabled = json["alarmEvalEnabled"]?.jsonPrimitive?.content?.toBoolean()
                .also { json.remove("alarmEvalEnabled") },
            historyEnabled = json["historyEnabled"]?.jsonPrimitive?.content?.toBoolean()
                .also { json.remove("historyEnabled") },
            historyProvider = json["historyProvider"]?.jsonPrimitive?.content
                .also { json.remove("historyProvider") },
            historicalDeadbandStyle = json["historicalDeadbandStyle"]?.jsonPrimitive?.content
                .also { json.remove("historicalDeadbandStyle") },
            historicalDeadbandMode = json["historicalDeadbandMode"]?.jsonPrimitive?.content
                .also { json.remove("historicalDeadbandMode") },
            historicalDeadband = json["historicalDeadband"]?.jsonPrimitive?.content?.toDouble()
                .also { json.remove("historicalDeadband") },
            sampleMode = json["sampleMode"]?.jsonPrimitive?.content.also { json.remove("sampleMode") },
            historySampleRate = json["historySampleRate"]?.jsonPrimitive?.content?.toInt()
                .also { json.remove("historySampleRate") },
            historySampleRateUnits = json["historySampleRateUnits"]?.jsonPrimitive?.content
                .also { json.remove("historySampleRateUnits") },
            historyTagGroup = json["historyTagGroup"]?.jsonPrimitive?.content
                .also { json.remove("historyTagGroup") },
            historyTimeDeadband = json["historyTimeDeadband"]?.jsonPrimitive?.content?.toInt()
                .also { json.remove("historyTimeDeadband") },
            historyTimeDeadbandUnits = json["historyTimeDeadbandUnits"]?.jsonPrimitive?.content
                .also { json.remove("historyTimeDeadbandUnits") },
            historyMaxAge = json["historyMaxAge"]?.jsonPrimitive?.content?.toInt()
                .also { json.remove("historyMaxAge") },
            historyMaxAgeUnits = json["historyMaxAgeUnits"]?.jsonPrimitive?.content
                .also { json.remove("historyMaxAgeUnits") },
            parameters = json["parameters"]?.jsonObject.also { json.remove("parameters") },
            customProperties = JsonObject(json),
        )
    }

    override fun serialize(encoder: Encoder, value: TagConfig) = error("Serialization is not supported")
}
