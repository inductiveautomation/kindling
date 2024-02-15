package io.github.inductiveautomation.kindling.utils

import com.formdev.flatlaf.extras.components.FlatScrollPane
import io.github.inductiveautomation.kindling.core.Kindling.Preferences.UI.Theme
import io.github.inductiveautomation.kindling.core.Theme.Companion.theme
import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import java.awt.event.ItemEvent
import java.awt.event.WindowEvent
import java.awt.event.WindowStateListener
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextArea
import javax.swing.JToggleButton
import javax.swing.SwingUtilities
import javax.swing.text.JTextComponent
import kotlinx.coroutines.launch
import net.miginfocom.swing.MigLayout
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea
import org.fife.ui.rtextarea.RTextScrollPane

class DiffView(
    pre: List<String>,
    post: List<String>,
) : JPanel(MigLayout("fill, ins 10, hidemode 3")), WindowStateListener {
    // Main data object to get all relevant diff information, lists, etc.
    // Even though this is running on the EDT,
    // in my testing it's fast enough to not matter for any normal stacktrace length
    private val diffData = DiffData(pre, post)

    private val singleMultiToggle = JToggleButton("Toggle Unified View").apply {
        addItemListener { event ->
            if (event.stateChange == ItemEvent.SELECTED) {
                singleView.isVisible = false
                sideBySide.isVisible = true
                SwingUtilities.invokeLater {
                    sideBySide.revalidate()
                    sideBySide.repaint()
                }
            } else {
                sideBySide.isVisible = false
                singleView.isVisible = true
                SwingUtilities.invokeLater {
                    singleView.revalidate()
                    singleView.repaint()
                }
            }
        }
    }

    // Text Areas
    private val unifiedTextArea = RSyntaxTextArea().apply {
        theme = Theme.currentValue
        text = diffData.unifiedDiffList.joinToString("\n") { it.value }

        addLineHighlighter(addBackground) { _, lineNum ->
            diffData.unifiedDiffList[lineNum].type == DiffType.ADDITION
        }

        addLineHighlighter(delBackground) { _, lineNum ->
            diffData.unifiedDiffList[lineNum].type == DiffType.DELETION
        }
    }

    private val leftTextArea = RSyntaxTextArea().apply {
        isEditable = false
        text = diffData.leftDiffList.joinToString("\n") {
            if (it is Diff.Addition) " " else it.value
        }

        theme = Theme.currentValue

        addLineHighlighter(delBackground) { _, lineNum ->
            diffData.leftDiffList[lineNum].type == DiffType.DELETION
        }

        preferredSize = unifiedTextArea.preferredSize
    }

    private val rightTextArea = RSyntaxTextArea().apply {
        isEditable = false
        text = diffData.rightDiffList.joinToString("\n") {
            if (it is Diff.Deletion) " " else it.value
        }

        theme = Theme.currentValue

        addLineHighlighter(addBackground) { _, lineNum ->
            diffData.rightDiffList[lineNum].type == DiffType.ADDITION
        }
    }

    // ScrollPanes

    private val leftScrollPane = FlatScrollPane(leftTextArea) {
        verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_ALWAYS
        horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS
    }

    private val rightScrollPane = FlatScrollPane(rightTextArea) {
        horizontalScrollBar.model = leftScrollPane.horizontalScrollBar.model
        verticalScrollBar.model = leftScrollPane.verticalScrollBar.model
        verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_ALWAYS
        horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS
    }

    private val unifiedScrollPane = RTextScrollPane(unifiedTextArea, false).apply {
        verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_ALWAYS
        horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS
    }

    init {
        // Ensures both text areas are the same size so that they scroll the same way, and nothing is cut off
        val prefSize = Dimension(
            unifiedTextArea.preferredSize.width + unifiedScrollPane.verticalScrollBar.preferredSize.width,
            leftTextArea.preferredSize.height,
        )

        leftTextArea.preferredSize = prefSize
        rightTextArea.preferredSize = prefSize
    }

    // Gutters

    private val leftGutter = Gutter(leftScrollPane, Gutter.GutterType.SIDE_BY_SIDE) {
        val width = Gutter.GutterType.SIDE_BY_SIDE.width
        text = diffData.leftDiffList.joinToString("\n") { diff ->
            when (diff) {
                is Diff.Addition -> diff.type.symbol
                is Diff.Deletion -> diff.type.symbol + " " + String.format("%${width - 2}d", diff.index + 1)
                is Diff.NoChange -> String.format("%${width}d", diff.index + 1)
            }
        }
    }

    private val rightGutter = Gutter(rightScrollPane, Gutter.GutterType.SIDE_BY_SIDE) {
        val width = Gutter.GutterType.SIDE_BY_SIDE.width
        text = diffData.rightDiffList.joinToString("\n") { diff ->
            when (diff) {
                is Diff.Deletion -> diff.type.symbol
                is Diff.Addition -> diff.type.symbol + " " + String.format("%${width - 2}d", diff.index + 1)
                is Diff.NoChange -> String.format("%${width}d", diff.index + 1)
            }
        }
    }

    private val unifiedGutter = Gutter(unifiedScrollPane, Gutter.GutterType.UNIFIED) {
        text = diffData.unifiedDiffList.joinToString("\n") { diff ->
            val width = Gutter.GutterType.UNIFIED.width
            when (diff) {
                is Diff.Addition -> {
                    String.format(
                        "%${width - 4}s %${width - 6}d",
                        diff.type.symbol,
                        diff.index + 1,
                    )
                }

                is Diff.Deletion -> {
                    String.format(
                        "%${width - 6}d %-${width - 4}s",
                        diff.index + 1,
                        diff.type.symbol,
                    )
                }

                is Diff.NoChange -> {
                    String.format("%3d %s %3d", diff.preIndex!! + 1, "|", diff.postIndex!! + 1)
                }
            }
        }
    }

    // Labels

    private val deletionLabel = JLabel("(-) ${diffData.deletions.size} Deletions.").apply {
        font = font.deriveFont(Font.BOLD)
    }

    private val additionLabel = JLabel("(+) ${diffData.additions.size} Additions").apply {
        font = font.deriveFont(Font.BOLD)
    }

    // Main Views

    private val sideBySide = JPanel(MigLayout("fill, ins 0")).apply {
        add(deletionLabel, "spanx 2")
        add(additionLabel, "spanx 2, wrap")
        add(leftGutter, "aligny top")
        add(leftScrollPane, "push, grow, sizegroup")
        add(rightGutter, "aligny top")
        add(rightScrollPane, "push, grow, sizegroup")
    }

    private val singleView = JPanel(MigLayout("fill, ins 3")).apply {
        add(unifiedGutter, "aligny top")
        add(unifiedScrollPane, "push, grow, span")
    }

    // Accounts for when the jFrame is maximized
    override fun windowStateChanged(e: WindowEvent?) {
        EDT_SCOPE.launch {
            sideBySide.revalidate()
            sideBySide.repaint()

            singleView.revalidate()
            singleView.repaint()
        }
    }

    init {
        add(singleMultiToggle, "align right, wrap")
        add(singleView, "push, grow, span")
        add(sideBySide, "push, grow, span")

        sideBySide.isVisible = false

        SwingUtilities.invokeLater {
            SwingUtilities.getWindowAncestor(this).addWindowStateListener(this)
            SwingUtilities.getUnwrappedParent(this)
        }
    }

    companion object {
        private val addBackground: Color
            get() = if (Theme.currentValue.isDark) Color(9, 230, 100, 40) else Color(0xE6FFEC)

        private val delBackground: Color
            get() = if (Theme.currentValue.isDark) Color(255, 106, 70, 40) else Color(0xFFEBE9)
    }
}

