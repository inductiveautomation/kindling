package io.github.inductiveautomation.kindling.utils

import io.github.inductiveautomation.kindling.core.Theme
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.pathString

object PathSerializer : KSerializer<Path> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor(Path::class.java.name, PrimitiveKind.STRING)

    val Path.serializedForm: String get() = pathString

    fun fromString(string: String): Path = Path(string)

    override fun deserialize(decoder: Decoder): Path = fromString(decoder.decodeString())

    override fun serialize(encoder: Encoder, value: Path) = encoder.encodeString(value.serializedForm)
}

object ThemeSerializer : KSerializer<Theme> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor(Theme::class.java.name, PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Theme) = encoder.encodeString(value.name)
    override fun deserialize(decoder: Decoder): Theme = Theme.themes.getValue(decoder.decodeString())
}
