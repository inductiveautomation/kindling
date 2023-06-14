package io.github.inductiveautomation.kindling.core

import io.github.inductiveautomation.kindling.utils.configureCellRenderer
import net.miginfocom.swing.MigLayout
import java.awt.Color
import java.awt.EventQueue
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JSpinner
import javax.swing.SpinnerNumberModel

class PreferencesPanel : JFrame("Preferences") {
    private val uiPanel = UIPanel()
    private val generalPanel = GeneralPanel()

    private val submitButton = JButton("OK").apply {
        addActionListener {
            this@PreferencesPanel.isVisible = false
            Kindling.apply {
                showFullLoggerNames.currentValue = generalPanel.showFullLoggerNames.isSelected
                uiScaleFactor.currentValue = uiPanel.uiScaleSelector.value as Double
                theme.currentValue = uiPanel.themeSelection.selectedItem
            }
        }
        EventQueue.invokeLater {
            rootPane.defaultButton = this
        }
    }

    private val cancelButton = JButton("Cancel").apply {
        addActionListener {
            this@PreferencesPanel.isVisible = false
        }
    }

    init {
        setSize(800, 600)
        iconImage = Kindling.frameIcon
        defaultCloseOperation = HIDE_ON_CLOSE
        setLocationRelativeTo(null)

        contentPane = JPanel(MigLayout("fillx, ins 10, gap 10")).apply {
            add(uiPanel, "push, grow, wrap")
            add(generalPanel, "grow, wrap")
            add(
                JPanel(MigLayout("fill, ins 10")).apply {
                    add(cancelButton, "east, gapright 10")
                    add(submitButton, "east, gapright 10")
                },
                "south, gapbottom 10",
            )
        }
        pack()
    }

    class UIPanel : JPanel(MigLayout("fill, gap 10")) {
        val themeSelection = ThemeSelectionDropdown()

        val uiScaleSelector = JSpinner(
            SpinnerNumberModel(Kindling.uiScaleFactor.currentValue, 1.0, 2.0, 0.1),
        )

        init {
            border = BorderFactory.createTitledBorder("Appearance/UI")

            val themeLabel = JLabel("Theme")
            val uiScaleLabel = JLabel("UI Scale (Requires restart)")

            add(themeLabel, "grow")
            add(themeSelection, "grow, wrap")

            add(uiScaleLabel)
            add(uiScaleSelector)
        }

        class ThemeSelectionDropdown : JComboBox<Kindling.Theme>(Kindling.themes.values.sortedWith(themeComparator).toTypedArray()) {
            init {
                selectedItem = Kindling.theme.currentValue

                configureCellRenderer { _, value, _, _, _ ->
                    text = value?.name
                    if (value?.isDark == true) {
                        background = Color(0x3c3f41)
                        foreground = Color(0xBBBBBB)
                    } else {
                        background = Color(0xF2F2F2)
                        foreground = Color(0x000000)
                    }
                }

                addActionListener {
                    Kindling.theme.currentValue = selectedItem
                }
            }

            override fun getSelectedItem(): Kindling.Theme = super.getSelectedItem() as Kindling.Theme

            companion object {
                private val themeComparator = compareBy<Kindling.Theme> { !it.isDark } then compareBy { it.name }
            }
        }
    }

    class GeneralPanel : JPanel(MigLayout("fill, ins 10")) {
        val showFullLoggerNames = JCheckBox(Kindling.showFullLoggerNames.description).apply {
            isSelected = Kindling.showFullLoggerNames.currentValue
        }

        init {
            border = BorderFactory.createTitledBorder("General")
            add(showFullLoggerNames)
        }
    }
}
