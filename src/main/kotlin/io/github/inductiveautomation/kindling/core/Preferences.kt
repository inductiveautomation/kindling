package io.github.inductiveautomation.kindling.core

import io.github.inductiveautomation.kindling.utils.FlatScrollPane
import io.github.inductiveautomation.kindling.utils.StyledLabel
import io.github.inductiveautomation.kindling.utils.jFrame
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import kotlinx.serialization.serializer
import net.miginfocom.swing.MigLayout
import java.awt.BorderLayout
import java.awt.EventQueue
import java.awt.Font
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JFrame
import javax.swing.JPanel
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.div
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream
import kotlin.properties.ReadOnlyProperty

sealed class PreferenceCategory(
    protected val preferences: MutableMap<String, Preference<*>> = mutableMapOf(),
) : Collection<Preference<*>> by preferences.values {
    protected inline fun <reified T : Any> preference(
        name: String? = null,
        description: String? = null,
        requiresRestart: Boolean = false,
        default: T,
        serializer: KSerializer<T> = serializer<T>(),
        noinline editor: Preference<T>.() -> JComponent,
    ): ReadOnlyProperty<PreferenceCategory, Preference<T>> = ReadOnlyProperty { _, property ->
        @Suppress("UNCHECKED_CAST")
        preferences.getOrPut(property.name) {
            object : Preference<T>(
                name = name ?: property.name,
                description = description,
                requiresRestart = requiresRestart,
                initial = runCatching {
                    persistentPreferences[property.name.lowercase()]?.let { preferencesJson.decodeFromJsonElement(serializer, it) }
                }.getOrNull() ?: default,
                setter = { value ->
                    val newValue = preferencesJson.encodeToJsonElement(serializer, value)
                    // add it to the map in memory
                    persistentPreferences[property.name.lowercase()] = newValue

                    // and write it out to disk
                    preferencesPath.outputStream().use { outputStream ->
                        @OptIn(ExperimentalSerializationApi::class)
                        preferencesJson.encodeToStream<MutableMap<String, JsonElement>>(persistentPreferences, outputStream)
                    }
                },
            ) {
                override fun createEditor(): JComponent = editor.invoke(this)
            }
        } as Preference<T>
    }

    companion object {
        private val cacheLocation = Path(System.getProperty("user.home"), ".kindling").also {
            it.createDirectories()
        }

        protected val preferencesPath = cacheLocation / "session.json"

        protected val preferencesJson = Json {
            ignoreUnknownKeys = true
            prettyPrint = true
        }

        protected val persistentPreferences: MutableMap<String, JsonElement> = try {
            preferencesPath.inputStream().use { inputStream ->
                @OptIn(ExperimentalSerializationApi::class)
                preferencesJson.decodeFromStream(inputStream)
            }
        } catch (e: Exception) { // Fallback to default session.
            mutableMapOf()
        }
    }
}

abstract class Preference<T : Any>(
    val name: String,
    val description: String?,
    val requiresRestart: Boolean,
    initial: T,
    private val setter: (T) -> Unit,
) {
    var currentValue: T = initial
        set(value) {
            field = value
            setter(value)
            for (listener in listeners) {
                listener(value)
            }
        }

    private val listeners = mutableListOf<(T) -> Unit>()

    fun addChangeListener(listener: (newValue: T) -> Unit) {
        listeners.add(listener)
    }

    abstract fun createEditor(): JComponent
}

val preferencesFrame by lazy {
    jFrame("Preferences", 800, 600, initiallyVisible = false) {
        defaultCloseOperation = JFrame.HIDE_ON_CLOSE

        Kindling.UI.Theme.addChangeListener {
            // sometimes changing the theme affects the sizing of components and leads to annoying scrollbars, so we'll just resize when that happens
            pack()
        }

        val closeButton = JButton("Close").apply {
            addActionListener {
                this@jFrame.isVisible = false
            }
            EventQueue.invokeLater {
                rootPane.defaultButton = this
            }
        }

        contentPane = JPanel(BorderLayout()).apply {
            add(
                FlatScrollPane(
                    JPanel(MigLayout("ins 10")).apply {
                        for (category in Kindling.preferenceCategories) {
                            val categoryPanel = JPanel(MigLayout("fill, gap 10")).apply {
                                border = BorderFactory.createTitledBorder(category.toString())
                                for (preference in category) {
                                    add(
                                        StyledLabel {
                                            add(preference.name, Font.BOLD)
                                            if (preference.requiresRestart) {
                                                add(" Requires restart", "superscript")
                                            }
                                            if (preference.description != null) {
                                                add("\n")
                                                add(preference.description)
                                            }
                                        },
                                        "grow, wrap, gapy 0",
                                    )
                                    add(preference.createEditor(), "grow, wrap, gapy 0")
                                }
                            }
                            add(categoryPanel, "grow, wrap")
                        }
                    },
                ) {
                    border = null
                },
                BorderLayout.CENTER,
            )
            add(
                JPanel(MigLayout("fill, ins 10")).apply {
                    add(closeButton, "east, gap 10 10 10 10")
                },
                BorderLayout.SOUTH,
            )
        }
        pack()
    }
}
