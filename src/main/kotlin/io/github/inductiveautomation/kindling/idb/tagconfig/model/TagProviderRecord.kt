package io.github.inductiveautomation.kindling.idb.tagconfig.model

import io.github.inductiveautomation.kindling.idb.tagconfig.TagConfigView
import io.github.inductiveautomation.kindling.idb.tagconfig.model.NodeGroup.Companion.toNodeGroup
import io.github.inductiveautomation.kindling.utils.executeQuery
import io.github.inductiveautomation.kindling.utils.get
import io.github.inductiveautomation.kindling.utils.toList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.encodeToStream
import org.intellij.lang.annotations.Language
import java.nio.file.Path
import java.sql.Connection
import java.sql.PreparedStatement
import kotlin.io.path.outputStream

/**
 * Primary constructor parameters represent an entry in the IDB for a tag provider.
 * The class body is all the parsing logic to parse the tag config table and build the node hierarchy for this provider.
 */
@Suppress("MemberVisibilityCanBePrivate")
data class TagProviderRecord(
    val id: Int,
    val name: String,
    val uuid: String,
    val description: String?,
    val enabled: Boolean,
    val typeId: String,
    val statement: PreparedStatement,
) {
    val providerStatistics = ProviderStatistics()

    // Lazy Job which can be started manually with join(), or automatically when getProviderNode() is called.
    val loadProvider: Job = CoroutineScope(Dispatchers.Default).launch(start = CoroutineStart.LAZY) {
        providerNode = createProviderNode(typesNode).apply {
            // Main Tag Config resolution loop.
            for ((_, nodeGroup) in nodeGroups) {
                // Resolve and process tags
                with(nodeGroup) {
                    if (parentNode.statistics.isUdtDefinition || parentNode.statistics.isUdtInstance) {
                        if (!isResolved) {
                            resolveInheritance(nodeGroups, udtDefinitions)
                        }
                    }
                    resolveHierarchy()
                    when (val folderId = parentNode.folderId) {
                        "_types_" -> typesNode.addChildTag(parentNode)
                        null -> addChildTag(parentNode)
                        else -> {
                            val folderGroup = nodeGroups[folderId]
                            folderGroup?.parentNode?.addChildTag(parentNode)
                                ?: providerStatistics.orphanedTags.value.add(parentNode)
                        }
                    }
                }

                // Gather Statistics
                if (nodeGroup.parentNode.statistics.isUdtDefinition) {
                    providerStatistics.processNodeForStatistics(nodeGroup.parentNode)
                } else {
                    nodeGroup.forEach(providerStatistics::processNodeForStatistics)
                }
            }

            if (orphanedParentNode.config.tags.isNotEmpty()) {
                addChildTag(orphanedParentNode)
            }
        }
    }

    // Effectively just the TagConfig table in memory.
    val rawNodeData by lazy {
        statement
            .apply {
                setInt(1, id)
            }
            .executeQuery()
            .toList { rs ->
                try {
                    Node(
                        id = rs["id"],
                        providerId = rs["providerId"],
                        folderId = rs["folderId"],
                        config = TagConfigView.TagExportJson.decodeFromString(TagConfigSerializer, rs["cfg"]),
                        rank = rs["rank"],
                        name = rs["name"],
                    )
                } catch (e: NullPointerException) {
                    // Null records will be ignored.
                    null
                }
            }.filterNotNull()
    }

    /**
     * Group and sort nodes by their "NodeGroup".
     *
     * A node's ID tells us how many UDT's deep it is.
     * A node with a standard 36-length ID is a "top-level" node. i.e., it is not within a UDT.
     *
     * Nodes are grouped by the first UUID in their total UUID, so a nodegroup will consist of the parent UDT + any children.
     * Some NodeGroups, like top-level folders, only contain themselves.
     */
    val nodeGroups: Map<String, NodeGroup> by lazy {
        rawNodeData.groupBy { node ->
            node.id.substring(0, 36)
        }.mapValues { (_, nodes) ->
            nodes.toNodeGroup()
        }.toList()
            .sortedBy { (_, nodes) ->
                nodes.first().rank
            }.toMap()
    }

    val udtDefinitions: Map<String, Node> by lazy {
        rawNodeData.filter { it.statistics.isUdtDefinition }
            .associateBy { it.getFullUdtDefinitionPath(nodeGroups) }
    }

    val typesNode = createTypesNode(id)

    // This gets initialized when the loadProvider job is finished.
    // loadProvider is started when getProviderNode() is called, or when loadProvider.join() is called
    private lateinit var providerNode: Node

    suspend fun getProviderNode() = CoroutineScope(Dispatchers.Default).async {
        loadProvider.join()
        providerNode
    }

    val isInitialized: Boolean
        get() = ::providerNode.isInitialized

    val orphanedParentNode by lazy {
        Node(
            id = "orphaned_nodes",
            providerId = id,
            folderId = null,
            config = TagConfig(
                name = "Orphaned Nodes by Missing Parent",
                tags = run {
                    val orphanedTags = mutableMapOf<String, Node>()
                    providerStatistics.orphanedTags.value.forEach { orphanedNode ->
                        val falseParent = orphanedTags.getOrPut(orphanedNode.folderId!!) {
                            Node(
                                id = orphanedNode.folderId,
                                config = TagConfig(),
                                folderId = "orphaned_nodes",
                                rank = 2,
                                name = "${orphanedNode.folderId}",
                                providerId = id,
                                inferredNode = true,
                            )
                        }
                        falseParent.addChildTag(orphanedNode)
                    }
                    orphanedTags.values.toNodeGroup()
                },
            ),
            rank = 1,
            name = "Orphaned Nodes by Missing Parent",
            isMeta = true,
        )
    }

    /**
     *  Export the whole tag provider to JSON, effectively just exporting the root node's config.
     */
    @OptIn(ExperimentalSerializationApi::class)
    fun exportToJson(selectedFilePath: Path) {
        selectedFilePath.outputStream().use {
            TagConfigView.TagExportJson.encodeToStream(providerNode, it)
        }
    }

    // Traversal Helper Functions:
    private fun Node.getParentType(): Node? {
        require((statistics.isUdtDefinition || statistics.isUdtInstance) && config.typeId != null) {
            "Not a top level UDT Instance or type! $this"
        }
        return udtDefinitions[config.typeId.lowercase()]
    }

    private fun Node.getFullUdtDefinitionPath(nodeGroups: Map<String, NodeGroup>): String {
        val lowercaseName = actualName.lowercase()
        return if (folderId == "_types_") {
            lowercaseName
        } else {
            val parentName =
                nodeGroups[folderId!!]?.parentNode?.getFullUdtDefinitionPath(nodeGroups) ?: "<No Name Found?>"
            "$parentName/$lowercaseName"
        }
    }

    private fun NodeGroup.resolveNestedChildInstances(
        nodeGroups: Map<String, NodeGroup>,
        udtDefinitions: Map<String, Node>,
    ) {
        require(parentNode.statistics.isUdtDefinition)

        val childInstances = childNodes.filter { it.statistics.isUdtInstance }

        for (childInstance in childInstances) {
            val childDefinition = childInstance.getParentType() ?: continue
            val childDefinitionGroup = nodeGroups[childDefinition.id] ?: throw IllegalStateException(
                "This should never happen. Please report this issue to the maintainers of Kindling.",
            )

            // nodeGroups being ordered by rank will make this check not succeed very often
            if (!childDefinitionGroup.isResolved) {
                childDefinitionGroup.resolveInheritance(nodeGroups, udtDefinitions)
            }

            copyChildrenFrom(childDefinitionGroup, instanceId = childInstance.id)
        }
    }

    // Copy node configs based on UDT inheritance. This also creates new nodes which have no overrides, thus no IDB entry.
    private fun NodeGroup.resolveInheritance(
        nodeGroups: Map<String, NodeGroup>,
        udtDefinitions: Map<String, Node>,
    ) {
        if (parentNode.statistics.isUdtDefinition) {
            resolveNestedChildInstances(nodeGroups, udtDefinitions)
        }

        if (parentNode.config.typeId.isNullOrEmpty()) {
            isResolved = true
            return
        }

        val inheritedParentNode = parentNode.getParentType() ?: run {
            isResolved = true
            return
        }
        val inheritedNodeGroup = checkNotNull(nodeGroups[inheritedParentNode.id]) { "This should never happen" }

        if (!inheritedNodeGroup.isResolved) {
            inheritedNodeGroup.resolveInheritance(nodeGroups, udtDefinitions)
        }

        copyChildrenFrom(inheritedNodeGroup)

        isResolved = true
    }

    // Build the node hierarchy by adding child tags throughout the group
    private fun NodeGroup.resolveHierarchy() {
        if (size == 1) return

        for (i in 1..<size) {
            val childNode = get(i)
            find { node -> node.id == childNode.folderId }?.addChildTag(childNode)
                ?: providerStatistics.orphanedTags.value.add(childNode)
        }
    }

    companion object {
        @Language("SQLite")
        private val TAG_PROVIDER_TABLE_QUERY = """
            SELECT
                tagprovidersettings_id AS id,
                name,
                providerid,
                description,
                enabled,
                typeid
            FROM
                tagprovidersettings
            ORDER BY
                name
        """.trimIndent()

        @Language("SQLite")
        private val TAG_CONFIG_TABLE_QUERY = """
            SELECT
                id,
                providerid,
                folderid,
                cfg,
                rank,
                JSON_EXTRACT(cfg, '$.name') AS name
            FROM
                tagconfig
            WHERE
                providerid = ?
            ORDER BY
                id
        """.trimIndent()

        fun TagProviderRecord.createProviderNode(typesNode: Node? = null): Node = Node(
            id = this.uuid,
            providerId = this.id,
            folderId = null,
            config = TagConfig(
                name = "",
                tagType = "Provider",
                tags = typesNode?.let { NodeGroup(it) } ?: NodeGroup(),
            ),
            rank = 0,
            name = this.name,
        )

        fun createTypesNode(providerId: Int): Node = Node(
            id = "_types_",
            providerId = providerId,
            folderId = null,
            config = TagConfig(
                name = "_types_",
                tagType = "Folder",
                tags = NodeGroup(),
            ),
            rank = 1,
            name = "_types_",
            isMeta = true,
        )

        private fun NodeGroup.copyChildrenFrom(
            otherGroup: NodeGroup,
            instanceId: String = this.parentNode.id,
        ) {
            otherGroup.childNodes.forEach { childNode ->
                val newId = childNode.id.replace(otherGroup.parentNode.id, instanceId)
                val newFolderId = childNode.folderId!!.replace(otherGroup.parentNode.id, instanceId)
                val overrideNode = find { it.id == newId }

                if (overrideNode == null) {
                    add(
                        childNode.copy(
                            id = newId,
                            folderId = newFolderId,
                            config = TagConfig(
                                name = childNode.config.name,
                                tagType = childNode.config.tagType,
                            ),
                            inferredNode = true,
                        ).apply {
                            statistics.copyToNewNode(childNode.statistics)
                        },
                    )
                } else {
                    remove(overrideNode)
                    add(
                        overrideNode.copy(
                            config =
                            overrideNode.config.copy(
                                name = childNode.config.name,
                                tagType = childNode.config.tagType,
                            ),
                        ).apply {
                            statistics.copyToOverrideNode(childNode.statistics)
                        },
                    )
                }
            }
        }

        fun getProvidersFromDB(connection: Connection): List<TagProviderRecord> {
            val configStatement = connection.prepareStatement(TAG_CONFIG_TABLE_QUERY)

            return connection.executeQuery(TAG_PROVIDER_TABLE_QUERY)
                .toList { rs ->
                    runCatching {
                        TagProviderRecord(
                            id = rs["id"],
                            name = rs["name"],
                            uuid = rs["providerid"],
                            description = rs["description"],
                            enabled = rs["enabled"],
                            typeId = rs["typeid"],
                            statement = configStatement,
                        )
                    }.getOrNull()
                }.filterNotNull()
        }
    }
}
