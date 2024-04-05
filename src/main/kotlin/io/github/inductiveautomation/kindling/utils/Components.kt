@file:Suppress("FunctionName") // ktlint is dumb and doesn't understand this

package io.github.inductiveautomation.kindling.utils

import com.formdev.flatlaf.FlatClientProperties.MACOS_WINDOW_BUTTONS_SPACING
import com.formdev.flatlaf.FlatClientProperties.MACOS_WINDOW_BUTTONS_SPACING_LARGE
import com.formdev.flatlaf.extras.components.FlatScrollPane
import com.formdev.flatlaf.extras.components.FlatSplitPane
import com.formdev.flatlaf.extras.components.FlatSplitPane.ExpandableSide
import com.formdev.flatlaf.util.SystemInfo
import com.jidesoft.swing.StyledLabel
import com.jidesoft.swing.StyledLabelBuilder
import io.github.inductiveautomation.kindling.core.Kindling
import net.miginfocom.swing.MigLayout
import java.awt.Component
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.ButtonGroup
import javax.swing.JFrame
import javax.swing.JPanel
import javax.swing.JSplitPane
import javax.swing.border.EmptyBorder

inline fun FlatScrollPane(
    component: Component,
    block: FlatScrollPane.() -> Unit = {},
): FlatScrollPane {
    return FlatScrollPane().apply {
        setViewportView(component)
        block(this)
    }
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
        rootPane.putClientProperty(MACOS_WINDOW_BUTTONS_SPACING, MACOS_WINDOW_BUTTONS_SPACING_LARGE)
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

fun HorizontalSplitPane(
    left: Component,
    right: Component,
    resizeWeight: Double = 0.5,
    expandableSide: ExpandableSide = ExpandableSide.right,
    block: JSplitPane.() -> Unit = {},
) = createSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, right, resizeWeight, expandableSide, block)

fun VerticalSplitPane(
    top: Component,
    bottom: Component,
    resizeWeight: Double = 0.5,
    // the top ("left") is the one that's allowed to expand
    expandableSide: ExpandableSide = ExpandableSide.left,
    block: JSplitPane.() -> Unit = {},
) = createSplitPane(JSplitPane.VERTICAL_SPLIT, top, bottom, resizeWeight, expandableSide, block)

private fun createSplitPane(
    orientation: Int,
    left: Component,
    right: Component,
    resizeWeight: Double,
    expandableSide: ExpandableSide,
    block: JSplitPane.() -> Unit,
): FlatSplitPane {
    return FlatSplitPane().apply {
        this.orientation = orientation
        this.leftComponent = left
        this.rightComponent = right
        this.isOneTouchExpandable = true
        this.expandableSide = expandableSide
        this.resizeWeight = resizeWeight
        addComponentListener(
            object : ComponentAdapter() {
                override fun componentShown(e: ComponentEvent?) {
                    setDividerLocation(resizeWeight)
                }
            },
        )

        block()
    }
}

/**
 * Constructs a MigLayout JPanel containing each element of [group] in the first row.
 */
fun ButtonPanel(group: ButtonGroup) =
    JPanel(MigLayout("ins 3 0, fill")).apply {
        border = EmptyBorder()
        val sortGroupEnumeration = group.elements
        add(sortGroupEnumeration.nextElement(), "split ${group.buttonCount}, flowx, align right, gapbottom 3")
        for (element in sortGroupEnumeration) {
            add(element, "gapx 2, align right, gapbottom 3")
        }
    }
