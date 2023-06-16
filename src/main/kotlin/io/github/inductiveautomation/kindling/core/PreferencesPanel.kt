package io.github.inductiveautomation.kindling.core

import io.github.inductiveautomation.kindling.utils.FlatScrollPane
import io.github.inductiveautomation.kindling.utils.StyledLabel
import net.miginfocom.swing.MigLayout
import java.awt.BorderLayout
import java.awt.EventQueue
import java.awt.Font
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JFrame
import javax.swing.JPanel

class PreferencesPanel : JFrame("Preferences") {
    private val closeButton = JButton("Close").apply {
        addActionListener {
            this@PreferencesPanel.isVisible = false
        }
        EventQueue.invokeLater {
            rootPane.defaultButton = this
        }
    }

    init {
        setSize(800, 600)
        iconImage = Kindling.frameIcon
        defaultCloseOperation = HIDE_ON_CLOSE
        setLocationRelativeTo(null)

        Kindling.theme.addChangeListener {
            // sometimes changing the theme affects the sizing of components and leads to annoying scrollbars, so we'll just resize when that happens
            pack()
        }

        contentPane = JPanel(BorderLayout()).apply {
            add(
                FlatScrollPane(
                    JPanel(MigLayout("ins 10")).apply {
                        for ((category, properties) in Kindling.properties) {
                            val categoryPanel = JPanel(MigLayout("fill, gap 10")).apply {
                                border = BorderFactory.createTitledBorder(category.name)
                                for (property in properties.values) {
                                    add(
                                        StyledLabel {
                                            add(property.name, Font.BOLD)
                                            if (property.description != null) {
                                                add("\n")
                                                add(property.description)
                                            }
                                        },
                                        "grow, wrap, gapy 0",
                                    )
                                    add(property.createEditor(), "grow, wrap, gapy 0")
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
