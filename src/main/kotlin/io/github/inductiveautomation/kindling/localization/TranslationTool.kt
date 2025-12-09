package io.github.inductiveautomation.kindling.localization

import com.formdev.flatlaf.extras.FlatSVGIcon
import io.github.inductiveautomation.kindling.core.ClipboardTool
import io.github.inductiveautomation.kindling.core.Kindling.Preferences.General.HomeLocation
import io.github.inductiveautomation.kindling.core.Kindling.Preferences.UI.Theme
import io.github.inductiveautomation.kindling.core.MultiTool
import io.github.inductiveautomation.kindling.core.ToolOpeningException
import io.github.inductiveautomation.kindling.core.ToolPanel
import io.github.inductiveautomation.kindling.localization.TranslationTool.exportZipFileChooser
import io.github.inductiveautomation.kindling.utils.Action
import io.github.inductiveautomation.kindling.utils.Column
import io.github.inductiveautomation.kindling.utils.ColumnList
import io.github.inductiveautomation.kindling.utils.FileFilter
import io.github.inductiveautomation.kindling.utils.FlatScrollPane
import io.github.inductiveautomation.kindling.utils.ReifiedJXTable
import io.github.inductiveautomation.kindling.utils.ReifiedTableModel
import io.github.inductiveautomation.kindling.utils.clipboardString
import io.github.inductiveautomation.kindling.utils.configureCellRenderer
import net.miginfocom.swing.MigLayout
import java.awt.BorderLayout
import java.awt.Toolkit
import java.awt.event.KeyEvent
import java.io.InputStream
import java.nio.file.Path
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.Properties
import java.util.SortedMap
import java.util.TreeMap
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JFileChooser
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.KeyStroke
import javax.swing.ListSelectionModel
import javax.swing.filechooser.FileNameExtensionFilter
import javax.swing.table.AbstractTableModel
import kotlin.io.path.absolutePathString
import kotlin.io.path.extension
import kotlin.io.path.inputStream
import kotlin.io.path.name
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.outputStream

private data class TableRow(
    val term: String,
    val translationMap: MutableMap<Locale, String?>,
)

