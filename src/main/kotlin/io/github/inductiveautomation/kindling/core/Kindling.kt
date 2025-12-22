package io.github.inductiveautomation.kindling.core

import com.formdev.flatlaf.FlatLightLaf
import com.formdev.flatlaf.themes.FlatMacLightLaf
import com.formdev.flatlaf.util.SystemInfo
import com.github.weisj.jsvg.parser.SVGLoader
import io.github.inductiveautomation.kindling.core.Preference.Companion.PreferenceCheckbox
import io.github.inductiveautomation.kindling.core.Preference.Companion.preference
import io.github.inductiveautomation.kindling.idb.IdbViewer
import io.github.inductiveautomation.kindling.thread.MultiThreadViewer
import io.github.inductiveautomation.kindling.utils.ACTION_ICON_SCALE_FACTOR
import io.github.inductiveautomation.kindling.utils.CharsetSerializer
import io.github.inductiveautomation.kindling.utils.ColumnList
import io.github.inductiveautomation.kindling.utils.DocumentAdapter
import io.github.inductiveautomation.kindling.utils.FlatScrollPane
import io.github.inductiveautomation.kindling.utils.PathSerializer
import io.github.inductiveautomation.kindling.utils.PathSerializer.serializedForm
import io.github.inductiveautomation.kindling.utils.ReifiedJXTable
import io.github.inductiveautomation.kindling.utils.ReifiedLabelProvider.Companion.setDefaultRenderer
import io.github.inductiveautomation.kindling.utils.ReifiedListTableModel
import io.github.inductiveautomation.kindling.utils.ThemeSerializer
import io.github.inductiveautomation.kindling.utils.ToolSerializer
import io.github.inductiveautomation.kindling.utils.ZoneIdSerializer
import io.github.inductiveautomation.kindling.utils.configureCellRenderer
import io.github.inductiveautomation.kindling.utils.debounce
import io.github.inductiveautomation.kindling.utils.render
import io.github.inductiveautomation.kindling.zip.ZipViewer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import org.jdesktop.swingx.JXTextField
import java.awt.Component
import java.awt.Dimension
import java.awt.Image
import java.net.URI
import java.nio.charset.Charset
import java.nio.file.Path
import java.time.ZoneId
import java.util.Vector
import javax.swing.DefaultCellEditor
import javax.swing.DefaultComboBoxModel
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JSpinner
import javax.swing.JTable
import javax.swing.SpinnerNumberModel
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream
import kotlin.time.Duration.Companion.seconds
import io.github.inductiveautomation.kindling.core.Theme.Companion as KindlingTheme

data object Kindling {
    val logo = SVGLoader().run {
        val svgUrl =
            checkNotNull(Kindling::class.java.getResource("/logo.svg")) { "Unable to load logo SVG" }
        checkNotNull(load(svgUrl)) { "Unable to load logo SVG" }
    }

    val frameIcons: List<Image> = listOf(16, 32, 44, 64, 128, 150, 256, 512, 1024).map { dim ->
        logo.render(dim, dim)
    }

    val homepage = URI("https://github.com/inductiveautomation/kindling")
    val forumThread = URI("https://forum.inductiveautomation.com/t/54689")

    data object Preferences {
        data object General : PreferenceCategory {
            val HomeLocation: Preference<Path> = preference(
                name = "Browse Location",
                description = "The default path to start looking for files",
                default = Path(System.getProperty("user.home"), "Downloads"),
                serializer = PathSerializer,
                editor = {
                    JXTextField("The fully qualified location to open by default").apply {
                        text = currentValue.serializedForm

                        document.addDocumentListener(
                            DocumentAdapter {
                                currentValue = PathSerializer.fromString(text)
                            },
                        )
                    }
                },
            )

            val DefaultTool: Preference<Tool> = preference(
                name = "Default Tool",
                description = "The default tool to use when invoking the file selector",
                default = IdbViewer,
                serializer = ToolSerializer,
                editor = {
                    JComboBox(Vector(Tool.sortedByTitle)).apply {
                        selectedItem = currentValue

                        configureCellRenderer { _, value, _, _, _ ->
                            text = value?.title
                            toolTipText = value?.description
                            icon = value?.icon?.derive(ACTION_ICON_SCALE_FACTOR)
                        }

                        addActionListener {
                            currentValue = selectedItem as Tool
                        }
                    }
                },
            )

            val DefaultsByExtension = preference(
                name = "Default Tool By Extension",
                serialKey = "defaultToolByExtension",
                description = "Configure which tool to use when dragging a file with the given extension into Kindling",
                default = mapOf(
                    "zip" to ZipViewer,
                    "json" to MultiThreadViewer,
                ),
                serializer = MapSerializer(String.serializer(), ToolSerializer),
                editor = {
                    createToolPreferenceTable()
                },
            )

            val ChoosableEncodings = arrayOf(
                Charsets.UTF_8,
                Charsets.ISO_8859_1,
                Charsets.US_ASCII,
            )

