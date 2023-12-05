package io.github.inductiveautomation.kindling.idb.tagconfig.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
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
    var resolved: Boolean = false,
    val inferredNode: Boolean = false,
) {
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
                    tags = mutableListOf(),
                ),
                rank = 1,
                name = "_types_",
            )
    }
}

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

typealias NodeGroup = MutableList<Node>
