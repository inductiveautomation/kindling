package io.github.inductiveautomation.kindling.gatewaynetwork

import com.formdev.flatlaf.extras.FlatSVGIcon
import io.github.inductiveautomation.kindling.core.ClipboardTool
import io.github.inductiveautomation.kindling.core.Kindling.Preferences.General.DefaultEncoding
import io.github.inductiveautomation.kindling.core.ToolOpeningException
import io.github.inductiveautomation.kindling.core.ToolPanel
import io.github.inductiveautomation.kindling.utils.Action
import io.github.inductiveautomation.kindling.utils.FileFilter
import io.github.inductiveautomation.kindling.utils.FlatScrollPane
import io.github.inductiveautomation.kindling.utils.transferTo
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import java.awt.Desktop
import java.net.URI
import java.nio.file.Path
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import javax.swing.JButton
import javax.swing.JOptionPane
import javax.swing.JTextArea
import kotlin.io.path.createTempDirectory
import kotlin.io.path.name
import kotlin.io.path.outputStream
import kotlin.io.path.useLines
import kotlin.io.path.writeText

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

class GatewayNetworkViewer(tabName: String, tooltip: String, json: String) : ToolPanel() {
    override val icon = GatewayNetworkTool.icon

    private val textArea = JTextArea().apply {
        text = json
        isEditable = false
    }

    private val openBrowserAction: Action = Action(
        name = "View diagram in browser",
        description = "View diagram in browser",
        action = {
            // We need to load the contents from the text area and place them in a file
            // with the other static files
            val tmpDir = writeStaticFiles(textArea.text)

            try {
                Desktop.getDesktop().browse(tmpDir.resolve("index.html"))
            } catch (error: Exception) {
                JOptionPane.showMessageDialog(
                    null,
                    "Error: ${error.message}",
                    "Browser Opening Error",
                    JOptionPane.ERROR_MESSAGE,
                )
            }
        },
    )

    init {
        name = tabName
        toolTipText = tooltip

        validateJson(json)

        add(JButton(openBrowserAction))
        add(FlatScrollPane(textArea), "newline, push, grow, span")
    }

    /**
     * Verifies that the provided text is valid gateway network diagram JSON
     * @throws ToolOpeningException to abort the parsing file early, before we actually create any temp files.
     */
    private fun validateJson(json: String) {
        try {
            val model: DiagramModel = JSON.decodeFromString(serializer(), json)
            if (model.connections.isEmpty()) {
                throw ToolOpeningException("Incomplete GAN diagram - no connections")
            }
        } catch (e: SerializationException) {
            throw ToolOpeningException("Error parsing GAN diagram JSON", e)
        }
    }

    private fun writeStaticFiles(jsonText: String): URI {
        // Make a temp dir for the static html/js files
        val tmpDir: Path = createTempDirectory("kindling-gateway-network-diagram").apply {
            for (resource in RESOURCES) {
                writeStaticFile(resource)
            }

            // This file is what the compiled React js file uses to populate the gateway network diagram.
            resolve(EXTERNAL_DIAGRAM_JS).writeText(PREAMBLE + jsonText)
        }
        tmpDir.toFile().deleteOnExit()

        return tmpDir.toUri()
    }

    companion object {
        private val RESOURCES = listOf(
            "favicon.ico",
            "favicon-32x32.png",
            "favicon-48x48.png",
            "favicon-160x160.png",
            "index.html",
            "main.js",
            "style.css",
        )
        private const val EXTERNAL_DIAGRAM_JS = "external-diagram.js"
        private const val PREAMBLE = "window.externalDiagram = "

        private fun Path.writeStaticFile(file: String) {
            resolve(file).outputStream().use { fileStream ->
                val inputStream = GatewayNetworkViewer::class.java.getResourceAsStream(file) ?: return
                inputStream transferTo fileStream
            }
        }

        private val JSON = Json {
            ignoreUnknownKeys = true
        }
    }
}

data object GatewayNetworkTool : ClipboardTool {
    override val serialKey = "gan-diagram"
    override val title = "Gateway Network Diagram"
    override val description = "GAN Diagram (.json, .txt)"
    override val icon = FlatSVGIcon("icons/bx-sitemap.svg")
    override val filter = FileFilter(description, "json", "txt")
    override val respectsEncoding = true

    override fun open(data: String) = GatewayNetworkViewer(
        tabName = "Paste at ${LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))}",
        tooltip = "",
        json = data,
    )

    override fun open(path: Path): ToolPanel {
        val diagram: String = path.useLines(DefaultEncoding.currentValue) { lines ->
            lines.filter(String::isNotBlank).joinToString(separator = "\n")
        }

        return GatewayNetworkViewer(
            tabName = path.name,
            tooltip = path.toString(),
            json = diagram,
        )
    }
}
