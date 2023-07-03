package io.github.inductiveautomation.kindling.utils

import io.github.inductiveautomation.kindling.core.Theme
import io.github.inductiveautomation.kindling.core.Tool
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.nio.file.Path
import java.time.ZoneId
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

object ToolSerializer : KSerializer<Tool> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor(Tool::class.java.name, PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): Tool = Tool.byTitle.getValue(decoder.decodeString())

    override fun serialize(encoder: Encoder, value: Tool) = encoder.encodeString(value.title)
}

object ZoneIdSerializer : KSerializer<ZoneId> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor(ZoneId::class.java.name, PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): ZoneId = ZoneId.of(decoder.decodeString())

    override fun serialize(encoder: Encoder, value: ZoneId) = encoder.encodeString(value.id)
}
