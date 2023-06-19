package io.github.inductiveautomation.kindling.zip.views

import com.formdev.flatlaf.extras.FlatSVGIcon
import io.github.inductiveautomation.kindling.core.Kindling.UI.Theme
import io.github.inductiveautomation.kindling.core.Theme.Companion.theme
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea
import org.fife.ui.rsyntaxtextarea.SyntaxConstants.SYNTAX_STYLE_CSS
import org.fife.ui.rsyntaxtextarea.SyntaxConstants.SYNTAX_STYLE_JSON
import org.fife.ui.rsyntaxtextarea.SyntaxConstants.SYNTAX_STYLE_NONE
import org.fife.ui.rsyntaxtextarea.SyntaxConstants.SYNTAX_STYLE_PROPERTIES_FILE
import org.fife.ui.rsyntaxtextarea.SyntaxConstants.SYNTAX_STYLE_PYTHON
import org.fife.ui.rsyntaxtextarea.SyntaxConstants.SYNTAX_STYLE_XML
import org.fife.ui.rtextarea.RTextScrollPane
import java.awt.EventQueue
import java.awt.Rectangle
import java.nio.file.Path
import java.nio.file.spi.FileSystemProvider
import kotlin.io.path.extension
import kotlin.io.path.name

class TextFileView(override val provider: FileSystemProvider, override val path: Path) : SinglePathView() {
    private val textArea = RSyntaxTextArea().apply {
        isEditable = false
        syntaxEditingStyle = KNOWN_EXTENSIONS[path.extension] ?: SYNTAX_STYLE_NONE

        theme = Theme.currentValue
    }

    override val icon: FlatSVGIcon = FlatSVGIcon("icons/bx-file.svg").derive(16, 16)

    init {
        val text = provider.newInputStream(path).use {
            it.bufferedReader().readText()
        }

        textArea.text = if (path.extension == "json") {
            // pretty-print/normalize json
            JSON_FORMAT.run {
                encodeToString(JsonElement.serializer(), parseToJsonElement(text))
            }
        } else {
            text
        }

        Theme.addChangeListener { newTheme ->
            textArea.theme = newTheme
        }

        add(RTextScrollPane(textArea), "push, grow")
        EventQueue.invokeLater {
            textArea.scrollRectToVisible(Rectangle(0, 0))
        }
    }

    companion object {
        @OptIn(ExperimentalSerializationApi::class)
        private val JSON_FORMAT = Json {
            prettyPrint = true
            prettyPrintIndent = "  "
        }

        private val KNOWN_EXTENSIONS = mapOf(
            "conf" to SYNTAX_STYLE_PROPERTIES_FILE,
            "properties" to SYNTAX_STYLE_PROPERTIES_FILE,
            "py" to SYNTAX_STYLE_PYTHON,
            "json" to SYNTAX_STYLE_JSON,
            "svg" to SYNTAX_STYLE_XML,
            "xml" to SYNTAX_STYLE_XML,
            "css" to SYNTAX_STYLE_CSS,
            "txt" to SYNTAX_STYLE_NONE,
            "md" to SYNTAX_STYLE_NONE,
            "p7b" to SYNTAX_STYLE_NONE,
            "log" to SYNTAX_STYLE_NONE,
        )

        private val KNOWN_FILENAMES = setOf(
            "README",
            ".uuid",
            "wrapper.log.1",
            "wrapper.log.2",
            "wrapper.log.3",
            "wrapper.log.4",
            "wrapper.log.5",
        )

        fun isTextFile(path: Path) = path.extension in KNOWN_EXTENSIONS || path.name in KNOWN_FILENAMES
    }
}
