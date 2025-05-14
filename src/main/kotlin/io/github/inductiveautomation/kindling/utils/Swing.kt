package io.github.inductiveautomation.kindling.utils

import com.formdev.flatlaf.extras.FlatSVGIcon
import com.github.weisj.jsvg.SVGDocument
import com.github.weisj.jsvg.view.ViewBox
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.swing.Swing
import org.jdesktop.swingx.decorator.AbstractHighlighter
import org.jdesktop.swingx.decorator.ColorHighlighter
import org.jdesktop.swingx.decorator.ComponentAdapter
import org.jdesktop.swingx.decorator.Highlighter
import org.jdesktop.swingx.prompt.BuddySupport
import java.awt.Color
import java.awt.Component
import java.awt.Container
import java.awt.Point
import java.awt.RenderingHints
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.image.BufferedImage
import java.io.File
import java.util.EventListener
import javax.swing.JComponent
import javax.swing.JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT
import javax.swing.JFileChooser
import javax.swing.JFrame
import javax.swing.JPopupMenu
import javax.swing.JScrollPane
import javax.swing.JTextField
import javax.swing.KeyStroke
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

/**
 * A convenience property to get or set the contents of the system clipboard as a string.
 */
var Toolkit.clipboardString: String?
    get() {
        return runCatching {
            systemClipboard.getData(DataFlavor.stringFlavor) as? String
        }.getOrNull()
    }
    set(value) {
        systemClipboard.setContents(StringSelection(value), null)
    }

val Document.text: String
    get() = getText(0, length)

fun JFrame.dismissOnEscape() {
    rootPane.actionMap.put(
        "dismiss",
        Action {
            dispose()
        },
    )
    rootPane.getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
        .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "dismiss")
}

inline fun <T : Component> T.attachPopupMenu(crossinline menuFn: T.(event: MouseEvent) -> JPopupMenu?) {
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

fun JFileChooser.chooseFiles(parent: JComponent?): List<File>? {
    return if (showOpenDialog(parent) == JFileChooser.APPROVE_OPTION) {
        selectedFiles.toList()
    } else {
        null
    }
}

inline fun <reified T : EventListener> EventListenerList.add(listener: T) {
    add(T::class.java, listener)
}

inline fun <reified T : EventListener> EventListenerList.remove(listener: T) {
    remove(T::class.java, listener)
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
        render(null as Component?, g, ViewBox(x.toFloat(), y.toFloat(), width.toFloat(), height.toFloat()))
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

fun JScrollPane.scrollToTop() = EDT_SCOPE.launch {
    viewport.viewPosition = Point(0, 0)
}

@Suppress("FunctionName")
fun DocumentAdapter(block: (e: DocumentEvent) -> Unit): DocumentListener = object : DocumentListener {
    override fun changedUpdate(e: DocumentEvent) = block(e)
    override fun insertUpdate(e: DocumentEvent) = block(e)
    override fun removeUpdate(e: DocumentEvent) = block(e)
}

typealias HighlightPredicateKt = (component: Component, adapter: ComponentAdapter) -> Boolean

data class ColorPalette(
    val background: Color?,
    val foreground: Color?,
) {
    fun toHighLighter(
        predicate: HighlightPredicateKt = { _, _ -> true },
    ): ColorHighlighter {
        return ColorHighlighter(predicate, background, foreground)
    }
}

fun ColorHighlighter(
    background: Color?,
    foreground: Color?,
    predicate: HighlightPredicateKt = { _, _ -> true },
) = ColorHighlighter(predicate, background, foreground)

@Suppress("FunctionName")
fun ColorHighlighter(
    fgSupplier: (() -> Color)?,
    bgSupplier: (() -> Color)?,
    predicate: HighlightPredicateKt = { _, _ -> true },
): Highlighter = object : AbstractHighlighter(predicate) {
    override fun doHighlight(
        target: Component,
        adapter: ComponentAdapter,
    ): Component {
        return target.apply {
            fgSupplier?.invoke()?.let { foreground = it }
            bgSupplier?.invoke()?.let { background = it }
        }
    }
}

@OptIn(ExperimentalStdlibApi::class)
fun Color.toHexString(alpha: Boolean = false): String {
    val hexString = rgb.toHexString()
    return "#${
        if (alpha) {
            hexString
        } else {
            hexString.substring(2)
        }
    }"
}
