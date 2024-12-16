package io.github.inductiveautomation.kindling.localization

import com.formdev.flatlaf.extras.FlatSVGIcon
import io.github.inductiveautomation.kindling.core.ClipboardTool
import io.github.inductiveautomation.kindling.core.Kindling.Preferences.General.HomeLocation
import io.github.inductiveautomation.kindling.core.Kindling.Preferences.UI.Theme
import io.github.inductiveautomation.kindling.core.MultiTool
import io.github.inductiveautomation.kindling.core.ToolPanel
import io.github.inductiveautomation.kindling.localization.TranslationTool.exportZipFileChooser
import io.github.inductiveautomation.kindling.utils.Action
import io.github.inductiveautomation.kindling.utils.Column
import io.github.inductiveautomation.kindling.utils.ColumnList
import io.github.inductiveautomation.kindling.utils.FileFilter
import io.github.inductiveautomation.kindling.utils.FlatScrollPane
import io.github.inductiveautomation.kindling.utils.ReifiedJXTable
import io.github.inductiveautomation.kindling.utils.ReifiedListTableModel
import net.miginfocom.swing.MigLayout
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
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
import javax.swing.JFileChooser
import javax.swing.JPanel
import javax.swing.filechooser.FileNameExtensionFilter
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
    private val bundles: SortedMap<Locale, Properties>,
) : ToolPanel() {
    init {
        val tableRows = buildTableRows(bundles)

        val columns: ColumnList<TableRow> = createColumnList(bundles.keys)

        val table = ReifiedJXTable(ReifiedListTableModel(tableRows, columns))
        add(FlatScrollPane(table), "push, grow")

        val copyAsCsv = Action(description = "Copy As CSV", icon = FlatSVGIcon("icons/bx-clipboard.svg")) {
            val csv = buildString {
                append("Term, ")
                bundles.keys.joinTo(this, separator = ", ", postfix = "\n") {
                    it.toLanguageTag()
                }

                for (row in tableRows) {
                    append(row.term).append(", ")
                    bundles.keys.joinTo(this, separator = ", ", postfix = "\n") {
                        row.translationMap[it].orEmpty()
                    }
                }
            }

            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
            clipboard.setContents(StringSelection(csv), null)
        }

        val exportPropertiesZip = Action(description = "Save as Zip", icon = FlatSVGIcon("icons/bx-save.svg")) {
            exportZipFileChooser.selectedFile = HomeLocation.currentValue.resolve("$name.zip").toFile()
            if (exportZipFileChooser.showSaveDialog(this@TranslationView) == JFileChooser.APPROVE_OPTION) {
                val exportLocation = exportZipFileChooser.selectedFile.toPath()

                val updatedPropertiesMap: MutableMap<Locale, Properties> = mutableMapOf()
                for (row in tableRows) {
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

        val actionPanel = JPanel(MigLayout("flowy, top, ins 0")).apply {
            add(
                JButton(copyAsCsv).apply {
                    hideActionText = true
                },
            )
            add(
                JButton(exportPropertiesZip).apply {
                    hideActionText = true
                },
            )
        }
        add(actionPanel, "east")
    }

    private fun Locale.toIgnitionParseableString() = toString()

    /**
     * Builds a list of [TableRow] objects from the given bundles.
     */
    private fun buildTableRows(bundles: Map<Locale, Properties>): List<TableRow> {
        val mapByTerm = mutableMapOf<String, MutableMap<Locale, String?>>()

        for ((locale, properties) in bundles) {
            for ((term, translation) in properties.entries) {
                val rowMap = mapByTerm.getOrPut(term.toString()) { mutableMapOf() }
                rowMap[locale] = translation.toString().takeUnless(String::isEmpty)
            }
        }

        val tableRows = mapByTerm.map { (term, translationMap) ->
            TableRow(term, translationMap)
        }
        return tableRows
    }

    private fun createColumnList(locales: Collection<Locale>) = object : ColumnList<TableRow>() {
        @Suppress("unused")
        val Term by column { it.term }

        init {
            for (locale in locales) {
                add(
                    Column(
                        locale.toLanguageTag(),
                        getValue = { it.translationMap[locale] },
                        setValue = { value ->
                            translationMap[locale] = value
                        },
                        columnCustomization = {
                            toolTipText = locale.displayName
                        },
                    ),
                )
            }
        }
    }

    override val icon: Icon = TranslationTool.icon
}

data object TranslationTool : MultiTool, ClipboardTool {
    override fun open(paths: List<Path>): ToolPanel {
        val bundles = parseToBundles(paths)
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
    override val description: String = ""
    override val icon: FlatSVGIcon = FlatSVGIcon("icons/bx-globe.svg")
    override val filter: FileFilter = FileFilter(description, "properties", "xml")
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
