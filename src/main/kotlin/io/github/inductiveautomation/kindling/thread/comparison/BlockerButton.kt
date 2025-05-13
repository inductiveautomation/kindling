package io.github.inductiveautomation.kindling.thread.comparison

import com.formdev.flatlaf.extras.FlatSVGIcon
import com.formdev.flatlaf.extras.components.FlatButton
import io.github.inductiveautomation.kindling.utils.asActionIcon

internal class BlockerButton : FlatButton() {
    var blocker: Long? = null
        set(value) {
            isVisible = value != null
            text = value?.toString()
            field = value
        }

    init {
        icon = FlatSVGIcon("icons/bx-block.svg").asActionIcon()
        toolTipText = "Jump to blocking thread"
        isVisible = false
    }
}
