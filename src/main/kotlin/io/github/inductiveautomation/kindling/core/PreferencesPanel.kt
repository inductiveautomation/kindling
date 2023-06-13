package io.github.inductiveautomation.kindling.core

import io.github.inductiveautomation.kindling.utils.configureCellRenderer
import net.miginfocom.swing.MigLayout
import java.awt.event.ItemEvent
import javax.swing.BorderFactory
import javax.swing.DefaultComboBoxModel
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JSpinner
import javax.swing.JToggleButton
import javax.swing.SpinnerNumberModel
import javax.swing.UIManager

class PreferencesPanel : JFrame("Preferences") {

    private val uiPanel = UIPanel()

    private val generalPanel = GeneralPanel()

    private val submitButton = JButton("OK").apply {
        addActionListener {
            this@PreferencesPanel.isVisible = false
            saveChanges()
        }
    }

    private val cancelButton = JButton("Cancel").apply {
        isDefaultCapable = false
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
            add(uiPanel, "push, grow")
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

    private fun saveChanges() {
        Kindling.session.showFullLoggerNames = generalPanel.showFullLoggerNames.isSelected
        Kindling.session.uiScaleFactor = uiPanel.uiScaleSelector.value as Double

        if (Kindling.session.theme != uiPanel.themeSelection.selectedItem) {
            Kindling.session.theme = uiPanel.themeSelection.selectedItem
        }
    }

    class UIPanel : JPanel(MigLayout("fill, gap 10")) {
        private val lightDarkToggle = JToggleButton().apply {
            if (Kindling.session.theme.isDark) {
                isSelected = false
                text = "Dark Themes"
            } else {
                isSelected = true
                text = "Light Themes"
            }

            addItemListener { event ->
                text = if (event.stateChange == ItemEvent.SELECTED) "Light Themes" else "Dark Themes"
            }
        }

        val themeSelection = ThemeSelectionDropdown()

        val uiScaleSelector = JSpinner(
            SpinnerNumberModel(Kindling.session.uiScaleFactor, 1.0, 2.0, 0.1),
        )

        init {
            border = BorderFactory.createTitledBorder("Appearance/UI")

            val themeLabel = JLabel("Theme")
            val uiScaleLabel = JLabel("UI Scale (Requires restart)")

            add(themeLabel, "grow")
            add(lightDarkToggle)
            add(themeSelection, "grow, wrap")

            add(uiScaleLabel)
            add(uiScaleSelector)
        }

        inner class ThemeSelectionDropdown private constructor(
            private val lightThemes: Array<Kindling.Theme>,
            private val darkThemes: Array<Kindling.Theme>,
        ) : JComboBox<Kindling.Theme>() {

            constructor() : this(Kindling.Theme.lightThemes.toTypedArray(), Kindling.Theme.darkThemes.toTypedArray())

            init {
                val initialTheme = Kindling.session.theme
                select(initialTheme)

                configureCellRenderer { _, value, _, _, _ ->
                    text = value?.name
                }

                lightDarkToggle.addItemListener { event ->
                    model = if (event.stateChange == ItemEvent.SELECTED) {
                        DefaultComboBoxModel(lightThemes)
                    } else {
                        DefaultComboBoxModel(darkThemes)
                    }
                    setLafForSelectedItem()
                }

                addActionListener {
                    setLafForSelectedItem()
                }
            }

            override fun getSelectedItem(): Kindling.Theme = super.getSelectedItem() as Kindling.Theme

            private fun setLafForSelectedItem() {
                val oldLaf = UIManager.getLookAndFeel()
                UIManager.setLookAndFeel(selectedItem.lookAndFeel)
                updateUI()
                UIManager.setLookAndFeel(oldLaf)
            }

            private fun select(theme: Kindling.Theme) {
                model = if (theme.isDark) {
                    DefaultComboBoxModel(darkThemes)
                } else {
                    DefaultComboBoxModel(lightThemes)
                }
                selectedItem = theme
            }
        }
    }

    class GeneralPanel : JPanel(MigLayout("fill, ins 10")) {

        val showFullLoggerNames = JCheckBox("Show full logger names by default").apply {
            isSelected = Kindling.session.showFullLoggerNames
        }

        init {
            border = BorderFactory.createTitledBorder("General")
            add(showFullLoggerNames)
        }
    }
}
