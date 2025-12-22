@file:OptIn(ExperimentalSerializationApi::class)

package io.github.inductiveautomation.kindling.tagconfig.model

import io.github.inductiveautomation.kindling.utils.SQLiteConnection
import io.github.inductiveautomation.kindling.utils.get
import io.github.inductiveautomation.kindling.utils.toList
import io.github.inductiveautomation.kindling.utils.transferTo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.jsonObject
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import kotlin.io.path.div
import kotlin.io.path.inputStream
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.outputStream
import kotlin.io.path.walk
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class TagProvider private constructor(
    name: String,
    uuid: Uuid,
    description: String?,
    enabled: Boolean,
    private val configDir: Path,
    private val valuePersistence: ValuePersistence,
) : AbstractTagProvider(name, uuid, description, enabled) {
    override val providerStatistics = ProviderStatistics()

    override val loadProvider: Job = CoroutineScope(Dispatchers.Default).launch(start = CoroutineStart.LAZY) {
        providerNode = Node(
            config = TagConfig(
                name = "",
                tagType = "Provider",
                tags = mutableListOf(typesNode),
            ),
            isMeta = true,
        )

        loadData()
    }

    private val valueStore: Connection by lazy {
        val idbPath = configDir / "ignition/tags/valueStore.idb"
        val file = Files.createTempFile("kindling", idbPath.name).also { tempFile ->
            idbPath.inputStream() transferTo tempFile.outputStream()
        }

        SQLiteConnection(file)
    }

    override val Node.parentType: Node?
        get() {
            require((statistics.isUdtDefinition || statistics.isUdtInstance) && config.typeId != null) {
                "Not a top level UDT Instance or type! $this"
            }
            return this@TagProvider["_types_/${config.typeId}"]
        }

    fun getValueStoreEntry(node: Node): ValueStoreEntry? = valueStore.prepareStatement(VALUE_STORE_QUERY).run {
        setString(1, name.lowercase())
        setString(2, node.tagPath.substringAfter("]").lowercase())
        executeQuery().toList { rs ->
            ValueStoreEntry(
                rs["provider"],
                rs["path"],
                rs["dataType"],
                rs.getBytes("textValue")?.decodeToString(),
                rs["numericValue"],
                rs["nullValue"],
                rs["quality"],
                rs["t_stamp"],
                rs["updatedAt"],
            )
        }.singleOrNull()
    }

    operator fun get(tagPath: String): Node? {
        if (tagPath == "[$name]" || tagPath == "") {
            return providerNode
        } else {
            val names = tagPath.substringAfter("[$name]").split("/")
            var currentNode = providerNode

            for (childName in names) {
                currentNode = (currentNode / childName) ?: return null
            }

            return currentNode
        }
    }

    private fun loadData() {
        loadDefinitions()
        loadTags()
        resolveUdtTypes()
        resolveTags()

        if (typesNode.config.tags.isEmpty()) {
            providerNode.config.tags.remove(typesNode)
        }

        providerNode.walkTopLevel {
            providerStatistics.processNodeForStatistics(it)
            if (it.statistics.isUdtInstance) {
                it.walk { tag ->
                    providerStatistics.processNodeForStatistics(tag)
                }
            }
        }
    }

    private fun loadDefinitions() {
        val udtDir = configDir / "resources/core/ignition/tag-type-definition" / name
        loadFolderTagStructure(udtDir, typesNode, listOf("udts.json"))
    }

    private fun loadTags() {
        val tagDir = configDir / "resources/core/ignition/tag-definition" / name
        loadFolderTagStructure(tagDir, providerNode, listOf("tags.json", "udts.json"))
    }

    private fun resolveUdtTypes() {
        typesNode.walkTopLevel {
            if (it.statistics.isUdtDefinition && !it.resolved) it.resolveInheritance()
        }
    }

    private fun resolveTags() {
        providerNode.walkTopLevel {
            if (it.statistics.isUdtInstance && !it.resolved) {
                it.resolveInheritance()
            }

            if (it.statistics.isAtomicTag && it.config.defaultValue != null) {
                if (it.config.value == null && valuePersistence == ValuePersistence.DATABASE) {
                    val valueStoreEntry = getValueStoreEntry(it)

                    if (valueStoreEntry == null) {
                        it.config.value = it.config.defaultValue
                    } else {
                        when (ValueStoreDataType.fromDbValue(valueStoreEntry.dataType)) {
                            ValueStoreDataType.SHORT,
                            ValueStoreDataType.BYTE,
                            ValueStoreDataType.INTEGER,
                            -> {
                                (valueStoreEntry.numericValue as Number?)?.toInt()?.let { num ->
                                    it.config.value = JsonPrimitive(num)
                                }
                            }
                            ValueStoreDataType.LONG,
                            ValueStoreDataType.DATETIME,
                            -> {
                                (valueStoreEntry.numericValue as Number?)?.toLong()?.let { num ->
                                    it.config.value = JsonPrimitive(num)
                                }
                            }
                            ValueStoreDataType.FLOAT,
                            ValueStoreDataType.DOUBLE,
                            -> {
                                (valueStoreEntry.numericValue as Number?)?.toDouble()?.let { num ->
                                    it.config.value = JsonPrimitive(num)
                                }
                            }
                            ValueStoreDataType.BOOLEAN -> {
                                if (valueStoreEntry.numericValue != null) {
                                    it.config.value = JsonPrimitive(valueStoreEntry.numericValue == 1)
                                }
                            }
                            ValueStoreDataType.STRING -> {
                                valueStoreEntry.textValue?.let { str ->
                                    it.config.value = JsonPrimitive(str)
                                }
                            }
                            null -> Unit
                        }
                    }
                }
            }
        }
    }

    override fun Node.resolveInheritance() {
        if (resolved) return

        if (statistics.isUdtDefinition) {
            resolveNestedUdtInstances()
        }

        if (config.typeId.isNullOrEmpty()) {
            resolved = true
            return
        }

        // typeId already checked. We should find a definition for this, otherwise we're missing a def.
        val inheritedParent = checkNotNull(parentType) { "Parent type is null!" }

        if (!inheritedParent.resolved) {
            inheritedParent.resolveInheritance()
        }

        copyChildrenFrom(inheritedParent)
        resolved = true
    }

    override fun Node.resolveNestedUdtInstances() {
        require(statistics.isUdtDefinition) { "Not a UDT Definition!" }

        walkTopLevel { child ->
            if (child.statistics.isUdtInstance) {
                if (child.config.typeId.isNullOrEmpty()) {
                    child.resolved = true
                    return@walkTopLevel
                }

                val childDefinition = checkNotNull(child.parentType) { "Child definition is null!" }

                if (!childDefinition.resolved) {
                    childDefinition.resolveInheritance()
                }

                child.copyChildrenFrom(childDefinition)
                child.resolved = true
            }
        }
    }

    override fun Node.copyChildrenFrom(other: Node) {
        for (tag in other.children()) {
            var overrideTag = config.tags.find { it.name == tag.name && it.config.tagType == tag.config.tagType }

            if (overrideTag == null) {
                checkNotNull(tag.config.tagType) { "Tag type is null!" }
                overrideTag = Node(
                    TagConfig(
                        name = tag.name,
                        tagType = tag.config.tagType,
                    ),
                    inferredFrom = tag,
                    resolved = true,
                )

                tag.statistics.copyToNewNode(overrideTag.statistics)
                addChildTag(overrideTag)
            } else {
                tag.statistics.copyToOverrideNode(overrideTag.statistics)
            }

            if (!overrideTag.statistics.isAtomicTag) {
                overrideTag.copyChildrenFrom(tag)
            }

            tag.resolved = true
        }
    }

    companion object {
        private val VALUE_STORE_QUERY = """
            SELECT * FROM TAG_VALUE_STORE WHERE provider = ? AND path = ?
        """.trimIndent()

        fun loadProviders(configDir: Path): List<TagProvider> {
            val providersDir = configDir / "resources/core/ignition/tag-provider"

            return providersDir.listDirectoryEntries().mapNotNull {
                if (!it.isDirectory()) {
                    return@mapNotNull null
                }

                val resourceFile = it / "resource.json"
                val resource: TagProviderResourceDelegate = resourceFile.inputStream().use(Json::decodeFromStream)

                val configFile = it / "config.json"
                val config: JsonObject = configFile.inputStream().use(Json::decodeFromStream)

                val valuePersistence = runCatching {
                    Json.decodeFromJsonElement<ValuePersistence>(config["settings"]!!.jsonObject["valuePersistence"]!!)
                }.getOrNull()

                TagProvider(
                    name = it.name,
                    uuid = resource.attributes.uuid,
                    description = resource.description,
                    enabled = resource.attributes.enabled,
                    configDir = configDir,
                    valuePersistence = valuePersistence ?: ValuePersistence.NONE,
                )
            }
        }

        private fun loadFolderTagStructure(dir: Path, rootNode: Node, fileNames: List<String>) {
            val allFiles = dir.walk().filter {
                it.name in fileNames
            }

            val folders = mutableMapOf(
                dir to rootNode,
            )

            for (path in allFiles) {
                val pathParts = path - dir

                var lastSeen = rootNode
                var currentPath = dir

                for (part in pathParts) {
                    currentPath /= part

                    if (currentPath.isDirectory()) {
                        var exists = true
                        val folderConfig = folders.getOrPut(currentPath) {
                            exists = false
                            Node(
                                config = TagConfig(
                                    name = part.toString(),
                                    tagType = "Folder",
                                ),
                            )
                        }

                        if (!exists) {
                            lastSeen.addChildTag(folderConfig)
                        }

                        lastSeen = folderConfig
                    } else {
                        val tags: List<Node> = currentPath.inputStream().use(Json::decodeFromStream)
                        lastSeen.addChildTags(tags)
                    }
                }
            }
        }

        /**
         * Walk through a tags structure without going into UDT Instances or Definitions.
         * This will hit every atomic tag, folder, and UDT inside all folders, but not tags inside UDTs
         */
        private fun Node.walkTopLevel(block: (Node) -> Unit) {
            for (child in children()) {
                block(child)
                if (child.statistics.isFolder) {
                    child.walkTopLevel(block)
                }
            }
        }

        /**
         * Walks through an entire tag structure.
         */
        private fun Node.walk(block: (Node) -> Unit) {
            for (child in children()) {
                block(child)
                child.walk(block)
            }
        }
    }

    @Serializable
    private data class TagProviderResourceDelegate(
        val scope: String,
        val description: String? = null,
        val version: Int,
        val restricted: Boolean,
        val overridable: Boolean,
        val files: List<String>,
        val attributes: TagProviderAttributes,
    )

    @Serializable
    private data class TagProviderAttributes(
        val lastModification: TagProviderLastModification,
        val uuid: Uuid,
        val lastModificationSignature: String,
        val enabled: Boolean,
    )

    @Serializable
    private data class TagProviderLastModification(
        val actor: String,
        val timestamp: String,
    )
}
