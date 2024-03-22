package io.github.inductiveautomation.kindling.utils

import com.formdev.flatlaf.extras.FlatSVGIcon
import org.jdesktop.swingx.JXTable
import org.jdesktop.swingx.renderer.CellContext
import org.jdesktop.swingx.renderer.ComponentProvider
import org.jdesktop.swingx.renderer.DefaultTableRenderer
import org.jdesktop.swingx.renderer.JRendererLabel
import java.awt.Component
import javax.swing.DefaultListCellRenderer
import javax.swing.Icon
import javax.swing.JComboBox
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JTree
import javax.swing.ListCellRenderer
import javax.swing.plaf.basic.BasicComboBoxRenderer
import javax.swing.tree.DefaultTreeCellRenderer
import javax.swing.tree.TreeCellRenderer
import kotlin.reflect.KClass
import kotlin.reflect.safeCast

typealias StringProvider<T> = (T?) -> String?
typealias IconProvider<T> = (T?) -> Icon?

class ReifiedLabelProvider<T : Any>(
    private val valueClass: KClass<T>,
    private val getText: StringProvider<T>,
    private val getIcon: IconProvider<T>,
    private val getTooltip: StringProvider<T>,
) : ComponentProvider<JLabel>() {
    override fun createRendererComponent(): JLabel = JRendererLabel()

    override fun configureState(context: CellContext) {
        // TODO - Color icon when selected
        rendererComponent.horizontalAlignment = horizontalAlignment
    }

    override fun format(context: CellContext) {
        rendererComponent.apply {
            val value = valueClass.safeCast(context.value)
            text = getText(value)
            icon = getIcon(value)
            toolTipText = getTooltip(value)
        }
    }

    companion object {
        private val NULL_ICON = FlatSVGIcon("icons/null.svg")

        fun <T> defaultIconFunction(): IconProvider<T> = {
            if (it == null) {
                NULL_ICON
            } else {
                null
            }
        }

        inline operator fun <reified T : Any> invoke(
            noinline getText: StringProvider<T>,
            noinline getIcon: IconProvider<T> = defaultIconFunction(),
            noinline getTooltip: StringProvider<T> = { null },
        ): ReifiedLabelProvider<T> {
            return ReifiedLabelProvider(T::class, getText, getIcon, getTooltip)
        }

        inline fun <reified T : Any> JXTable.setDefaultRenderer(
            noinline getText: StringProvider<T>,
            noinline getIcon: IconProvider<T> = defaultIconFunction(),
            noinline getTooltip: StringProvider<T> = { null },
        ) {
            this.setDefaultRenderer(
                T::class.java,
                DefaultTableRenderer(ReifiedLabelProvider(getText, getIcon, getTooltip)),
            )
        }
    }
}

inline fun <reified T> listCellRenderer(
    crossinline customize: JLabel.(
        list: JList<*>,
        value: T,
        index: Int,
        selected: Boolean,
        focused: Boolean,
    ) -> Unit,
): ListCellRenderer<Any> {
    return object : DefaultListCellRenderer() {
        override fun getListCellRendererComponent(
            list: JList<*>,
            value: Any?,
            index: Int,
            selected: Boolean,
            focused: Boolean,
        ): Component {
            return super.getListCellRendererComponent(list, value, index, selected, focused).apply {
                try {
                    if (value is T) {
                        customize.invoke(this as JLabel, list, value, index, selected, focused)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
}

fun treeCellRenderer(
    customize: DefaultTreeCellRenderer.(
        tree: JTree,
        value: Any?,
        selected: Boolean,
        expanded: Boolean,
        leaf: Boolean,
        row: Int,
        hasFocus: Boolean,
    ) -> Component,
): TreeCellRenderer {
    return object : DefaultTreeCellRenderer() {
        override fun getTreeCellRendererComponent(
            tree: JTree,
            value: Any?,
            sel: Boolean,
            expanded: Boolean,
            leaf: Boolean,
            row: Int,
            hasFocus: Boolean,
        ): Component {
            val soup = super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus)
            return customize.invoke(soup as DefaultTreeCellRenderer, tree, value, sel, expanded, leaf, row, hasFocus)
        }
    }
}

inline fun <reified T> JComboBox<T>.configureCellRenderer(
    noinline block: BasicComboBoxRenderer.(
        list: JList<*>,
        value: T?,
        index: Int,
        isSelected: Boolean,
        cellHasFocus: Boolean,
    ) -> Unit,
) {
    renderer = object : BasicComboBoxRenderer() {
        override fun getListCellRendererComponent(
            list: JList<*>,
            value: Any?,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean,
        ): Component {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
            block(list, value as T?, index, isSelected, cellHasFocus)
            return this
        }
    }
}
