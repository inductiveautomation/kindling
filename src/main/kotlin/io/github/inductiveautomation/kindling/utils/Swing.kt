package io.github.inductiveautomation.kindling.utils

import com.formdev.flatlaf.extras.FlatSVGIcon
import com.formdev.flatlaf.extras.components.FlatScrollPane
import com.formdev.flatlaf.util.SystemInfo
import com.github.weisj.jsvg.SVGDocument
import com.github.weisj.jsvg.attributes.ViewBox
import com.jidesoft.swing.StyledLabel
import com.jidesoft.swing.StyledLabelBuilder
import io.github.evanrupert.excelkt.workbook
import io.github.inductiveautomation.kindling.core.Kindling.frameIcons
import io.github.inductiveautomation.kindling.utils.ReifiedLabelProvider.Companion.setDefaultRenderer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.swing.Swing
import org.jdesktop.swingx.JXTable
import org.jdesktop.swingx.decorator.ColorHighlighter
import org.jdesktop.swingx.decorator.HighlightPredicate
import org.jdesktop.swingx.renderer.CellContext
import org.jdesktop.swingx.renderer.ComponentProvider
import org.jdesktop.swingx.renderer.DefaultTableRenderer
import org.jdesktop.swingx.renderer.JRendererLabel
import org.jdesktop.swingx.sort.SortController
import org.jdesktop.swingx.table.ColumnControlButton
import java.awt.Color
import java.awt.Component
import java.awt.Container
import java.awt.RenderingHints
import java.awt.Toolkit
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.image.BufferedImage
import java.io.File
import java.io.FileFilter
import java.nio.file.Path
import java.util.Collections
import java.util.Enumeration
import java.util.EventListener
import javax.swing.DefaultListCellRenderer
import javax.swing.DefaultListSelectionModel
import javax.swing.Icon
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JFileChooser
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPopupMenu
import javax.swing.JSplitPane
import javax.swing.JTable
import javax.swing.JTree
import javax.swing.ListCellRenderer
import javax.swing.SortOrder.ASCENDING
import javax.swing.SortOrder.DESCENDING
import javax.swing.SortOrder.UNSORTED
import javax.swing.SwingConstants
import javax.swing.UIManager
import javax.swing.border.EmptyBorder
import javax.swing.event.EventListenerList
import javax.swing.plaf.basic.BasicComboBoxRenderer
import javax.swing.table.TableModel
import javax.swing.text.Document
import javax.swing.tree.DefaultTreeCellRenderer
import javax.swing.tree.TreeCellRenderer
import javax.swing.tree.TreeNode
import kotlin.io.path.extension
import kotlin.io.path.isDirectory
import kotlin.reflect.KClass
import kotlin.reflect.safeCast
import kotlin.time.Duration.Companion.milliseconds
import javax.swing.filechooser.FileFilter as SwingFileFilter

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

fun JTable.selectedRowIndices(): IntArray {
    return selectionModel.selectedIndices
        .filter { isRowSelected(it) }
        .map { convertRowIndexToModel(it) }
        .toIntArray()
}

fun JTable.selectedOrAllRowIndices(): IntArray {
    return if (selectionModel.isSelectionEmpty) {
        IntArray(model.rowCount) { it }
    } else {
        selectedRowIndices()
    }
}

inline fun <reified T> listCellRenderer(crossinline customize: JLabel.(list: JList<*>, value: T, index: Int, selected: Boolean, focused: Boolean) -> Unit): ListCellRenderer<Any> {
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

fun treeCellRenderer(customize: JLabel.(tree: JTree, value: Any?, selected: Boolean, expanded: Boolean, leaf: Boolean, row: Int, hasFocus: Boolean) -> Component): TreeCellRenderer {
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
            return customize.invoke(soup as JLabel, tree, value, sel, expanded, leaf, row, hasFocus)
        }
    }
}

inline fun FlatScrollPane(component: Component, block: FlatScrollPane.() -> Unit = {}): FlatScrollPane {
    return FlatScrollPane().apply {
        setViewportView(component)
        block(this)
    }
}

val Document.text: String
    get() = getText(0, length)

/**
 * A common CoroutineScope bound to the event dispatch thread (see [Dispatchers.Swing]).
 */
val EDT_SCOPE by lazy { CoroutineScope(Dispatchers.Swing) }

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
                    menuFn.invoke(this@attachPopupMenu, e)?.show(e.component, e.x, e.y)
                }
            }
        },
    )
}

fun FlatSVGIcon.derive(colorer: (Color) -> Color): FlatSVGIcon {
    return FlatSVGIcon(name, scale).apply {
        colorFilter = FlatSVGIcon.ColorFilter(colorer)
    }
}

