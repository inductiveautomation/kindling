package io.github.inductiveautomation.kindling.idb.tagconfig.model

import io.github.inductiveautomation.kindling.idb.tagconfig.model.NodeGroup.Companion.toNodeGroup
import io.github.inductiveautomation.kindling.utils.AbstractTreeNode
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = NodeDelegateSerializer::class)
data class Node(
    val id: String,
    val providerId: Int,
    val folderId: String?,
    @Serializable(with = TagConfigSerializer::class)
    val config: TagConfig,
    val rank: Int,
    val name: String?,
    var resolved: Boolean = false, // Improves parsing efficiency a bit.
    val inferredNode: Boolean = false, //  "Inferred" means that there is no IDB entry for this node, but it will exist at runtime
) : AbstractTreeNode() {
    val statistics = NodeStatistics(this)

    companion object {
        fun typesNode(providerId: Int): Node =
            Node(
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
            )
    }
}

/*
The JSON serialization of a Node is simply its config. The Node class represents an entry in the IDB.
Here, we delegate the serialization of a node to just use the TagConfig serializer.
 */
object NodeDelegateSerializer : KSerializer<Node> {
    @OptIn(ExperimentalSerializationApi::class)
    override val descriptor: SerialDescriptor = SerialDescriptor("ExportNode", TagConfig.serializer().descriptor)

    override fun deserialize(decoder: Decoder): Node {
        throw UnsupportedOperationException("Deserialization not supported.")
    }

    override fun serialize(encoder: Encoder, value: Node) {
        encoder.encodeSerializableValue(TagConfigSerializer, value.config)
    }
}

@Serializable(with=NodeGroupSerializer::class)
class NodeGroup private constructor(
    internal val list: MutableList<Node>,
) : MutableList<Node> by list {
    val parentNode: Node by ::first

    val childNodes: MutableList<Node>
        get() = subList(1, size)

    // Delegation doesn't work here?
    var isResolved: Boolean
        get() = first.resolved
        set(value) {
            first.resolved = value
        }

    companion object {
        operator fun invoke(vararg nodes: Node): NodeGroup = NodeGroup(nodes.toMutableList())
        fun Collection<Node>.toNodeGroup(): NodeGroup = NodeGroup(toMutableList())
    }
}

object NodeGroupSerializer : KSerializer<NodeGroup> {
    private val delegateSerializer = ListSerializer(NodeDelegateSerializer)

    override val descriptor: SerialDescriptor = delegateSerializer.descriptor

    override fun serialize(encoder: Encoder, value: NodeGroup) {
        encoder.encodeSerializableValue(delegateSerializer, value.list)
    }

    override fun deserialize(decoder: Decoder): NodeGroup {
        return decoder.decodeSerializableValue(delegateSerializer).toNodeGroup()
    }
}
