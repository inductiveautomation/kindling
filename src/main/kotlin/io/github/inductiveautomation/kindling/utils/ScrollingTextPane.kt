package io.github.inductiveautomation.kindling.utils

import com.formdev.flatlaf.extras.components.FlatScrollPane
import java.awt.Dimension
import java.awt.Rectangle
import javax.swing.JTextPane
import javax.swing.event.HyperlinkListener

class ScrollingTextPane : FlatScrollPane() {
    var text: String?
        get() = textPane.text
        set(value) {
            val lineHeight = 21
            val lineCount = value?.lineSequence()?.count() ?: 1
            val newPrefHeight = ((lineHeight * lineCount) + 2 * horizontalScrollBar.preferredSize.height).coerceAtMost(250)
            preferredSize = Dimension(Integer.MAX_VALUE, newPrefHeight)
            textPane.text = value
            viewport.scrollRectToVisible(Rectangle(0, 0))
        }

    private val textPane =
        JTextPane().apply {
            isEditable = false
            contentType = "text/html"
        }

    fun addHyperlinkListener(listener: HyperlinkListener) {
        textPane.addHyperlinkListener(listener)
    }

    init {
        setViewportView(textPane)

        horizontalScrollBar.preferredSize = Dimension(0, SCROLLBAR_WIDTH)
        verticalScrollBar.preferredSize = Dimension(SCROLLBAR_WIDTH, 0)

        verticalScrollBarPolicy = VERTICAL_SCROLLBAR_ALWAYS
        preferredSize = Dimension(Integer.MAX_VALUE, 0)
        viewport.scrollRectToVisible(Rectangle(0, 0))
    }

    companion object {
        private const val SCROLLBAR_WIDTH = 10
    }
}
