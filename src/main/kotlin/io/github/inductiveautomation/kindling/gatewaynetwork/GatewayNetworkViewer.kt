package io.github.inductiveautomation.kindling.gatewaynetwork

import com.formdev.flatlaf.extras.FlatSVGIcon
import io.github.inductiveautomation.kindling.core.ClipboardTool
import io.github.inductiveautomation.kindling.core.ToolPanel
import io.github.inductiveautomation.kindling.utils.Action
import io.github.inductiveautomation.kindling.utils.FileFilter
import io.github.inductiveautomation.kindling.utils.FlatScrollPane
import kotlinx.serialization.MissingFieldException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import java.awt.Desktop
import java.net.URI
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import javax.swing.JButton
import javax.swing.JOptionPane
import javax.swing.JTextArea
import kotlin.io.path.createTempDirectory
import kotlin.io.path.name

/**
 * Opens the raw file that contains a gateway network diagram as JSON text. After opening, the user can click a
 * button to open a browser that will show the actual diagram. The diagram is served from html and javascript files
 * copied out to a temp folder on the local file system. The source of the static files is the
 * cytoscape-server IA internal repository, in the 'cytoscape-server.zip' file. Within the zip file,
 * navigate to 'server/static' to view the actual files.
 *
 * To get a JSON diagram in Ignition 8.1:
 * Set the 'gateway.routes.status.GanRoutes' logger set to DEBUG in an Ignition gateway to generate diagram JSON while
 * viewing the gateway network live diagram page.
 */
@kotlinx.serialization.ExperimentalSerializationApi
class GatewayNetworkViewer(tabName: String, tooltip: String, json: String) : ToolPanel() {
    override val icon = GatewayNetworkTool.icon
    private val browserBtn = JButton("View diagram in browser")

    init {
        name = tabName
        toolTipText = tooltip
        add(browserBtn)

        val textArea = JTextArea()
        add(FlatScrollPane(textArea), "newline, push, grow, span")
        textArea.setText(json)
        textArea.isEditable = false

        val diagramButtonAction = Action(
            name = "View diagram",
            description = "View diagram in browser",
            action = {
                val theText = textArea.text
                if (!validateJson(theText)) {
                    return@Action
                }

                // We need to load the contents from the text area and place them in a file
                // with the other static files
                val tmpDir: URL = writeStaticFiles(theText)

                val tempAddress = "${tmpDir}index.html"
                val desktop = Desktop.getDesktop()
                try {
                    desktop.browse(URI(tempAddress))
                } catch (error: Exception) {
                    JOptionPane.showMessageDialog(
                        null,
                        ERROR + error.message,
                        "Error opening browser",
                        JOptionPane.ERROR_MESSAGE,
                    )
                }
            },
        )

        browserBtn.action = diagramButtonAction
    }

    companion object {
        private const val FAVICON = "favicon.ico"
        private const val FAVICON_32 = "favicon-32x32.png"
        private const val FAVICON_48 = "favicon-48x48.png"
        private const val FAVICON_160 = "favicon-160x160.png"
        private const val INDEX_HTML = "index.html"
        private const val MAIN_JS = "main.js"
        private const val STYLE_CSS = "style.css"
        private const val EXTERNAL_DIAGRAM_JS = "external-diagram.js"
        private const val PREAMBLE = "window.externalDiagram = "
        private const val ERROR = "Error: "

        private val JSON = Json {
            ignoreUnknownKeys = true
        }
    }

    /**
     * @return true if the provided text is valid gateway network diagram JSON
     */
    private fun validateJson(theText: String): Boolean {
        var isValid = true
        // Verify that the provided text is valid JSON, as the user is free to copy and paste in text.
        try {
            val theJson: DiagramModel = JSON.decodeFromString(serializer(), theText)
            if (theJson.connections.isEmpty()) {
                isValid = false
                JOptionPane.showMessageDialog(
                    null,
                    "${ERROR}no connections defined in the JSON data",
                    "JSON missing data",
                    JOptionPane.ERROR_MESSAGE,
                )
            }
        } catch (e: SerializationException) {
            isValid = false
            if (e is MissingFieldException) {
                val missingEx: MissingFieldException = e
                val msg = "the JSON data does not represent a valid gateway network diagram. The following fields" +
                    " are missing: ${missingEx.missingFields}"
                JOptionPane.showMessageDialog(
                    null,
                    ERROR + msg,
                    "JSON parsing error",
                    JOptionPane.ERROR_MESSAGE,
                )
            } else {
                JOptionPane.showMessageDialog(
                    null,
                    ERROR + e.message,
                    "JSON parsing error",
                    JOptionPane.ERROR_MESSAGE,
                )
            }
        }

        return isValid
    }

    private fun writeStaticFiles(jsonText: String): URL {
        // Make a temp dir for the static html/js files
        val tmpDir: Path = createTempDirectory("kindling-gateway-network-diagram")
        tmpDir.toFile().deleteOnExit()

        writeStaticFile(FAVICON_32, tmpDir)
        writeStaticFile(FAVICON_48, tmpDir)
        writeStaticFile(FAVICON_160, tmpDir)
        writeStaticFile(FAVICON, tmpDir)
        writeStaticFile(INDEX_HTML, tmpDir)
        writeStaticFile(MAIN_JS, tmpDir)
        writeStaticFile(STYLE_CSS, tmpDir)

        // This file is what the compiled React js file uses to populate the gateway network diagram.
        val externalDiagramFile = tmpDir.resolve(EXTERNAL_DIAGRAM_JS)
        externalDiagramFile.toFile().deleteOnExit()
        Files.writeString(externalDiagramFile, PREAMBLE + jsonText)

        return tmpDir.toUri().toURL()
    }

    private fun writeStaticFile(fileName: String, tmpDir: Path) {
        val fileUrl: URL? = GatewayNetworkViewer::class.java.getResource(fileName)
        val filePath = tmpDir.resolve(fileName)
        filePath.toFile().deleteOnExit()
        Files.copy(Path.of(fileUrl?.toURI()), filePath)
    }
}

@kotlinx.serialization.ExperimentalSerializationApi
object GatewayNetworkTool : ClipboardTool {
    override val title = "Gateway Network Diagram"
    override val description = "Gateway network diagram (.json or .txt) files"
    override val icon = FlatSVGIcon("icons/bx-sitemap.svg")
    override val filter = FileFilter(description, listOf("json", "txt"))

    override fun open(data: String): ToolPanel {
        return GatewayNetworkViewer(
            tabName = "Paste at ${LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))}",
            tooltip = "",
            json = data,
        )
    }

    override fun open(path: Path): ToolPanel {
        val lines: String = parseLines(path.toFile().readLines())

        return GatewayNetworkViewer(
            tabName = path.name,
            tooltip = path.toString(),
            json = lines,
        )
    }

    private fun parseLines(lines: List<String>) = buildString {
        for (line in lines) {
            if (line.isBlank()) {
                continue
            } else {
                appendLine(line)
            }
        }
    }
}
