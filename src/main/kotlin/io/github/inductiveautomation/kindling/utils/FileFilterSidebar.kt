package io.github.inductiveautomation.kindling.utils

import com.formdev.flatlaf.extras.FlatSVGIcon
import com.formdev.flatlaf.util.SystemInfo
import io.github.inductiveautomation.kindling.core.CustomIconView
import io.github.inductiveautomation.kindling.core.FilterPanel
import io.github.inductiveautomation.kindling.core.Kindling
import io.github.inductiveautomation.kindling.core.Kindling.Preferences.General.HomeLocation
import io.github.inductiveautomation.kindling.core.Tool
import io.github.inductiveautomation.kindling.internal.FileTransferHandler
import net.miginfocom.swing.MigLayout
import org.jdesktop.swingx.decorator.ColorHighlighter
import org.jdesktop.swingx.renderer.DefaultTableRenderer
import java.awt.Color
import java.awt.Cursor
import java.awt.Desktop
import java.awt.EventQueue
import java.awt.event.MouseEvent
import java.awt.event.MouseMotionAdapter
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.EventListener
import java.util.IdentityHashMap
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JFileChooser
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.UIManager
import javax.swing.event.TableModelEvent
import javax.swing.event.TableModelListener
import kotlin.io.path.absolutePathString
import kotlin.io.path.div
import kotlin.io.path.name
import kotlin.time.Duration.Companion.milliseconds

/**
 * A Filter Sidebar which automatically manages its filters by setting the models appropriately.
 * The FilterPanel must implement setModelData() in order for it to be responsive to file changes.
 * - Files must correspond to an instance of the `FileFilterableCollection` interface.
 * - `FilterPanel`s added to this sidebar must implement the `FileFilterResponsive` interface
 */
