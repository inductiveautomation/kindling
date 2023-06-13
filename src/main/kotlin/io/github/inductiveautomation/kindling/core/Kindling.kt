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
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea
import org.jfree.chart.JFreeChart
import java.awt.Image
import java.awt.Toolkit
import java.io.File
import javax.swing.UIManager
import kotlin.io.path.Path
import kotlin.io.path.createDirectory
import kotlin.io.path.inputStream
import kotlin.io.path.notExists
import kotlin.io.path.outputStream
import kotlin.reflect.full.createInstance
import org.fife.ui.rsyntaxtextarea.Theme as RSyntaxTheme

@OptIn(ExperimentalSerializationApi::class)
object Kindling {
    val homeLocation: File = Path(System.getProperty("user.home"), "Downloads").toFile()

    val cacheLocation = Path(System.getProperty("user.home"), ".kindling").also {
        if (it.notExists()) it.createDirectory()
    }

    val frameIcon: Image = Toolkit.getDefaultToolkit().getImage(this::class.java.getResource("/icons/kindling.png"))

    private val themeListeners = mutableListOf<(Theme) -> Unit>()

    fun addThemeChangeListener(listener: (Theme) -> Unit) {
        themeListeners.add(listener)
    }

    fun initTheme() {
        session.theme.apply(false)
    }

    private fun Theme.apply(animate: Boolean) {
        try {
            if (animate) {
                FlatAnimatedLafChange.showSnapshot()
            }
            UIManager.setLookAndFeel(lookAndFeel)
            FlatLaf.updateUI()
        } finally {
            // Will no-op if not animated
            FlatAnimatedLafChange.hideSnapshotWithAnimation()
        }
    }

    val session: UserSession = run {
        try {
            val session: UserSession = cacheLocation.resolve(UserSession.sessionFile).inputStream().use(Json::decodeFromStream)
            // Throw away the newly created theme instance and grab a copy of what we have from Theme.companion
            session.apply {
                theme = (Theme.lightThemes + Theme.darkThemes).find { it.name == theme.name } ?: Theme.defaultTheme
            }
        } catch (e: Exception) { // A few exceptions can happen here. Fallback to default session.
            UserSession()
        }
    }

    @Serializable
    class UserSession {
        var theme = Theme.defaultTheme
            set(newValue) {
                field = newValue
                newValue.apply(true)
                for (listener in themeListeners) {
                    listener.invoke(newValue)
                }
            }

        var showFullLoggerNames = false

        var uiScaleFactor = 1.0

        fun saveSession() {
            val sessionOutput = cacheLocation.resolve(sessionFile)

            sessionOutput.outputStream().use {
                Json.encodeToStream(this, it)
            }
        }

        companion object {
            const val sessionFile = "session.json"
        }
    }

    @Suppress("ktlint:trailing-comma-on-declaration-site")
    @Serializable
    class Theme(
        val name: String,
        @Serializable(with = LafSerializer::class)
        val lookAndFeel: FlatLaf,
        val isDark: Boolean,
        private val rSyntaxThemeName: String
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

        companion object {
            private val defaultDark = Theme(
                name = "Default Dark",
                lookAndFeel = if (SystemInfo.isMacOS) FlatMacDarkLaf() else FlatDarkLaf(),
                isDark = true,
                rSyntaxThemeName = "dark.xml",
            )
            private val defaultLight = Theme(
                name = "Default Light",
                lookAndFeel = if (SystemInfo.isMacOS) FlatMacLightLaf() else FlatLightLaf(),
                isDark = false,
                rSyntaxThemeName = "idea.xml",
            )

            val lightThemes = listOf(defaultLight) + FlatAllIJThemes.INFOS.filter { !it.isDark }.map { info ->
                Theme(
                    name = info.name,
                    lookAndFeel = Class.forName(info.className).kotlin.createInstance() as FlatLaf,
                    isDark = info.isDark,
                    rSyntaxThemeName = "idea.xml",
                )
            }

            val darkThemes = listOf(defaultDark) + FlatAllIJThemes.INFOS.filter { it.isDark }.map { info ->
                Theme(
                    name = info.name,
                    lookAndFeel = Class.forName(info.className).kotlin.createInstance() as FlatLaf,
                    isDark = info.isDark,
                    rSyntaxThemeName = "dark.xml",
                )
            }

            val defaultTheme = defaultLight
        }
    }
}

object LafSerializer : KSerializer<FlatLaf> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("FlatLaf", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: FlatLaf) {
        encoder.encodeString(value::class.qualifiedName!!) // Class will not be local or within an anonymous object
    }

    override fun deserialize(decoder: Decoder): FlatLaf {
        val className = decoder.decodeString()
        return Class.forName(className).kotlin.createInstance() as FlatLaf
    }
}
