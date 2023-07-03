package io.github.inductiveautomation.kindling.core

import com.formdev.flatlaf.extras.FlatSVGIcon
import com.formdev.flatlaf.extras.components.FlatTextPane
import io.github.inductiveautomation.kindling.core.Kindling.Preferences.Advanced.HyperlinkStrategy
import io.github.inductiveautomation.kindling.internal.DetailsIcon
import io.github.inductiveautomation.kindling.utils.Action
import io.github.inductiveautomation.kindling.utils.FlatScrollPane
import io.github.inductiveautomation.kindling.utils.escapeHtml
import net.miginfocom.swing.MigLayout
import java.awt.Component
import java.awt.EventQueue
import java.awt.Rectangle
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import javax.swing.JButton
import javax.swing.JFileChooser
import javax.swing.JPanel
import javax.swing.event.HyperlinkEvent
import javax.swing.filechooser.FileNameExtensionFilter
import javax.swing.text.ComponentView
import javax.swing.text.Element
import javax.swing.text.StyleConstants
import javax.swing.text.View
import javax.swing.text.ViewFactory
import javax.swing.text.html.HTML
import javax.swing.text.html.HTMLEditorKit
import kotlin.properties.Delegates

class DetailsPane(initialEvents: List<Detail> = emptyList()) : JPanel(MigLayout("ins 0, fill")) {
    var events: List<Detail> by Delegates.observable(initialEvents) { _, _, newValue ->
        textPane.text = newValue.toDisplayFormat()
        EventQueue.invokeLater {
            textPane.scrollRectToVisible(Rectangle(0, 0, 0, 0))
        }
    }

    private val textPane = FlatTextPane().apply {
        isEditable = false
        editorKit = DetailsEditorKit()

        addHyperlinkListener { event ->
            if (event.eventType == HyperlinkEvent.EventType.ACTIVATED) {
                HyperlinkStrategy.currentValue.handleEvent(event)
            }
        }
    }

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
            val save = showSaveDialog(this@DetailsPane)
            if (save == JFileChooser.APPROVE_OPTION) {
                selectedFile.writeText(events.toClipboardFormat())
            }
        }
    }

    private val actionPanel = JPanel(MigLayout("flowy, top, ins 0"))

    val actions: MutableList<Action> = object : ArrayList<Action>() {
        init {
            add(copy)
            add(save)
        }

        override fun add(element: Action) = super.add(element).also {
            actionPanel.add(
                JButton(element).apply {
                    hideActionText = true
                },
            )
        }
    }

    init {
        add(FlatScrollPane(textPane), "push, grow")
        add(actionPanel, "east")
        textPane.text = events.toDisplayFormat()
    }

    private fun List<Detail>.toDisplayFormat(): String {
        return joinToString(separator = "", prefix = "<html>") { event ->
            buildString {
                append("<b>").append(event.title)
                if (event.details.isNotEmpty()) {
                    append("&nbsp;<object ")
                    event.details.entries.joinTo(buffer = this, separator = " ") { (key, value) ->
                        "$detailPrefix$key = \"$value\""
                    }
                    append("/>")
                }
                append("</b>")
                if (event.message != null) {
                    append("<br>")
                    append(event.message.escapeHtml())
                }
                if (event.body.isNotEmpty()) {
                    event.body.joinTo(buffer = this, separator = "\n", prefix = "<pre>", postfix = "</pre>") { (text, link) ->
                        if (link != null) {
                            """<a href="$link">$text</a>"""
                        } else {
                            text
                        }
                    }
                } else {
                    append("<br>")
                }
            }
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
}

private const val detailPrefix = "data-"

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
                }
                object { 
                    padding-left: 16px; 
                }
                """.trimIndent(),
            )
        }
    }

    override fun getViewFactory(): ViewFactory {
        return object : HTMLFactory() {
            override fun create(elem: Element): View {
                val attrs = elem.attributes
                val o = attrs.getAttribute(StyleConstants.NameAttribute)
                if (o == HTML.Tag.OBJECT) {
                    return object : ComponentView(elem) {
                        override fun createComponent(): Component {
                            val details: Map<String, String> =
                                elem.attributes.attributeNames.asSequence()
                                    .filterIsInstance<String>()
                                    .associate { rawAttribute ->
                                        rawAttribute.removePrefix(detailPrefix) to elem.attributes.getAttribute(rawAttribute) as String
                                    }
                            return DetailsIcon(details)
                        }
                    }
                }
                return super.create(elem)
            }
        }
    }
}