class FileFilterSidebar<T> private constructor(
    initialPanels: List<FilterPanel<T>?>,
    initialFileData: Map<Path, FileFilterableCollection<T>>,
) : FilterSidebar<T>(initialPanels.filterNotNull()), TableModelListener {
    private val filePanel = FileFilterPanel(initialFileData)

    private lateinit var fileChooser: JFileChooser

    private val highlighters = mutableMapOf<FileFilterableCollection<T>, ColorHighlighter>()

    var filterModelsAreAdjusting = false
        private set

    val selectedFiles by filePanel::selectedFiles

    val allData: Map<Path, FileFilterableCollection<T>>
        get() = filePanel.table.model.data.associate { it.path to it.filterableCollection }

    init {
        val initialData = initialFileData.values.flatMap { it.items }
        for (panel in this) {
            if (panel is FileFilterResponsive<*>) {
                @Suppress("unchecked_cast")
                panel as FileFilterResponsive<T>
                panel.setModelData(initialData)
            }
            panel.reset()
        }

        if (initialFileData.size > 1) {
            addTab(
                null,
                filePanel.icon,
                filePanel.component,
                filePanel.formattedTabName,
            )

            filePanel.table.model.addTableModelListener(this)
        }

        attachPopupMenu { event ->
            val tabIndex = indexAtLocation(event.x, event.y)
            if (getComponentAt(tabIndex) !== filePanel.component) return@attachPopupMenu null

            JPopupMenu().apply {
                add(
                    Action("Reset") {
                        filePanel.reset()
                    },
                )
            }
        }
    }

    private var selectedFileIndices = filePanel.table.model.data.mapIndexedNotNull { index, item ->
        if (item.show) index else null
    }

    override fun tableChanged(e: TableModelEvent?) = with(filePanel.table.model) {
        if (e?.column == columns[columns.Show] || e?.type == TableModelEvent.INSERT) {
            update.invoke()
        }
    }

    private val update = debounce(400.milliseconds, EDT_SCOPE) {
        val newSelectedIndices = filePanel.table.model.data.mapIndexedNotNull { index, item ->
            if (item.show) index else null
        }

        if (newSelectedIndices == selectedFileIndices) {
            return@debounce
        } else {
            selectedFileIndices = newSelectedIndices
        }

        filterModelsAreAdjusting = true

        val selectedData = selectedFiles.flatMap { f -> f.items }
        if (selectedData.isNotEmpty()) {
            for (panel in this) {
                @Suppress("unchecked_cast")
                (panel as? FileFilterResponsive<T>)?.setModelData(selectedData)
            }
        }
        filterModelsAreAdjusting = false

        filePanel.updateTabState()

        // Fire the "external" listeners.
        listenerList.getAll<FileFilterChangeListener>().forEach { e -> e.fileFilterChanged() }
    }

    /**
     * Provide a reference to the table which will "subscribe" to highlighter changes and additions.
     *
     * @param table The table which will be highlighting rows based on the current data
     */
    fun registerHighlighters(table: ReifiedJXTable<out ReifiedListTableModel<out T>>) {
        highlighters.clear()

        highlighters.putAll(
            filePanel.table.model.data.associate { (_, file, color, _) ->
                file to ColorHighlighter(color, null) { _, adapter ->
                    val itemAtRow = table.model[adapter.convertRowIndexToModel(adapter.row)]
                    val itemFile = filePanel.filesByFilterItems[itemAtRow]
                    itemFile === file
                }
            },
        )

        if (filePanel.enableHighlightCheckbox.isSelected) {
            filePanel.table.getColumnExt(filePanel.table.model.columns.Color).isVisible = true
            highlighters.values.forEach(table::addHighlighter)
        }

        filePanel.table.model.apply {
            addTableModelListener { tableModelEvent ->
                if (tableModelEvent.column == columns[columns.Color]) {
                    val rowData = data[tableModelEvent.firstRow]

                    table.removeHighlighter(highlighters[rowData.filterableCollection])

                    val newHighlighter = ColorHighlighter(rowData.color, null) { _, adapter ->
                        val logEventAtRow = table.model[adapter.convertRowIndexToModel(adapter.row)]
                        val eventLogFile = filePanel.filesByFilterItems.getOrElse(logEventAtRow) {
                            error("No file for filter Item")
                        }
                        eventLogFile === rowData.filterableCollection
                    }

                    highlighters[rowData.filterableCollection] = newHighlighter
                    table.addHighlighter(newHighlighter)
                }
            }
        }

        filePanel.enableHighlightCheckbox.apply {
            isVisible = true

            addActionListener {
                val checkbox = it.source as JCheckBox
                if (checkbox.isSelected) {
                    filePanel.table.getColumnExt(filePanel.table.model.columns.Color).isVisible = true
                    highlighters.values.forEach(table::addHighlighter)
                } else {
                    filePanel.table.getColumnExt(filePanel.table.model.columns.Color).isVisible = false
                    highlighters.values.forEach(table::removeHighlighter)
                }
            }
        }
    }

    /**
     * Configure file drop functionality for the sidebar.
     *
     * @param configure A Function which is responsible for mapping the dropped list of files
     * to a map of paths to `FileFilterableCollection<T>`. This gives the `ToolPanel` the opportunity
     * to do what it needs with the data before passing it back to the sidebar for filter updating.
     */
    fun configureFileDrop(
        configure: (List<Path>) -> Map<Path, FileFilterableCollection<T>>,
    ) {
        filePanel.dropEnabled = true

        filePanel.fileDropButton.transferHandler = FileTransferHandler { files ->
            try {
                val paths = files.map { it.toPath() }
                val addedEntries = configure(paths) // Might throw exception

                addFileEntries(addedEntries)
            } catch (e: Exception) {
                e.printStackTrace()
                JOptionPane.showMessageDialog(
                    null,
                    "Unable to open file:\n${e.message}",
                    "Error",
                    JOptionPane.ERROR_MESSAGE,
                )
            }
        }

        filePanel.fileDropButton.addActionListener {
            if (!::fileChooser.isInitialized) {
                val firstFile = filePanel.table.model.data.firstOrNull()?.path
                fileChooser = JFileChooser((firstFile ?: HomeLocation.currentValue).toFile()).apply {
                    isMultiSelectionEnabled = true
                    fileView = CustomIconView()
                }

                if (firstFile != null) {
                    fileChooser.fileFilter = Tool[firstFile].filter
                }
            }

            try {
                val newEntries = fileChooser.chooseFiles(null)?.map(File::toPath)?.let {
                    configure(it)
                }

                if (!newEntries.isNullOrEmpty()) addFileEntries(newEntries)
            } catch (e: Exception) {
                JOptionPane.showMessageDialog(
                    null,
                    "Unable to open file:\n${e.message}",
                    "Error",
                    JOptionPane.ERROR_MESSAGE,
                )
            }
        }
    }

    private fun addFileEntries(addedEntries: Map<Path, FileFilterableCollection<T>>) {
        val startIndex = filePanel.table.model.rowCount
        val endIndex = startIndex + addedEntries.size - 1

        // Add new data to the table and to the item map
        filePanel.table.model.data.addAll(
            addedEntries.entries.map { (path, file) ->
                FileFilterConfigItem(
                    path = path,
                    filterableCollection = file,
                    color = UIManager.getColor("Table.background"),
                    show = true,
                )
            },
        )

        filePanel.filesByFilterItems.putAll(
            addedEntries.entries.flatMap { (_, c) -> c.items.map { it to c } },
        )

        filePanel.table.model.fireTableRowsInserted(startIndex, endIndex)
    }

    /**
     * A special listener fired when entire files are filtered in and out or added.
     */
    fun addFileFilterChangeListener(l: FileFilterChangeListener) {
        listenerList.add(l)
    }

    private inner class FileFilterPanel<T>(
        data: Map<Path, FileFilterableCollection<T>>,
    ) : FilterPanel<FileFilterConfigItem<T>>() {

        // A map whose keys are unique by the system default implementations of hashcode()
        // Used for O(n)-ish access to an item's parent collection
        val filesByFilterItems: MutableMap<T, FileFilterableCollection<T>> = run {
            val mappedData = data.flatMap { (_, c) -> c.items.map { it to c } }
            IdentityHashMap<T, FileFilterableCollection<T>>(mappedData.size * 2).apply {
                putAll(mappedData)
            }
        }

        val enableHighlightCheckbox = JCheckBox(
            "Enable Highlight",
            Kindling.Preferences.General.HighlightByDefault.currentValue,
        ).apply {
            horizontalTextPosition = JCheckBox.RIGHT
            isVisible = false
        }

        val table = ReifiedJXTable(
            FileFilterTableModel(
                data.entries.run {
                    val palette = if (Kindling.Preferences.UI.Theme.currentValue.isDark) DARK_COLORS else LIGHT_COLORS

                    mapIndexed { index, (path, filterable) ->
                        FileFilterConfigItem(
                            path = path,
                            filterableCollection = filterable,
                            color = palette.getOrElse(index) {
                                if (Kindling.Preferences.UI.Theme.currentValue.isDark) Color.DARK_GRAY else Color.LIGHT_GRAY
                            },
                            show = true,
                        )
                    }
                }.toMutableList(),
            ),
        ).apply {
            dragEnabled = false
            isColumnControlVisible = false
            selectionModel = NoSelectionModel()

            // Hide Color column until highlighting is configured
            getColumnExt(model.columns.Color).isVisible = false

            highlighters.forEach(::removeHighlighter)

            addMouseMotionListener(
                object : MouseMotionAdapter() {
                    override fun mouseMoved(e: MouseEvent?) {
                        if (e == null) return

                        val colViewIndex = columnAtPoint(e.point)
                        val rowIndex = rowAtPoint(e.point)
                        val colModelIndex = convertColumnIndexToModel(colViewIndex)
                        val col = model.columns.getOrNull(colModelIndex)

                        cursor = when {
                            rowIndex < 0 -> Cursor.getDefaultCursor()
                            col == model.columns.Name -> Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR)
                            col == model.columns.Color -> Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR)
                            else -> Cursor.getDefaultCursor()
                        }
                    }
                },
            )

            attachPopupMenu { e ->
                val rowIndex = rowAtPoint(e.point)

                JPopupMenu().also { menu ->
                    // Open file Location
                    if (rowIndex >= 0) {
                        val desktop = Desktop.getDesktop()

                        if (desktop.isSupported(Desktop.Action.BROWSE_FILE_DIR)) {
                            val fileBrowserName = when {
                                SystemInfo.isMacOS -> "Finder"
                                SystemInfo.isWindows -> "Explorer"
                                else -> "File Browser"
                            }

                            menu.add(
                                Action("Open in $fileBrowserName") {
                                    desktop.browseFileDirectory(model[rowIndex].path.toFile())
                                },
                            )
                        }

                        menu.add(
                            Action("Clear Highlight Color") {
                                model.data[rowIndex].color = null
                                model.fireTableCellUpdated(rowIndex, model.columns[model.columns.Color])
                            },
                        )
                    }
                }
            }
        }

        val fileDropButton = JButton("Drop files here").apply {
            isVisible = false
            putClientProperty("FlatLaf.styleClass", "h2.regular")
        }

        var dropEnabled: Boolean
            get() = fileDropButton.isVisible
            set(value) {
                fileDropButton.isVisible = value
            }

        val selectedFiles: List<FileFilterableCollection<T>>
            get() = table.model.data.filter { it.show }.map { it.filterableCollection }

        override val tabName: String = "Files"
        override val icon = FlatSVGIcon("icons/bx-file.svg")

        override val component: JComponent = JPanel(MigLayout("fill, ins 4, hidemode 3")).apply {
            add(enableHighlightCheckbox, "north")
            add(FlatScrollPane(table), "grow")
            add(fileDropButton, "south, h 200!")
        }

        override fun filter(item: FileFilterConfigItem<T>) = item.show

        override fun isFilterApplied(): Boolean = table.model.data.any { !it.show }

        override fun reset() {
            table.model.data.forEach { it.show = true }
            table.model.fireTableColumnDataChanged(table.model.columns[table.model.columns.Show])
        }

        override fun customizePopupMenu(
            menu: JPopupMenu,
            column: Column<out FileFilterConfigItem<T>, *>,
            event: FileFilterConfigItem<T>,
        ) = Unit
    }

    companion object {
        /**
         * Quiz: Figure out why this doesn't work with `vararg panels: S`
         */
        operator fun <S, T> invoke(
            panels: List<S>,
            fileData: Map<Path, FileFilterableCollection<T>>,
        ): FileFilterSidebar<T> where S : FilterPanel<T>, S : FileFilterResponsive<T> {
            return FileFilterSidebar(panels.toList(), fileData)
        }

        // Can be adjusted for better colors. Default background for first 5 files opened.
        // https://youtrack.jetbrains.com/issue/KT-2780
        private val LIGHT_COLORS: List<Color> = listOf(
            Color(0x80DC8300.toInt(), true),
            Color(0x80d9d200.toInt(), true),
            Color(0x80f5b9b5.toInt(), true),
            Color(0x800081ac.toInt(), true),
            Color(0x80c1b0df.toInt(), true),
        )

        private val DARK_COLORS: List<Color> = listOf(
            Color(0x80b115c5.toInt(), true),
            Color(0x80ee1a5a.toInt(), true),
            Color(0x80006b3b.toInt(), true),
            Color(0x80aa1d0f.toInt(), true),
            Color(0x80004d92.toInt(), true),
        )
    }
}

