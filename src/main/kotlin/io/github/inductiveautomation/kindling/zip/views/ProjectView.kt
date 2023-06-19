package io.github.inductiveautomation.kindling.zip.views

import com.formdev.flatlaf.extras.FlatSVGIcon
import io.github.inductiveautomation.kindling.core.Kindling.General.HomeLocation
import io.github.inductiveautomation.kindling.core.Kindling.UI.Theme
import java.nio.file.FileVisitResult
import java.nio.file.Path
import java.nio.file.spi.FileSystemProvider
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.swing.JButton
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.div
import kotlin.io.path.name
import kotlin.io.path.outputStream
import kotlin.io.path.readBytes
import kotlin.io.path.visitFileTree

@OptIn(ExperimentalPathApi::class)
class ProjectView(override val provider: FileSystemProvider, override val path: Path) : SinglePathView() {
    private val exportButton = JButton("Export Project")

    init {
        exportButton.addActionListener {
            exportZipFileChooser.selectedFile = HomeLocation.currentValue.resolve("${path.name}.zip").toFile()
            if (exportZipFileChooser.showSaveDialog(this@ProjectView) == JFileChooser.APPROVE_OPTION) {
                val exportLocation = exportZipFileChooser.selectedFile.toPath()

                ZipOutputStream(exportLocation.outputStream()).use { zos ->
                    path.visitFileTree {
                        onVisitFile { file, _ ->
                            zos.run {
                                putNextEntry(ZipEntry(path.relativize(file).toString()))
                                write(file.readBytes())
                                closeEntry()
                                FileVisitResult.CONTINUE
                            }
                        }
                    }
                }
            }
        }

        add(exportButton, "north")
        add(TextFileView(provider, path / "project.json"), "push, grow")
    }

    override val icon: FlatSVGIcon = FlatSVGIcon("icons/bx-box.svg").derive(16, 16)

    companion object {
        val exportZipFileChooser = JFileChooser(HomeLocation.currentValue.toFile()).apply {
            isMultiSelectionEnabled = false
            isAcceptAllFileFilterUsed = false
            fileSelectionMode = JFileChooser.FILES_ONLY
            fileFilter = FileNameExtensionFilter("ZIP Files", "zip")

            Theme.addChangeListener {
                updateUI()
            }
        }

        fun isProjectDirectory(path: Path) = path.parent?.name == "projects"
    }
}
