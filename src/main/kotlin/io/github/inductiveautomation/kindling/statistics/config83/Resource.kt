package io.github.inductiveautomation.kindling.statistics.config83

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromStream
import java.nio.file.Path
import kotlin.io.path.div
import kotlin.io.path.inputStream
import kotlin.io.path.name
import kotlin.io.path.walk
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalSerializationApi::class)
class Resource(val filePath: Path) {
    val deploymentId: String
    val moduleId: String
    val categoryName: String
    val name: String

    init {
        val pathParts = filePath.map { it.name }
        val resourceIndex = pathParts.indexOf("resources")

        deploymentId = pathParts[resourceIndex + 1]
        moduleId = pathParts[resourceIndex + 2]
        categoryName = pathParts[resourceIndex + 3]
        name = filePath.parent.name
    }

    val config: JsonObject by lazy {
        (filePath.parent!! / "config.json").inputStream().use(Json::decodeFromStream)
    }

    val data: ResourceJson by lazy {
        filePath.inputStream().use(Json::decodeFromStream)
    }
}

@Serializable
data class ResourceJson(
    val scope: String,
    val description: String? = null,
    val version: Int,
    val restricted: Boolean,
    val overridable: Boolean,
    val files: List<String>,
    val attributes: Attributes,
) {
    @Serializable
    data class Attributes
    @OptIn(ExperimentalUuidApi::class)
    constructor(
        val lastModification: LastModification,
        val uuid: Uuid? = null,
        val lastModificationSignature: String,
        val enabled: Boolean = true,
    )

    @Serializable
    data class LastModification
    @OptIn(ExperimentalTime::class)
    constructor(
        val actor: String,
        val timestamp: Instant,
    )
}

class ResourceCategory(val directory: Path) {
    val name: String by directory::name

    val resources: Sequence<Resource> = directory.walk().mapNotNull {
        if (it.name == "resource.json") {
            Resource(it)
        } else {
            null
        }
    }

    operator fun get(resourceName: String): Resource? = resources.find { it.name == resourceName }
}