            val DefaultEncoding: Preference<Charset> = preference(
                name = "Encoding",
                description = "The default encoding to use when loading text files",
                default = if (SystemInfo.isWindows) Charsets.ISO_8859_1 else Charsets.UTF_8,
                serializer = CharsetSerializer,
                editor = {
                    JComboBox(ChoosableEncodings).apply {
                        selectedItem = currentValue

                        configureCellRenderer { _, value, _, _, _ ->
                            text = value?.displayName()
                            toolTipText = value?.displayName()
                        }

                        addActionListener {
                            currentValue = selectedItem as Charset
                        }
                    }
                },
            )

            val ShowFullLoggerNames: Preference<Boolean> = preference(
                name = "Logger Names",
                default = false,
                editor = {
                    PreferenceCheckbox("Always show full logger names in tools")
                },
            )

            val ShowLogTree: Preference<Boolean> = preference(
                name = "Logger Name Format",
                default = false,
                editor = {
                    PreferenceCheckbox("Show logger names as a tree view for system log files")
                },
            )

            val UseHyperlinks: Preference<Boolean> = preference(
                name = "Hyperlinks",
                default = true,
                editor = {
                    PreferenceCheckbox("Enable hyperlinks in stacktraces")
                },
            )

            val HighlightByDefault = preference(
                name = "Highlight",
                default = true,
                editor = {
                    PreferenceCheckbox("Enable table highlighting by default for multiple log files")
                },
            )

            val DefaultTimezone = preference(
                name = "Timezone",
                description = "Timezone to use when displaying timestamps",
                legacyValueProvider = { allPrefs ->
                    allPrefs["logview"]?.get("timezone")?.let {
                        Json.decodeFromJsonElement(ZoneIdSerializer, it)
                    }
                },
                default = ZoneId.systemDefault(),
                serializer = ZoneIdSerializer,
                editor = {
                    val zoneIds = ZoneId.getAvailableZoneIds().filter { id ->
                        id !in ZoneId.SHORT_IDS.keys
                    }.sorted()
                    JComboBox(Vector(zoneIds)).apply {
                        selectedItem = currentValue.id
                        addActionListener {
                            currentValue = ZoneId.of(selectedItem as String)
                        }
                    }
                },
            )

            override val displayName: String = "General"
            override val serialKey: String = "general"
            override val preferences: List<Preference<*>> = listOf(
                HomeLocation,
                DefaultTool,
                DefaultsByExtension,
                ShowFullLoggerNames,
                ShowLogTree,
                UseHyperlinks,
                HighlightByDefault,
                DefaultTimezone,
            )
        }

        data object UI : PreferenceCategory {
            val Theme: Preference<Theme> = preference(
                name = "Theme",
                default = KindlingTheme.themes.getValue(if (SystemInfo.isMacOS) FlatMacLightLaf.NAME else FlatLightLaf.NAME),
                serializer = ThemeSerializer,
                editor = {
                    ThemeSelectionDropdown().apply {
                        addActionListener {
                            currentValue = selectedItem
                        }
                    }
                },
            )

            val ScaleFactor: Preference<Double> = preference(
                name = "Scale Factor",
                description = "Percentage to scale the UI.",
                requiresRestart = true,
                default = 1.0,
                editor = {
                    JSpinner(SpinnerNumberModel(currentValue, 1.0, 2.0, 0.1)).apply {
                        editor = JSpinner.NumberEditor(this, "0%")
                        addChangeListener {
                            currentValue = value as Double
                        }
                    }
                },
            )

            override val displayName: String = "UI"
            override val serialKey: String = "ui"
            override val preferences: List<Preference<*>> = listOf(Theme, ScaleFactor)
        }

        data object Advanced : PreferenceCategory {
            val Debug: Preference<Boolean> = preference(
                name = "Debug Mode",
                default = false,
                editor = {
                    PreferenceCheckbox("Enable debug features")
                },
            )

            val HyperlinkStrategy: Preference<LinkHandlingStrategy> = preference(
                name = "Hyperlink Strategy",
                default = LinkHandlingStrategy.OpenInBrowser,
                serializer = LinkHandlingStrategy.serializer(),
                editor = {
                    JComboBox(Vector(LinkHandlingStrategy.entries)).apply {
                        selectedItem = currentValue

                        configureCellRenderer { _, value, _, _, _ ->
                            text = value?.description
                        }

                        addActionListener {
                            currentValue = selectedItem as LinkHandlingStrategy
                        }
                    }
                },
            )

            override val displayName: String = "Advanced"
            override val serialKey: String = "advanced"
            override val preferences: List<Preference<*>> = listOf(Debug, HyperlinkStrategy)
        }

        private val preferencesPath: Path =
            Path(System.getProperty("user.home"), ".kindling").also {
                it.createDirectories()
            }.resolve("preferences.json")

