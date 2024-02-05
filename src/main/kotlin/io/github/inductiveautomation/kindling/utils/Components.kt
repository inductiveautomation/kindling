package io.github.inductiveautomation.kindling.utils

import com.formdev.flatlaf.extras.components.FlatScrollPane
import com.formdev.flatlaf.util.SystemInfo
import com.jidesoft.swing.StyledLabel
import com.jidesoft.swing.StyledLabelBuilder
import io.github.inductiveautomation.kindling.core.Kindling
import net.miginfocom.swing.MigLayout
import java.awt.Component
import javax.swing.ButtonGroup
import javax.swing.JFrame
import javax.swing.JPanel
import javax.swing.JSplitPane
import javax.swing.SwingConstants
import javax.swing.border.EmptyBorder

@Suppress("FunctionName")
inline fun FlatScrollPane(
    component: Component,
    block: FlatScrollPane.() -> Unit = {},
) = FlatScrollPane().apply {
    setViewportView(component)
    block(this)
}

/**
 * Constructs and (optionally) immediately displays a JFrame of the given dimensions, centered on the screen.
 */
inline fun jFrame(
    title: String,
    width: Int,
    height: Int,
    initiallyVisible: Boolean = true,
    embedContentIntoTitleBar: Boolean = false,
    block: JFrame.() -> Unit,
) = JFrame(title).apply {
    setSize(width, height)

    if (embedContentIntoTitleBar && SystemInfo.isMacFullWindowContentSupported) {
        rootPane.putClientProperty("apple.awt.windowTitleVisible", false)
        rootPane.putClientProperty("apple.awt.fullWindowContent", true)
        rootPane.putClientProperty("apple.awt.transparentTitleBar", true)
    }

    iconImages = Kindling.frameIcons
    defaultCloseOperation = JFrame.DISPOSE_ON_CLOSE

    setLocationRelativeTo(null)

    block()

    isVisible = initiallyVisible
}

inline fun StyledLabel(block: StyledLabelBuilder.() -> Unit): StyledLabel {
    return StyledLabelBuilder().apply(block).createLabel()
}

inline fun StyledLabel.style(block: StyledLabelBuilder.() -> Unit) {
    StyledLabelBuilder().apply {
        block()
        clearStyleRanges()
        configure(this@style)
    }
}

fun EmptyBorder(): EmptyBorder = EmptyBorder(0, 0, 0, 0)

@Suppress("FunctionName")
fun HorizontalSplitPane(
    left: Component,
    right: Component,
    resizeWeight: Double = 0.5,
    block: JSplitPane.() -> Unit = {},
) = JSplitPane(SwingConstants.VERTICAL, left, right).apply {
    isOneTouchExpandable = true
    this.resizeWeight = resizeWeight

    block()
}

@Suppress("FunctionName")
fun VerticalSplitPane(
    top: Component,
    bottom: Component,
    resizeWeight: Double = 0.5,
    block: JSplitPane.() -> Unit = {},
) = JSplitPane(SwingConstants.HORIZONTAL, top, bottom).apply {
    isOneTouchExpandable = true
    this.resizeWeight = resizeWeight

    block()
}

/**
 * Constructs a MigLayout JPanel containing each element of [group] in the first row.
 */
@Suppress("FunctionName")
fun ButtonPanel(group: ButtonGroup) =
    JPanel(MigLayout("ins 2 0, fill")).apply {
        val sortGroupEnumeration = group.elements
        add(sortGroupEnumeration.nextElement(), "split ${group.buttonCount}, flowx, align right")
        for (element in sortGroupEnumeration) {
            add(element, "gapx 2, align right")
        }
    }
