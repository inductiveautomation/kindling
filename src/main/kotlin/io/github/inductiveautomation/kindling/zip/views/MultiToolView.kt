package io.github.inductiveautomation.kindling.zip.views

import com.formdev.flatlaf.extras.FlatSVGIcon
import io.github.inductiveautomation.kindling.core.MultiTool
import io.github.inductiveautomation.kindling.core.Tool
import io.github.inductiveautomation.kindling.core.ToolPanel
import io.github.inductiveautomation.kindling.utils.transferTo
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.spi.FileSystemProvider
import javax.swing.JPopupMenu
import kotlin.io.path.extension
import kotlin.io.path.name
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.outputStream

class MultiToolView(
    override val provider: FileSystemProvider,
    override val paths: List<Path>,
) : PathView("ins 0, fill") {
    private val multiTool: MultiTool
    private val toolPanel: ToolPanel

    override val tabName by lazy {
        val roots = paths.mapTo(mutableSetOf()) { path ->
            path.nameWithoutExtension.trimEnd { it.isDigit() || it == '-' || it == '.' }
        }
        "[${paths.size}] ${roots.joinToString()}.${paths.first().extension}"
    }
    override val tabTooltip by lazy { paths.joinToString("\n") { it.toString().substring(1) } }

    override fun toString(): String = "MultiToolView(paths=$paths)"

    init {
        val tempFiles = paths.map { path ->
            Files.createTempFile("kindling", path.name).also { tempFile ->
                provider.newInputStream(path) transferTo tempFile.outputStream()
            }
        }

        multiTool = Tool[tempFiles.first()] as MultiTool
        toolPanel = multiTool.open(tempFiles)

        add(toolPanel, "push, grow")
    }

    override val icon: FlatSVGIcon = toolPanel.icon as FlatSVGIcon

    override fun customizePopupMenu(menu: JPopupMenu) = toolPanel.customizePopupMenu(menu)
}
