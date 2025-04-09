package io.github.inductiveautomation.kindling.thread.comparison

import com.formdev.flatlaf.extras.FlatSVGIcon
import io.github.inductiveautomation.kindling.core.Kindling.Preferences.Advanced.HyperlinkStrategy
import io.github.inductiveautomation.kindling.thread.comparison.ThreadComparisonPane.Companion.threadHighlightColor
import io.github.inductiveautomation.kindling.utils.EDT_SCOPE
import io.github.inductiveautomation.kindling.utils.FlatScrollPane
import io.github.inductiveautomation.kindling.utils.scrollToTop
import kotlinx.coroutines.launch
import net.miginfocom.swing.MigLayout
import java.awt.Dimension
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.JTextPane
import javax.swing.UIManager
import javax.swing.event.HyperlinkEvent

internal class DetailContainer(
    val prefix: String,
    collapsed: Boolean = true,
) : JPanel(MigLayout("ins 0, fill, flowy, hidemode 3")) {
    var isCollapsed: Boolean = collapsed
        set(value) {
            field = value
            scrollPane.isVisible = !value
            headerButton.icon = if (value) expandIcon else collapseIcon

            // Preferred size needs to be recalculated or something like that
            EDT_SCOPE.launch {
                revalidate()
                repaint()
            }
        }

    var itemCount = 0
        set(value) {
            headerButton.text = "$prefix: $value"
            field = value
        }

    var isHighlighted: Boolean = false
        set(value) {
            field = value
            if (value) {
                headerButton.foreground = threadHighlightColor
            } else {
                headerButton.foreground = UIManager.getColor("Button.foreground")
            }
        }

    private val headerButton = object : JButton(prefix, if (collapsed) expandIcon else collapseIcon) {
        init {
            horizontalTextPosition = LEFT
            horizontalAlignment = LEFT

            addActionListener { isCollapsed = !isCollapsed }
        }

        // This effectively right-aligns the icon
        override fun getIconTextGap(): Int {
            val fm = getFontMetrics(font)
            return size.width - insets.left - insets.right - icon.iconWidth - fm.stringWidth(text)
        }
    }

    private val textArea = JTextPane().apply {
        isEditable = false
        contentType = "text/html"

        addHyperlinkListener { event ->
            if (event.eventType == HyperlinkEvent.EventType.ACTIVATED) {
                HyperlinkStrategy.currentValue.handleEvent(event)
            }
        }
    }

    private val scrollPane = FlatScrollPane(textArea) {
        horizontalScrollBar.preferredSize = Dimension(0, 10)
        verticalScrollBar.preferredSize = Dimension(10, 0)

        isVisible = !collapsed
    }

    var text: String?
        get() = textArea.text
        set(value) {
            textArea.text = value
            scrollPane.scrollToTop()
        }

    init {
        add(headerButton, "growx, top, wmax 100%")
        add(scrollPane, "push, grow, top, wmax 100%")
    }

    companion object {
        private val collapseIcon = FlatSVGIcon("icons/bx-chevrons-up.svg")
        private val expandIcon = FlatSVGIcon("icons/bx-chevrons-down.svg")
    }
}
