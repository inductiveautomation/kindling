package io.github.inductiveautomation.kindling.xml

import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.awt.event.FocusListener
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.KeyListener
import java.util.Locale
import javax.swing.ComboBoxEditor
import javax.swing.ComboBoxModel
import javax.swing.JComboBox
import javax.swing.JFrame
import javax.swing.SwingUtilities
import javax.swing.text.AttributeSet
import javax.swing.text.BadLocationException
import javax.swing.text.JTextComponent
import javax.swing.text.PlainDocument

/* This work is hereby released into the Public Domain.
* To view a copy of the public domain dedication, visit
* http://creativecommons.org/licenses/publicdomain/
*/

/***********************************************************
 * JComboBox Autocomplete decorator class.
 * Written by Thomas Bierhance
 * http://www.orbital-computer.de/JComboBox/
 **********************************************************/

class AutoCompletion(var comboBox: JComboBox<*>) : PlainDocument() {
    var model: ComboBoxModel<*>
    var editor: JTextComponent? = null

    // flag to indicate if setSelectedItem has been called
    // subsequent calls to remove/insertString should be ignored
    private var selecting = false
    var hidePopupOnFocusLoss: Boolean
    var hitBackspace = false
    var hitBackspaceOnSelection = false
    private var editorKeyListener: KeyListener
    private var editorFocusListener: FocusListener

    init {
        model = comboBox.model
        comboBox.addActionListener { if (!selecting) highlightCompletedText(0) }
        comboBox.addPropertyChangeListener { e ->
            if (e.propertyName == "editor") configureEditor(e.newValue as ComboBoxEditor)
            if (e.propertyName == "model") model = e.newValue as ComboBoxModel<*>
        }
        editorKeyListener =
            object : KeyAdapter() {
                override fun keyPressed(e: KeyEvent) {
                    if (comboBox.isDisplayable) comboBox.isPopupVisible = true
                    hitBackspace = false
                    when (e.keyCode) {
                        KeyEvent.VK_BACK_SPACE -> {
                            hitBackspace = true
                            hitBackspaceOnSelection = editor!!.selectionStart != editor!!.selectionEnd
                        }

                        KeyEvent.VK_DELETE -> {
                            e.consume()
                            comboBox.toolkit.beep()
                        }
                    }
                }
            }
        // Bug 5100422 on Java 1.5: Editable JComboBox won't hide popup when tabbing out
        hidePopupOnFocusLoss = System.getProperty("java.version").startsWith("1.5")
        // Highlight whole text when gaining focus
        editorFocusListener =
            object : FocusAdapter() {
                override fun focusGained(e: FocusEvent) {
                    highlightCompletedText(0)
                }

                override fun focusLost(e: FocusEvent) {
                    // Workaround for Bug 5100422 - Hide Popup on focus loss
                    if (hidePopupOnFocusLoss) comboBox.isPopupVisible = false
                }
            }
        configureEditor(comboBox.editor)
        // Handle initially selected object
        val selected = comboBox.selectedItem
        if (selected != null) setText(selected.toString())
        highlightCompletedText(0)
    }

    private fun configureEditor(newEditor: ComboBoxEditor?) {
        if (editor != null) {
            editor!!.removeKeyListener(editorKeyListener)
            editor!!.removeFocusListener(editorFocusListener)
        }
        if (newEditor != null) {
            editor = newEditor.editorComponent as JTextComponent
            editor!!.addKeyListener(editorKeyListener)
            editor!!.addFocusListener(editorFocusListener)
            editor!!.document = this
        }
    }

    @Throws(BadLocationException::class)
    override fun remove(
        offs: Int,
        len: Int,
    ) {
        // return immediately when selecting an item
        var offset = offs
        if (selecting) return
        if (hitBackspace) {
            // user hit backspace => move the selection backwards
            // old item keeps being selected
            if (offset > 0) {
                if (hitBackspaceOnSelection) offset--
            } else {
                // User hit backspace with the cursor positioned on the start => beep
                comboBox.toolkit.beep() // when available use: UIManager.getLookAndFeel().provideErrorFeedback(comboBox);
            }
            highlightCompletedText(offset)
        } else {
            super.remove(offset, len)
        }
    }

    @Throws(BadLocationException::class)
    override fun insertString(
        offs: Int,
        str: String,
        a: AttributeSet,
    ) {
        // return immediately when selecting an item
        var offs = offs
        if (selecting) return
        // insert the string into the document
        super.insertString(offs, str, a)
        // lookup and select a matching item
        var item = lookupItem(getText(0, length))
        if (item != null) {
            setSelectedItem(item)
        } else {
            // keep old item selected if there is no match
            item = comboBox.selectedItem
            // imitate no insert (later on offs will be incremented by str.length(): selection won't move forward)
            offs -= str.length
            // provide feedback to the user that his input has been received but can not be accepted
            comboBox.toolkit.beep() // when available use: UIManager.getLookAndFeel().provideErrorFeedback(comboBox);
        }
        setText(item.toString())
        // select the completed part
        highlightCompletedText(offs + str.length)
    }

    private fun setText(text: String) {
        try {
            // remove all text and insert the completed string
            super.remove(0, length)
            super.insertString(0, text, null)
        } catch (e: BadLocationException) {
            throw RuntimeException(e.toString())
        }
    }

    private fun highlightCompletedText(start: Int) {
        editor!!.caretPosition = length
        editor!!.moveCaretPosition(start)
    }

    private fun setSelectedItem(item: Any) {
        selecting = true
        model.selectedItem = item
        selecting = false
    }

    private fun lookupItem(pattern: String): Any? {
        val selectedItem = model.selectedItem
        // only search for a different item if the currently selected does not match
        if (selectedItem != null && startsWithIgnoreCase(selectedItem.toString(), pattern)) {
            return selectedItem
        } else {
            // iterate over all items
            var i = 0
            val n = model.size
            while (i < n) {
                val currentItem = model.getElementAt(i)
                // current item starts with the pattern?
                if (currentItem != null && startsWithIgnoreCase(currentItem.toString(), pattern)) {
                    return currentItem
                }
                i++
            }
        }
        // no item starts with the pattern => return null
        return null
    }

    // checks if str1 starts with str2 - ignores case
    private fun startsWithIgnoreCase(
        str1: String,
        str2: String,
    ): Boolean {
        return str1.uppercase(Locale.getDefault()).startsWith(str2.uppercase(Locale.getDefault()))
    }

    companion object {
        private fun enable(comboBox: JComboBox<*>) {
            // has to be editable
            comboBox.isEditable = true
            // change the editor's document
            AutoCompletion(comboBox)
        }

        private fun createAndShowGUI() {
            // the combo box (add/modify items if you like to)
            val comboBox: JComboBox<*> = JComboBox(arrayOf<Any?>("Ester", "Jordi", "Jordana", "Jorge", "Sergi"))
            enable(comboBox)

            // create and show a window containing the combo box
            val frame = JFrame()
            frame.defaultCloseOperation = 3
            frame.contentPane.add(comboBox)
            frame.pack()
            frame.isVisible = true
        }

        @JvmStatic
        fun main(args: Array<String>) {
            SwingUtilities.invokeLater { createAndShowGUI() }
        }
    }
}