        private val preferencesJson = Json {
            ignoreUnknownKeys = true
            prettyPrint = true
        }

        val categories: List<PreferenceCategory> = buildList {
            add(General)
            add(UI)
            addAll(Tool.tools.filterIsInstance<PreferenceCategory>())
            // put advanced last
            add(Advanced)
        }

        private val internalState: MutableMap<String, MutableMap<String, JsonElement>> = try {
            // try to deserialize from file
            preferencesPath.inputStream().use { inputStream ->
                @OptIn(ExperimentalSerializationApi::class)
                preferencesJson.decodeFromStream(inputStream)
            }
        } catch (_: Exception) {
            // Fallback to empty; defaults will be read and serialized if modified
            mutableMapOf()
        }

        operator fun <T : Any> get(category: PreferenceCategory, preference: Preference<T>): T? {
            val categoryData = internalState.getOrPut(category.serialKey) { mutableMapOf() }
            return categoryData[preference.serialKey]?.let { currentValue ->
                preferencesJson.decodeFromJsonElement(preference.serializer, currentValue)
            } ?: preference.legacyValueProvider?.invoke(internalState)
        }

        operator fun <T : Any> set(
            category: PreferenceCategory,
            preference: Preference<T>,
            value: T,
        ) {
            internalState.getOrPut(category.serialKey) { mutableMapOf() }[preference.serialKey] =
                preferencesJson.encodeToJsonElement(preference.serializer, value)
            syncToDisk()
        }

        private val preferenceScope = CoroutineScope(Dispatchers.IO)

        // debounced store to disk operation, prevents unnecessarily clashing of file updates
        private val syncToDisk: () -> Unit = debounce(
            waitTime = 2.seconds,
            coroutineScope = preferenceScope,
        ) {
            preferencesPath.outputStream().use { outputStream ->
                @OptIn(ExperimentalSerializationApi::class)
                preferencesJson.encodeToStream(
                    // (deeply) sort keys
                    internalState.mapValues { (_, value) ->
                        value.toSortedMap().toMap()
                    }.toSortedMap().toMap(),
                    outputStream,
                )
            }
        }
    }
}

context(pref: Preference<Map<String, Tool>>)
private fun createToolPreferenceTable(): JComponent {
    val data = Tool.byExtension.filterValues { it.size > 1 }

    data class Row(
        val extension: String,
        var tool: Tool?,
    )

    val rows = data.keys
        .sorted()
        .map { ext -> Row(ext, pref.currentValue[ext]) }

    @Suppress("unused")
    val columns = object : ColumnList<Row>() {
        val Extension by column<String> { it.extension }
        val Tool by column<Tool?> { it.tool }
    }

    val model = object : ReifiedListTableModel<Row>(rows, columns) {
        override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean = columnIndex == columns[columns.Tool]

        override fun setValueAt(value: Any?, rowIndex: Int, columnIndex: Int) {
            if (columnIndex == columns[columns.Tool]) {
                val ext = get(rowIndex).extension
                rows[rowIndex].tool = value as Tool?
                val updated = pref.currentValue.toMutableMap()
                if (rows[rowIndex].tool == null) {
                    updated.remove(ext)
                } else {
                    updated[ext] = get(rowIndex).tool as Tool
                }
                pref.currentValue = updated
                fireTableCellUpdated(rowIndex, columnIndex)
            }
        }
    }

    val table = ReifiedJXTable(model, columns).apply {
        tableHeader = null

        setDefaultRenderer<Tool>(
            getText = { it?.title },
            getIcon = { it?.icon?.derive(ACTION_ICON_SCALE_FACTOR) },
            getTooltip = { it?.description },
        )

        setDefaultEditor(
            Tool::class.java,
            object : DefaultCellEditor(JComboBox<Tool?>()) {
                @Suppress("UNCHECKED_CAST")
                private val combo: JComboBox<Tool?> = editorComponent as JComboBox<Tool?>

                init {
                    combo.isEditable = false
                    combo.configureCellRenderer<Tool?> { _, value, _, _, _ ->
                        text = value?.title ?: "None"
                        icon = value?.icon?.derive(ACTION_ICON_SCALE_FACTOR)
                        toolTipText = value?.description
                    }
                }

                override fun getTableCellEditorComponent(
                    table: JTable?,
                    value: Any?,
                    isSelected: Boolean,
                    row: Int,
                    column: Int,
                ): Component {
                    val ext = model[row].extension
                    val options = buildList {
                        add(null)
                        addAll(data[ext].orEmpty())
                    }
                    combo.model = DefaultComboBoxModel(options.toTypedArray())
                    combo.selectedItem = value as Tool?
                    return combo
                }

                override fun getCellEditorValue(): Any? = combo.selectedItem
            },
        )
    }

    val visibleRows = 4
    table.preferredScrollableViewportSize = Dimension(0, table.rowHeight * visibleRows)

    return FlatScrollPane(table)
}
