package io.github.inductiveautomation.kindling.core

import com.formdev.flatlaf.FlatLightLaf
import com.formdev.flatlaf.themes.FlatMacLightLaf
import com.formdev.flatlaf.util.SystemInfo
import com.github.weisj.jsvg.attributes.ViewBox
import com.github.weisj.jsvg.parser.SVGLoader
import io.github.inductiveautomation.kindling.core.Preference.Companion.PreferenceCheckbox
import io.github.inductiveautomation.kindling.core.Preference.Companion.preference
import io.github.inductiveautomation.kindling.utils.CharsetSerializer
import io.github.inductiveautomation.kindling.utils.PathSerializer
import io.github.inductiveautomation.kindling.utils.PathSerializer.serializedForm
import io.github.inductiveautomation.kindling.utils.ThemeSerializer
import io.github.inductiveautomation.kindling.utils.ToolSerializer
import io.github.inductiveautomation.kindling.utils.configureCellRenderer
import io.github.inductiveautomation.kindling.utils.debounce
import io.github.inductiveautomation.kindling.utils.derive
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import org.jdesktop.swingx.JXTextField
import java.awt.Image
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.net.URI
import java.nio.charset.Charset
import java.nio.file.Path
import java.util.Vector
import javax.swing.JComboBox
import javax.swing.JSpinner
import javax.swing.SpinnerNumberModel
import javax.swing.UIManager
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream
import kotlin.time.Duration.Companion.seconds
import io.github.inductiveautomation.kindling.core.Theme.Companion as KindlingTheme

data object Kindling {
    val frameIcons: List<Image> = SVGLoader().run {
        val svgUrl = checkNotNull(Kindling::class.java.getResource("/logo.svg")) { "Unable to load logo SVG" }
        val svgDocument = checkNotNull(load(svgUrl)) { "Unable to load logo SVG" }

        listOf(16, 32, 44, 64, 128, 150, 256, 512, 1024).map { dim ->
            BufferedImage(dim, dim, BufferedImage.TYPE_INT_ARGB).apply {
                val g = createGraphics()
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                svgDocument.render(null, g, ViewBox(dim.toFloat(), dim.toFloat()))
                g.dispose()
            }
        }
    }

    val homepage = URI("https://github.com/inductiveautomation/kindling")
    val forumThread = URI("https://forum.inductiveautomation.com/t/54689")

    const val SECONDARY_ACTION_ICON_SCALE = 0.75F

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
                            object : DocumentListener {
                                fun onChange() {
                                    currentValue = PathSerializer.fromString(text)
                                }

                                override fun insertUpdate(e: DocumentEvent?) = onChange()
                                override fun removeUpdate(e: DocumentEvent?) = onChange()
                                override fun changedUpdate(e: DocumentEvent?) = onChange()
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
                    JComboBox(Vector(Tool.tools)).apply {
                        selectedItem = currentValue

                        configureCellRenderer { _, value, _, selected, focused ->
                            text = value?.title
                            toolTipText = value?.description

                            icon = value?.icon?.derive(0.8f)?.let {
                                if (selected || focused) {
                                    it.derive { UIManager.getColor("Tree.selectionForeground") }
                                } else {
                                    it
                                }
                            }
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

            val UseHyperlinks: Preference<Boolean> = preference(
                name = "Hyperlinks",
                default = true,
                editor = {
                    PreferenceCheckbox("Enable hyperlinks in stacktraces")
                },
            )

            override val displayName: String = "General"
            override val key: String = "general"
            override val preferences: List<Preference<*>> = listOf(HomeLocation, DefaultTool, ShowFullLoggerNames, UseHyperlinks)
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
            override val key: String = "ui"
            override val preferences: List<Preference<*>> = listOf(Theme, ScaleFactor)
        }

        data object Advanced : PreferenceCategory {
            val Debug: Preference<Boolean> = preference(
                name = "Debug Mode",
                description = null,
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
            override val key: String = "advanced"
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

        operator fun <T : Any> get(category: PreferenceCategory, preference: Preference<T>): T? {
            return internalState.getOrPut(category.key) { mutableMapOf() }[preference.key]?.let { currentValue ->
                preferencesJson.decodeFromJsonElement(preference.serializer, currentValue)
            }
        }

        private val preferenceScope = CoroutineScope(Dispatchers.IO)

        operator fun <T : Any> set(category: PreferenceCategory, preference: Preference<T>, value: T) {
            internalState.getOrPut(category.key) { mutableMapOf() }[preference.key] = preferencesJson.encodeToJsonElement(preference.serializer, value)
            syncToDisk()
        }

        // debounced store to disk operation, prevents unnecessarily clashing of file updates
        private val syncToDisk: () -> Unit = debounce(
            waitTime = 2.seconds,
            coroutineScope = preferenceScope,
        ) {
            preferencesPath.outputStream().use { outputStream ->
                @OptIn(ExperimentalSerializationApi::class)
                preferencesJson.encodeToStream(
                    // (deeply) sort keys
                    buildMap {
                        for (category in internalState.keys.sorted()) {
                            put(
                                category,
                                buildMap {
                                    val categoryMap = internalState.getValue(category)
                                    for (preference in categoryMap.keys.sorted()) {
                                        put(preference, categoryMap.getValue(preference))
                                    }
                                },
                            )
                        }
                    },
                    outputStream,
                )
            }
        }
    }
}
