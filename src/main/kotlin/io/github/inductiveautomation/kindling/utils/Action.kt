package io.github.inductiveautomation.kindling.utils

import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.Icon
import javax.swing.KeyStroke
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * More idiomatic Kotlin wrapper for AbstractAction.
 */
open class Action(
    name: String? = null,
    description: String? = null,
    icon: Icon? = null,
    accelerator: KeyStroke? = null,
    selected: Boolean = false,
    private val action: Action.(e: ActionEvent) -> Unit,
) : AbstractAction() {
    var name: String? by actionValue(NAME, name)
    var description: String? by actionValue(SHORT_DESCRIPTION, description)
    var icon: Icon? by actionValue(SMALL_ICON, icon)
    var accelerator: KeyStroke? by actionValue(ACCELERATOR_KEY, accelerator)
    var selected: Boolean by actionValue(SELECTED_KEY, selected)

    protected fun <V> actionValue(
        name: String,
        initialValue: V,
    ) = object : ReadWriteProperty<AbstractAction, V> {
        init {
            putValue(name, initialValue)
        }

        @Suppress("UNCHECKED_CAST")
        override fun getValue(
            thisRef: AbstractAction,
            property: KProperty<*>,
        ): V {
            return thisRef.getValue(name) as V
        }

        override fun setValue(
            thisRef: AbstractAction,
            property: KProperty<*>,
            value: V,
        ) {
            return thisRef.putValue(name, value)
        }
    }

    override fun actionPerformed(e: ActionEvent) = action(this, e)
}
