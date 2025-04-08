package io.github.inductiveautomation.kindling.thread.comparison

import io.github.inductiveautomation.kindling.core.Kindling.Preferences.UI.Theme
import io.github.inductiveautomation.kindling.core.Theme.Companion.theme
import io.github.inductiveautomation.kindling.thread.MultiThreadViewer.DefaultDiffView
import io.github.inductiveautomation.kindling.thread.MultiThreadViewer.DiffViewPreference
import io.github.inductiveautomation.kindling.thread.MultiThreadViewer.DiffViewPreference.SIDE_BY_SIDE
import io.github.inductiveautomation.kindling.thread.MultiThreadViewer.DiffViewPreference.UNIFIED
import io.github.inductiveautomation.kindling.thread.model.Thread
import io.github.inductiveautomation.kindling.utils.Action
import io.github.inductiveautomation.kindling.utils.FlatScrollPane
import io.github.inductiveautomation.kindling.utils.attachPopupMenu
import io.github.inductiveautomation.kindling.utils.configureCellRenderer
import io.github.inductiveautomation.kindling.utils.diff.Diff
import io.github.inductiveautomation.kindling.utils.diff.Difference
import io.github.inductiveautomation.kindling.utils.scrollToTop
import io.github.inductiveautomation.kindling.utils.systemClipboard
import net.miginfocom.swing.MigLayout
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea
import org.fife.ui.rtextarea.RTextScrollPane
import java.awt.Color
import java.awt.Desktop
import java.awt.Font
import java.awt.datatransfer.StringSelection
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.JScrollPane
import javax.swing.JTextArea
import javax.swing.UIManager
import kotlin.io.path.createTempFile
import kotlin.io.path.writeLines

class ThreadDiffView(
    first: Thread,
    second: Thread,
) : JPanel(MigLayout("fill, ins 10, hidemode 3")) {
    /*
       Main data object to get all relevant diff information, lists, etc.
       Even though this is running on the EDT,
       in my testing it's fast enough to not matter for any normal stacktrace length
     */
    private val difference = Difference.of(first.stacktrace, second.stacktrace)

    @Suppress("EnumValuesSoftDeprecate")
    private val viewCombo = JComboBox(DiffViewPreference.values()).apply {
        selectedItem = DefaultDiffView.currentValue

        configureCellRenderer { _, value, _, _, _ ->
            text = when (value) {
                UNIFIED -> "Unified"
                SIDE_BY_SIDE -> "Side-by-Side"
                null -> null
            }
        }

        addItemListener {
            unified.isVisible = selectedItem == UNIFIED
            sideBySide.isVisible = selectedItem == SIDE_BY_SIDE
        }
    }

    private val openInExternalEditor = JButton(
        Action("Open in External Editor") {
            val desktop = Desktop.getDesktop()

            listOf(
                createTempFile(
                    prefix = "stack-${first.id}-${first.name}-original-",
                    suffix = ".txt",
                ).writeLines(difference.original),
                createTempFile(
                    prefix = "stack-${second.id}-${second.name}-modified-",
                    suffix = ".txt",
                ).writeLines(difference.modified),
            ).forEach { desktop.open(it.toFile()) }
        },
    )

    private val header = JLabel(
        "Showing ${difference.additions.size} Additions and ${difference.deletions.size} Deletions",
    ).apply {
        putClientProperty("FlatLaf.styleClass", "h4")
    }

    private val sideBySide = SideBySideView(difference).apply {
        isVisible = DefaultDiffView.currentValue == SIDE_BY_SIDE
    }

    private val unified = UnifiedView(difference.unifiedDiffList, difference.columnCount).apply {
        isVisible = DefaultDiffView.currentValue == UNIFIED
    }

    init {
        add(header)
        add(viewCombo, "align right")
        add(openInExternalEditor, "align right, wrap")
        add(unified, "dock center, span")
        add(sideBySide, "dock center, span")
    }
}

private val Difference<String>.columnCount: Int
    get() = unifiedDiffList.maxOf { it.value.length }

private val addBackground: Color
    get() = if (Theme.currentValue.isDark) Color(9, 230, 100, 40) else Color(0xE6FFEC)

private val delBackground: Color
    get() = if (Theme.currentValue.isDark) Color(255, 106, 70, 40) else Color(0xFFEBE9)

private val monospaced = Font(Font.MONOSPACED, Font.PLAIN, 12)

