package io.github.inductiveautomation.kindling.utils

import com.formdev.flatlaf.extras.components.FlatTabbedPane
import java.awt.AlphaComposite
import java.awt.Component
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Point
import java.awt.Rectangle
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.dnd.DnDConstants
import java.awt.dnd.DragGestureEvent
import java.awt.dnd.DragGestureListener
import java.awt.dnd.DragSource
import java.awt.dnd.DragSourceDragEvent
import java.awt.dnd.DragSourceDropEvent
import java.awt.dnd.DragSourceEvent
import java.awt.dnd.DragSourceListener
import java.awt.dnd.DropTarget
import java.awt.dnd.DropTargetDragEvent
import java.awt.dnd.DropTargetDropEvent
import java.awt.dnd.DropTargetEvent
import java.awt.dnd.DropTargetListener
import java.awt.image.BufferedImage
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.SwingUtilities
import javax.swing.UIManager
import kotlin.math.max
import kotlin.math.min

// Adapted from https://java-swing-tips.blogspot.com/2008/04/drag-and-drop-tabs-in-jtabbedpane.html
sealed class DnDTabbedPane : FlatTabbedPane() {
    private val glassPane = GhostGlassPane(this)
    internal var dragTabIndex = -1

    private var rectBackward = Rectangle()
    private var rectForward = Rectangle()

    private fun clickArrowButton(actionKey: String) {
        val b = if ("scrollTabsForwardAction" == actionKey) {
            components?.find { it is JButton }
        } else {
            components?.findLast { it is JButton }
        }
        if (b?.isEnabled == true && b is JButton) {
            b.doClick()
        }
    }

    fun autoScrollTest(glassPt: Point) {
        val r = tabAreaBounds
        rectBackward.setBounds(r.x, r.y, RWH, r.height)
        rectForward.setBounds(r.x + r.width - RWH - BUTTON_SIZE, r.y, RWH + BUTTON_SIZE, r.height)

        rectBackward = SwingUtilities.convertRectangle(parent, rectBackward, glassPane)
        rectForward = SwingUtilities.convertRectangle(parent, rectForward, glassPane)

        if (rectBackward.contains(glassPt)) {
            clickArrowButton("scrollTabsBackwardAction")
        } else if (rectForward.contains(glassPt)) {
            clickArrowButton("scrollTabsForwardAction")
        }
    }

    init {
        glassPane.setName("GlassPane")
        DropTarget(glassPane, DnDConstants.ACTION_COPY_OR_MOVE, TabDropTargetListener(), true)
        DragSource.getDefaultDragSource().createDefaultDragGestureRecognizer(
            this, DnDConstants.ACTION_COPY_OR_MOVE, TabDragGestureListener(),
        )
    }

    fun getTargetTabIndex(glassPanePoint: Point): Int {
        val tabPoint = SwingUtilities.convertPoint(glassPane, glassPanePoint, this)
        val d = Point(1, 0)
        return (0 until tabCount).find { i: Int ->
            val tabBounds = getBoundsAt(i)
            tabBounds.translate(-tabBounds.width * d.x / 2, -tabBounds.height * d.y / 2)
            tabPoint in tabBounds
        } ?: run {
            val count = tabCount
            val r = getBoundsAt(count - 1)
            r.translate(r.width * d.x / 2, r.height * d.y / 2)
            if (tabPoint in r) count else -1
        }
    }

    fun convertTab(prev: Int, next: Int) {
        if (next < 0 || prev == next) {
            return
        }
        val cmp = getComponentAt(prev)
        val tab = getTabComponentAt(prev)
        val title = getTitleAt(prev)
        val icon = getIconAt(prev)
        val tip = getToolTipTextAt(prev)
        val isEnabled = isEnabledAt(prev)
        val tgtIndex = if (prev > next) next else next - 1

        remove(prev)
        insertTab(title, icon, cmp, tip, tgtIndex)

        setEnabledAt(tgtIndex, isEnabled)
        if (isEnabled) {
            setSelectedIndex(tgtIndex)
        }
        setTabComponentAt(tgtIndex, tab)
    }