internal data class FileFilterConfigItem<T>(
    var path: Path,
    val filterableCollection: FileFilterableCollection<T>,
    var color: Color?,
    var show: Boolean,
)

internal class FileFilterTableModel<T>(
    override val data: MutableList<FileFilterConfigItem<T>>,
) : ReifiedListTableModel<FileFilterConfigItem<T>>(data, FileFilterColumns()) {
    override val columns: FileFilterColumns<T> = super.columns as FileFilterColumns<T>

    override fun isCellEditable(rowIndex: Int, columnIndex: Int) = true

    override fun setValueAt(aValue: Any?, rowIndex: Int, columnIndex: Int) {
        when (columns[columnIndex]) {
            columns.Name -> {
                if (aValue !is String) return

                val oldPath = data[rowIndex].path
                if (aValue == oldPath.name) return

                val newPath = oldPath.parent / aValue

                if (
                    JOptionPane.showConfirmDialog(
                        null,
                        "Rename ${oldPath.fileName} to $aValue?",
                    ) == JOptionPane.YES_OPTION
                ) {
                    if (Files.exists(oldPath)) {
                        Files.move(oldPath, newPath)
                        data[rowIndex].path = newPath
                        fireTableCellUpdated(rowIndex, columnIndex)
                    } else {
                        EventQueue.invokeLater {
                            JOptionPane.showMessageDialog(
                                null,
                                "Unable to rename file. Can't find path:\n${oldPath.absolutePathString()}",
                            )
                        }
                        return
                    }
                }
            }
            columns.Color -> {
                data[rowIndex].color = aValue as Color?
                fireTableCellUpdated(rowIndex, columnIndex)
            }
            columns.Show -> {
                data[rowIndex].show = aValue as Boolean
                fireTableCellUpdated(rowIndex, columnIndex)
            }
        }
    }
}

