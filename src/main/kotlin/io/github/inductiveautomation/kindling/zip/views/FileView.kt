package io.github.inductiveautomation.kindling.zip.views

import com.formdev.flatlaf.extras.FlatSVGIcon
import io.github.inductiveautomation.kindling.core.Kindling.Preferences.UI.Theme
import io.github.inductiveautomation.kindling.core.Theme.Companion.theme
import io.github.inductiveautomation.kindling.utils.asActionIcon
import io.github.inductiveautomation.kindling.utils.configureCellRenderer
import io.github.inductiveautomation.kindling.zip.views.FileView.SyntaxStyle.CSS
import io.github.inductiveautomation.kindling.zip.views.FileView.SyntaxStyle.JSON
import io.github.inductiveautomation.kindling.zip.views.FileView.SyntaxStyle.Plaintext
import io.github.inductiveautomation.kindling.zip.views.FileView.SyntaxStyle.Properties
import io.github.inductiveautomation.kindling.zip.views.FileView.SyntaxStyle.Python
import io.github.inductiveautomation.kindling.zip.views.FileView.SyntaxStyle.XML
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea
import org.fife.ui.rsyntaxtextarea.SyntaxConstants.SYNTAX_STYLE_CSS
import org.fife.ui.rsyntaxtextarea.SyntaxConstants.SYNTAX_STYLE_CSV
import org.fife.ui.rsyntaxtextarea.SyntaxConstants.SYNTAX_STYLE_DOCKERFILE
import org.fife.ui.rsyntaxtextarea.SyntaxConstants.SYNTAX_STYLE_HTML
import org.fife.ui.rsyntaxtextarea.SyntaxConstants.SYNTAX_STYLE_INI
import org.fife.ui.rsyntaxtextarea.SyntaxConstants.SYNTAX_STYLE_JAVA
import org.fife.ui.rsyntaxtextarea.SyntaxConstants.SYNTAX_STYLE_JAVASCRIPT
import org.fife.ui.rsyntaxtextarea.SyntaxConstants.SYNTAX_STYLE_JSON
import org.fife.ui.rsyntaxtextarea.SyntaxConstants.SYNTAX_STYLE_KOTLIN
import org.fife.ui.rsyntaxtextarea.SyntaxConstants.SYNTAX_STYLE_MARKDOWN
import org.fife.ui.rsyntaxtextarea.SyntaxConstants.SYNTAX_STYLE_NONE
import org.fife.ui.rsyntaxtextarea.SyntaxConstants.SYNTAX_STYLE_PROPERTIES_FILE
import org.fife.ui.rsyntaxtextarea.SyntaxConstants.SYNTAX_STYLE_PYTHON
import org.fife.ui.rsyntaxtextarea.SyntaxConstants.SYNTAX_STYLE_SQL
import org.fife.ui.rsyntaxtextarea.SyntaxConstants.SYNTAX_STYLE_TYPESCRIPT
import org.fife.ui.rsyntaxtextarea.SyntaxConstants.SYNTAX_STYLE_UNIX_SHELL
import org.fife.ui.rsyntaxtextarea.SyntaxConstants.SYNTAX_STYLE_WINDOWS_BATCH
import org.fife.ui.rsyntaxtextarea.SyntaxConstants.SYNTAX_STYLE_XML
import org.fife.ui.rsyntaxtextarea.SyntaxConstants.SYNTAX_STYLE_YAML
import org.fife.ui.rtextarea.RTextScrollPane
import java.awt.EventQueue
import java.awt.Rectangle
import java.nio.charset.Charset
import java.nio.file.Path
import java.nio.file.spi.FileSystemProvider
import javax.swing.JComboBox
import kotlin.io.path.extension
import kotlin.io.path.name

class FileView(override val provider: FileSystemProvider, override val path: Path) : SinglePathView() {
    private val encodingCombo = JComboBox(
        arrayOf(
            null,
            Charsets.UTF_8,
            Charsets.ISO_8859_1,
            Charsets.US_ASCII,
            Charsets.UTF_16,
        ),
    ).apply {
        selectedItem = Charsets.UTF_8.takeIf { isTextFile(path) }

        configureCellRenderer { _, value, _, _, _ ->
            text = if (value != null) {
                value.displayName()
            } else {
                "Binary"
            }
        }
    }

    private enum class SyntaxStyle(val style: String) {
        Plaintext(SYNTAX_STYLE_NONE),
        CSS(SYNTAX_STYLE_CSS),
        CSV(SYNTAX_STYLE_CSV),
        Dockerfile(SYNTAX_STYLE_DOCKERFILE),
        HTML(SYNTAX_STYLE_HTML),
        INI(SYNTAX_STYLE_INI),
        Java(SYNTAX_STYLE_JAVA),
        Javascript(SYNTAX_STYLE_JAVASCRIPT),
        JSON(SYNTAX_STYLE_JSON),
        Kotlin(SYNTAX_STYLE_KOTLIN),
        Markdown(SYNTAX_STYLE_MARKDOWN),
        Properties(SYNTAX_STYLE_PROPERTIES_FILE),
        Python(SYNTAX_STYLE_PYTHON),
        SQL(SYNTAX_STYLE_SQL),
        Typescript(SYNTAX_STYLE_TYPESCRIPT),
        Shell(SYNTAX_STYLE_UNIX_SHELL),
        Batch(SYNTAX_STYLE_WINDOWS_BATCH),
        XML(SYNTAX_STYLE_XML),
        YAML(SYNTAX_STYLE_YAML),
    }

