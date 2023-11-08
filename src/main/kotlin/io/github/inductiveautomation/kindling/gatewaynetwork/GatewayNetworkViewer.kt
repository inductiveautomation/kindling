package io.github.inductiveautomation.kindling.gatewaynetwork

import com.formdev.flatlaf.extras.FlatSVGIcon
import io.github.inductiveautomation.kindling.core.Kindling.Preferences.General.DefaultEncoding
import io.github.inductiveautomation.kindling.core.Tool
import io.github.inductiveautomation.kindling.core.ToolPanel
import io.github.inductiveautomation.kindling.utils.FileFilter
import io.github.inductiveautomation.kindling.utils.FlatScrollPane
import org.json.JSONException
import org.json.JSONObject
import java.awt.Desktop
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.io.File
import java.net.URI
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import javax.swing.JButton
import javax.swing.JOptionPane
import javax.swing.JTextArea
import kotlin.io.path.name
import kotlin.io.path.useLines

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
class GatewayNetworkViewer(path: Path) : ToolPanel() {
    override val icon = GatewayNetworkTool.icon

    private val browserBtn = JButton("View diagram in browser")

    init {
        name = path.name
        toolTipText = path.toString()
        add(browserBtn)

        val textArea = JTextArea()
        add(FlatScrollPane(textArea), "newline, push, grow, span")

        // Read the provided file and populate the text area with it
        path.useLines(DefaultEncoding.currentValue) { lines -> parseLines(lines, textArea) }

        browserBtn.addMouseListener(
            object : MouseAdapter() {
                override fun mousePressed(e: MouseEvent?) {
                    // We need to load the contents from the text area and place them in a file
                    // with the other static files
                    val theText = textArea.text

                    // Verify that the provided text is valid JSON, as the user is free to copy and paste in text.
                    try {
                        JSONObject(theText)
                    } catch (e: JSONException) {
                        JOptionPane.showMessageDialog(
                            null,
                            "Error: " + e.message,
                            "JSON parsing error",
                            JOptionPane.ERROR_MESSAGE,
                        )
                        return
                    }
                    JSONObject(theText)

                    val tmpDir: String = writeStaticFiles(theText)

                    val tempAddress = "file://$tmpDir/index.html"
                    val desktop = if (Desktop.isDesktopSupported()) Desktop.getDesktop() else null
                    if (desktop != null && desktop.isSupported(Desktop.Action.BROWSE)) {
                        try {
                            desktop.browse(URI(tempAddress))
                        } catch (error: Exception) {
                            JOptionPane.showMessageDialog(
                                null,
                                "Error: " + error.message,
                                "Error opening browser",
                                JOptionPane.ERROR_MESSAGE,
                            )
                        }
                    }
                }
            },
        )

        // Temp file cleanup when Kindling exits.
        val shutdownThread = Thread {
            val tmpDir: File = Path.of(System.getProperty("java.io.tmpdir"), "gateway-network-diagram").toFile()
            tmpDir.deleteRecursively()
        }

        Runtime.getRuntime().addShutdownHook(shutdownThread)
    }

    companion object {
        private val FAVICON = "favicon.ico"
        private val INDEX_HTML = "index.html"
        private val MAIN_JS = "main.js"
        private val STYLE_CSS = "style.css"
        private val EXTERNAL_DIAGRAM_JS = "external-diagram.js"
        private val PREAMBLE = "window.externalDiagram = "
    }

    private fun parseLines(lines: Sequence<String>, textArea: JTextArea) {
        val builder = StringBuilder()
        for (line in lines) {
            if (line.isBlank()) {
                continue
            } else {
                builder.append(line)
                builder.append("\n")
            }
        }
        textArea.setText(builder.toString())
    }

    private fun writeStaticFiles(jsonText: String): String {
        // Make a temp dir for the static html/js files
        val tmpDir: Path = Path.of(
            System.getProperty("java.io.tmpdir"),
            "gateway-network-diagram",
            UUID.randomUUID().toString(),
        )
        Files.deleteIfExists(tmpDir)
        Files.createDirectories(tmpDir)

        val favicon: URL? = GatewayNetworkViewer::class.java.getResource(FAVICON)
        Files.copy(Path.of(favicon?.toURI()), tmpDir.resolve(FAVICON))

        val indexHtml: URL? = GatewayNetworkViewer::class.java.getResource(INDEX_HTML)
        Files.copy(Path.of(indexHtml?.toURI()), tmpDir.resolve(INDEX_HTML))

        val mainJs: URL? = GatewayNetworkViewer::class.java.getResource(MAIN_JS)
        Files.copy(Path.of(mainJs?.toURI()), tmpDir.resolve(MAIN_JS))

        val styleCss: URL? = GatewayNetworkViewer::class.java.getResource(STYLE_CSS)
        Files.copy(Path.of(styleCss?.toURI()), tmpDir.resolve(STYLE_CSS))

        val externalDiagramFile = tmpDir.resolve(EXTERNAL_DIAGRAM_JS)
        Files.writeString(externalDiagramFile, PREAMBLE + jsonText)
        return tmpDir.toString().replace("\\", "/")
    }
}

object GatewayNetworkTool : Tool {
    override val title = "Gateway Network Diagram"
    override val description = "Gateway network diagram (.json or .txt) files"
    override val icon = FlatSVGIcon("icons/bx-sitemap.svg")
    override val filter = FileFilter(description, listOf("json", "txt"))
    override fun open(path: Path): ToolPanel {
        return GatewayNetworkViewer(path)
    }
}
