package io.github.inductiveautomation.kindling.utils

import java.text.NumberFormat
import javax.swing.InputVerifier
import javax.swing.JComponent
import javax.swing.JFormattedTextField
import javax.swing.SwingConstants
import javax.swing.text.DefaultFormatterFactory
import javax.swing.text.NumberFormatter

class NumericEntryField(inputValue: Long?) : JFormattedTextField(inputValue) {
    private val format = NumberFormat.getIntegerInstance().apply { isGroupingUsed = false }

    init {
        formatterFactory = DefaultFormatterFactory(NumberFormatter(format))
        horizontalAlignment = SwingConstants.CENTER
        inputVerifier =
            object : InputVerifier() {
                override fun verify(input: JComponent): Boolean {
                    return (input as JFormattedTextField).text.let { text ->
                        text.all { it.isDigit() } && text.length < 19
                    }
                }
            }
    }
}
