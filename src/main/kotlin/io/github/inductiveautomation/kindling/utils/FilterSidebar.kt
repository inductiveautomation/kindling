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

    val filterPanels = panels.filterNotNull().toList()

    override fun createToolTip(): JToolTip = JToolTip().apply {
        font = UIManager.getFont("h3.regular.font")
        minimumSize = Dimension(1, tabHeight)
    }

    override fun getToolTipLocation(event: MouseEvent): Point? {
        return if (event.x <= tabHeight && event.y <= tabHeight * tabCount) {
            Point(
                event.x.coerceAtLeast(tabHeight),
                event.y.floorDiv(tabHeight) * tabHeight,
            )
        } else {
            null
        }
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
        isShowContentSeparators = false

        preferredSize = Dimension(250, 100)

        filterPanels.forEachIndexed { index, filterPanel ->
            addTab(
                null,
                filterPanel.icon,
                filterPanel.component,
                buildString {
                    tag("html") {
                        tag("p", "style" to "margin: 3px;") {
                            append(filterPanel.tabName)
                        }
                    }
                },
            )
            filterPanel.addFilterChangeListener {
                filterPanel.updateTabState()
                selectedIndex = index
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
