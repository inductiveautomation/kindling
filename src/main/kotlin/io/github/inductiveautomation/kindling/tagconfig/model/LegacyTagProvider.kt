package io.github.inductiveautomation.kindling.tagconfig.model

import io.github.inductiveautomation.kindling.utils.executeQuery
import io.github.inductiveautomation.kindling.utils.get
import io.github.inductiveautomation.kindling.utils.toList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.intellij.lang.annotations.Language
import java.sql.Connection
import java.sql.PreparedStatement
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Primary constructor parameters represent an entry in the IDB for a tag provider.
 * The class body is all the parsing logic to parse the tag config table and build the node hierarchy for this provider.
 */
@Suppress("MemberVisibilityCanBePrivate")
@OptIn(ExperimentalUuidApi::class)
class LegacyTagProvider(
    val id: Int,
    name: String,
    uuid: Uuid,
    description: String?,
    enabled: Boolean,
    val typeId: String,
    val statement: PreparedStatement,
) : AbstractTagProvider(name, uuid, description, enabled) {
    override val providerStatistics = ProviderStatistics()

    override val typesNode = IdbNode(
        id = "_types_",
        providerId = id,
        folderId = null,
        config = TagConfig(
            name = "_types_",
            tagType = "Folder",
            tags = mutableListOf(),
        ),
        rank = 1,
        isMeta = true,
        idbName = "_types_",
    )

    // Lazy Job which can be started manually with join(), or automatically when getProviderNode() is called.
    override val loadProvider: Job = CoroutineScope(Dispatchers.Default).launch(start = CoroutineStart.LAZY) {
        providerNode = IdbNode(
            id = uuid.toString(),
            providerId = id,
            folderId = null,
            config = TagConfig(
                name = "",
                tagType = "Provider",
                tags = mutableListOf(typesNode),
            ),
            rank = 0,
            idbName = "",
        )

        // Main Tag Config resolution loop.
        for ((_, nodeGroup) in nodeGroups) {
            val parentNode = nodeGroup.first()
            // Resolve and process tags
            if (parentNode.statistics.isUdtDefinition || parentNode.statistics.isUdtInstance) {
                if (!parentNode.resolved) {
                    parentNode.resolveInheritance()
                }
            }
            nodeGroup.resolveHierarchy()
            when (val folderId = parentNode.folderId) {
                "_types_" -> typesNode.addChildTag(parentNode)
                null -> providerNode.addChildTag(parentNode)
                else -> {
                    val folderGroup = nodeGroups[folderId]
                    folderGroup?.first()?.addChildTag(parentNode)
                        ?: providerStatistics.orphanedTags.value.add(parentNode)
                }
            }

            // Gather Statistics
            if (parentNode.statistics.isUdtDefinition) {
                providerStatistics.processNodeForStatistics(parentNode)
            } else {
                nodeGroup.forEach {
                    providerStatistics.processNodeForStatistics(it)
                }
            }
        }

        if (orphanedParentNode.config.tags.isNotEmpty()) {
            providerNode.addChildTag(orphanedParentNode)
        }

        // Make the missing definitions list distinct
        val seen = HashSet<String>()
        val iterator = providerStatistics.missingUdtDefinition.value.iterator()
        while (iterator.hasNext()) {
            val element = iterator.next()
            if (!seen.add(element)) {
                iterator.remove()
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
                runCatching {
                    IdbNode(
                        id = rs["id"],
                        providerId = rs["providerId"],
                        folderId = rs["folderId"],
                        config = Json.decodeFromString(rs["cfg"]),
                        rank = rs["rank"],
                        idbName = rs["name"],
                    )
                }.getOrNull()
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
    val nodeGroups: Map<String, MutableList<IdbNode>> by lazy {
        rawNodeData.groupBy { node ->
            node.id.take(36)
        }.mapValues { (_, nodes) ->
            nodes.toMutableList()
        }.toList().sortedBy { (_, nodes) ->
            nodes.first().rank
        }.toMap()
    }

    val udtDefinitions: Map<String, IdbNode> by lazy {
        rawNodeData.filter { it.statistics.isUdtDefinition }
            .associateBy { it.getFullUdtDefinitionPath() }
    }

    val orphanedParentNode by lazy {
        IdbNode(
            id = "orphaned_nodes",
            providerId = id,
            folderId = null,
            config = TagConfig(
                name = "Orphaned Nodes by Missing Parent",
                tags = run {
                    val orphanedTags = mutableMapOf<String, IdbNode>()
                    providerStatistics.orphanedTags.value.forEach { orphanedNode ->
                        orphanedNode as IdbNode
                        val falseParent = orphanedTags.getOrPut(orphanedNode.folderId!!) {
                            IdbNode(
                                id = orphanedNode.folderId,
                                config = TagConfig(
                                    name = orphanedNode.folderId,
                                ),
                                folderId = "orphaned_nodes",
                                rank = 2,
                                providerId = id,
                                idbName = orphanedNode.folderId,
                            )
                        }
                        falseParent.addChildTag(orphanedNode)
                    }
                    orphanedTags.values.toMutableList()
                },
            ),
            rank = 1,
            isMeta = true,
            idbName = "Orphaned Nodes by Missing Parent",
        )
    }

    private fun IdbNode.getFullUdtDefinitionPath(): String = if (folderId == "_types_") {
        name
    } else {
        val parentName = nodeGroups[folderId!!]?.first()?.getFullUdtDefinitionPath()
        "$parentName/$name"
    }

    override fun Node.resolveInheritance() {
        if (statistics.isUdtDefinition) {
            resolveNestedUdtInstances()
        }

        if (config.typeId.isNullOrEmpty()) {
            resolved = true
            return
        }

        copyChildrenFrom(this)

        resolved = true
    }

    override fun Node.resolveNestedUdtInstances() {
        check(this is IdbNode) { "Not an IDB Node!" }
        require(statistics.isUdtDefinition)

        val group = nodeGroups[id]!!

        val childInstances = group.drop(1).filter { it.statistics.isUdtInstance }

        for (childInstance in childInstances) {
            copyChildrenFrom(childInstance)
        }
    }

    override val Node.parentType: IdbNode?
        get() {
            val typeId = config.typeId ?: return null
            // Some typeIds start with _types_, or even [ProviderName]_types_. It's unclear why.
            return udtDefinitions[typeId.substringAfter("_types_/")]
        }

    override fun Node.copyChildrenFrom(other: Node) {
        check(this is IdbNode) { "Not an IDB Node!" }
        check(other is IdbNode) { "Not an IDB Node!" }

        val thisNodeGroup = checkNotNull(nodeGroups[id]) { "This should never happen" }

        val otherDefinition = other.parentType ?: run {
            other.config.typeId?.let {
                providerStatistics.missingUdtDefinition.value.add(it)
            }
            return
        }
        val inheritedNodeGroup = checkNotNull(nodeGroups[otherDefinition.id]) { "This should never happen" }

        if (!otherDefinition.resolved) {
            otherDefinition.resolveInheritance()
        }

        check(other.statistics.isUdtInstance || other.statistics.isUdtDefinition) {
            "Not a UDT Structure!"
        }

        inheritedNodeGroup.drop(1).forEach { childNode ->
            val newId = childNode.id.replace(otherDefinition.id, other.id)
            val newFolderId = childNode.folderId!!.replace(otherDefinition.id, other.id)
            val overrideNode = thisNodeGroup.find { it.id == newId }

            if (overrideNode == null) {
                thisNodeGroup.add(
                    IdbNode(
                        id = newId,
                        folderId = newFolderId,
                        providerId = childNode.providerId,
                        rank = childNode.rank,
                        config = TagConfig(
                            name = childNode.name,
                            tagType = childNode.config.tagType,
                        ),
                        inferredFrom = childNode,
                        resolved = true,
                        idbName = childNode.name,
                    ).apply {
                        childNode.statistics.copyToNewNode(statistics)
                    },
                )
            } else {
                thisNodeGroup.remove(overrideNode)
                thisNodeGroup.add(
                    IdbNode(
                        id = overrideNode.id,
                        folderId = overrideNode.folderId,
                        providerId = overrideNode.providerId,
                        rank = overrideNode.rank,
                        config = overrideNode.config.copy(
                            name = childNode.name,
                            tagType = childNode.config.tagType,
                        ),
                        resolved = true,
                        idbName = childNode.name,
                    ).apply {
                        childNode.statistics.copyToOverrideNode(statistics)
                    },
                )
            }
        }
    }

    // Build the node hierarchy by adding child tags throughout the group
    private fun MutableList<IdbNode>.resolveHierarchy() {
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

        fun loadProviders(connection: Connection): List<LegacyTagProvider> {
            val configStatement = connection.prepareStatement(TAG_CONFIG_TABLE_QUERY)

            return connection.executeQuery(TAG_PROVIDER_TABLE_QUERY)
                .toList { rs ->
                    runCatching {
                        LegacyTagProvider(
                            id = rs["id"],
                            name = rs["name"],
                            uuid = Uuid.parse(rs.get<String>("providerid")),
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
