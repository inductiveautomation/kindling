package io.github.inductiveautomation.kindling.core

import com.formdev.flatlaf.FlatLightLaf
import com.formdev.flatlaf.themes.FlatMacLightLaf
import com.formdev.flatlaf.util.SystemInfo
import com.github.weisj.jsvg.parser.SVGLoader
import io.github.inductiveautomation.kindling.core.Preference.Companion.PreferenceCheckbox
import io.github.inductiveautomation.kindling.core.Preference.Companion.preference
import io.github.inductiveautomation.kindling.utils.ACTION_ICON_SCALE_FACTOR
import io.github.inductiveautomation.kindling.utils.CharsetSerializer
import io.github.inductiveautomation.kindling.utils.DocumentAdapter
import io.github.inductiveautomation.kindling.utils.PathSerializer
import io.github.inductiveautomation.kindling.utils.PathSerializer.serializedForm
import io.github.inductiveautomation.kindling.utils.ThemeSerializer
import io.github.inductiveautomation.kindling.utils.ToolSerializer
import io.github.inductiveautomation.kindling.utils.configureCellRenderer
import io.github.inductiveautomation.kindling.utils.debounce
import io.github.inductiveautomation.kindling.utils.render
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import net.miginfocom.swing.MigLayout
import org.jdesktop.swingx.JXTextField
import java.awt.Image
import java.awt.event.ItemEvent
import java.net.URI
import java.nio.charset.Charset
import java.nio.file.Path
import java.util.Vector
import javax.swing.DefaultListModel
import javax.swing.DefaultListSelectionModel
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JSpinner
import javax.swing.ListSelectionModel
import javax.swing.SpinnerNumberModel
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream
import kotlin.time.Duration.Companion.seconds
import io.github.inductiveautomation.kindling.core.Theme.Companion as KindlingTheme

data object Kindling {
    val logo = SVGLoader().run {
        val svgUrl = checkNotNull(Kindling::class.java.getResource("/logo.svg")) { "Unable to load logo SVG" }
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
                description = "The default path to start looking for files.",
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
                default = Tool.tools.first(),
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
                    PreferenceCheckbox("Enable table highlighting by default for multiple log files.")
                },
            )

            val DefaultTools = preference(
                name = "Default Tools for Extensions",
                description = "Configure which tool to prioritize when dragging a file of the given extension into Kindling.",
                default = emptyMap(),
                serializer = MapSerializer(String.serializer(), ToolSerializer),
                editor = {
                    val data = Tool.byExtension.filterValues { it.size > 1 }

                    val clearButton = JButton("Clear")
                    val extCombo = JComboBox(data.keys.toTypedArray()).apply {
                        selectedIndex = -1

                        configureCellRenderer { _, value, _, _, _ ->
                            text = value ?: "Select an extension"
                        }
                    }

                    val toolList = JList<Tool>().apply {
                        visibleRowCount = 5
                        selectionMode = ListSelectionModel.SINGLE_SELECTION
                        addListSelectionListener {
                            val list = it.source as JList<*>
                            val index = list.selectedIndex

                            val v = currentValue.toMutableMap()

                            if (index >= 0) {
                                val tool = list.model.getElementAt(index) as Tool
                                v[extCombo.selectedItem as String] = tool
                            } else {
                                v.remove(extCombo.selectedItem as String)
                            }

                            currentValue = v
                        }
                    }

                    extCombo.addItemListener {
                        if (it.stateChange == ItemEvent.SELECTED) {
                            val ext = extCombo.selectedItem as String
                            val newModel = DefaultListModel<Tool>().apply {
                                addAll(data[ext].orEmpty())
                            }

                            toolList.model = newModel

                            toolList.selectedIndex = newModel.indexOf(currentValue[extCombo.selectedItem])
                        }
                    }

                    clearButton.addActionListener {
                        if (extCombo.selectedIndex > -1 && toolList.selectedIndex > -1) {
                            (toolList.selectionModel as DefaultListSelectionModel).clearSelection()
                        }
                    }

                    JPanel(MigLayout("fill, ins 0")).apply {
                        add(extCombo, "pushx, growx")
                        add(clearButton, "gapleft 5, wrap")
                        add(toolList, "growx, gaptop 5")
                    }
                },
            )

            override val displayName: String = "General"
            override val serialKey: String = "general"
            override val preferences: List<Preference<*>> = listOf(
                HomeLocation,
                DefaultTool,
                ShowFullLoggerNames,
                ShowLogTree,
                UseHyperlinks,
                HighlightByDefault,
                DefaultTools,
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

        private val preferencesPath: Path = Path(System.getProperty("user.home"), ".kindling").also {
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
        } catch (e: Exception) {
            // Fallback to empty; defaults will be read and serialized if modified
            mutableMapOf()
        }

        operator fun <T : Any> get(category: PreferenceCategory, preference: Preference<T>): T? = internalState.getOrPut(category.serialKey) { mutableMapOf() }[preference.serialKey]?.let { currentValue ->
            preferencesJson.decodeFromJsonElement(preference.serializer, currentValue)
        }

        operator fun <T : Any> set(category: PreferenceCategory, preference: Preference<T>, value: T) {
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
