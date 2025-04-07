package io.github.inductiveautomation.kindling.utils

import com.jidesoft.icons.IconsFactory
import net.miginfocom.swing.MigLayout
import java.awt.Color
import java.awt.Component
import java.awt.EventQueue
import java.awt.event.MouseEvent
import java.util.EventObject
import javax.swing.AbstractCellEditor
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JColorChooser
import javax.swing.JComponent
import javax.swing.JDialog
import javax.swing.JLabel
import javax.swing.JTable
import javax.swing.colorchooser.AbstractColorChooserPanel
import javax.swing.table.TableCellEditor
import kotlin.random.Random

/*
 * A cell editor for editing table data which holds a java.awt.Color
 */
class TableColorCellEditor(
    private val showHex: Boolean = true, // Whether to show the hex code in the table cell
) : AbstractCellEditor(), TableCellEditor {
    private val label = JLabel()
    private val colorChooser = JColorChooser()

    // Lazily initialized the first time a cell is edited.
    private lateinit var dialog: JDialog

    init {
        colorChooser.selectionModel.addChangeListener {
            label.apply {
                isOpaque = true
                background = colorChooser.selectionModel.selectedColor
                if (showHex) text = colorChooser.selectionModel.selectedColor.toHexString() + " (editing...)"
            }
        }

        colorChooser.addChooserPanel(RandomColorPanel())
    }

    override fun getCellEditorValue(): Color = colorChooser.color

    override fun isCellEditable(e: EventObject?): Boolean {
        return e is MouseEvent && e.clickCount == 1
    }

    override fun getTableCellEditorComponent(
        table: JTable?,
        value: Any?,
        isSelected: Boolean,
        row: Int,
        column: Int,
    ): Component {
        colorChooser.color = value as Color?

        val renderer = table?.getCellRenderer(row, column)
        val component = renderer?.getTableCellRendererComponent(table, value, isSelected, true, row, column)

        if (component != null) {
            label.apply {
                isOpaque = value != null
                background = component.background

                if (component is JComponent) border = component.border
                if (showHex) text = component.background?.toHexString().orEmpty() + " (editing...)"
            }

            if (!::dialog.isInitialized) {
                dialog = JColorChooser.createDialog(
                    table,
                    "Choose a Color",
                    true,
                    colorChooser,
                    { _ -> stopCellEditing() }, // okListener
                    { _ -> cancelCellEditing() }, // cancelListener
                )
            }

            EventQueue.invokeLater {
                dialog.isVisible = true
            }
        } else {
            label.isOpaque = false
        }
        return label
    }
}

class RandomColorPanel : AbstractColorChooserPanel() {
    private val randomButton = JButton("Random Color")
    private val previewLabel = JLabel().apply {
        isOpaque = true
    }

    override fun updateChooser() {
        previewLabel.apply {
            text = colorFromModel.toHexString()
            background = colorFromModel
        }
    }

    override fun buildChooser() {
        layout = MigLayout()
        add(randomButton, "align 50% 50%, gapright 5px")
        add(previewLabel, "align 50% 50%, w 50!, h 50!")

        randomButton.addActionListener {
            val red = Random.nextInt(0, 0xFF)
            val green = Random.nextInt(0, 0xFF)
            val blue = Random.nextInt(0, 0xFF)
            colorSelectionModel.selectedColor = Color(red, green, blue, 0x80)
        }
    }

    override fun getDisplayName(): String = "Random Color"

    override fun getSmallDisplayIcon(): Icon {
        return IconsFactory.EMPTY_ICON
    }

    override fun getLargeDisplayIcon(): Icon {
        return IconsFactory.EMPTY_ICON
    }
}
