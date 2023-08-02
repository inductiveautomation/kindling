package io.github.inductiveautomation.kindling.zip.views

import com.formdev.flatlaf.extras.FlatSVGIcon
import io.github.inductiveautomation.kindling.core.Tool
import io.github.inductiveautomation.kindling.core.ToolOpeningException
import io.github.inductiveautomation.kindling.core.ToolPanel
import io.github.inductiveautomation.kindling.utils.transferTo
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.spi.FileSystemProvider
import java.util.zip.ZipException
import javax.swing.JPopupMenu
import kotlin.io.path.extension
import kotlin.io.path.name
import kotlin.io.path.outputStream

class ToolView(
    override val provider: FileSystemProvider,
    override val path: Path,
) : SinglePathView("ins 0, fill") {
    private val toolPanel: ToolPanel

    init {
        val tempFile = Files.createTempFile("kindling", path.name)
        try {
            provider.newInputStream(path) transferTo tempFile.outputStream()
            /* Tool.get() throws exception if tool not found, but this check is already done with isTool() */
            toolPanel = Tool.find(path)?.open(tempFile)
                ?: throw ToolOpeningException("No tool for files of type .${path.extension}")
            add(toolPanel, "push, grow")
        } catch (e: ZipException) {
            throw ToolOpeningException("Unable to open $path .${path.extension}")
        }
    }

    override val icon: FlatSVGIcon = (toolPanel.icon as FlatSVGIcon).derive(16, 16)

    override fun customizePopupMenu(menu: JPopupMenu) = toolPanel.customizePopupMenu(menu)

    companion object {
        fun maybeToolPath(path: Path): Boolean = Tool.find(path) != null

        fun safelyCreate(provider: FileSystemProvider, path: Path): ToolView? {
            return runCatching { ToolView(provider, path) }.getOrNull()
        }
    }
}
