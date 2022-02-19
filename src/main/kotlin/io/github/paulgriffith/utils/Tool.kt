package io.github.paulgriffith.utils

import com.formdev.flatlaf.extras.FlatSVGIcon
import io.github.paulgriffith.backup.BackupView
import io.github.paulgriffith.cache.CacheView
import io.github.paulgriffith.idb.IdbView
import io.github.paulgriffith.log.LogPanel
import io.github.paulgriffith.thread.ThreadView
import java.io.File
import java.nio.file.Path
import javax.swing.filechooser.FileNameExtensionFilter

class ToolOpeningException(message: String, cause: Throwable) : Exception(message, cause)

sealed interface MultiTool : Tool {
    fun open(paths: List<Path>): ToolPanel
}

sealed interface Tool {
    val title: String
    val description: String
    val icon: FlatSVGIcon
    val extensions: Array<String>

    fun open(path: Path): ToolPanel

    val filter: FileNameExtensionFilter
        get() = FileNameExtensionFilter(description, *extensions)

    companion object {
        operator fun get(file: File): Tool {
            return getOrNull(file) ?: throw IllegalArgumentException("No tool found for $file")
        }

        fun getOrNull(file: File): Tool? {
            return values().firstOrNull { tool ->
                tool.filter.accept(file)
            }
        }

        val byFilter = values().associateBy(Tool::filter)

        fun values(): List<Tool> = listOf(IdbViewer, LogViewer, ThreadViewer, CacheViewer, BackupViewer)
    }

    object IdbViewer : Tool {
        override val title = "Idb File"
        override val description = ".idb (SQLite3) files"
        override val icon = FlatSVGIcon("icons/bx-hdd.svg")
        override val extensions = arrayOf("idb")
        override fun open(path: Path): ToolPanel = IdbView(path)
    }

    object LogViewer : MultiTool {
        override val title = "Wrapper Log"
        override val description = "wrapper.log(.n) files"
        override val icon = FlatSVGIcon("icons/bx-file.svg")
        override val extensions = arrayOf("log", "1", "2", "3", "4", "5")
        override fun open(path: Path): ToolPanel = open(listOf(path))
        override fun open(paths: List<Path>): ToolPanel {
            return LogPanel.LogView(paths)
        }
    }

    object ThreadViewer : Tool {
        override val title = "Thread Dump"
        override val description = "Thread dump .json files"
        override val icon = FlatSVGIcon("icons/bx-chip.svg")
        override val extensions = arrayOf("json")
        override fun open(path: Path): ToolPanel = ThreadView(path)
    }

    object CacheViewer : Tool {
        override val title = "Cache Dump"
        override val description = "S&F Cache ZIP Files"
        override val icon = FlatSVGIcon("icons/bx-data.svg")
        override val extensions = arrayOf("zip")
        override fun open(path: Path): ToolPanel = CacheView(path)
    }

    object BackupViewer : Tool {
        override val title = "Gateway Backup"
        override val description = ".gwbk Files"
        override val icon = FlatSVGIcon("icons/bx-archive.svg")
        override val extensions = arrayOf("gwbk")
        override fun open(path: Path): ToolPanel = BackupView(path)
    }
}
