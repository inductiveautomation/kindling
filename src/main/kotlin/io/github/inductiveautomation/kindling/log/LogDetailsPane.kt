package io.github.inductiveautomation.kindling.log

import com.formdev.flatlaf.extras.FlatSVGIcon
import com.formdev.flatlaf.extras.components.FlatButton
import com.formdev.flatlaf.extras.components.FlatTextPane
import com.formdev.flatlaf.fonts.roboto.FlatRobotoFont
import io.github.inductiveautomation.kindling.core.Detail
import io.github.inductiveautomation.kindling.utils.Action
import io.github.inductiveautomation.kindling.utils.escapeHtml
import net.miginfocom.swing.MigLayout
import org.jdesktop.swingx.JXCollapsiblePane
import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JFileChooser
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.SwingUtilities.invokeLater
import javax.swing.UIManager
import javax.swing.event.HyperlinkEvent
import javax.swing.filechooser.FileNameExtensionFilter
import javax.swing.text.html.HTMLEditorKit
import kotlin.properties.Delegates

class LogDetailsPane : JPanel(MigLayout("ins 1, fill")) {
    var events: List<Detail> by Delegates.observable(emptyList()) { _, _, newValue ->
        detailsPanel.removeAll()
        newValue.forEach {
            detailsPanel.add(DetailContainer(it), "growx, wrap")
        }
        detailsScrollPane.verticalScrollBar.value = 0
        detailsPanel.revalidate()
        detailsPanel.repaint()
    }

    private val detailsPanel = JPanel(MigLayout("ins 2,  fillx"))
    private val detailsScrollPane = JScrollPane(detailsPanel).apply { verticalScrollBar.unitIncrement = 16 }

    private val copy = Action(
        description = "Copy to Clipboard",
        icon = FlatSVGIcon("icons/bx-clipboard.svg"),
    ) {
        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        clipboard.setContents(StringSelection(events.toClipboardFormat()), null)
    }

    private val save = Action(
        description = "Save to File",
        icon = FlatSVGIcon("icons/bx-save.svg"),
    ) {
        JFileChooser().apply {
            fileSelectionMode = JFileChooser.FILES_ONLY
            fileFilter = FileNameExtensionFilter("Text File", "txt")
            val save = showSaveDialog(this@LogDetailsPane)
            if (save == JFileChooser.APPROVE_OPTION) {
                selectedFile.writeText(events.toClipboardFormat())
            }
        }
    }

    private val popout = Action(
        description = "Open in New Window",
        icon = FlatSVGIcon("icons/bx-link-external.svg"),
    ) {
        DetailsPopup("Details", events.toPopoutFormat())
    }

    init {
        add(detailsScrollPane, "push, grow")
        add(JButton(copy), "cell 1 0, top, flowy, gap 0")
        add(JButton(save), "cell 1 0")
        add(JButton(popout), "cell 1 0")
    }

    private class TextPane(content: String) : FlatTextPane() {
        init {
            isEditable = false
            editorKit = DetailsEditorKit()
            background = null
            addHyperlinkListener { event ->
                if (event.eventType == HyperlinkEvent.EventType.ACTIVATED) {
                    val desktop = Desktop.getDesktop()
                    desktop.browse(event.url.toURI())
                }
            }
            text = content
        }
    }

    private class DetailsPopup(header: String, content: String) : JFrame() {
        init {
            title = header
            layout = MigLayout("fill, ins 0")
            setLocation(200, 100)
            setSize(700, ((content.split("<br>").size * 20) + 55).coerceAtMost(900))
            contentPane.add(
                JScrollPane(TextPane(content)).apply {
                    invokeLater { verticalScrollBar.value = 0 }
                },
                "grow, push",
            )
            isVisible = true
        }
    }

