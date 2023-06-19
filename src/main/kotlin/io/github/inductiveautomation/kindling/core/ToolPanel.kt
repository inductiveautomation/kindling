package io.github.inductiveautomation.kindling.core

import io.github.inductiveautomation.kindling.core.Kindling.General.HomeLocation
import io.github.inductiveautomation.kindling.core.Kindling.UI.Theme
import io.github.inductiveautomation.kindling.utils.Action
import io.github.inductiveautomation.kindling.utils.FileExtensionFilter
import io.github.inductiveautomation.kindling.utils.FloatableComponent
import io.github.inductiveautomation.kindling.utils.PopupMenuCustomizer
import io.github.inductiveautomation.kindling.utils.Properties
import io.github.inductiveautomation.kindling.utils.exportToCSV
import io.github.inductiveautomation.kindling.utils.exportToXLSX
import net.miginfocom.swing.MigLayout
import java.io.File
import javax.swing.Icon
import javax.swing.JFileChooser
import javax.swing.JMenu
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.filechooser.FileFilter
import javax.swing.table.TableModel

abstract class ToolPanel(
    layoutConstraints: String = "ins 6, fill, hidemode 3",
) : JPanel(MigLayout(layoutConstraints)), FloatableComponent, PopupMenuCustomizer {
    abstract override val icon: Icon?
    override val tabName: String get() = name
    override val tabTooltip: String get() = toolTipText

    override fun customizePopupMenu(menu: JPopupMenu) = Unit

    protected fun exportMenu(defaultFileName: String = "", modelSupplier: () -> TableModel): JMenu =
        JMenu("Export").apply {
            for (format in ExportFormat.values()) {
                add(
                    Action("Export as ${format.extension.uppercase()}") {
                        exportFileChooser.apply {
                            selectedFile = File(defaultFileName)
                            resetChoosableFileFilters()
                            fileFilter = format.fileFilter
                            if (showSaveDialog(this@ToolPanel) == JFileChooser.APPROVE_OPTION) {
                                val selectedFile =
                                    if (selectedFile.absolutePath.endsWith(format.extension)) {
                                        selectedFile
                                    } else {
                                        File(selectedFile.absolutePath + ".${format.extension}")
                                    }
                                format.action.invoke(modelSupplier(), selectedFile)
                            }
                        }
                    },
                )
            }
        }

    companion object {
        val exportFileChooser = JFileChooser(HomeLocation.currentValue.toFile()).apply {
            isMultiSelectionEnabled = false
            isAcceptAllFileFilterUsed = false
            fileView = CustomIconView()

            Theme.addChangeListener {
                updateUI()
            }
        }

        @Suppress("ktlint:trailing-comma-on-declaration-site")
        private enum class ExportFormat(
            description: String,
            val extension: String,
            val action: (TableModel, File) -> Unit,
        ) {
            CSV("Comma Separated Values", "csv", TableModel::exportToCSV),
            EXCEL("Excel Workbook", "xlsx", TableModel::exportToXLSX);

            val fileFilter: FileFilter = FileExtensionFilter(description, listOf(extension))
        }

        val classMapsByVersion by lazy {
            val versions = requireNotNull(this::class.java.getResourceAsStream("/javadocs/versions.txt")).reader().readLines()
            versions.associateWith { version ->
                Properties(requireNotNull(this::class.java.getResourceAsStream("/javadocs/$version/links.properties")))
            }
        }
    }
}
