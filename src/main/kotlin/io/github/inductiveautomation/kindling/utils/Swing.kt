package io.github.inductiveautomation.kindling.utils

import com.formdev.flatlaf.extras.FlatSVGIcon
import com.github.weisj.jsvg.SVGDocument
import com.github.weisj.jsvg.attributes.ViewBox
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.swing.Swing
import org.jdesktop.swingx.prompt.BuddySupport
import java.awt.Component
import java.awt.Container
import java.awt.RenderingHints
import java.awt.Toolkit
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.image.BufferedImage
import java.io.File
import java.util.EventListener
import javax.swing.JComponent
import javax.swing.JFileChooser
import javax.swing.JPopupMenu
import javax.swing.JTextField
import javax.swing.SwingUtilities
import javax.swing.UIManager
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.event.EventListenerList
import javax.swing.text.Document

/**
 * A common CoroutineScope bound to the event dispatch thread (see [Dispatchers.Swing]).
 */
val EDT_SCOPE by lazy { CoroutineScope(Dispatchers.Swing) }

val menuShortcutKeyMaskEx = Toolkit.getDefaultToolkit().menuShortcutKeyMaskEx

val Document.text: String
    get() = getText(0, length)

inline fun <T : Component> T.attachPopupMenu(
    crossinline menuFn: T.(event: MouseEvent) -> JPopupMenu?,
) {
    addMouseListener(
        object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                maybeShowPopup(e)
            }

            override fun mouseReleased(e: MouseEvent) {
                maybeShowPopup(e)
            }

            private fun maybeShowPopup(e: MouseEvent) {
                if (e.isPopupTrigger) {
                    e.consume()
                    menuFn.invoke(this@attachPopupMenu, e)?.show(e.component, e.x, e.y)
                }
            }
        },
    )
}

fun FlatSVGIcon.asActionIcon(selected: Boolean = false): FlatSVGIcon {
    return FlatSVGIcon(name, 0.75F).apply {
        if (selected) {
            colorFilter = FlatSVGIcon.ColorFilter { UIManager.getColor("Tree.selectionForeground") }
        }
    }
}

fun JFileChooser.chooseFiles(parent: JComponent): List<File>? {
    return if (showOpenDialog(parent) == JFileChooser.APPROVE_OPTION) {
        selectedFiles.toList()
    } else {
        null
    }
}

inline fun <reified T : EventListener> EventListenerList.add(listener: T) {
    add(T::class.java, listener)
}

inline fun <reified T : EventListener> EventListenerList.getAll(): Array<T> {
    return getListeners(T::class.java)
}

fun Component.traverseChildren(): Sequence<Component> = sequence {
    if (this@traverseChildren is Container) {
        val childComponents = synchronized(treeLock) { components.copyOf() }
        for (component in childComponents) {
            yield(component)
            yieldAll(component.traverseChildren())
        }
    }
}

fun SVGDocument.render(width: Int, height: Int, x: Int = 0, y: Int = 0): BufferedImage {
    return BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB).apply {
        val g = createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        render(null, g, ViewBox(x.toFloat(), y.toFloat(), width.toFloat(), height.toFloat()))
        g.dispose()
    }
}

inline fun <reified C> Component.getAncestorOfClass(): C? {
    return SwingUtilities.getAncestorOfClass(C::class.java, this) as? C
}

var JTextField.leftBuddy: JComponent?
    get() {
        return BuddySupport.getLeft(this)?.firstOrNull() as? JComponent
    }
    set(buddy) {
        BuddySupport.addLeft(buddy, this)
    }

var JTextField.rightBuddy: JComponent?
    get() {
        return BuddySupport.getRight(this)?.firstOrNull() as? JComponent
    }
    set(buddy) {
        BuddySupport.addRight(buddy, this)
    }

@Suppress("FunctionName")
fun DocumentAdapter(block: (e: DocumentEvent) -> Unit): DocumentListener = object : DocumentListener {
    override fun changedUpdate(e: DocumentEvent) = block(e)
    override fun insertUpdate(e: DocumentEvent) = block(e)
    override fun removeUpdate(e: DocumentEvent) = block(e)
}
