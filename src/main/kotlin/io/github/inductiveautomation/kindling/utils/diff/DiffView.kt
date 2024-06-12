package io.github.inductiveautomation.kindling.utils.diff

import io.github.inductiveautomation.kindling.core.Kindling.Preferences.UI.Theme
import io.github.inductiveautomation.kindling.core.Theme.Companion.theme
import io.github.inductiveautomation.kindling.utils.FlatScrollPane
import io.github.inductiveautomation.kindling.utils.addLineHighlighter
import java.awt.Color
import java.awt.Font
import java.awt.event.ItemEvent
import javax.swing.BorderFactory
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextArea
import javax.swing.JToggleButton
import javax.swing.SwingUtilities
import net.miginfocom.swing.MigLayout
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea
import org.fife.ui.rtextarea.RTextScrollPane

class DiffView(
    pre: List<String>,
    post: List<String>,
    equalityPredicate: (String, String) -> Boolean = String::equals,
) : JPanel(MigLayout("fill, ins 10, hidemode 3")) {
    // Main data object to get all relevant diff information, lists, etc.
    // Even though this is running on the EDT,
    // in my testing it's fast enough to not matter for any normal stacktrace length
    private val diffUtil = DiffUtil.create(pre, post, equalityPredicate)

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

        text = diffUtil.unifiedDiffList.map {
            it.value.also { s ->
                if (s.length > columnCount) columnCount = s.length
            }
        }.joinToString("\n") {
            it.padEnd(columnCount)
        }

        addLineHighlighter(addBackground) { _, lineNum ->
            diffUtil.unifiedDiffList[lineNum] is Diff.Addition
        }

        addLineHighlighter(delBackground) { _, lineNum ->
            diffUtil.unifiedDiffList[lineNum] is Diff.Deletion
        }
    }

    private val leftTextArea = RSyntaxTextArea(
        diffUtil.leftDiffList.size,
        columnCount,
    ).apply {
        theme = Theme.currentValue
        isEditable = false

        text = diffUtil.leftDiffList.joinToString("\n") {
            if (it is Diff.Addition) " " else it.value.padEnd(columnCount)
        }

        font = Font(Font.MONOSPACED, Font.PLAIN, 12)

        addLineHighlighter(delBackground) { _, lineNum ->
            diffUtil.leftDiffList[lineNum] is Diff.Deletion
        }
    }

    private val rightTextArea = RSyntaxTextArea(
        diffUtil.rightDiffList.size,
        columnCount,
    ).apply {
        theme = Theme.currentValue
        isEditable = false

        text = diffUtil.rightDiffList.joinToString("\n") {
            if (it is Diff.Deletion) " " else it.value.padEnd(columnCount)
        }

        font = Font(Font.MONOSPACED, Font.PLAIN, 12)

        addLineHighlighter(addBackground) { _, lineNum ->
            diffUtil.rightDiffList[lineNum] is Diff.Addition
        }
    }

    // Gutters
    private val sideBySideGutter = JTextArea(
        diffUtil.rightDiffList.size,
        SIDE_BY_SIDE_GUTTER_WIDTH * 2 + 3,
    ).apply {
        val w = SIDE_BY_SIDE_GUTTER_WIDTH
        text = diffUtil.leftDiffList.zip(diffUtil.rightDiffList).joinToString("\n") { (left, right) ->
            val leftText = when (left) {
                is Diff.Addition -> left.key + " ".repeat(w - 1)
                is Diff.Deletion -> left.key + " " + String.format("%${w - 2}d", left.index + 1)
                is Diff.NoChange -> "  " + String.format("%${w - 2}d", left.index + 1)
            }

            val rightText = when (right) {
                is Diff.Deletion -> " ".repeat(w - 1) + right.key
                is Diff.Addition -> String.format("%-${w - 2}d", right.index + 1) + " " + right.key
                is Diff.NoChange -> String.format("%-${w - 2}d", right.index + 1) + "  "
            }

            "$leftText | $rightText"
        }

        font = rightTextArea.font
    }

    private val unifiedGutter = JTextArea(
        diffUtil.unifiedDiffList.size,
        UNIFIED_GUTTER_WIDTH,
    ).apply {
        val w = UNIFIED_GUTTER_WIDTH

        text = diffUtil.unifiedDiffList.joinToString("\n") { diff ->
            when (diff) {
                is Diff.Addition -> {
                    String.format(
                        "%${w - 4}s %${w - 6}d",
                        diff.key,
                        diff.index + 1,
                    )
                }

                is Diff.Deletion -> {
                    String.format(
                        "%-${w - 6}d %-${w - 4}s",
                        diff.index + 1,
                        diff.key,
                    )
                }

                is Diff.NoChange -> {
                    String.format("%-3d %s %3d", diff.preIndex!! + 1, "|", diff.postIndex!! + 1)
                }
            }
        }

        margin = unifiedTextArea.margin
        font = unifiedTextArea.font
    }

    // ScrollPanes

    private val leftScrollPane = FlatScrollPane(leftTextArea) {
        verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_NEVER
        horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS

        setRowHeaderView(verticalScrollBar)
        border = BorderFactory.createEmptyBorder()
    }

    private val rightScrollPane = FlatScrollPane(rightTextArea) {
        horizontalScrollBar.model = leftScrollPane.horizontalScrollBar.model
        verticalScrollBar.model = leftScrollPane.verticalScrollBar.model
        verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_ALWAYS
        horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS

        setRowHeaderView(sideBySideGutter)
        border = BorderFactory.createEmptyBorder()
    }

    private val unifiedScrollPane = RTextScrollPane(unifiedTextArea, false).apply {
        verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_ALWAYS
        horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS

        setRowHeaderView(unifiedGutter)
    }

    private val additionDeletionLabel = JLabel(
        "Showing ${diffUtil.additions.size} Additions and ${diffUtil.deletions.size} Deletions",
    ).apply {
        font = font.deriveFont(Font.BOLD, 14F)
    }

    // Main Views
    private val sideBySide = JPanel(MigLayout("fill, ins 0")).apply {
        add(leftScrollPane, "push, grow")
        add(rightScrollPane, "push, grow")
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