@Suppress("PropertyName")
internal class FileFilterColumns<T> : ColumnList<FileFilterConfigItem<T>>() {
    val Name: Column<FileFilterConfigItem<T>, Path> by column(
        column = {
            isEditable = true
            cellRenderer = DefaultTableRenderer(
                ReifiedLabelProvider<Path>(
                    getText = { it?.name },
                    getTooltip = { it?.absolutePathString() + " (Double-click to rename)" },
                ),
            )
        },
        value = { it.path },
    )

    val Color by column(
        column = {
            isEditable = true
            cellEditor = TableColorCellEditor()
            minWidth = 80

            setCellRenderer { _, value, _, _, _, _ ->
                value as Color?
                JLabel().apply {
                    isOpaque = value != null
                    text = value?.toHexString().orEmpty()
                    background = value
                    this@apply.toolTipText = "Click to change color"
                }
            }
        },
        value = FileFilterConfigItem<*>::color,
    )

    val Show by column(
        column = {
            isEditable = true
            minWidth = 25
            maxWidth = 25

            headerRenderer = TableHeaderCheckbox()
            isSortable = false
        },
        value = FileFilterConfigItem<*>::show,
    )
}

fun interface FileFilterChangeListener : EventListener {
    fun fileFilterChanged()
}

/**
 * `FilterPanel`s should implement this interface in order to be compatible with FileFilterSidebar.
 */
interface FileFilterResponsive<T> {
    fun setModelData(data: List<T>)
}

/**
 * Used by FileFilterSidebar to organize and filter in/out entire files.
 */
interface FileFilterableCollection<T> {
    val items: Collection<T>
}