private class UnifiedView(
    difference: List<Diff<String>>,
    columnCount: Int,
) : JPanel(MigLayout("fill, ins 0")) {
    private val textArea = RSyntaxTextArea().apply {
        highlightCurrentLine = false
        isEditable = false
        theme = Theme.currentValue
        font = monospaced

        fun highlight() {
            removeAllLineHighlights()
            difference.forEachIndexed { i, diff ->
                when (diff) {
                    is Diff.Addition -> addLineHighlight(i, addBackground)
                    is Diff.Deletion -> addLineHighlight(i, delBackground)
                    is Diff.NoChange -> Unit
                }
            }
        }

        Theme.addChangeListener { newTheme ->
            theme = newTheme
            highlight()
        }

        text = difference.joinToString("\n") {
            it.value.padEnd(columnCount)
        }

        highlight()
    }

    private val gutterWidth = 9

    private val gutter = JTextArea(
        difference.size,
        gutterWidth,
    ).apply {
        font = monospaced

        text = difference.joinToString("\n") { diff ->
            when (diff) {
                is Diff.Addition -> "%${gutterWidth - 4}s %${gutterWidth - 6}d".format(diff.key, diff.index + 1)
                is Diff.Deletion -> "%-${gutterWidth - 6}d %-${gutterWidth - 4}s".format(diff.index + 1, diff.key)
                is Diff.NoChange -> "%-3d %s %3d".format(diff.preIndex!! + 1, "|", diff.postIndex!! + 1)
            }
        }

        margin = textArea.margin
    }

    private val scrollPane = RTextScrollPane(textArea, false).apply {
        verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_ALWAYS
        horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS

        setRowHeaderView(this@UnifiedView.gutter)
    }

    init {
        add(scrollPane, "push, grow")

        scrollPane.scrollToTop()

        attachPopupMenu {
            JPopupMenu().apply {
                add(
                    Action("Copy to clipboard") {
                        val gutterLines = gutter.text.split("\n")

                        val outputText = gutterLines.zip(difference).joinToString("\n") { (gutter, diff) ->
                            "$gutter ${diff.value}"
                        }
                        systemClipboard.setContents(StringSelection(outputText), null)
                    },
                )
            }
        }
    }
}

private class SideBySideView(
    difference: Difference<String>,
) : JPanel(MigLayout("fill, ins 0, gap 0")) {
    inner class SplitTextArea(
        private val diffList: List<Diff<String>>,
        private val originalStack: List<String>,
        private val highlighter: (Diff<String>) -> Color?,
        private val omitFromDisplay: (Diff<String>) -> Boolean,
        private val columnCount: Int,
    ) : RSyntaxTextArea(diffList.size, columnCount) {
        fun highlight() {
            removeAllLineHighlights()
            diffList.forEachIndexed { i, diff ->
                highlighter(diff)?.let { color -> addLineHighlight(i, color) }
            }
        }

        override fun createPopupMenu() = JPopupMenu().apply {
            add(
                Action("Copy original stacktrace") {
                    systemClipboard.setContents(StringSelection(originalStack.joinToString("\n")), null)
                },
            )
        }

        init {
            theme = Theme.currentValue
            font = monospaced
            isEditable = false
            highlightCurrentLine = false

            Theme.addChangeListener { newTheme ->
                theme = newTheme
                highlight()
            }

            text = diffList.joinToString("\n") { diff ->
                if (omitFromDisplay(diff)) {
                    " "
                } else {
                    diff.value.padEnd(columnCount)
                }
            }

            highlight()
        }
    }

    private val columnCount = difference.columnCount

    private val leftTextArea = SplitTextArea(
        diffList = difference.leftDiffList,
        originalStack = difference.original,
        highlighter = { if (it is Diff.Deletion) delBackground else null },
        omitFromDisplay = { it is Diff.Addition },
        columnCount = columnCount,
    )

    private val rightTextArea = SplitTextArea(
        diffList = difference.rightDiffList,
        originalStack = difference.modified,
        highlighter = { if (it is Diff.Addition) addBackground else null },
        omitFromDisplay = { it is Diff.Deletion },
        columnCount = columnCount,
    )

    private val gutterWidth = 5

    private val gutter = JTextArea(
        difference.rightDiffList.size,
        gutterWidth * 2 + 3,
    ).apply {
        font = monospaced

        text = difference.leftDiffList.zip(difference.rightDiffList).joinToString("\n") { (left, right) ->
            buildString {
                when (left) {
                    is Diff.Addition -> {
                        append(left.key)
                        append(" ".repeat(gutterWidth - 1))
                    }

                    is Diff.Deletion -> {
                        append(left.key)
                        append(" ")
                        append(String.format("%${gutterWidth - 2}d", left.index + 1))
                    }

                    is Diff.NoChange -> {
                        append("  ")
                        append(String.format("%${gutterWidth - 2}d", left.index + 1))
                    }
                }
                append(" | ")
                when (right) {
                    is Diff.Deletion -> {
                        append(" ".repeat(gutterWidth - 1))
                        append(right.key)
                    }

                    is Diff.Addition -> {
                        append(String.format("%-${gutterWidth - 2}d", right.index + 1))
                        append(" ")
                        append(right.key)
                    }

                    is Diff.NoChange -> {
                        append(String.format("%-${gutterWidth - 2}d", right.index + 1))
                        append("  ")
                    }
                }
            }
        }
    }

    private val leftScrollPane = FlatScrollPane(leftTextArea) {
        verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_NEVER
        horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS

        // Weird way to switch the scrollbar to the other side. Looks nice and symmetric
        setRowHeaderView(verticalScrollBar)
        border = BorderFactory.createEmptyBorder()
        background = null
    }

    private val rightScrollPane = FlatScrollPane(rightTextArea) {
        horizontalScrollBar.model = leftScrollPane.horizontalScrollBar.model
        verticalScrollBar.model = leftScrollPane.verticalScrollBar.model
        verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_ALWAYS
        horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS

        setRowHeaderView(gutter)
        border = BorderFactory.createEmptyBorder()
        background = null
    }

    init {
        add(leftScrollPane, "push, grow")
        add(rightScrollPane, "push, grow")

        border = BorderFactory.createLineBorder(UIManager.getColor("Component.borderColor"))

        leftScrollPane.scrollToTop()
        rightScrollPane.scrollToTop()
    }
}
