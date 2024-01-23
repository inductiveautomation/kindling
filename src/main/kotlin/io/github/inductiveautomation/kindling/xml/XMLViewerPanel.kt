package io.github.inductiveautomation.kindling.xml

import io.github.inductiveautomation.kindling.core.Kindling
import io.github.inductiveautomation.kindling.core.Theme.Companion.theme
import io.github.inductiveautomation.kindling.core.ToolPanel
import net.miginfocom.swing.MigLayout
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea
import org.fife.ui.rtextarea.RTextScrollPane
import java.io.File
import java.nio.file.Path
import javax.swing.Icon
import javax.swing.JPanel
import kotlin.io.path.name

class XMLViewerPanel(path: Path) : ToolPanel() {
    override val icon: Icon = XMLTool.icon

    init {
        name = path.name
        val content = File(path.toString()).readText()
        add(
            JPanel(
                MigLayout("fill, ins 6").apply {
                    add(
                        RTextScrollPane(
                            RSyntaxTextArea(content).apply {
                                isEditable = false
                                syntaxEditingStyle = "text/xml"
                                theme = Kindling.Preferences.UI.Theme.currentValue
                            },
                        ).apply {
                            lineNumbersEnabled = true
                        },
                        "grow, push",
                    )
                },
            ),
        )
    }
}
