package io.github.inductiveautomation.kindling.core

import com.formdev.flatlaf.FlatDarkLaf
import com.formdev.flatlaf.FlatLaf
import com.formdev.flatlaf.FlatLightLaf
import com.formdev.flatlaf.extras.FlatAnimatedLafChange
import com.formdev.flatlaf.intellijthemes.FlatAllIJThemes
import com.formdev.flatlaf.themes.FlatMacDarkLaf
import com.formdev.flatlaf.themes.FlatMacLightLaf
import com.formdev.flatlaf.util.SystemInfo
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea
import org.jfree.chart.JFreeChart
import java.awt.Image
import java.awt.Toolkit
import java.io.File
import javax.swing.UIManager
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.div
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty
import org.fife.ui.rsyntaxtextarea.Theme as RSyntaxTheme

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

    private val defaultLight = Theme(
        name = "Default Light",
        lookAndFeelClassname = (if (SystemInfo.isMacOS) FlatMacLightLaf::class.java else FlatLightLaf::class.java).name,
        isDark = false,
        rSyntaxThemeName = "idea.xml",
    )
    private val defaultDark = Theme(
        name = "Default Dark",
        lookAndFeelClassname = (if (SystemInfo.isMacOS) FlatMacDarkLaf::class.java else FlatDarkLaf::class.java).name,
        isDark = true,
        rSyntaxThemeName = "dark.xml",
    )

    val themes = buildMap<String, Theme> {
        put(defaultLight.name, defaultLight)
        put(defaultDark.name, defaultDark)

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

    private val properties: MutableMap<KProperty<*>, Property<*>> = mutableMapOf()

    val theme: Property<Theme> by persistentProperty(
        description = "UI Theme",
        serializer = ThemeSerializer,
        default = defaultLight,
    )
    val showFullLoggerNames by persistentProperty(
        description = "Show full logger names by default",
        serializer = Boolean.serializer(),
        default = false,
    )
    val uiScaleFactor by persistentProperty(
        description = "UI Scale Factor",
        serializer = Double.serializer(),
        default = 1.0,
    )

    init {
        theme.addListener { newValue ->
            newValue.apply(true)
        }
    }

    private fun <T : Any> persistentProperty(
        description: String,
        serializer: KSerializer<T>,
        default: T,
    ): ReadOnlyProperty<Kindling, Property<T>> = ReadOnlyProperty { thisRef, property ->
        @Suppress("UNCHECKED_CAST")
        thisRef.properties.getOrPut(property) {
            Property(
                initial = thisRef.preferences[property.name]?.let { Json.decodeFromJsonElement(serializer, it) }
                    ?: default,
                description = description,
                setter = { value ->
                    val newValue = Json.encodeToJsonElement(serializer, value)
                    thisRef.preferences[property.name] = newValue
                    @OptIn(ExperimentalSerializationApi::class)
                    preferencesPath.outputStream().use {
                        Json.encodeToStream<MutableMap<String, JsonElement>>(thisRef.preferences, it)
                    }
                },
            )
        } as Property<T>
    }

    class Theme(
        val name: String,
        val isDark: Boolean,
        val lookAndFeelClassname: String,
        private val rSyntaxThemeName: String,
    ) {
        private val rSyntaxTheme: RSyntaxTheme by lazy {
            RSyntaxTheme::class.java.getResourceAsStream("themes/$rSyntaxThemeName").use(org.fife.ui.rsyntaxtextarea.Theme::load)
        }

        fun apply(textArea: RSyntaxTextArea) {
            rSyntaxTheme.apply(textArea)
        }

        fun apply(chart: JFreeChart) {
            chart.xyPlot.apply {
                backgroundPaint = UIManager.getColor("Panel.background")
                domainAxis.tickLabelPaint = UIManager.getColor("ColorChooser.foreground")
                rangeAxis.tickLabelPaint = UIManager.getColor("ColorChooser.foreground")
            }
            chart.backgroundPaint = UIManager.getColor("Panel.background")
        }
    }

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

private object ThemeSerializer : KSerializer<Kindling.Theme> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Kindling.Theme", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Kindling.Theme) = encoder.encodeString(value.name)
    override fun deserialize(decoder: Decoder): Kindling.Theme = Kindling.themes.getValue(decoder.decodeString())
}

class Property<T : Any>(
    val description: String,
    initial: T,
    private val setter: (T) -> Unit,
) {
    var currentValue: T = initial
        set(value) {
            setter(value)
            for (listener in listeners) {
                listener(value)
            }
        }

    private val listeners = mutableListOf<(T) -> Unit>()

    fun addListener(listener: (T) -> Unit) {
        listeners.add(listener)
    }
}
