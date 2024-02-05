package io.github.inductiveautomation.kindling.utils

import com.formdev.flatlaf.extras.components.FlatTabbedPane
import io.github.inductiveautomation.kindling.core.FilterPanel
import java.awt.Dimension
import java.awt.Insets
import java.awt.Point
import java.awt.event.MouseEvent
import javax.swing.JPopupMenu
import javax.swing.JToolTip
import javax.swing.UIManager

class FilterSidebar<T>(
    vararg panels: FilterPanel<T>?,
) : FlatTabbedPane() {
    val filterPanels = panels.filterNotNull()

    private var tabToolTip = JToolTip()

    override fun createToolTip(): JToolTip = tabToolTip

    override fun getToolTipLocation(event: MouseEvent?): Point? {
        val point = Point(event!!.x, event.y)
        if (point.x <= tabHeight) {
            return if (point.y <= tabHeight * tabCount) {
                Point(tabHeight, (point.y / tabHeight) * tabHeight)
            } else {
                null
            }
        }
        return point
    }

    init {
        tabAreaAlignment = TabAreaAlignment.leading
        tabPlacement = LEFT
        tabInsets = Insets(1, 1, 1, 1)
        tabLayoutPolicy = SCROLL_TAB_LAYOUT
        tabsPopupPolicy = TabsPopupPolicy.asNeeded
        scrollButtonsPolicy = ScrollButtonsPolicy.never
        tabWidthMode = TabWidthMode.compact
        tabType = TabType.underlined

        preferredSize = Dimension(250, 100)
        filterPanels.forEachIndexed { i, filterPanel ->
            tabToolTip.apply {
                font = UIManager.getFont("h3.regular.font")
                preferredSize = Dimension((tabHeight * 2.5).toInt(), tabHeight)
                location = Point(tabHeight, tabHeight * i)
            }
            addTab(
                null,
                filterPanel.icon,
                filterPanel.component,
                """<html>
                    <style>
                    .vertical-center {
                    margin: 3px;
                    }
                    </style>
                    <div class="vertical-center">
                    <p>${filterPanel.tabName}</p>
                    </div>
                    </html>""",
            )
            filterPanel.addFilterChangeListener {
                filterPanel.updateTabState()
                selectedIndex = i
            }
        }

        attachPopupMenu { event ->
            val tabIndex = indexAtLocation(event.x, event.y)
            if (tabIndex == -1) return@attachPopupMenu null

            JPopupMenu().apply {
                val filterPanel = filterPanels[tabIndex]
                add(
                    Action("Reset") {
                        filterPanel.reset()
                    },
                )
                if (filterPanel is PopupMenuCustomizer) {
                    filterPanel.customizePopupMenu(this)
                }
            }
        }
        selectedIndex = 0
    }

    private fun FilterPanel<*>.updateTabState() {
        val index = indexOfComponent(component)
        if (isFilterApplied()) {
            setBackgroundAt(index, UIManager.getColor("TabbedPane.focusColor"))
        } else {
            setBackgroundAt(index, UIManager.getColor("TabbedPane.background"))
        }
    }

    override fun updateUI() {
        super.updateUI()
        @Suppress("UNNECESSARY_SAFE_CALL")
        filterPanels?.forEach {
            it.updateTabState()
        }
    }
}
