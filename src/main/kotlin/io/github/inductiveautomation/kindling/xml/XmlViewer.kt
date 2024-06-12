package io.github.inductiveautomation.kindling.xml

import io.github.inductiveautomation.kindling.core.Kindling.Preferences.UI.Theme
import io.github.inductiveautomation.kindling.core.Theme.Companion.theme
import net.miginfocom.swing.MigLayout
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea
import org.fife.ui.rtextarea.RTextScrollPane
import javax.swing.JPanel

internal class XmlViewer(file: List<String>) : JPanel(MigLayout("ins 6, fill, hidemode 3")) {
    init {
        add(
            RTextScrollPane(
                RSyntaxTextArea(file.joinToString("\n")).apply {
                    isEditable = false
                    syntaxEditingStyle = "text/xml"
                    theme = Theme.currentValue

                    Theme.addChangeListener { newValue ->
                        theme = newValue
                    }
                },
            ),
            "grow, push",
        )
    }
}
