package io.github.inductiveautomation.kindling.resources

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNames
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Path
import kotlin.io.path.div
import kotlin.io.path.inputStream

internal val ResourceJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}

data class ResourceType(
    val moduleId: String,
    val typeId: String,
) {
    override fun toString(): String = "$moduleId/$typeId"

    companion object {
        const val PLATFORM_MODULE_ID = "ignition"
    }
}

/**
 * The location of a resource within a collection. Singleton resources have no [segments]; named resources have one
 * segment per (unescaped) directory below the type directory.
 */
data class ResourcePath(
    val type: ResourceType,
    val segments: List<String> = emptyList(),
) {
    val isSingleton: Boolean
        get() = segments.isEmpty()

    val name: String?
        get() = segments.lastOrNull()

    override fun toString(): String = if (isSingleton) type.toString() else "$type/${segments.joinToString("/")}"
}

/**
 * The contents of a `config-mode.json` or `project.json` file.
 */
@Serializable
data class CollectionManifest(
    val title: String? = null,
    val description: String? = null,
    val enabled: Boolean = true,
    val inheritable: Boolean = false,
    val parent: String? = null,
)

/**
 * The contents of a `resource.json` or `unary-resource.json` file.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class ResourceManifest(
    val scope: String? = null,
    @JsonNames("documentation") // config vs projects use different keys for description
    val description: String? = null,
    val version: Int = 1,
    val restricted: Boolean = false,
    val overridable: Boolean = true,
    val files: Set<String> = emptySet(),
    val attributes: JsonObject = JsonObject(emptyMap()),
) {
    val enabled: Boolean
        get() = attributes["enabled"]?.jsonPrimitive?.booleanOrNull ?: true

    val uuid: String?
        get() = attributes["uuid"]?.jsonPrimitive?.contentOrNull
}

class Resource(
    val collection: String,
    val path: ResourcePath,
    val manifest: ResourceManifest,
    val directory: Path,
) {
    val type: ResourceType by path::type
    val name: String? by path::name
    val description: String? by manifest::description
    val enabled: Boolean by manifest::enabled

    fun file(name: String): Path = directory / name

    @OptIn(ExperimentalSerializationApi::class)
    fun readJson(name: String): JsonObject = file(name).inputStream().use(ResourceJson::decodeFromStream)

    val config: JsonObject by lazy { readJson(CONFIG_JSON) }

    override fun toString(): String = "Resource($collection:$path)"

    companion object {
        const val CONFIG_JSON = "config.json"
    }
}
