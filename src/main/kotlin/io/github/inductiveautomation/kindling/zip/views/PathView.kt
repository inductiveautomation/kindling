package io.github.inductiveautomation.kindling.zip.views

import io.github.inductiveautomation.kindling.utils.FloatableComponent
import io.github.inductiveautomation.kindling.utils.PopupMenuCustomizer
import net.miginfocom.swing.MigLayout
import java.nio.file.Path
import java.nio.file.spi.FileSystemProvider
import javax.swing.JPanel
import javax.swing.JPopupMenu
import kotlin.io.path.name

sealed class PathView(constraints: String) : JPanel(MigLayout(constraints)), FloatableComponent, PopupMenuCustomizer {
    abstract val paths: List<Path>
    abstract val provider: FileSystemProvider
    open val closable: Boolean = true

    override fun customizePopupMenu(menu: JPopupMenu) = Unit
}

abstract class SinglePathView(constraints: String = "ins 6, fill") : PathView(constraints) {
    protected abstract val path: Path

    override val paths: List<Path> by lazy { listOf(path) }
    override val tabName by lazy { path.name }
    override val tabTooltip by lazy { path.toString().substring(1) }

    override fun toString(): String = "${this::class.simpleName}(path=$path)"
}
