package io.github.inductiveautomation.kindling.internal

import com.formdev.flatlaf.extras.FlatSVGIcon
import io.github.inductiveautomation.kindling.utils.asActionIcon
import org.jdesktop.swingx.JXTable
import org.jdesktop.swingx.decorator.HighlighterFactory
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JLabel
import javax.swing.Popup
import javax.swing.PopupFactory

class DetailsIcon(details: Map<String, String>) : JLabel(DETAILS_ICON) {
    private val table = JXTable(DetailsModel(details.entries.toList())).apply {
        addHighlighter(HighlighterFactory.createSimpleStriping())
        packAll()
    }

    init {
        alignmentY = 0.7F

        addMouseListener(
            object : MouseAdapter() {
                var popup: Popup? = null

                override fun mouseEntered(e: MouseEvent) {
                    popup = PopupFactory.getSharedInstance().getPopup(
                        this@DetailsIcon,
                        table,
                        locationOnScreen.x + DETAILS_ICON.iconWidth,
                        locationOnScreen.y,
                    ).also {
                        it.show()
                    }
                }

                override fun mouseExited(e: MouseEvent) {
                    popup?.hide()
                }
            },
        )
    }

    companion object {
        private val DETAILS_ICON = FlatSVGIcon("icons/bx-search.svg").asActionIcon()
    }
}
