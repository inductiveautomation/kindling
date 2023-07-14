package io.github.inductiveautomation.kindling.core

import com.formdev.flatlaf.FlatDarkLaf
import com.formdev.flatlaf.FlatLaf
import com.formdev.flatlaf.FlatLightLaf
import com.formdev.flatlaf.extras.FlatAnimatedLafChange
import com.formdev.flatlaf.themes.FlatMacDarkLaf
import com.formdev.flatlaf.themes.FlatMacLightLaf
import com.formdev.flatlaf.util.SystemInfo
import com.jthemedetecor.OsThemeDetector
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea
import org.jfree.chart.JFreeChart
import java.awt.Image
import java.awt.Toolkit
import java.io.File
import javax.swing.UIManager
import kotlin.io.path.Path
import kotlin.properties.Delegates
import org.fife.ui.rsyntaxtextarea.Theme as RSyntaxTheme

object Kindling {
    val homeLocation: File = Path(System.getProperty("user.home"), "Downloads").toFile()

    val frameIcon: Image = Toolkit.getDefaultToolkit().getImage(this::class.java.getResource("/icons/kindling.png"))

    val wrapperEncodings = arrayOf(
            Charsets.ISO_8859_1,
            Charsets.UTF_8,
            Charsets.US_ASCII
    )

    var selectedWrapperEncoding = wrapperEncodings[0]

    @Suppress("ktlint:trailing-comma-on-declaration-site")
    enum class Theme(val lookAndFeel: FlatLaf, private val rSyntaxThemeName: String) {
        Light(
            lookAndFeel = if (SystemInfo.isMacOS) FlatMacLightLaf() else FlatLightLaf(),
            rSyntaxThemeName = "idea.xml",
        ),
        Dark(
            lookAndFeel = if (SystemInfo.isMacOS) FlatMacDarkLaf() else FlatDarkLaf(),
            rSyntaxThemeName = "dark.xml",
        );

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

    private val themeListeners = mutableListOf<(Theme) -> Unit>()

    fun addThemeChangeListener(listener: (Theme) -> Unit) {
        themeListeners.add(listener)
    }

    private val themeDetector = OsThemeDetector.getDetector()

    var theme: Theme by Delegates.observable(if (themeDetector.isDark) Theme.Dark else Theme.Light) { _, _, newValue ->
        newValue.apply(true)
        for (listener in themeListeners) {
            listener.invoke(newValue)
        }
    }

    fun initTheme() {
        theme.apply(false)
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
}