class TranslationView(
    bundles: SortedMap<Locale, Properties>,
) : ToolPanel() {
    private val model = TranslationTableModel(
        data = bundles.toRows().toMutableList(),
        locales = bundles.keys,
    )

    private val table = ReifiedJXTable(model).apply {
        selectionMode = ListSelectionModel.SINGLE_SELECTION
    }

    private val addRow = Action(
        description = "Add Row",
        icon = FlatSVGIcon("icons/bx-plus.svg"),
    ) {
        val input = JOptionPane.showInputDialog(this@TranslationView, "Enter Term")
        if (!input.isNullOrBlank()) {
            val added = model.addTerm(input)
            if (added != -1) {
                table.setRowSelectionInterval(added, added)
                table.requestFocusInWindow()
            }
        }
    }

    private val deleteRow = Action(
        description = "Delete Row",
        icon = FlatSVGIcon("icons/bx-minus.svg"),
        accelerator = KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0),
    ) {
        val selectedRow = table.selectedRow
        if (selectedRow != -1) {
            model.removeTerm(selectedRow)
            val maxRow = selectedRow.coerceAtMost(model.size)
            table.setRowSelectionInterval(maxRow, maxRow)
            table.requestFocusInWindow()
        }
    }

    private val addLocale = Action(
        description = "Add Locale",
        icon = FlatSVGIcon("icons/bx-book-add.svg"),
    ) {
        val locales = buildList {
            add(Locale.ROOT)
            addAll(
                Locale.getAvailableLocales()
                    .filter { it.displayName.isNotEmpty() }
                    .sortedBy { it.displayName },
            )
        }

        val localeComboBox = JComboBox(locales.toTypedArray()).apply {
            configureCellRenderer { _, locale, _, _, _ ->
                text = when (locale) {
                    Locale.ROOT -> "Select Locale"
                    null -> null
                    else -> locale.displayName
                }
            }
            selectedIndex = 0
        }

        val panel = JPanel(BorderLayout(5, 5))
        panel.add(localeComboBox, BorderLayout.CENTER)

        val result = JOptionPane.showOptionDialog(
            this@TranslationView,
            panel,
            "Add Locale",
            JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.PLAIN_MESSAGE,
            null,
            null,
            null,
        )

        if (result == JOptionPane.OK_OPTION) {
            val selectedLocale = localeComboBox.selectedItem as? Locale

            if (selectedLocale != null && selectedLocale != Locale.ROOT) {
                // Call the model's function with the selected Locale
                model.addLocale(selectedLocale)
            }
        }
    }

    private val deleteLocale = Action(
        description = "Remove Locale",
        icon = FlatSVGIcon("icons/bx-book.svg"),
    ) {
        val selectedColumn = table.selectedColumn
        if (selectedColumn > 0) {
            val locale = Locale.forLanguageTag(table.getColumnName(selectedColumn))
            model.removeLocale(locale)
        }
    }

    private val copyAsCsv = Action(
        description = "Copy As CSV",
        icon = FlatSVGIcon("icons/bx-clipboard.svg"),
    ) {
        Toolkit.getDefaultToolkit().clipboardString = model.toString(", ")
    }

    private val copyAsTsv = Action(
        description = "Copy As TSV (Excel)",
        icon = FlatSVGIcon("icons/bx-spreadsheet.svg"),
    ) {
        Toolkit.getDefaultToolkit().clipboardString = model.toString("\t")
    }

    private val exportPropertiesZip = Action(
        description = "Export Zip",
        icon = FlatSVGIcon("icons/bx-save.svg"),
    ) {
        exportZipFileChooser.selectedFile = HomeLocation.currentValue.resolve("${this@TranslationView.name}.zip").toFile()

        if (exportZipFileChooser.showSaveDialog(this@TranslationView) == JFileChooser.APPROVE_OPTION) {
            val exportLocation = exportZipFileChooser.selectedFile.toPath()

            val updatedPropertiesMap: MutableMap<Locale, Properties> = mutableMapOf()
            for (row in model) {
                for ((locale, translation) in row.translationMap) {
                    val props = updatedPropertiesMap.getOrPut(locale) { Properties() }
                    props[row.term] = translation
                }
            }

            ZipOutputStream(exportLocation.outputStream()).use { zos ->
                for ((locale, properties) in updatedPropertiesMap) {
                    zos.putNextEntry(ZipEntry("${locale.toIgnitionParseableString()}.properties"))
                    properties.store(zos, null)
                    zos.closeEntry()
                }
            }
        }
    }

    private val actionPanel = JPanel(MigLayout("flowy, top, ins 4")).apply {
        add(actionButton(addRow))
        add(actionButton(deleteRow), "top, pushy")
        add(actionButton(addLocale))
        add(actionButton(deleteLocale))
        add(actionButton(copyAsCsv))
        add(actionButton(copyAsTsv))
        add(actionButton(exportPropertiesZip))
    }

    init {
        add(FlatScrollPane(table), "push, grow")
        add(actionPanel, "east")
    }

    private fun actionButton(action: Action): JButton = JButton(action).apply {
        hideActionText = true
    }

    private fun Locale.toIgnitionParseableString() = toString()

    /**
     * Builds a list of [TableRow] objects from the given bundles.
     */
    private fun Map<Locale, Properties>.toRows(): List<TableRow> {
        val mapByTerm = mutableMapOf<String, MutableMap<Locale, String?>>()

        for ((locale, properties) in this) {
            for ((term, translation) in properties.entries) {
                val rowMap = mapByTerm.getOrPut(term.toString()) { mutableMapOf() }
                rowMap[locale] = translation.toString().takeUnless(String::isBlank)
            }
        }

        return mapByTerm.map { (term, translationMap) ->
            TableRow(term, translationMap)
        }
    }

    override val icon: Icon = TranslationTool.icon
}

