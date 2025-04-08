package io.github.inductiveautomation.kindling.thread.comparison

import com.formdev.flatlaf.extras.FlatSVGIcon
import com.formdev.flatlaf.extras.components.FlatButton
import javax.swing.Icon

internal class BlockerButton : FlatButton() {
    var blocker: Int? = null
        set(value) {
            isVisible = value != null
            text = value?.toString()
            field = value
        }

    init {
        icon = blockedIcon
        toolTipText = "Jump to blocking thread"
        isVisible = false
    }

    private companion object {
        val blockedIcon: Icon = FlatSVGIcon("icons/bx-block.svg").derive(12, 12)
    }
}
