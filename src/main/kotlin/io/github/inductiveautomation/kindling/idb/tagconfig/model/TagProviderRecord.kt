package io.github.inductiveautomation.kindling.idb.tagconfig.model

import io.github.inductiveautomation.kindling.idb.tagconfig.TagConfigView
import io.github.inductiveautomation.kindling.utils.toList
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.encodeToStream
import java.nio.file.Path
import java.sql.Connection
import kotlin.io.path.outputStream

@Suppress("MemberVisibilityCanBePrivate")
data class TagProviderRecord(
    val id: Int,
    val name: String,
    val uuid: String,
    val description: String?,
    val enabled: Boolean,
    val typeId: String,
    val allowBackFill: Boolean,
    val dbConnection: Connection,
) {
    val providerStatistics = ProviderStatistics()

    val rawNodeData by lazy {
        dbConnection.prepareStatement(TAG_CONFIG_TABLE_QUERY).apply { setInt(1, id) }.executeQuery()
            .toList { rs ->
                try {
                    Node(
                        id = rs.getString(1),
                        providerId = rs.getInt(2),
                        folderId = rs.getString(3),
                        config = TagConfigView.TagExportJson.decodeFromString(TagConfigSerializer, rs.getString(4)),
                        rank = rs.getInt(5),
                        name = rs.getString(6),
                    )
                } catch (e: NullPointerException) {
                    // println("Found null record. Id: ${rs.getString(1)}")
                    // Null records will be ignored.
                    null
                }
            }.filterNotNull()
    }

    val nodeGroups: Map<String, NodeGroup> by lazy {
        rawNodeData.groupBy { node ->
            node.id.substring(0, 36)
        }.mapValues { (_, nodes) ->
            nodes.sortedBy { it.id }.toMutableList()
        }.toList().sortedBy { (_, nodes) ->
            nodes.first().rank
        }.toMap()
    }

    val udtDefinitions: Map<String, Node> by lazy {
        rawNodeData.filter { it.statistics.isUdtDefinition }.associateBy {
            it.getFullUdtDefinitionPath(nodeGroups)
        }
    }

    val typesNode = Node.typesNode(id)

    val providerNode =
        lazy {
            providerNode(typesNode).apply {
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
                            "_types_" -> typesNode.config.tags.add(parentNode)
                            null -> config.tags.add(parentNode)
                            else -> {
                                val folderGroup = nodeGroups[folderId]
                                folderGroup?.parentNode?.config?.tags?.add(parentNode) ?: providerStatistics.orphanedTags.value.add(
                                    parentNode,
                                )
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
            }
        }

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
                        falseParent.config.tags.add(orphanedNode)
                    }
                    orphanedTags.values.toMutableList()
                },
            ),
            rank = 1,
            name = "Orphaned Nodes by Missing Parent",
        )
    }

    @OptIn(ExperimentalSerializationApi::class)
    fun exportToJson(selectedFilePath: Path) {
        selectedFilePath.outputStream().use {
            TagConfigView.TagExportJson.encodeToStream(providerNode.value, it)
        }
    }

    fun initProviderNode() {
        providerNode.value
    }

    // Traversal Helper Functions:
    private fun Node.getParentType(udtDefinitions: Map<String, Node>): Node? {
        require((statistics.isUdtDefinition || statistics.isUdtInstance) && config.typeId != null) {
            "Not a top level UDT Instance or type! $this"
        }
        return udtDefinitions[config.typeId.lowercase()]
    }

    private fun Node.getFullUdtDefinitionPath(nodeGroups: Map<String, NodeGroup>): String {
        val lowercaseName = config.name!!.lowercase()
        return if (folderId == "_types_") {
            lowercaseName
        } else {
            val parentName = nodeGroups[folderId!!]?.parentNode?.getFullUdtDefinitionPath(nodeGroups) ?: "<No Name Found?>"
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
            val childDefinition = childInstance.getParentType(udtDefinitions) ?: continue
            val childDefinitionGroup =
                nodeGroups[childDefinition.id] ?: throw IllegalStateException(
                    "This should never happen. Please report this issue to the maintainers of Kindling.",
                )

            // nodeGroups being ordered by rank will make this check not happen very often
            if (!childDefinitionGroup.isResolved) childDefinitionGroup.resolveInheritance(nodeGroups, udtDefinitions)

            copyChildrenFrom(childDefinitionGroup, instanceId = childInstance.id)
        }
    }

    private fun NodeGroup.resolveInheritance(
        nodeGroups: Map<String, NodeGroup>,
        udtDefinitions: Map<String, Node>,
    ) {
        if (parentNode.statistics.isUdtDefinition) resolveNestedChildInstances(nodeGroups, udtDefinitions)

        if (parentNode.config.typeId.isNullOrEmpty()) {
            isResolved = true
            return
        }

        val inheritedParentNode =
            parentNode.getParentType(udtDefinitions) ?: run {
                isResolved = true
                println("Missing UDT Definition: ${parentNode.config.typeId}")
                return
            }
        val inheritedNodeGroup =
            nodeGroups[inheritedParentNode.id] ?: throw IllegalStateException("This should never happen")

        if (!inheritedNodeGroup.isResolved) inheritedNodeGroup.resolveInheritance(nodeGroups, udtDefinitions)

        copyChildrenFrom(inheritedNodeGroup)

        isResolved = true
    }

    private fun NodeGroup.resolveHierarchy() {
        if (size == 1) return

        for (i in 1..<size) {
            val childNode = get(i)
            find { node -> node.id == childNode.folderId }?.config?.tags?.add(childNode)
                ?: providerStatistics.orphanedTags.value.add(childNode)
        }
    }

    companion object {
        private const val TAG_PROVIDER_TABLE_QUERY = "SELECT * FROM TAGPROVIDERSETTINGS ORDER BY NAME"
        private const val TAG_CONFIG_TABLE_QUERY = "SELECT * FROM TAGCONFIG WHERE PROVIDERID = ? ORDER BY ID"

        val NodeGroup.parentNode: Node
            get() = first()

        val NodeGroup.childNodes: MutableList<Node>
            get() = subList(1, size)

        var NodeGroup.isResolved: Boolean
            get() = first().resolved
            set(value) {
                first().resolved = value
            }

        fun TagProviderRecord.providerNode(typesNode: Node? = null): Node =
            Node(
                id = this.uuid,
                providerId = this.id,
                folderId = null,
                config = TagConfig(
                    name = "",
                    tagType = "Provider",
                    tags = typesNode?.let { mutableListOf(it) } ?: mutableListOf(),
                ),
                rank = 0,
                name = this.name,
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
            return connection.prepareStatement(TAG_PROVIDER_TABLE_QUERY).executeQuery().toList { rs ->
                runCatching {
                    TagProviderRecord(
                        id = rs.getInt(1),
                        name = rs.getString(2),
                        uuid = rs.getString(3),
                        description = rs.getString(4),
                        enabled = rs.getBoolean(5),
                        typeId = rs.getString(6),
                        allowBackFill = rs.getBoolean(7),
                        dbConnection = connection,
                    )
                }.getOrNull()
            }.filterNotNull()
        }
    }
}
