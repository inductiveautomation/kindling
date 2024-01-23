package io.github.inductiveautomation.kindling.utils

import java.text.NumberFormat
import java.util.EventListener
import javax.swing.JFormattedTextField
import javax.swing.SwingConstants
import javax.swing.SwingUtilities
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.event.EventListenerList
import javax.swing.text.DefaultFormatterFactory
import javax.swing.text.NumberFormatter

fun interface NumericChangeListener : EventListener {
    fun valueChanged()
}

class NumericEntryField(inputValue: Long) : JFormattedTextField(inputValue) {
    private val format = NumberFormat.getIntegerInstance().apply { isGroupingUsed = false }
    private var previousValue = inputValue.toString()

    private val listeners = EventListenerList()

    fun addNumericChangeListener(listener: NumericChangeListener) {
        listeners.add(listener)
    }

    fun fireListeners() {
        listeners.getAll<NumericChangeListener>().forEach(NumericChangeListener::valueChanged)
    }

    private var isValidating = 2

    fun validateTextField(): Boolean {
        return if (text.all { it.isDigit() } && text.length < 19) {
            previousValue = text
            if (isValidating > 0) {
                isValidating -= 1
                false
            } else {
                true
            }
        } else {
            isValidating = 2
            text = previousValue
            false
        }
    }

    init {
        formatterFactory = DefaultFormatterFactory(NumberFormatter(format))
        horizontalAlignment = SwingConstants.CENTER
        document.addDocumentListener(
            object : DocumentListener {
                override fun insertUpdate(e: DocumentEvent?) {
                    SwingUtilities.invokeLater {
                        if (validateTextField()) {
                            fireListeners()
                        }
                    }
                }

                override fun removeUpdate(e: DocumentEvent?) {
                    SwingUtilities.invokeLater {
                        if (validateTextField()) {
                            fireListeners()
                        }
                    }
                }

                override fun changedUpdate(e: DocumentEvent?) { }
            },
        )
    }
}
