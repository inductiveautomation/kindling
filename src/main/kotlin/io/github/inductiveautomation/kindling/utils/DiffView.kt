package io.github.inductiveautomation.kindling.utils

import io.github.inductiveautomation.kindling.core.Kindling.Preferences.UI.Theme
import io.github.inductiveautomation.kindling.core.Theme.Companion.theme
import net.miginfocom.swing.MigLayout
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea
import org.fife.ui.rtextarea.RTextScrollPane
import java.awt.Color
import java.awt.Font
import java.awt.event.ItemEvent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextArea
import javax.swing.JToggleButton
import javax.swing.SwingUtilities

class DiffView<T>(
    pre: List<T>,
    post: List<T>,
    equalityPredicate: (T, T) -> Boolean = { item1, item2 -> item1 == item2 },
) : JPanel(MigLayout("fill, ins 10, hidemode 3")) {
    // Main data object to get all relevant diff information, lists, etc.
    // Even though this is running on the EDT,
    // in my testing it's fast enough to not matter for any normal stacktrace length
    private val diffData = DiffData(pre, post, equalityPredicate)

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

    private var columnCount: Int = 0

    // Text Areas
    private val unifiedTextArea = RSyntaxTextArea().apply {
        theme = Theme.currentValue
        isEditable = false

        text = diffData.unifiedDiffList.map {
            it.value.toString().also { s ->
                if (s.length > columnCount) columnCount = s.length
            }
        }.joinToString("\n") {
            it.padEnd(columnCount)
        }

        addLineHighlighter(addBackground) { _, lineNum ->
            diffData.unifiedDiffList[lineNum].type == DiffType.ADDITION
        }

        addLineHighlighter(delBackground) { _, lineNum ->
            diffData.unifiedDiffList[lineNum].type == DiffType.DELETION
        }
    }

    private val leftTextArea = JTextArea(
        diffData.leftDiffList.size,
        columnCount,
    ).apply {
        isEditable = false

        text = diffData.leftDiffList.joinToString("\n") {
            if (it is Diff.Addition) " " else it.value.toString().padEnd(columnCount)
        }

        font = Font(Font.MONOSPACED, Font.PLAIN, 12)

        addLineHighlighter(delBackground) { _, lineNum ->
            diffData.leftDiffList[lineNum].type == DiffType.DELETION
        }
    }

    private val rightTextArea = JTextArea(
        diffData.rightDiffList.size,
        columnCount,
    ).apply {
        isEditable = false

        text = diffData.rightDiffList.joinToString("\n") {
            if (it is Diff.Deletion) " " else it.value.toString().padEnd(columnCount)
        }

        font = Font(Font.MONOSPACED, Font.PLAIN, 12)

        addLineHighlighter(addBackground) { _, lineNum ->
            diffData.rightDiffList[lineNum].type == DiffType.ADDITION
        }
    }

    // Gutters

    private val leftGutter = JTextArea(
        diffData.leftDiffList.size,
        SIDE_BY_SIDE_GUTTER_WIDTH,
    ).apply {
        val width = SIDE_BY_SIDE_GUTTER_WIDTH
        text = diffData.leftDiffList.joinToString("\n") { diff ->
            when (diff) {
                is Diff.Addition -> diff.type.symbol
                is Diff.Deletion -> diff.type.symbol + " " + String.format("%${width - 2}d", diff.index + 1)
                is Diff.NoChange -> String.format("%${width}d", diff.index + 1)
            }
        }
        font = leftTextArea.font
    }

    private val rightGutter = JTextArea(
        diffData.rightDiffList.size,
        SIDE_BY_SIDE_GUTTER_WIDTH,
    ).apply {
        val width = SIDE_BY_SIDE_GUTTER_WIDTH
        text = diffData.rightDiffList.joinToString("\n") { diff ->
            when (diff) {
                is Diff.Deletion -> diff.type.symbol
                is Diff.Addition -> diff.type.symbol + " " + String.format("%${width - 2}d", diff.index + 1)
                is Diff.NoChange -> String.format("%${width}d", diff.index + 1)
            }
        }
        font = rightTextArea.font
    }

    private val unifiedGutter = JTextArea(
        diffData.unifiedDiffList.size,
        UNIFIED_GUTTER_WIDTH,
    ).apply {
        val width = UNIFIED_GUTTER_WIDTH

        text = diffData.unifiedDiffList.joinToString("\n") { diff ->
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
        font = unifiedTextArea.font
    }

    // ScrollPanes

    private val leftScrollPane = FlatScrollPane(leftTextArea) {
        verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_ALWAYS
        horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS

        setRowHeaderView(leftGutter)
    }

    private val rightScrollPane = FlatScrollPane(rightTextArea) {
        horizontalScrollBar.model = leftScrollPane.horizontalScrollBar.model
        verticalScrollBar.model = leftScrollPane.verticalScrollBar.model
        verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_ALWAYS
        horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS

        setRowHeaderView(rightGutter)
    }

    private val unifiedScrollPane = RTextScrollPane(unifiedTextArea, false).apply {
        verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_ALWAYS
        horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS

        setRowHeaderView(unifiedGutter)
    }

    private val additionDeletionLabel = JLabel(
        "Showing ${diffData.additions.size} Additions and ${diffData.deletions.size} Deletions",
    ).apply {
        font = font.deriveFont(Font.BOLD, 14F)
    }

    // Main Views
    private val sideBySide = JPanel(MigLayout("fill, ins 0")).apply {
        add(leftScrollPane, "push, grow, sg")
        add(rightScrollPane, "push, grow, sg")
    }

    private val singleView = JPanel(MigLayout("fill, ins 3")).apply {
        add(unifiedScrollPane, "push, grow, span")
    }

    init {
        add(additionDeletionLabel)
        add(singleMultiToggle, "align right, wrap")
        add(singleView, "push, grow, span")
        add(sideBySide, "push, grow, span")

        sideBySide.isVisible = false
    }

    companion object {
        private const val SIDE_BY_SIDE_GUTTER_WIDTH = 5
        private const val UNIFIED_GUTTER_WIDTH = 9

        private val addBackground: Color
            get() = if (Theme.currentValue.isDark) Color(9, 230, 100, 40) else Color(0xE6FFEC)

        private val delBackground: Color
            get() = if (Theme.currentValue.isDark) Color(255, 106, 70, 40) else Color(0xFFEBE9)
    }
}
