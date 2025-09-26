package io.github.inductiveautomation.kindling.tagconfig.model

import com.jidesoft.comparator.AlphanumComparator
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.util.Collections
import java.util.Enumeration
import javax.swing.tree.TreeNode
import kotlin.collections.forEach

@Serializable(with = NodeDelegateSerializer::class)
open class Node(
    val config: TagConfig,
    val isMeta: Boolean = false,
    val inferredFrom: Node? = null,
    var resolved: Boolean = false,
) : TreeNode {
    val inferred: Boolean
        get() = inferredFrom != null

    open val name: String
        get() = config.name!!

    val statistics = NodeStatistics(this)
    private var parent: Node? = null

    init {
        for (child in children()) {
            child.parent = this
        }
    }

    fun addChildTag(node: Node) {
        config.tags.add(node)
        node.parent = this
        config.tags.sortWith(nodeChildComparator)
    }

    fun addChildTags(children: Collection<Node>) {
        children.forEach {
            config.tags.add(it)
            it.parent = this
        }
        config.tags.sortWith(nodeChildComparator)
    }

    operator fun div(childName: String): Node? = config.tags.find { it.name == childName }

    override fun getChildAt(childIndex: Int) = config.tags[childIndex]
    override fun getChildCount() = config.tags.size
    override fun getParent(): Node? = parent
    override fun getIndex(node: TreeNode?) = config.tags.indexOf(node)
    override fun getAllowsChildren() = !statistics.isAtomicTag
    override fun isLeaf() = config.tags.isEmpty()
    override fun children(): Enumeration<Node> = Collections.enumeration(config.tags)

    companion object {
        private val nodeChildComparator = compareByDescending<Node> { it.isMeta }
            .thenBy { it.config.tagType }
            .thenBy(AlphanumComparator(false)) { it.name }
    }
}

class IdbNode(
    val id: String,
    val providerId: Int,
    val folderId: String?,
    val rank: Int,
    val idbName: String?,
    config: TagConfig,
    // Improves parsing efficiency a bit.
    resolved: Boolean = false,
    //  "Inferred" means that there is no config entry for this node, but it will exist at runtime
    inferredFrom: Node? = null,
    // Used for sorting. A "meta" node is either the _types_ folder or the orphaned tags folder
    isMeta: Boolean = false,
) : Node(config, isMeta, inferredFrom, resolved) {
    override val name: String = idbName ?: config.name ?: "NULL"
}

/**
 * The JSON serialization of a Node is simply its config. The Node class represents an entry in the IDB.
 * Here, we delegate the serialization of a node to just use the TagConfig serializer.
 *
 * Serializing a node will recursively serialize all child tags, creating the complete json export.
 */
object NodeDelegateSerializer : KSerializer<Node> {
    @OptIn(ExperimentalSerializationApi::class)
    override val descriptor: SerialDescriptor = TagConfigSerializer.descriptor

    override fun deserialize(decoder: Decoder): Node {
        val config = decoder.decodeSerializableValue(TagConfigSerializer)

        return Node(config)
    }

    override fun serialize(encoder: Encoder, value: Node) {
        encoder.encodeSerializableValue(TagConfigSerializer, value.config)
    }
}
