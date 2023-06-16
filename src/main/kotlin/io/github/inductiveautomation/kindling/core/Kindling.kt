package io.github.inductiveautomation.kindling.core

import com.formdev.flatlaf.FlatDarkLaf
import com.formdev.flatlaf.FlatLaf
import com.formdev.flatlaf.FlatLightLaf
import com.formdev.flatlaf.extras.FlatAnimatedLafChange
import com.formdev.flatlaf.intellijthemes.FlatAllIJThemes
import com.formdev.flatlaf.themes.FlatMacDarkLaf
import com.formdev.flatlaf.themes.FlatMacLightLaf
import com.formdev.flatlaf.util.SystemInfo
import io.github.inductiveautomation.kindling.utils.configureCellRenderer
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import kotlinx.serialization.serializer
import java.awt.Image
import java.awt.Toolkit
import java.awt.event.ItemEvent
import java.io.File
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JSpinner
import javax.swing.SpinnerNumberModel
import javax.swing.UIManager
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.div
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream

object Kindling {
    val homeLocation: File = Path(System.getProperty("user.home"), "Downloads").toFile()

    val frameIcon: Image = Toolkit.getDefaultToolkit().getImage(this::class.java.getResource("/icons/kindling.png"))

    fun initTheme() {
        theme.currentValue.apply(false)
    }

    private val cacheLocation = Path(System.getProperty("user.home"), ".kindling").also {
        it.createDirectories()
    }

    private val preferencesPath = cacheLocation / "session.json"

    private val preferences: MutableMap<String, JsonElement> = try {
        @OptIn(ExperimentalSerializationApi::class)
        preferencesPath.inputStream().use(Json::decodeFromStream)
    } catch (e: Exception) { // Fallback to default session.
        mutableMapOf()
    }

    private inline fun <reified T> MutableMap<String, Theme>.putLaf(name: String, isDark: Boolean = false) {
        put(
            name,
            Theme(
                name,
                isDark,
                T::class.java.name,
                if (isDark) "dark.xml" else "idea.xml",
            ),
        )
    }

    val themes = buildMap {
        putLaf<FlatLightLaf>(FlatLightLaf.NAME)
        putLaf<FlatMacLightLaf>(FlatMacLightLaf.NAME)
        putLaf<FlatDarkLaf>(FlatDarkLaf.NAME, isDark = true)
        putLaf<FlatMacDarkLaf>(FlatMacDarkLaf.NAME, isDark = true)

        for (info in FlatAllIJThemes.INFOS) {
            put(
                info.name,
                Theme(
                    name = info.name,
                    lookAndFeelClassname = info.className,
                    isDark = info.isDark,
                    rSyntaxThemeName = if (info.isDark) "dark.xml" else "idea.xml",
                ),
            )
        }
    }

    private val _properties: Map<PropertyCategory, MutableMap<String, Property<*>>> = PropertyCategory.values().associateWith { mutableMapOf() }
    val properties: Map<PropertyCategory, MutableMap<String, Property<*>>> = _properties

    val theme: Property<Theme> = persistentProperty(
        category = PropertyCategory.UI,
        name = "Theme",
        description = "UI color scheme.",
        default = themes.getValue(if (SystemInfo.isMacOS) FlatMacLightLaf.NAME else FlatLightLaf.NAME),
        serializer = Theme.ThemeSerializer,
        editor = {
            ThemeSelectionDropdown().apply {
                addActionListener {
                    currentValue = selectedItem
                }
            }
        },
    )

    val uiScaleFactor: Property<Double> = persistentProperty(
        category = PropertyCategory.UI,
        name = "UI Scale Factor",
        description = "Proportion to scale the UI. Requires restart.",
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

    val showFullLoggerNames = persistentProperty(
        category = PropertyCategory.General,
        name = "Logger Names",
        description = null,
        default = false,
        editor = {
            JCheckBox("Show full logger names by default on newly created tool tabs").apply {
                isSelected = currentValue
                addItemListener { e ->
                    currentValue = e.stateChange == ItemEvent.SELECTED
                }
            }
        },
    )

    init {
        theme.addChangeListener { newValue ->
            newValue.apply(true)
        }
    }

    enum class PropertyCategory {
        General,
        UI,
    }

    @Suppress("UNCHECKED_CAST")
    private inline fun <reified T : Any> persistentProperty(
        category: PropertyCategory,
        name: String,
        description: String?,
        default: T,
        serializer: KSerializer<T> = serializer<T>(),
        noinline editor: Property<T>.() -> JComponent,
    ): Property<T> = properties.getValue(category).getOrPut(name) {
        object : Property<T>(
            name = name,
            description = description,
            initial = preferences[name]?.let { Json.decodeFromJsonElement(serializer, it) }
                ?: default,
            setter = { value ->
                val newValue = Json.encodeToJsonElement(serializer, value)
                preferences[name] = newValue
                @OptIn(ExperimentalSerializationApi::class)
                preferencesPath.outputStream().use {
                    Json.encodeToStream<MutableMap<String, JsonElement>>(preferences, it)
                }
            },
        ) {
            override fun createEditor(): JComponent = editor.invoke(this)
        }
    } as Property<T>

    private fun Theme.apply(animate: Boolean) {
        try {
            if (animate) {
                FlatAnimatedLafChange.showSnapshot()
            }
            UIManager.setLookAndFeel(lookAndFeelClassname)
            FlatLaf.updateUI()
        } finally {
            // Will no-op if not animated
            FlatAnimatedLafChange.hideSnapshotWithAnimation()
        }
    }
}

abstract class Property<T : Any>(
    val name: String,
    val description: String?,
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

    fun addChangeListener(listener: (T) -> Unit) {
        listeners.add(listener)
    }

    abstract fun createEditor(): JComponent
}

private val themeComparator = compareBy<Theme> { it.isDark } then compareBy { it.name }

class ThemeSelectionDropdown : JComboBox<Theme>(Kindling.themes.values.sortedWith(themeComparator).toTypedArray()) {
    init {
        selectedItem = Kindling.theme.currentValue

        configureCellRenderer { _, value, _, _, _ ->
            text = value?.name
            val fg = UIManager.getColor("ComboBox.foreground")
            val bg = UIManager.getColor("ComboBox.background")

            if (Kindling.theme.currentValue.isDark != value?.isDark) {
                foreground = bg
                background = fg
            } else {
                foreground = fg
                background = bg
            }
        }
    }

    override fun getSelectedItem(): Theme = super.getSelectedItem() as Theme
}
