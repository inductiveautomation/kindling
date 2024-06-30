package io.github.inductiveautomation.kindling.idb.tagconfig.model

import com.jidesoft.comparator.AlphanumComparator
import io.github.inductiveautomation.kindling.idb.tagconfig.model.NodeGroup.Companion.toNodeGroup
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.util.Collections
import java.util.Enumeration
import javax.swing.tree.TreeNode

@Serializable(with = NodeDelegateSerializer::class)
data class Node(
    val id: String,
    val providerId: Int,
    val folderId: String?,
    @Serializable(with = TagConfigSerializer::class)
    val config: TagConfig,
    val rank: Int,
    val name: String?,
    // Improves parsing efficiency a bit.
    var resolved: Boolean = false,
    //  "Inferred" means that there is no IDB entry for this node, but it will exist at runtime
    val inferredNode: Boolean = false,
    // Used for sorting. A "meta" node is either the _types_ folder or the orphaned tags folder
    val isMeta: Boolean = false,
) : TreeNode {
    val statistics = NodeStatistics(this)
    private var parent: Node? = null

    // One of the names, either the IDB column or the config, should be non-null.
    val actualName: String
        get() = name ?: config.name ?: "null"

    fun addChildTag(node: Node) {
        config.tags.add(node)
        node.parent = this
        config.tags.sortWith(nodeChildComparator)
    }

    override fun getChildAt(childIndex: Int) = config.tags[childIndex]
    override fun getChildCount() = config.tags.size
    override fun getParent(): TreeNode? = parent
    override fun getIndex(node: TreeNode?) = config.tags.indexOf(node)
    override fun getAllowsChildren() = true
    override fun isLeaf() = config.tags.size == 0
    override fun children(): Enumeration<out TreeNode> = Collections.enumeration(config.tags)

    companion object {
        private val nodeChildComparator = compareByDescending<Node> { it.isMeta }
            .thenBy { it.config.tagType }
            .thenBy(AlphanumComparator(false)) { it.actualName }
    }
}

/**
 * The JSON serialization of a Node is simply its config. The Node class represents an entry in the IDB.
 * Here, we delegate the serialization of a node to just use the TagConfig serializer.
 *
 * Serializing a node will recursively serialize all child tags, creating the complete json export.
 */
object NodeDelegateSerializer : KSerializer<Node> {
    @OptIn(ExperimentalSerializationApi::class)
    override val descriptor: SerialDescriptor = SerialDescriptor("ExportNode", TagConfig.serializer().descriptor)

    override fun deserialize(decoder: Decoder): Node = throw UnsupportedOperationException("Deserialization not supported.")

    override fun serialize(encoder: Encoder, value: Node) {
        encoder.encodeSerializableValue(TagConfigSerializer, value.config)
    }
}

@Serializable(with = NodeGroupSerializer::class)
class NodeGroup private constructor(
    internal val list: MutableList<Node>,
) : MutableList<Node> by list {
    val parentNode: Node
        get() = first()

    val childNodes: MutableList<Node>
        get() = subList(1, size)

    // Delegation doesn't work here?
    var isResolved: Boolean
        get() = first().resolved
        set(value) {
            first().resolved = value
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

    override fun deserialize(decoder: Decoder): NodeGroup = decoder.decodeSerializableValue(delegateSerializer).toNodeGroup()
}
