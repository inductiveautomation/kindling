package io.github.inductiveautomation.kindling.core

import com.formdev.flatlaf.FlatLightLaf
import com.formdev.flatlaf.themes.FlatMacLightLaf
import com.formdev.flatlaf.util.SystemInfo
import io.github.inductiveautomation.kindling.utils.PathSerializer
import io.github.inductiveautomation.kindling.utils.PathSerializer.serializedForm
import io.github.inductiveautomation.kindling.utils.ThemeSerializer
import org.jdesktop.swingx.JXTextField
import java.awt.event.ItemEvent
import java.nio.file.Path
import javax.swing.JCheckBox
import javax.swing.JSpinner
import javax.swing.SpinnerNumberModel
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import kotlin.io.path.Path
import io.github.inductiveautomation.kindling.core.Theme as KindlingTheme

object Kindling {
    object General : PreferenceCategory() {
        val HomeLocation: Preference<Path> by preference(
            name = "Home Location",
            description = "The default path to start looking for files.",
            requiresRestart = true,
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

        val ShowFullLoggerNames: Preference<Boolean> by preference(
            name = "Logger Names",
            default = false,
            editor = {
                JCheckBox("Show full logger names on newly created tool tabs").apply {
                    isSelected = currentValue
                    addItemListener { e ->
                        currentValue = e.stateChange == ItemEvent.SELECTED
                    }
                }
            },
        )

        init {
            // ensure delegates are initialized and in declaration order
            HomeLocation.name
            ShowFullLoggerNames.name
        }

        override fun toString(): String = "General"
    }

    object UI : PreferenceCategory() {
        val Theme: Preference<KindlingTheme> by preference(
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

        val ScaleFactor: Preference<Double> by preference(
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

        override fun toString(): String = "UI"
    }

    object Advanced : PreferenceCategory() {
        val Debug: Preference<Boolean> by preference(
            name = "Debug Mode",
            default = false,
            editor = {
                JCheckBox("Enable debug features").apply {
                    isSelected = currentValue
                    addItemListener { e ->
                        currentValue = e.stateChange == ItemEvent.SELECTED
                    }
                }
            },
        )

        override fun toString(): String = "Advanced"
    }

    val preferenceCategories: List<PreferenceCategory> = listOf(General, UI, Advanced)
}
