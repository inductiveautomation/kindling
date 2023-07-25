package io.github.inductiveautomation.kindling.core

import com.formdev.flatlaf.extras.FlatSVGIcon
import io.github.inductiveautomation.kindling.cache.CacheViewer
import io.github.inductiveautomation.kindling.idb.IdbViewer
import io.github.inductiveautomation.kindling.log.LogViewer
import io.github.inductiveautomation.kindling.thread.MultiThreadViewer
import io.github.inductiveautomation.kindling.utils.FileExtensionFilter
import io.github.inductiveautomation.kindling.utils.loadService
import io.github.inductiveautomation.kindling.zip.ZipViewer
import java.io.File
import java.nio.file.Path
import javax.swing.filechooser.FileFilter

interface Tool {
    val title: String
    val description: String
    val icon: FlatSVGIcon
    val extensions: List<String>
    val respectsEncoding: Boolean
        get() = false

    fun open(path: Path): ToolPanel

    val filter: FileExtensionFilter
        get() = FileExtensionFilter(description, extensions)

    companion object {
        val tools: List<Tool> by lazy {
            listOf(
                ZipViewer,
                MultiThreadViewer,
                LogViewer,
                IdbViewer,
                CacheViewer,
            ) + loadService<Tool>().sortedBy { it.title }
        }

        val byFilter: Map<FileFilter, Tool> by lazy {
            tools.associateBy(Tool::filter)
        }

        val byTitle: Map<String, Tool> by lazy {
            tools.associateBy(Tool::title)
        }

        val byExtension by lazy {
            buildMap {
                for (tool in tools) {
                    for (extension in tool.extensions) {
                        put(extension, tool)
                    }
                }
            }
        }

        operator fun get(file: File): Tool {
            return checkNotNull(
                tools.find { tool ->
                    tool.filter.accept(file)
                },
            ) { "No tool found for $file" }
        }
    }
}

interface MultiTool : Tool {
    fun open(paths: List<Path>): ToolPanel

    override fun open(path: Path): ToolPanel = open(listOf(path))
}

interface ClipboardTool : Tool {
    fun open(data: String): ToolPanel
}

class ToolOpeningException(message: String, cause: Throwable? = null) : Exception(message, cause)
