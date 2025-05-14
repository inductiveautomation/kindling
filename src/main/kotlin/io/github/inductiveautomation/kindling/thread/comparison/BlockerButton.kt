package io.github.inductiveautomation.kindling.thread.comparison

import com.formdev.flatlaf.extras.components.FlatButton
import io.github.inductiveautomation.kindling.utils.FlatActionIcon

internal class BlockerButton : FlatButton() {
    var blocker: Int? = null
        set(value) {
            isVisible = value != null
            text = value?.toString()
            field = value
        }

    init {
        icon = FlatActionIcon("icons/bx-block.svg")
        toolTipText = "Jump to blocking thread"
        isVisible = false
    }
}
