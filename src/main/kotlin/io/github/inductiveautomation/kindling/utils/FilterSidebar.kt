package io.github.inductiveautomation.kindling.utils

import com.formdev.flatlaf.extras.components.FlatTabbedPane
import io.github.inductiveautomation.kindling.core.FilterPanel
import java.awt.Dimension
import javax.swing.JPopupMenu
import javax.swing.UIManager

class FilterSidebar<T>(
    vararg panels: FilterPanel<T>?,
) : FlatTabbedPane() {
    val filterPanels = panels.filterNotNull()

    init {
        tabLayoutPolicy = SCROLL_TAB_LAYOUT
        tabsPopupPolicy = TabsPopupPolicy.asNeeded
        scrollButtonsPolicy = ScrollButtonsPolicy.never
        tabWidthMode = TabWidthMode.equal
        tabType = TabType.underlined
        tabHeight = 16

        preferredSize = Dimension(250, 100)

        filterPanels.forEachIndexed { i, filterPanel ->
            addTab(filterPanel.tabName, filterPanel.component)

            filterPanel.addFilterChangeListener {
                filterPanel.updateTabState()
                selectedIndex = i
            }
        }

        attachPopupMenu { event ->
            val tabIndex = indexAtLocation(event.x, event.y)
            if (tabIndex == -1) return@attachPopupMenu null

            JPopupMenu().apply {
                add(
                    Action("Reset") {
                        filterPanels[tabIndex].reset()
                    },
                )
            }
        }

        selectedIndex = 0
    }

    private fun FilterPanel<*>.updateTabState() {
        val index = indexOfComponent(component)
        if (isFilterApplied()) {
            setBackgroundAt(index, UIManager.getColor("TabbedPane.focusColor"))
            setTitleAt(index, "$tabName *")
        } else {
            setBackgroundAt(index, UIManager.getColor("TabbedPane.background"))
            setTitleAt(index, tabName)
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
