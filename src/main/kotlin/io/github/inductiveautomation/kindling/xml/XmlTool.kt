package io.github.inductiveautomation.kindling.xml

import com.formdev.flatlaf.extras.FlatSVGIcon
import com.formdev.flatlaf.extras.components.FlatTabbedPane
import io.github.inductiveautomation.kindling.core.ClipboardTool
import io.github.inductiveautomation.kindling.core.Kindling.Preferences.General.DefaultEncoding
import io.github.inductiveautomation.kindling.core.ToolPanel
import io.github.inductiveautomation.kindling.utils.FileFilter
import io.github.inductiveautomation.kindling.utils.TabStrip
import io.github.inductiveautomation.kindling.utils.getLogger
import io.github.inductiveautomation.kindling.xml.logback.LogbackEditor
import io.github.inductiveautomation.kindling.xml.quarantine.QuarantineViewer
import java.nio.file.Path
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import javax.swing.Icon
import javax.swing.JPanel
import kotlin.io.path.name
import kotlin.io.path.readText

internal enum class XmlTools {
    XmlViewer {
        override val displayName: String = "Raw"
        override fun supports(topLevelElement: String): Boolean = true
        override fun open(file: List<String>): JPanel = XmlViewer(file)
    },
    LogbackEditor {
        override val displayName: String = "Logback Editor"
        override fun supports(topLevelElement: String): Boolean = topLevelElement.contains("</configuration>", ignoreCase = true)
        override fun open(file: List<String>): JPanel = LogbackEditor(file)
    },
    QuarantineViewer {
        override val displayName: String = "Encoded Quarantine Data"
        override fun supports(topLevelElement: String): Boolean = topLevelElement.contains("</cachedata>")

        /**
         * Returns null if there were no base64 elements in the document to decode.
         */
        override fun open(file: List<String>): JPanel? = QuarantineViewer(file)
    },
    ;

    abstract val displayName: String

    /**
     * Check if this tool supports the supplied top-level element (determined by the last line in the input file).
     */
    abstract fun supports(topLevelElement: String): Boolean

    /**
     * @return a new tool panel, or null if the tool is not supported for the given path
     */
    abstract fun open(file: List<String>): JPanel?
}

internal class XmlToolPanel(name: String, tooltip: String, content: List<String>) : ToolPanel() {
    private val tabs = TabStrip().apply {
        trailingComponent = null
        isTabsClosable = false
        tabType = FlatTabbedPane.TabType.underlined
        tabHeight = 16
        isHideTabAreaWithOneTab = true
    }

    init {
        this.name = name
        toolTipText = tooltip

        val topLevelElement = content.last(String::isNotEmpty)

        XmlTools.entries
            .filter { it.supports(topLevelElement) }
            .forEach { tool ->
                try {
                    val toolPanel = tool.open(content)
                    if (toolPanel != null) {
                        tabs.addTab(tool.displayName, toolPanel)
                    }
                } catch (e: Exception) {
                    XmlTool.logger.error("Unable to open ${tool.displayName} tab", e)
                    tabs.addErrorTab(e) {
                        "Unable to open ${tool.displayName} tab: ${e.message}"
                    }
                }
            }
        add(tabs, "push, grow")
    }

    override val icon: Icon = XmlTool.icon
}

object XmlTool : ClipboardTool {
    override val serialKey = "xml-editor"
    override val title = "XML File"
    override val description = "Ignition XML (.xml)"
    override val icon = FlatSVGIcon("icons/bx-code.svg")
    override val respectsEncoding = true
    override val filter = FileFilter(description, "xml")

    internal val logger = getLogger<XmlTool>()

    override fun open(path: Path): ToolPanel = XmlToolPanel(
        name = path.name,
        tooltip = path.toString(),
        content = path.readText(DefaultEncoding.currentValue).lines(),
    )
    override fun open(data: String): ToolPanel = XmlToolPanel(
        name = "Paste at ${LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))}",
        tooltip = "",
        content = data.lines(),
    )
}