class Gutter(
    private val mainScrollPane: JScrollPane,
    private val type: GutterType,
    textAreaFormat: JTextArea.() -> Unit,
) : FlatScrollPane() {
    private val mainTextArea = mainScrollPane.viewport.view as JTextComponent

    private val gutterWidth = run {
        val fm = mainTextArea.getFontMetrics(mainTextArea.font)
        fm.stringWidth("X".repeat(type.width)) + 5 // Some padding
    }

    private val textDisplay = JTextArea().apply {
        textAreaFormat()
        font = mainTextArea.font
    }

    init {
        viewport.view = textDisplay

        horizontalScrollBarPolicy = HORIZONTAL_SCROLLBAR_NEVER
        verticalScrollBarPolicy = VERTICAL_SCROLLBAR_NEVER
        verticalScrollBar.model = mainScrollPane.verticalScrollBar.model
    }

    // Ensure the width is always correct, and the height matches the dependent scrollpane
    override fun getMinimumSize() = preferredSize

    override fun getPreferredSize(): Dimension {
        val height = mainScrollPane.height - mainScrollPane.horizontalScrollBar.height
        return Dimension(gutterWidth, height)
    }

    override fun getMaximumSize() = preferredSize

    enum class GutterType(val width: Int) {
        SIDE_BY_SIDE(5),
        UNIFIED(9),
    }
}