    private val syntaxCombo = JComboBox(SyntaxStyle.entries.toTypedArray()).apply {
        selectedItem = guessSyntaxScheme()

        configureCellRenderer { _, value, _, _, _ ->
            text = value?.name
        }
    }

    private val textArea = RSyntaxTextArea().apply {
        isEditable = false
        syntaxEditingStyle = (syntaxCombo.selectedItem as SyntaxStyle).style

        theme = Theme.currentValue
    }

    override val icon: FlatSVGIcon = FlatSVGIcon("icons/bx-file.svg").asActionIcon()

    init {
        updateText()

        Theme.addChangeListener { theme ->
            textArea.theme = theme
        }

        encodingCombo.addActionListener {
            syntaxCombo.selectedItem = if (encodingCombo.selectedItem == null) {
                Plaintext
            } else {
                guessSyntaxScheme()
            }
            updateText()
        }
        syntaxCombo.addActionListener {
            textArea.syntaxEditingStyle = (syntaxCombo.selectedItem as SyntaxStyle).style
        }

        add(encodingCombo, "split")
        add(syntaxCombo)
        add(RTextScrollPane(textArea), "newline, push, grow")
    }

    private fun guessSyntaxScheme() = KNOWN_EXTENSIONS[path.extension.lowercase()] ?: Plaintext

    private fun updateText() {
        val charset = encodingCombo.selectedItem as? Charset
        val text = if (charset != null) {
            readFileAsString(charset)
        } else {
            readFileAsBytes()
        }

        textArea.text = if (syntaxCombo.selectedItem as SyntaxStyle == JSON) {
            // pretty-print/normalize json
            JSON_FORMAT.run {
                encodeToString(JsonElement.serializer(), parseToJsonElement(text))
            }
        } else {
            text
        }
        EventQueue.invokeLater {
            textArea.scrollRectToVisible(Rectangle(0, 0))
        }
    }

    private fun readFileAsString(charset: Charset): String {
        provider.newInputStream(path).use { file ->
            return file.bufferedReader(charset).readText()
        }
    }

    @OptIn(ExperimentalStdlibApi::class)
    private fun readFileAsBytes(): String {
        provider.newInputStream(path).use { file ->
            val windowSize = 16
            return sequence {
                val buffer = ByteArray(windowSize)
                var numberOfBytesRead: Int
                do {
                    numberOfBytesRead = file.readNBytes(buffer, 0, windowSize)

                    // the last read might not be complete, so there could be stale data in the buffer
                    val toRead = buffer.sliceArray(0 until numberOfBytesRead)
                    val hexBytes = toRead.toHexString(HEX_FORMAT)
                    val decodedBytes = decodeBytes(toRead)
                    yield("${hexBytes.padEnd(47)}  $decodedBytes")
                } while (numberOfBytesRead == windowSize)
            }.joinToString(separator = "\n")
        }
    }

    private fun decodeBytes(toRead: ByteArray): String {
        return String(
            CharArray(toRead.size) { i ->
                val byte = toRead[i]
                if (byte >= 0 && !Character.isISOControl(byte.toInt())) {
                    Char(byte.toUShort())
                } else {
                    '.'
                }
            },
        )
    }

    companion object {
        @OptIn(ExperimentalSerializationApi::class)
        private val JSON_FORMAT = Json {
            prettyPrint = true
            prettyPrintIndent = "  "
        }

        @OptIn(ExperimentalStdlibApi::class)
        private val HEX_FORMAT = HexFormat {
            bytes {
                byteSeparator = " "
            }
        }

        private val KNOWN_EXTENSIONS: Map<String, SyntaxStyle> = mapOf(
            "conf" to Properties,
            "properties" to Properties,
            "py" to Python,
            "json" to JSON,
            "svg" to XML,
            "xml" to XML,
            "css" to CSS,
            "txt" to Plaintext,
            "md" to Plaintext,
            "p7b" to Plaintext,
            "log" to Plaintext,
            "ini" to Plaintext,
        )

        private val KNOWN_FILENAMES = setOf(
            "readme",
            ".uuid",
            "wrapper.log.1",
            "wrapper.log.2",
            "wrapper.log.3",
            "wrapper.log.4",
            "wrapper.log.5",
        )

        fun isTextFile(path: Path) = path.extension.lowercase() in KNOWN_EXTENSIONS || path.name.lowercase() in KNOWN_FILENAMES
    }
}
