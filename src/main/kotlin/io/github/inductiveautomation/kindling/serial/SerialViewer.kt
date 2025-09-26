package io.github.inductiveautomation.kindling.serial

import com.formdev.flatlaf.extras.FlatSVGIcon
import com.inductiveautomation.ignition.common.xmlserialization.deserialization.XMLDeserializer
import com.inductiveautomation.ignition.common.xmlserialization.serialization.BinaryWriter
import deser.SerializationDumper
import io.github.inductiveautomation.kindling.core.Tool
import io.github.inductiveautomation.kindling.core.ToolPanel
import io.github.inductiveautomation.kindling.utils.FileFilter
import io.github.inductiveautomation.kindling.utils.FlatScrollPane
import io.github.inductiveautomation.kindling.utils.HorizontalSplitPane
import io.github.inductiveautomation.kindling.utils.toHumanReadableBinary
import org.apache.commons.lang3.builder.ReflectionToStringBuilder
import org.apache.commons.lang3.builder.ToStringStyle
import java.awt.Font
import java.nio.file.Path
import java.util.zip.GZIPInputStream
import javax.swing.Icon
import javax.swing.JLabel
import javax.swing.JTextArea
import kotlin.io.path.inputStream
import kotlin.io.path.name
import kotlin.io.path.readBytes

class SerialViewPanel(private val path: Path) : ToolPanel() {
    private val serialDump = JTextArea().apply {
        font = Font(Font.MONOSPACED, Font.PLAIN, 12)
        isEditable = false
    }

    private val rawBytes = JTextArea().apply {
        font = Font(Font.MONOSPACED, Font.PLAIN, 12)
        isEditable = false
    }

    init {
        var data = path.readBytes()
        if (data.isGzip()) {
           data = data.inputStream().let(::GZIPInputStream).use(GZIPInputStream::readBytes)
        }

        serialDump.text = if (data.isIaXmlSerialized()) {
            deserializeBinaryXml(data)
        } else {
            deserializeJavaSerialized(data)
        }

        rawBytes.text = path.inputStream().toHumanReadableBinary()
        name = path.name

        add(
            JLabel("Java Serialized Data: ${data.size} bytes").apply {
                putClientProperty("FlatLaf.styleClass", "h3.regular")
            },
            "wrap",
        )
        add(
            HorizontalSplitPane(
                FlatScrollPane(serialDump),
                FlatScrollPane(rawBytes),
                resizeWeight = 0.8,
            ) {
            },
            "push, grow",
        )
    }

    private fun deserializeJavaSerialized(data: ByteArray): String = SerializationDumper(data).parseStream()

    private fun deserializeBinaryXml(data: ByteArray): String {
        val context = XMLDeserializer().also {
            it.initDefaults()
        }.deserializeBinary(data)

        return buildString {
            appendLine("XML Deserialized Data:")
            if (context.warnings.isNotEmpty()) {
                context.warnings.joinTo(this, prefix = "Warnings: \n", separator = "\n", postfix = "\n") {
                    it.message.orEmpty()
                }
            }
            context.rootObjects.joinTo(this, prefix = "Root Objects: \n", separator = "\n", postfix = "\n") {
                ReflectionToStringBuilder.toString(it, ToStringStyle.MULTI_LINE_STYLE)
            }
        }
    }

    private fun ByteArray.isGzip(): Boolean = this.size > 2
            && this[0] == 0x1f.toByte()
            && this[1] == 0x8b.toByte()

    private fun ByteArray.isIaXmlSerialized(): Boolean {
        if (this.size < 16) return false
        for ((i, byte) in SerialViewer.xmlDeserializerMagic.withIndex()) {
            if (byte != this[i]) return false
        }
        return true
    }

    override val icon: Icon = SerialViewer.icon

    override fun getToolTipText(): String {
        return path.toString()
    }
}

data object SerialViewer : Tool {
    override val title: String = "Java Serialization Viewer"
    override val description: String = "Serial files"
    override val icon: FlatSVGIcon = FlatSVGIcon("icons/bx-code.svg")

    override fun open(path: Path): ToolPanel = SerialViewPanel(path)

    override val filter: FileFilter = FileFilter("Java Serialized File", "bin")
    override val serialKey: String = "serial-viewer"

    override val isAdvanced: Boolean = true

    /**
     * Ref [BinaryWriter.MAGIC_NUMBER], a random UUID used as magic number
     */
    internal val xmlDeserializerMagic = byteArrayOf(
        0x98.toByte(),
        0x29.toByte(),
        0x8F.toByte(),
        0xAA.toByte(),
        0x43.toByte(),
        0xF7.toByte(),
        0x4A.toByte(),
        0x4F.toByte(),
        0xB2.toByte(),
        0x8D.toByte(),
        0xCA.toByte(),
        0x3C.toByte(),
        0x48.toByte(),
        0x96.toByte(),
        0xAF.toByte(),
        0xC9.toByte(),
    )
}