    fun initTargetLine(next: Int) {
        val isSideNeighbor = next < 0 || dragTabIndex == next || next - dragTabIndex == 1
        if (isSideNeighbor) {
            glassPane.setTargetRect(0, 0, 0, 0)
            return
        }
        val index = max(0, next - 1)
        getBoundsAt(index)?.let { bounds ->
            val r = SwingUtilities.convertRectangle(this, bounds, glassPane)
            val a = min(next, 1)
            glassPane.setTargetRect(
                x = r.x + r.width * a - LINE_SIZE / 2,
                y = r.y,
                width = LINE_SIZE,
                height = r.height,
            )
        }
    }

    fun initGlassPane(tabPt: Point) {
        rootPane.setGlassPane(glassPane)
        val c = getTabComponentAt(dragTabIndex)
        val copy: Component = c ?: run {
            val title = getTitleAt(dragTabIndex)
            val icon = getIconAt(dragTabIndex)
            val label = JLabel(title, icon, LEADING)
            label.setIconTextGap(UIManager.getInt("TabbedPane.textIconGap"))
            label
        }
        val d = copy.preferredSize
        val image = BufferedImage(d.width, d.height, BufferedImage.TYPE_INT_ARGB)
        val g2 = image.createGraphics()
        SwingUtilities.paintComponent(g2, copy, glassPane, 0, 0, d.width, d.height)
        g2.dispose()
        glassPane.setImage(image)
        c?.let { setTabComponentAt(dragTabIndex, it) }

        val glassPt = SwingUtilities.convertPoint(this, tabPt, glassPane)
        glassPane.setPoint(glassPt)
        glassPane.setVisible(true)
    }

    private val tabAreaBounds: Rectangle
        get() {
            val tabbedRect = bounds

            val compRect = selectedComponent?.bounds ?: Rectangle()

            tabbedRect.height -= compRect.height
            tabbedRect.grow(2, 2)
            return tabbedRect
        }

    companion object {
        private const val LINE_SIZE = 3
        private const val RWH = 20
        private const val BUTTON_SIZE = 30 // XXX 30 is magic number of scroll button size
    }
}

internal class TabTransferable(private val tabbedPane: Component) : Transferable {
    override fun getTransferData(flavor: DataFlavor): Any = tabbedPane

    override fun getTransferDataFlavors(): Array<DataFlavor> = FLAVORS

    override fun isDataFlavorSupported(flavor: DataFlavor): Boolean {
        return NAME == flavor.humanPresentableName
    }

    companion object {
        private const val NAME = "tabTransferable"
        private val FLAVORS = arrayOf(DataFlavor(DataFlavor.javaJVMLocalObjectMimeType, NAME))
    }
}

internal class TabDragSourceListener : DragSourceListener {
    override fun dragEnter(e: DragSourceDragEvent) {
        e.dragSourceContext.setCursor(DragSource.DefaultMoveDrop)
    }

    override fun dragExit(e: DragSourceEvent) {
        e.dragSourceContext.setCursor(DragSource.DefaultMoveNoDrop)
    }

    override fun dragOver(e: DragSourceDragEvent) = Unit

    override fun dragDropEnd(e: DragSourceDropEvent) {
        (e.dragSourceContext.component as? JComponent)?.rootPane?.glassPane?.isVisible = false
    }

    override fun dropActionChanged(e: DragSourceDragEvent) = Unit
}

internal class TabDragGestureListener : DragGestureListener {
    private val handler: DragSourceListener = TabDragSourceListener()

    override fun dragGestureRecognized(e: DragGestureEvent) {
        (e.component as? DnDTabbedPane)?.takeIf { it.tabCount > 1 }?.let { startDrag(e, it) }
    }

