package io.github.inductiveautomation.kindling.core

import io.github.inductiveautomation.kindling.utils.FlatScrollPane
import io.github.inductiveautomation.kindling.utils.StyledLabel
import io.github.inductiveautomation.kindling.utils.jFrame
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer
import net.miginfocom.swing.MigLayout
import org.jdesktop.swingx.JXTaskPane
import org.jdesktop.swingx.JXTaskPaneContainer
import java.awt.BorderLayout
import java.awt.EventQueue
import java.awt.Font
import java.awt.event.ItemEvent
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JFrame
import javax.swing.JPanel
import javax.swing.UIManager
import javax.swing.border.MatteBorder

interface PreferenceCategory {
    val displayName: String
    val key: String

    val preferences: List<Preference<*>>
}

class Preference<T : Any>(
    val category: PreferenceCategory,
    val name: String,
    val description: String? = null,
    val requiresRestart: Boolean = false,
    private val default: T,
    val serializer: KSerializer<T>,
    createEditor: (Preference<T>.() -> JComponent)?,
) {
    val key: String = name.lowercase().filter(Char::isJavaIdentifierStart)

    var currentValue
        get() = Kindling.Preferences[category, this] ?: default
        set(value) {
            Kindling.Preferences[category, this] = value
            for (listener in listeners) {
                listener(value)
            }
        }

    val listeners: MutableList<(newValue: T) -> Unit> = mutableListOf()

    fun addChangeListener(listener: (newValue: T) -> Unit) {
        listeners.add(listener)
    }

    val editor: JComponent? by lazy { createEditor?.invoke(this) }

    companion object {
        @Suppress("FunctionName")
        fun Preference<Boolean>.PreferenceCheckbox(description: String): JCheckBox {
            return JCheckBox(description).apply {
                isSelected = currentValue
                addItemListener { e ->
                    currentValue = e.stateChange == ItemEvent.SELECTED
                }
            }
        }

        /**
         * Creates a new [Preference] instance, automatically contained within the current [PreferenceCategory].
         *
         * [description] is optional, but should be provided or set on the editor
         * component (e.g. [PreferenceCheckbox]).
         *
         * [serializer] can be omitted if the type is natively serializable, but is otherwise required.
         *
         * [editor] should return null if this is not a 'user-facing' preference.
         */
        inline fun <reified T : Any> PreferenceCategory.preference(
            name: String,
            description: String? = null,
            requiresRestart: Boolean = false,
            default: T,
            serializer: KSerializer<T> = serializer(),
            noinline editor: (Preference<T>.() -> JComponent)?,
        ): Preference<T> = Preference(
            name = name,
            category = this,
            description = description,
            requiresRestart = requiresRestart,
            default = default,
            serializer = serializer,
            createEditor = editor,
        )
    }
}

val preferencesEditor by lazy {
    jFrame("Preferences", 600, 800, initiallyVisible = false) {
        defaultCloseOperation = JFrame.HIDE_ON_CLOSE

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
                    JXTaskPaneContainer().apply {
                        for (category in Kindling.Preferences.categories) {
                            val categoryPane = JXTaskPane(category.displayName).apply {
                                isAnimated = false
                                isCollapsed = category.displayName == "Advanced"

                                for (preference in category.preferences) {
                                    val editor = preference.editor
                                    if (editor != null) {
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
                                        add(editor, "grow, wrap, gapy 0")
                                    }
                                }
                            }
                            add(categoryPane)
                        }
                    },
                ) {
                    border = null
                    verticalScrollBar.apply {
                        unitIncrement = 5
                        blockIncrement = 15
                    }
                },
                BorderLayout.CENTER,
            )
            add(
                JPanel(MigLayout("fill, ins 10")).apply {
                    border = MatteBorder(1, 0, 0, 0, UIManager.getColor("Component.borderColor"))
                    add(closeButton, "east, gap 10 10 10 10")
                },
                BorderLayout.SOUTH,
            )
        }
    }
}
