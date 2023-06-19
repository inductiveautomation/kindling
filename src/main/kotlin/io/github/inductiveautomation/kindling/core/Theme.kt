package io.github.inductiveautomation.kindling.core

import com.formdev.flatlaf.FlatDarkLaf
import com.formdev.flatlaf.FlatLightLaf
import com.formdev.flatlaf.intellijthemes.FlatAllIJThemes
import com.formdev.flatlaf.themes.FlatMacDarkLaf
import com.formdev.flatlaf.themes.FlatMacLightLaf
import io.github.inductiveautomation.kindling.core.Kindling.UI.Theme
import io.github.inductiveautomation.kindling.core.Theme.Companion.themes
import io.github.inductiveautomation.kindling.utils.configureCellRenderer
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea
import org.jfree.chart.JFreeChart
import javax.swing.JComboBox
import javax.swing.UIManager
import org.fife.ui.rsyntaxtextarea.Theme as RSyntaxTheme

data class Theme(
    val name: String,
    val isDark: Boolean,
    val lookAndFeelClassname: String,
    val rSyntaxThemeName: String,
) {
    private val rSyntaxTheme: RSyntaxTheme by lazy {
        RSyntaxTheme::class.java.getResourceAsStream("themes/$rSyntaxThemeName").use(RSyntaxTheme::load)
    }

    companion object {
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

        var RSyntaxTextArea.theme: Theme
            get() = throw NotImplementedError("Write only property")
            set(value) {
                value.rSyntaxTheme.apply(this)
            }

        var JFreeChart.theme: Theme
            get() = throw NotImplementedError("Write only property")
            set(_) {
                xyPlot.apply {
                    backgroundPaint = UIManager.getColor("Panel.background")
                    domainAxis.tickLabelPaint = UIManager.getColor("ColorChooser.foreground")
                    rangeAxis.tickLabelPaint = UIManager.getColor("ColorChooser.foreground")
                }
                backgroundPaint = UIManager.getColor("Panel.background")
            }
    }
}

private val themeComparator = compareBy<Theme> { it.isDark } then compareBy { it.name }

class ThemeSelectionDropdown : JComboBox<Theme>(themes.values.sortedWith(themeComparator).toTypedArray()) {
    init {
        selectedItem = Theme.currentValue

        configureCellRenderer { _, value, _, _, _ ->
            text = value?.name
            val fg = UIManager.getColor("ComboBox.foreground")
            val bg = UIManager.getColor("ComboBox.background")

            if (Theme.currentValue.isDark != value?.isDark) {
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