    private class DetailContainer(val content: Detail) : JPanel(MigLayout("fill, ins 0")) {
        private val collapsablePane = JXCollapsiblePane().apply {
            isAnimated = false
            this.contentPane.background = UIManager.getColor("TitlePane.background")
            val textPane = TextPane(content.toDisplayFormat())
            this.contentPane.add(textPane, "ins 0, growx, push, wrap")
        }

        private val header = JPanel(MigLayout("fill, ins 0")).apply {
            val detailsButton = FlatButton().apply {
                icon = FlatSVGIcon("icons/bx-link-external.svg").derive(12, 12)
                background = null
                addActionListener {
                    DetailsPopup(content.title, content.toDisplayFormat())
                }
            }

            val title = JLabel("  " + content.title).apply {
                font = UIManager.getFont("h3.font")
            }

            val collapseButton = JButton("▽").apply {
                border = null
                background = null
                font = UIManager.getFont("h3.font")
                addActionListener {
                    if (collapsablePane.isCollapsed) {
                        collapsablePane.isCollapsed = false
                        text = "▽"
                    } else {
                        collapsablePane.isCollapsed = true
                        text = "▷"
                    }
                }
            }

            this.add(collapseButton)
            this.add(detailsButton)
            this.add(title, "pushx, growx")
        }

        private fun Detail.toDisplayFormat(): String {
            return let { event ->
                buildString {
                    append("<html>")
                    append("<pre>")
                    if (event.details.isNotEmpty()) {
                        append("<b>Entries:</b><br>")
                        event.details.entries.joinTo(buffer = this, separator = " ") { (key, value) ->
                            "data-$key = \"$value\""
                        }
                        append("<br><br>")
                    }
                    if (event.message != null) {
                        append("<b>Message:</b><br>")
                        append(event.message.escapeHtml().replace("\t", "       "))
                        append("<br>")
                    }
                    if (event.body.isNotEmpty()) {
                        append("<br><b>StackTrace:</b><br>")
                        event.body.joinTo(buffer = this, separator = "<br>") { (text, link) ->
                            if (link != null) {
                                """<a href="$link">$text</a>"""
                            } else {
                                text.replace("\t", "       ")
                            }
                        }
                    }
                }
            }
        }

        init {
            border = BorderFactory.createLineBorder(UIManager.getColor("Component.borderColor"), 1)
            add(header, "growx, wrap")
            add(collapsablePane, "growx")
        }
    }

    private fun List<Detail>.toClipboardFormat(): String {
        return joinToString(separator = "\n\n") { event ->
            buildString {
                appendLine(event.title)
                if (event.message != null) {
                    appendLine(event.message)
                }
                event.body.joinTo(buffer = this, separator = "\n") { "\t${it.text}" }
            }
        }
    }

    private fun List<Detail>.toPopoutFormat(): String {
        return joinToString(separator = "", prefix = "<html><pre>") { event ->
            buildString {
                append("<b>${event.title.escapeHtml()}</b><br>")
                if (event.details.isNotEmpty()) {
                    event.details.entries.joinTo(buffer = this, separator = " ") { (key, value) ->
                        "data-$key = \"$value\""
                    }
                    append("<br>")
                }
                if (event.message != null) {
                    append(event.message.escapeHtml().replace("\t", "       "))
                    append("<br>")
                }
                if (event.body.isNotEmpty()) {
                    event.body.joinTo(buffer = this, separator = "<br>") { (text, link) ->
                        if (link != null) {
                            """<a href="$link">$text</a>"""
                        } else {
                            text.replace("\t", "       ")
                        }
                    }
                    append("<br>")
                }
            }
        }
    }
}

class DetailsEditorKit : HTMLEditorKit() {
    init {
        styleSheet.apply {
            //language=CSS
            addRule(
                """
                b { 
                    font-size: larger; 
                }
                pre { 
                    font-size: 10px; 
                    font-family: ${FlatRobotoFont.FAMILY}
                }
                object { 
                    padding-left: 16px; 
                }
                """,
            )
        }
    }
}