class NoSelectionModel : DefaultListSelectionModel() {
    override fun setSelectionInterval(index0: Int, index1: Int) = Unit
    override fun addSelectionInterval(index0: Int, index1: Int) = Unit
    override fun removeSelectionInterval(index0: Int, index1: Int) = Unit
    override fun getMinSelectionIndex(): Int = -1
    override fun getMaxSelectionIndex(): Int = -1
    override fun isSelectedIndex(index: Int): Boolean = false
    override fun getAnchorSelectionIndex(): Int = -1
    override fun setAnchorSelectionIndex(index: Int) = Unit
    override fun getLeadSelectionIndex(): Int = -1
    override fun setLeadSelectionIndex(index: Int) = Unit
    override fun clearSelection() = Unit
    override fun isSelectionEmpty(): Boolean = true
    override fun insertIndexInterval(index: Int, length: Int, before: Boolean) = Unit
    override fun removeIndexInterval(index0: Int, index1: Int) = Unit
}

class ReifiedJXTable<T : TableModel>(
    model: T,
    private val modelClass: Class<T>,
    columns: ColumnList<*>?,
) : JXTable(model) {
    private val setup = true

    private val packLater: () -> Unit = debounce(
        waitTime = 500.milliseconds,
        coroutineScope = EDT_SCOPE,
        destinationFunction = ::packAll,
    )

    init {
        if (columns != null) {
            columnFactory = columns.toColumnFactory()
            createDefaultColumnsFromModel()
        }
        isColumnControlVisible = true

        setDefaultRenderer<String>(
            getText = { it },
            getTooltip = { it },
        )

        setSortOrderCycle(ASCENDING, DESCENDING, UNSORTED)

        // TODO header name as tooltip without breaking sorting

        addHighlighter(
            object : ColorHighlighter(HighlightPredicate.ODD) {
                override fun getBackground(): Color? = UIManager.getColor("UIColorHighlighter.stripingBackground")
            },
        )

        packLater()
        actionMap.remove("find")
    }

    override fun createDefaultColumnControl(): JComponent {
        return ColumnControlButton(this, FlatSVGIcon("icons/bx-column.svg").derive(0.8F))
    }

    @Suppress("UNCHECKED_CAST")
    override fun getModel(): T = super.getModel() as T

    override fun setModel(model: TableModel) {
        if (setup) { // not sure why this is required, something with Kotlin's late initialization
            require(model::class.java == modelClass) { "Expected $modelClass but got ${model::class.java}" }
        }
        val sortedColumn = sortedColumnIndex
        val sortedColumnIsVisible = convertColumnIndexToView(sortedColumn) != -1

        val sortOrder = if (sortedColumn >= 0) (rowSorter as SortController<*>).getSortOrder(sortedColumn) else null

        val previousColumnSizes = IntArray(columnCount) { i ->
            getColumnExt(i).preferredWidth
        }

        super.setModel(model)

        if (sortOrder != null && sortedColumnIsVisible) {
            setSortOrder(sortedColumn, sortOrder)
        }
        if (setup) {
            for (index in 0 until columnCount) {
                val previousSize = previousColumnSizes.getOrNull(index)
                if (previousSize != null) {
                    getColumnExt(index).preferredWidth = previousSize
                }
            }
            packLater()
        }
    }

    companion object {
        inline operator fun <reified T : TableModel> invoke(
            model: T,
            columns: ColumnList<*>? = null,
        ): ReifiedJXTable<T> {
            return ReifiedJXTable(model, T::class.java, columns)
        }
    }
}

class FileFilter(
    private val description: String,
    private val predicate: (path: Path) -> Boolean,
) : SwingFileFilter(), FileFilter {
    constructor(description: String, extensions: List<String>) : this(description, { path -> path.extension in extensions })

    fun accept(path: Path): Boolean {
        return path.isDirectory() || predicate(path)
    }

    override fun accept(file: File): Boolean = accept(file.toPath())

    override fun getDescription(): String = description
}

fun JFileChooser.chooseFiles(parent: JComponent): List<File>? {
    return if (showOpenDialog(parent) == JFileChooser.APPROVE_OPTION) {
        selectedFiles.toList()
    } else {
        null
    }
}

abstract class AbstractTreeNode : TreeNode {
    open val children: MutableList<TreeNode> = object : ArrayList<TreeNode>() {
        override fun add(element: TreeNode): Boolean {
            element as AbstractTreeNode
            element.parent = this@AbstractTreeNode
            return super.add(element)
        }
    }
    var parent: AbstractTreeNode? = null

