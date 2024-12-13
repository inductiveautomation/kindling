package io.github.inductiveautomation.kindling.core

import com.formdev.flatlaf.extras.FlatSVGIcon
import io.github.inductiveautomation.kindling.utils.Column
import io.github.inductiveautomation.kindling.utils.add
import java.util.EventListener
import javax.swing.JComponent
import javax.swing.JPopupMenu
import javax.swing.event.EventListenerList

fun interface Filter<in T> {
    /**
     * Return true if this filter should display this item.
     */
    fun filter(item: T): Boolean
}

fun interface FilterChangeListener : EventListener {
    fun filterChanged()
}

abstract class FilterPanel<T> : Filter<T> {
    abstract val tabName: String

    abstract fun isFilterApplied(): Boolean

    abstract val component: JComponent

    abstract val icon: FlatSVGIcon

    protected val listeners = EventListenerList()

    fun addFilterChangeListener(listener: FilterChangeListener) {
        listeners.add(listener)
    }

    abstract fun reset()

    abstract fun customizePopupMenu(
        menu: JPopupMenu,
        column: Column<out T, *>,
        event: T,
    )
}
