package io.github.inductiveautomation.kindling.utils

import java.io.File
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.isDirectory
import java.io.FileFilter as IoFileFilter
import javax.swing.filechooser.FileFilter as SwingFileFilter

/**
 * A unified abstraction over the [java.io.FileFilter][IoFileFilter] interface and the Swing
 * [filechooser.FileFilter][SwingFileFilter] abstract class.
 */
class FileFilter(
    private val description: String,
    private val predicate: (path: Path) -> Boolean,
) : SwingFileFilter(), IoFileFilter {
    constructor(description: String, vararg extensions: String) : this(
        description,
        { path -> path.extension.lowercase() in extensions },
    )

    fun accept(path: Path): Boolean {
        return path.isDirectory() || predicate(path)
    }

    override fun accept(file: File): Boolean = file.isDirectory() || accept(file.toPath())

    override fun getDescription(): String = description
}
