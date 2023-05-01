package io.github.inductiveautomation.kindling.thread.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object NoneAsNullStringSerializer : KSerializer<String?> {
    override val descriptor: SerialDescriptor = String.serializer().descriptor

    override fun deserialize(decoder: Decoder): String? {
        return decoder.decodeString().takeIf { it != "None" }
    }

    override fun serialize(encoder: Encoder, value: String?) {
        when (value) {
            "None" -> Unit
            null -> Unit
            else -> encoder.encodeString(value)
        }
    }
}
