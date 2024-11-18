package io.github.inductiveautomation.kindling.localization

import com.formdev.flatlaf.extras.FlatSVGIcon
import io.github.inductiveautomation.kindling.core.ClipboardTool
import io.github.inductiveautomation.kindling.core.MultiTool
import io.github.inductiveautomation.kindling.core.ToolPanel
import io.github.inductiveautomation.kindling.utils.ColumnList
import io.github.inductiveautomation.kindling.utils.FileFilter
import io.github.inductiveautomation.kindling.utils.FlatScrollPane
import io.github.inductiveautomation.kindling.utils.ReifiedJXTable
import io.github.inductiveautomation.kindling.utils.ReifiedLabelProvider.Companion.setDefaultRenderer
import io.github.inductiveautomation.kindling.utils.ReifiedListTableModel
import java.io.InputStream
import java.nio.file.Path
import java.util.Locale
import java.util.Properties
import javax.swing.Icon
import kotlin.io.path.extension
import kotlin.io.path.inputStream
import kotlin.io.path.name
import kotlin.io.path.nameWithoutExtension

private data class TableRow(
    val locale: Locale,
    val term: String,
    var translation: String?,
)

private data object TableColumns : ColumnList<TableRow>() {
    val Locale by column { it.locale }
    val Term by column { it.term }
    val Translation by column(
        setValue = { newValue ->
            translation = newValue
        },
        value = { it.translation },
    )
}

class TranslationView(paths: List<Path>) : ToolPanel() {
    init {
        name = paths.first().nameWithoutExtension.substringBefore('_')
        toolTipText = paths.joinToString(separator = "\n") { it.name }

        // look underneath our path for any properties or XML files
        // if we find any, extract a locale from the filename
        val localesToPaths = paths.mapNotNull { path ->
            val parts = path.nameWithoutExtension.split('_').drop(1)
            if (parts.isEmpty()) return@mapNotNull null
            val builder = Locale.Builder()
            builder.setLanguage(parts[0])
            if (parts.size >= 2) {
                builder.setRegion(parts[1])
            }
            if (parts.size >= 3) {
                builder.setVariant(parts[2])
            }
            builder.build() to path
        }

        // for each successful locale parse, create a bundle-like representation
        // add the file's data to the bundle
        val bundles = localesToPaths.associate { (locale, path) ->
            val propBundle = Properties()
            val loader: Properties.(InputStream) -> Unit = when (path.extension.lowercase()) {
                "properties" -> Properties::load
                "xml" -> Properties::loadFromXML
                else -> throw IllegalStateException()
            }
            path.inputStream().use { file ->
                loader.invoke(propBundle, file)
            }
            locale to propBundle
        }

        val tableRows = bundles.flatMap { (locale, properties) ->
            properties.map { (term, translation) ->
                TableRow(locale, term.toString(), translation.toString())
            }
        }

        // display using a flat table for now
        val table = ReifiedJXTable(ReifiedListTableModel(tableRows, TableColumns))
        table.setDefaultRenderer<Locale>(
            getText = { it?.toLanguageTag() },
        )
        add(FlatScrollPane(table), "push, grow")
    }

    override val icon: Icon = TranslationTool.icon
}

data object TranslationTool : MultiTool, ClipboardTool {
    override fun open(paths: List<Path>): ToolPanel = TranslationView(paths)

    override fun open(data: String): ToolPanel {
        TODO("Not yet implemented")
    }

    override val title: String = "Translation Bundle"
    override val description: String = ""
    override val icon: FlatSVGIcon = FlatSVGIcon("error") // TODO
    override val filter: FileFilter = FileFilter(description, "properties", "xml")
    override val serialKey: String = "bundle-view"
}