    private fun startDrag(e: DragGestureEvent, tabs: DnDTabbedPane) {
        val tabLocation = e.dragOrigin
        val idx = tabs.indexAtLocation(tabLocation.x, tabLocation.y)

        tabs.dragTabIndex = idx
        if (tabs.dragTabIndex >= 0 && tabs.isEnabledAt(tabs.dragTabIndex)) {
            tabs.initGlassPane(tabLocation)
            e.startDrag(DragSource.DefaultMoveDrop, TabTransferable(tabs), handler)
        }
    }
}

internal class TabDropTargetListener : DropTargetListener {
    override fun dragEnter(e: DropTargetDragEvent) {
        e.dropTargetContext.component.asGhostGlassPane()?.let {
            val t = e.transferable
            val f = e.currentDataFlavors
            if (t.isDataFlavorSupported(f.first())) {
                e.acceptDrag(e.dropAction)
            } else {
                e.rejectDrag()
            }
        }
    }

    override fun dragExit(e: DropTargetEvent) {
        e.dropTargetContext.component.asGhostGlassPane()?.let { glassPane: GhostGlassPane ->
            glassPane.setPoint(HIDDEN_POINT)
            glassPane.setTargetRect(0, 0, 0, 0)
            glassPane.repaint()
        }
    }

    override fun dropActionChanged(e: DropTargetDragEvent) = Unit

    override fun dragOver(e: DropTargetDragEvent) {
        e.dropTargetContext.component.asGhostGlassPane()?.let { glassPane: GhostGlassPane ->
            val glassPt = e.location
            val tabbedPane = glassPane.tabbedPane
            tabbedPane.initTargetLine(tabbedPane.getTargetTabIndex(glassPt))
            tabbedPane.autoScrollTest(glassPt)
            glassPane.setPoint(glassPt)
            glassPane.repaint()
        }
    }

    override fun drop(e: DropTargetDropEvent) {
        e.dropTargetContext.component.asGhostGlassPane()?.let { glassPane: GhostGlassPane ->
            val tabbedPane = glassPane.tabbedPane
            val t = e.transferable
            val f = t.transferDataFlavors
            val prev = tabbedPane.dragTabIndex
            val next = tabbedPane.getTargetTabIndex(e.location)
            if (t.isDataFlavorSupported(f.first()) && prev != next) {
                tabbedPane.convertTab(prev, next)
                e.dropComplete(true)
            } else {
                e.dropComplete(false)
            }
            glassPane.setVisible(false)
        }
    }

    companion object {
        private val HIDDEN_POINT = Point(0, -1000)

        private fun Component.asGhostGlassPane(): GhostGlassPane? = this as? GhostGlassPane
    }
}

internal class GhostGlassPane(val tabbedPane: DnDTabbedPane) : JComponent() {
    private val lineRect = Rectangle()
    private val lineColor = UIManager.getColor("textHighlight")
    private val location = Point()

    private var draggingGhost: BufferedImage? = null

    fun setTargetRect(x: Int, y: Int, width: Int, height: Int) {
        lineRect.setBounds(x, y, width, height)
    }

    fun setImage(draggingImage: BufferedImage?) {
        draggingGhost = draggingImage
    }

    fun setPoint(pt: Point?) {
        location.location = pt
    }

    override fun isOpaque(): Boolean {
        return false
    }

    override fun setVisible(v: Boolean) {
        super.setVisible(v)
        if (!v) {
            setTargetRect(0, 0, 0, 0)
            setImage(null)
        }
    }

    override fun paintComponent(g: Graphics) {
        val g2 = g.create() as Graphics2D
        g2.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, .5f)

        val ghost = draggingGhost
        if (ghost != null) {
            val xx = location.getX() - ghost.getWidth(this) / 2.0
            val yy = location.getY() - ghost.getHeight(this) / 2.0
            g2.drawImage(ghost, xx.toInt(), yy.toInt(), this)
        }
        g2.paint = lineColor
        g2.fill(lineRect)
        g2.dispose()
    }
}