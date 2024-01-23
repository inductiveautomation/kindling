package io.github.inductiveautomation.kindling.xml

import com.formdev.flatlaf.extras.FlatSVGIcon
import com.formdev.flatlaf.extras.components.FlatTabbedPane
import io.github.inductiveautomation.kindling.core.Tool
import io.github.inductiveautomation.kindling.core.ToolPanel
import io.github.inductiveautomation.kindling.utils.FileFilter
import io.github.inductiveautomation.kindling.utils.TabStrip
import java.io.File
import java.nio.file.Path
import javax.swing.Icon
import kotlin.io.path.name

enum class XMLTools {
    LogbackEditor {
        override fun supports(topLevelElement: String): Boolean = topLevelElement.contains("</configuration>", ignoreCase = true)

        override fun open(path: Path): ToolPanel {
            return LogbackEditor(path)
        }
    },
    XMLViewer {
        override fun supports(topLevelElement: String): Boolean = true

        override fun open(path: Path): ToolPanel {
            return XMLViewerPanel(path)
        }
    }, ;

    abstract fun supports(topLevelElement: String): Boolean

    abstract fun open(path: Path): ToolPanel
}

class XMLToolPanel(path: Path) : ToolPanel() {
    private val tabs =
        TabStrip().apply {
            trailingComponent = null
            isTabsClosable = false
            tabType = FlatTabbedPane.TabType.underlined
            tabHeight = 16
            isHideTabAreaWithOneTab = true
        }

    init {
        name = path.name
        toolTipText = path.toString()
        var addedTabs = 0
        val topLevelElement = File(path.toString()).useLines { it.toList() }.toMutableList().apply { removeAll(listOf("")) }.last()
        for (tool in XMLTools.entries) {
            if (tool.supports(topLevelElement)) {
                tabs.addLazyTab(
                    tabName = tool.name,
                ) {
                    tool.open(path)
                }
                addedTabs += 1
            }
        }
        if (addedTabs == 1) {
            tabs.selectedIndex = tabs.indices.last
        }
        add(tabs, "push, grow")
    }

    override val icon: Icon = XMLTool.icon
}

object XMLTool : Tool {
    override val title = "Logback Editor"
    override val description = "Logback (.xml) files"
    override val icon = FlatSVGIcon("icons/bx-code.svg")
    private val extensions = listOf("xml")
    override val filter = FileFilter(description, *extensions.toTypedArray())

    override fun open(path: Path): ToolPanel = XMLToolPanel(path)
}
