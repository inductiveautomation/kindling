package io.github.inductiveautomation.kindling.core

import com.formdev.flatlaf.extras.FlatSVGIcon
import io.github.inductiveautomation.kindling.alarm.AlarmViewer
import io.github.inductiveautomation.kindling.cache.CacheViewer
import io.github.inductiveautomation.kindling.gatewaynetwork.GatewayNetworkTool
import io.github.inductiveautomation.kindling.idb.IdbViewer
import io.github.inductiveautomation.kindling.log.LogViewer
import io.github.inductiveautomation.kindling.thread.MultiThreadViewer
import io.github.inductiveautomation.kindling.utils.FileFilter
import io.github.inductiveautomation.kindling.utils.loadService
import io.github.inductiveautomation.kindling.xml.XmlTool
import io.github.inductiveautomation.kindling.zip.ZipViewer
import java.io.File
import java.nio.file.Path

interface Tool : KindlingSerializable {
    /**
     * A display title for this tool, such as on 'Open' buttons.
     */
    val title: String

    /**
     * A description for the _type_ of files supported by this tool, suitable for display in a file chooser UI.
     */
    val description: String
    val icon: FlatSVGIcon

    /**
     * True if this tool cares about the text encoding of the input file(s).
     */
    val respectsEncoding: Boolean
        get() = false

    /**
     * True if the file chooser UI should show hidden files when this tool is active.
     * Important for files with a leading ., which will be hidden by default on *nix OSes.
     */
    val requiresHiddenFiles: Boolean
        get() = false

    fun open(path: Path): ToolPanel

    val filter: FileFilter

    companion object {
        val tools: List<Tool> by lazy {
            buildList {
                add(ZipViewer)
                add(MultiThreadViewer)
                add(LogViewer)
                add(IdbViewer)
                add(CacheViewer)
                add(GatewayNetworkTool)
                add(AlarmViewer)
                add(XmlTool)
                addAll(loadService<Tool>())
            }
        }

        val sortedByTitle: List<Tool> by lazy {
            tools.sortedBy { it.title }
        }

        val byFilter: Map<FileFilter, Tool> by lazy {
            tools.associateBy(Tool::filter)
        }

        fun find(path: Path): Tool? = tools.find { tool ->
            tool.filter.accept(path)
        }

        fun find(file: File): Tool? = find(file.toPath())

        operator fun get(file: File): Tool = get(file.toPath())

        operator fun get(path: Path): Tool = checkNotNull(find(path)) { "No tool found for $path" }
    }
}

/**
 * Extension interface allowing a tool to support opening multiple files in one operation.
 */
interface MultiTool : Tool {
    fun open(paths: List<Path>): ToolPanel

    override fun open(path: Path): ToolPanel = open(listOf(path))
}

/**
 * Extension interface allowing a tool to read data directly from the system clipboard as a string.
 */
interface ClipboardTool : Tool {
    fun open(data: String): ToolPanel
}

/**
 * Should be thrown during initialization/construction of a tool to return a user-friendly error to the end user.
 */
class ToolOpeningException(message: String, cause: Throwable? = null) : Exception(message, cause)