private class TranslationTableModel(
    private val data: MutableList<TableRow>,
    locales: Collection<Locale>,
) : AbstractTableModel(),
    ReifiedTableModel<TableRow>,
    List<TableRow> by data {
    private val index: MutableList<Locale> = mutableListOf()

    override val columns = object : ColumnList<TableRow>() {
        val Term by column { it.term }
    }

    init {
        for ((i, locale) in locales.withIndex()) {
            index.add(locale)

            columns.add(locale.toColumn())
        }
    }

    override fun getRowCount(): Int = data.size
    override fun getColumnCount(): Int = columns.size
    override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean = columnIndex != 0
    override fun getColumnClass(columnIndex: Int): Class<*> = columns[columnIndex].clazz
    override fun getColumnName(column: Int): String = columns[column].header

    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any? = columns[columnIndex].getValue.invoke(data[rowIndex])

    override fun setValueAt(aValue: Any?, rowIndex: Int, columnIndex: Int) {
        if (columnIndex == 0) return
        val locale = index.getOrNull(columnIndex - 1) ?: return
        data[rowIndex].translationMap[locale] = aValue?.toString()
        fireTableCellUpdated(rowIndex, columnIndex)
    }

    fun addTerm(key: String): Int {
        val conflict = data.find { it.term == key }
        if (conflict != null) {
            return -1
        }

        data.add(TableRow(key, mutableMapOf()))
        fireTableRowsInserted(data.lastIndex, data.lastIndex)
        return data.lastIndex
    }

    fun removeTerm(index: Int) {
        data.removeAt(index)
        fireTableRowsDeleted(index, index)
    }

    fun addLocale(locale: Locale): Int {
        if (index.contains(locale)) {
            return -1
        }

        index.add(locale)

        columns.add(locale.toColumn())

        fireTableStructureChanged()
        return index.lastIndex
    }

    fun removeLocale(locale: Locale) {
        val i = index.indexOf(locale)
        if (i == -1) {
            return
        }

        index.removeAt(i)
        columns.removeAt(i + 1)

        for (row in data) {
            row.translationMap.remove(locale)
        }

        fireTableStructureChanged()
    }

    private fun Locale.toColumn(): Column<TableRow, String?> = Column(
        toLanguageTag(),
        getValue = { row -> row.translationMap[this] },
        columnCustomization = {
            title = "$displayName [${toLanguageTag()}]"
            toolTipText = title
        },
    )

    fun toString(delimiter: String): String = buildString {
        append("Term").append(delimiter)
        index.joinTo(this, separator = delimiter, postfix = "\n") {
            it.toLanguageTag()
        }

        for (row in data) {
            append(row.term).append(delimiter)
            index.joinTo(this, separator = delimiter, postfix = "\n") {
                row.translationMap[it].orEmpty()
            }
        }
    }
}

data object TranslationTool : MultiTool, ClipboardTool {
    override fun open(paths: List<Path>): ToolPanel {
        val bundles = parseToBundles(paths)
        if (bundles.isEmpty()) {
            throw ToolOpeningException("No locale bundles found")
        }
        return TranslationView(bundles).apply {
            name = paths.first().name.substringBefore('_')
            toolTipText = paths.joinToString("\n") {
                it.absolutePathString()
            }
        }
    }

    /**
     * Parses the given list of paths into a map of [Locale] to [Properties].
     * If a path is not a valid bundle file, it is skipped.
     */
    private fun parseToBundles(paths: List<Path>): SortedMap<Locale, Properties> {
        val map: SortedMap<Locale, Properties> = TreeMap(compareBy(Locale::toLanguageTag))

        for (path in paths) {
            val parts = path.nameWithoutExtension.split('_').drop(1)
            if (parts.isEmpty()) continue

            val locale = Locale.Builder().apply {
                setLanguage(parts[0])
                if (parts.size >= 2) setRegion(parts[1])
                if (parts.size >= 3) setVariant(parts[2])
            }.build()

            val properties = Properties()
            val loader: Properties.(InputStream) -> Unit = when (path.extension.lowercase()) {
                "properties" -> Properties::load
                "xml" -> Properties::loadFromXML
                else -> throw IllegalStateException()
            }
            path.inputStream().use { file ->
                loader(properties, file)
            }
            map[locale] = properties
        }

        return map
    }

    /**
     * Parses the given string as CSV data into a map of [Locale] to [Properties].
     */
    override fun open(data: String): ToolPanel {
        val map: SortedMap<Locale, Properties> = TreeMap(compareBy(Locale::toLanguageTag))

        val locales = data
            .substringBefore('\n')
            .substringAfter(',')
            .split(',')
            .map(String::trim)
            .map(Locale::forLanguageTag)

        for (line in data.lineSequence().drop(1)) {
            val elements = line.splitToSequence(',').withIndex()
            for ((index, element) in elements) {
                if (index == 0) {
                    continue
                }
                val term = elements.first().value
                val locale = locales[index - 1]
                val properties = map.getOrPut(locale) { Properties() }
                properties[term] = element
            }
        }

        return TranslationView(map).apply {
            name = "Paste at ${LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))}"
            toolTipText = locales.joinToString("\n") { it.displayName }
        }
    }

    override val title: String = "Translation Bundle"
    override val description: String = "Translation Bundle Files (\$locale.properties, \$locale.xml)"
    override val icon: FlatSVGIcon = FlatSVGIcon("icons/bx-globe.svg")
    override val extensions: Array<String> = arrayOf("properties", "xml")
    override val serialKey: String = "bundle-view"

    internal val exportZipFileChooser by lazy {
        JFileChooser(HomeLocation.currentValue.toFile()).apply {
            isMultiSelectionEnabled = false
            isAcceptAllFileFilterUsed = false
            fileSelectionMode = JFileChooser.FILES_ONLY
            fileFilter = FileNameExtensionFilter("ZIP Files", "zip")

            Theme.addChangeListener {
                updateUI()
            }
        }
    }
}
