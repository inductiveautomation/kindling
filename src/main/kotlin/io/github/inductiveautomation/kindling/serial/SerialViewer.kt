package io.github.inductiveautomation.kindling.serial

import com.formdev.flatlaf.extras.FlatSVGIcon
import deser.SerializationDumper
import io.github.inductiveautomation.kindling.core.Tool
import io.github.inductiveautomation.kindling.core.ToolPanel
import io.github.inductiveautomation.kindling.utils.FileFilter
import io.github.inductiveautomation.kindling.utils.FlatScrollPane
import io.github.inductiveautomation.kindling.utils.HorizontalSplitPane
import io.github.inductiveautomation.kindling.utils.toHumanReadableBinary
import java.awt.Font
import java.nio.file.Path
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
        val data = path.readBytes()
        serialDump.text = SerializationDumper(data).parseStream()
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

    override val icon: Icon = SerialViewer.icon

    override fun getToolTipText(): String? = path.toString()
}

data object SerialViewer : Tool {
    override val title: String = "Java Serialization Viewer"
    override val description: String = "Serial files"
    override val icon: FlatSVGIcon = FlatSVGIcon("icons/bx-code.svg")

    override fun open(path: Path): ToolPanel = SerialViewPanel(path)

    override val extensions: Array<String> = arrayOf("bin")
    override val filter: FileFilter = FileFilter("Java Serialized File", *extensions)
    override val serialKey: String = "serial-viewer"

    override val isAdvanced: Boolean = true
}
