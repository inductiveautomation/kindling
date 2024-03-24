package io.github.inductiveautomation.kindling.utils

import io.github.inductiveautomation.kindling.cache.CacheViewer
import io.github.inductiveautomation.kindling.core.Theme
import io.github.inductiveautomation.kindling.core.Tool
import io.github.inductiveautomation.kindling.idb.IdbViewer
import io.github.inductiveautomation.kindling.thread.MultiThreadViewer
import io.github.inductiveautomation.kindling.zip.ZipViewer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.nio.charset.Charset
import java.nio.file.Path
import java.time.ZoneId
import kotlin.io.path.Path
import kotlin.io.path.pathString

data object PathSerializer : KSerializer<Path> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor(Path::class.java.name, PrimitiveKind.STRING)

    val Path.serializedForm: String get() = pathString

    fun fromString(string: String): Path = Path(string)

    override fun deserialize(decoder: Decoder): Path = fromString(decoder.decodeString())

    override fun serialize(encoder: Encoder, value: Path) = encoder.encodeString(value.serializedForm)
}

data object ThemeSerializer : KSerializer<Theme> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor(Theme::class.java.name, PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Theme) = encoder.encodeString(value.name)
    override fun deserialize(decoder: Decoder): Theme = Theme.themes.getValue(decoder.decodeString())
}

data object ToolSerializer : KSerializer<Tool> {
    private val bySerialKey: Map<String, Tool> by lazy {
        Tool.tools.associateBy(Tool::serialKey)
    }

    // we used to store keys by their 'title' instead of their serial key
    // so to be nice on _de_serialization, we'll map those old values over
    private val aliases = mapOf(
        "Thread Viewer" to MultiThreadViewer.serialKey,
        "Ignition Archive" to ZipViewer.serialKey,
        "Cache Dump" to CacheViewer.serialKey,
        "Idb File" to IdbViewer.serialKey,
    )

    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor(Tool::class.java.name, PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): Tool {
        val storedKey = decoder.decodeString()
        val actualKey = aliases[storedKey] ?: storedKey
        return bySerialKey.getValue(actualKey)
    }

    override fun serialize(encoder: Encoder, value: Tool) = encoder.encodeString(value.serialKey)
}

data object ZoneIdSerializer : KSerializer<ZoneId> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor(ZoneId::class.java.name, PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): ZoneId = ZoneId.of(decoder.decodeString())

    override fun serialize(encoder: Encoder, value: ZoneId) = encoder.encodeString(value.id)
}

data object CharsetSerializer : KSerializer<Charset> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor(Charset::class.java.name, PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): Charset = Charset.forName(decoder.decodeString())

    override fun serialize(encoder: Encoder, value: Charset) = encoder.encodeString(value.name())
}