    override fun getAllowsChildren(): Boolean = true
    override fun getChildCount(): Int = children.size
    override fun isLeaf(): Boolean = children.isEmpty()
    override fun getChildAt(childIndex: Int): TreeNode = children[childIndex]
    override fun getIndex(node: TreeNode?): Int = children.indexOf(node)
    override fun getParent(): TreeNode? = this.parent
    override fun children(): Enumeration<out TreeNode> = Collections.enumeration(children)
}

abstract class TypedTreeNode<T> : AbstractTreeNode() {
    abstract val userObject: T
}

inline fun <reified T : EventListener> EventListenerList.add(listener: T) {
    add(T::class.java, listener)
}

inline fun <reified T : EventListener> EventListenerList.getAll(): Array<T> {
    return getListeners(T::class.java)
}

/**
 * Constructs and (optionally) immediately displays a JFrame of the given dimensions, centered on the screen.
 */
inline fun jFrame(
    title: String,
    width: Int,
    height: Int,
    initiallyVisible: Boolean = true,
    embedContentIntoTitleBar: Boolean = false,
    block: JFrame.() -> Unit,
) = JFrame(title).apply {
    setSize(width, height)

    if (embedContentIntoTitleBar && SystemInfo.isMacFullWindowContentSupported) {
        rootPane.putClientProperty("apple.awt.windowTitleVisible", false)
        rootPane.putClientProperty("apple.awt.fullWindowContent", true)
        rootPane.putClientProperty("apple.awt.transparentTitleBar", true)
    }

    iconImages = frameIcons
    defaultCloseOperation = JFrame.DISPOSE_ON_CLOSE

    setLocationRelativeTo(null)

    block()

    isVisible = initiallyVisible
}

inline fun <reified T> JComboBox<T>.configureCellRenderer(
    noinline block: BasicComboBoxRenderer.(list: JList<*>, value: T?, index: Int, isSelected: Boolean, cellHasFocus: Boolean) -> Unit,
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

inline fun StyledLabel(block: StyledLabelBuilder.() -> Unit): StyledLabel {
    return StyledLabelBuilder().apply(block).createLabel()
}

inline fun StyledLabel.style(block: StyledLabelBuilder.() -> Unit) {
    StyledLabelBuilder().apply {
        block()
        clearStyleRanges()
        configure(this@style)
    }
}

fun EmptyBorder(): EmptyBorder = EmptyBorder(0, 0, 0, 0)

val TableModel.rowIndices get() = 0 until rowCount
val TableModel.columnIndices get() = 0 until columnCount

fun TableModel.exportToCSV(file: File) {
    file.printWriter().use { out ->
        columnIndices.joinTo(buffer = out, separator = ",") { col ->
            getColumnName(col)
        }
        out.println()
        for (row in rowIndices) {
            columnIndices.joinTo(buffer = out, separator = ",") { col ->
                "\"${getValueAt(row, col)?.toString().orEmpty()}\""
            }
            out.println()
        }
    }
}

fun TableModel.exportToXLSX(file: File) = file.outputStream().use { fos ->
    workbook {
        sheet("Sheet 1") { // TODO: Some way to pipe in a more useful sheet name (or multiple sheets?)
            row {
                for (col in columnIndices) {
                    cell(getColumnName(col))
                }
            }
            for (row in rowIndices) {
                row {
                    for (col in columnIndices) {
                        when (val value = getValueAt(row, col)) {
                            is Double -> cell(
                                value,
                                createCellStyle {
                                    dataFormat = xssfWorkbook.createDataFormat().getFormat("0.00")
                                },
                            )

                            else -> cell(value ?: "")
                        }
                    }
                }
            }
        }
    }.xssfWorkbook.write(fos)
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

val menuShortcutKeyMaskEx = Toolkit.getDefaultToolkit().menuShortcutKeyMaskEx

fun SVGDocument.render(width: Int, height: Int, x: Int = 0, y: Int = 0): BufferedImage {
    return BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB).apply {
        val g = createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        render(null, g, ViewBox(x.toFloat(), y.toFloat(), width.toFloat(), height.toFloat()))
        g.dispose()
    }
}

@Suppress("FunctionName")
fun HorizontalSplitPane(
    left: Component,
    right: Component,
    resizeWeight: Double = 0.5,
    block: JSplitPane.() -> Unit = {},
) = JSplitPane(SwingConstants.VERTICAL, left, right).apply {
    isOneTouchExpandable = true
    this.resizeWeight = resizeWeight

    block()
}

@Suppress("FunctionName")
fun VerticalSplitPane(
    top: Component,
    bottom: Component,
    resizeWeight: Double = 0.5,
    block: JSplitPane.() -> Unit = {},
) = JSplitPane(SwingConstants.HORIZONTAL, top, bottom).apply {
    isOneTouchExpandable = true
    this.resizeWeight = resizeWeight

    block()
}
