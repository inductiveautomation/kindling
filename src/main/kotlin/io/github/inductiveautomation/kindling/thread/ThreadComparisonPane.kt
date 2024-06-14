package io.github.inductiveautomation.kindling.thread

import com.formdev.flatlaf.extras.FlatSVGIcon
import com.formdev.flatlaf.extras.components.FlatButton
import com.formdev.flatlaf.extras.components.FlatLabel
import com.jidesoft.swing.StyledLabel
import io.github.inductiveautomation.kindling.core.DetailsPane
import io.github.inductiveautomation.kindling.core.Kindling.Preferences.Advanced.HyperlinkStrategy
import io.github.inductiveautomation.kindling.core.Kindling.Preferences.General.UseHyperlinks
import io.github.inductiveautomation.kindling.thread.MultiThreadView.Companion.toDetail
import io.github.inductiveautomation.kindling.thread.MultiThreadViewer.ShowEmptyValues
import io.github.inductiveautomation.kindling.thread.MultiThreadViewer.ShowNullThreads
import io.github.inductiveautomation.kindling.thread.model.Thread
import io.github.inductiveautomation.kindling.thread.model.ThreadLifespan
import io.github.inductiveautomation.kindling.utils.Action
import io.github.inductiveautomation.kindling.utils.diff.DiffView
import io.github.inductiveautomation.kindling.utils.EDT_SCOPE
import io.github.inductiveautomation.kindling.utils.FlatScrollPane
import io.github.inductiveautomation.kindling.utils.add
import io.github.inductiveautomation.kindling.utils.attachPopupMenu
import io.github.inductiveautomation.kindling.utils.escapeHtml
import io.github.inductiveautomation.kindling.utils.getAll
import io.github.inductiveautomation.kindling.utils.jFrame
import io.github.inductiveautomation.kindling.utils.scrollToTop
import io.github.inductiveautomation.kindling.utils.style
import io.github.inductiveautomation.kindling.utils.tag
import io.github.inductiveautomation.kindling.utils.toBodyLine
import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import java.text.DecimalFormat
import java.util.EventListener
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.JTextPane
import javax.swing.UIManager
import javax.swing.event.EventListenerList
import javax.swing.event.HyperlinkEvent
import kotlin.properties.Delegates
import net.miginfocom.swing.MigLayout
import kotlinx.coroutines.launch

class ThreadComparisonPane(
    totalThreadDumps: Int,
    private val version: String,
) : JPanel(MigLayout("fill, ins 0")) {
    private val listeners = EventListenerList()

    var threads: ThreadLifespan by Delegates.observable(emptyList()) { _, _, _ ->
        updateData()
    }

    private val header = HeaderPanel()
    private val body = JPanel((MigLayout("fill, ins 0, hidemode 3")))
    private val footer = if (totalThreadDumps > 3) FooterPanel() else null

    private val threadContainers: List<ThreadContainer> = List(totalThreadDumps) { i ->
        ThreadContainer(version).apply {
            blockerButton.addActionListener {
                blockerButton.blocker?.let {
                    fireBlockerSelectedEvent(it)
                }
            }

            if (i + 1 < totalThreadDumps) {
                attachPopupMenu {
                    JPopupMenu().apply {
                        add(
                            Action("Show diff with Next Trace") {
                                jFrame(
                                    title = "Stacktrace Diff",
                                    width = 1000,
                                    height = 600,
                                ) {
                                    add(DiffView(threads[i]!!.stacktrace, threads[i + 1]!!.stacktrace))
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    private val diffCheckBoxList = threadContainers.map { it.diffCheckBox }

    init {
        for (checkBox in diffCheckBoxList) {
            val others = diffCheckBoxList.filter { it !== checkBox }
            others.forEach { otherCheckBox ->
                otherCheckBox.addItemListener { _ ->
                    checkBox.isEnabled = others.count { it.isSelected } < 2
                }
            }
        }
    }

    private val header = HeaderPanel()

    init {
        ShowNullThreads.addChangeListener {
            for (container in threadContainers) {
                container.updateThreadInfo()
            }

            footer?.reset()
        }
        ShowEmptyValues.addChangeListener {
            for (container in threadContainers) {
                container.updateThreadInfo()
            }
        }
        UseHyperlinks.addChangeListener {
            for (container in threadContainers) {
                container.updateThreadInfo()
            }
        }

        add(header, "growx, spanx")
        add(
            body.apply {
                for ((index, container) in threadContainers.withIndex()) {
                    add(container, "grow, sizegroup, w 33%!")

                    if (index > 2) container.isVisible = false
                }
            },
            "pushy, grow,  spanx, w 100%!",
        )

        if (footer != null) {
            add(footer, "growx")
        }
    }

    private fun updateData() {
        threads.firstOrNull { it != null }?.let {
            header.setThread(it)
        }

        val moreThanOneThread = threads.count { it != null } > 1

        val highestCpu = if (moreThanOneThread) {
            val cpuUsages = threads.map { it?.cpuUsage ?: 0.0 }
            cpuUsages.max().takeIf { maxVal ->
                cpuUsages.count { it == maxVal } == 1
            }
        } else {
            null
        }

        val largestDepth = if (moreThanOneThread) {
            val sizes = threads.map { it?.stacktrace?.size ?: 0 }
            sizes.max().takeIf { maxVal ->
                sizes.count { it == maxVal } == 1
            }
        } else {
            null
        }

        for ((container, thread) in threadContainers.zip(threads)) {
            container.highlightCpu = highestCpu != null && thread?.cpuUsage == highestCpu
            container.highlightStacktrace = largestDepth != null && thread?.stacktrace?.size == largestDepth

            container.thread = thread
        }

        footer?.reset() ?: recalculateConstraints()
    }

    /* Need to calculate the width of the containers after threads have changed. */
    private fun recalculateConstraints() {
        val containerSize = 100 / threadContainers.count { it.isViewable && it.isSelected }
        for (container in threadContainers) {
            (body.layout as MigLayout).setComponentConstraints(
                container,
                "grow, sizegroup, w $containerSize%!",
            )
        }
    }

    fun addBlockerSelectedListener(listener: BlockerSelectedEventListener) {
        listeners.add(listener)
    }

    private fun fireBlockerSelectedEvent(threadID: Int) {
        for (listener in listeners.getAll<BlockerSelectedEventListener>()) {
            listener.onBlockerSelected(threadID)
        }
    }

    fun interface BlockerSelectedEventListener : EventListener {
        fun onBlockerSelected(threadId: Int)
    }

    private inner class HeaderPanel : JPanel(MigLayout("fill, ins 3")) {
        private val nameLabel = StyledLabel().apply {
            isLineWrap = false
        }

        private val diffSelection = JButton(
            Action("Compare Diffs") {
                val selectedIndices = diffCheckBoxList.mapIndexedNotNull { index, jCheckBox ->
                    if (jCheckBox.isSelected) index else null
                }

                // Should never happen but might as well check
                if (selectedIndices.size != 2) {
                    JOptionPane.showMessageDialog(
                        null,
                        "Please Select 2 Thread Dumps",
                        "Unable to show Diff",
                        JOptionPane.ERROR_MESSAGE,
                    )
                } else {
                    val thread1 = threads[selectedIndices[0]]!!
                    val thread2 = threads[selectedIndices[1]]!!

                    val i1 = selectedIndices[0] + 1
                    val i2 = selectedIndices[1] + 1

                    jFrame(
                        title = "Comparing stacks for ${thread1.name} from thread dumps $i1 and $i2",
                        width = 1000,
                        height = 600,
                    ) {
                        add(DiffView(thread1.stacktrace, thread2.stacktrace))
                    }
                }
            },
        ).apply {
            isEnabled = false
            diffCheckBoxList.forEach { checkBox ->
                checkBox.addItemListener { _ ->
                    isEnabled = diffCheckBoxList.count { it.isSelected } == 2
                }
            }
        }

        init {
            add(nameLabel, "pushx, growx")
            add(diffSelection, "east")
        }

        fun setThread(thread: Thread) {
            nameLabel.style {
                add(thread.name, Font.BOLD)
                add("\n")
                add("ID: ", Font.BOLD)
                add(thread.id.toString())
                add(" | ")
                add("Daemon: ", Font.BOLD)
                add(thread.isDaemon.toString())
                add(" | ")

                if (thread.system != null) {
                    add("System: ", Font.BOLD)
                    add(thread.system)
                    add(" | ")
                }
                if (thread.scope != null) {
                    add("Scope: ", Font.BOLD)
                    add(thread.scope)
                }
            }
        }
    }

    inner class FooterPanel : JPanel(MigLayout("fill, ins 3")) {
        private val nextButton = JButton(nextIcon).apply {
            addActionListener { selectNext() }
            isEnabled = false
        }

        private val previousButton = JButton(previousIcon).apply {
            addActionListener { selectPrevious() }
            isEnabled = false
        }

        private val infoLabel = JLabel().apply {
            horizontalAlignment = JLabel.CENTER
        }

        private var selectedIndices = emptyList<Int>()
            set(value) {
                field = value
                nextButton.isEnabled = canSelectNext
                previousButton.isEnabled = canSelectPrevious
                infoLabel.text = "Showing threads from dumps " + value.joinToString(", ", postfix = ".") {
                    (it + 1).toString()
                }

                threadContainers.forEachIndexed { index, threadContainer ->
                    threadContainer.isSelected = index in value
                }

                recalculateConstraints()
            }
        private val canSelectNext: Boolean
            get() {
                if (selectedIndices.isEmpty()) return false
                return threadContainers.indexOfLast { it.isViewable } > selectedIndices.last()
            }

        private val canSelectPrevious: Boolean
            get() {
                if (selectedIndices.isEmpty()) return false
                return threadContainers.indexOfFirst { it.isViewable } < selectedIndices.first()
            }

        init {
            add(previousButton, "west")
            add(infoLabel, "growx")
            add(nextButton, "east")
        }

        fun reset() {
            selectedIndices = threadContainers.mapIndexedNotNull { index, threadContainer ->
                if (threadContainer.isViewable) index else null
            }.take(3).onEach {
                threadContainers[it].isSelected = true
            }
        }

        private fun selectNext() {
            val lastSelectedIndex = selectedIndices.lastOrNull() ?: return
            val nextIndex = threadContainers.withIndex().find { (index, value) ->
                value.isViewable && index > lastSelectedIndex
            }?.index ?: return

            selectedIndices = selectedIndices.drop(1) + listOf(nextIndex)
        }

        private fun selectPrevious() {
            val firstSelectedIndex = selectedIndices.firstOrNull() ?: return
            val previousIndex = threadContainers.subList(0, firstSelectedIndex).indexOfLast { it.isViewable }

            if (previousIndex != -1) {
                selectedIndices = listOf(previousIndex) + selectedIndices.dropLast(1)
            }
        }
    }

    private class BlockerButton : FlatButton() {
        var blocker: Int? = null
            set(value) {
                isVisible = value != null
                text = value?.toString()
                field = value
            }

        init {
            icon = blockedIcon
            toolTipText = "Jump to blocking thread"
            isVisible = false
        }
    }

    private class DetailContainer(
        val prefix: String,
        collapsed: Boolean = true,
    ) : JPanel(MigLayout("ins 0, fill, flowy, hidemode 3")) {
        var isCollapsed: Boolean = collapsed
            set(value) {
                field = value
                scrollPane.isVisible = !value
                headerButton.icon = if (value) expandIcon else collapseIcon

                // Preferred size needs to be recalculated or something like that
                EDT_SCOPE.launch {
                    revalidate()
                    repaint()
                }
            }

        var itemCount = 0
            set(value) {
                headerButton.text = "$prefix: $value"
                field = value
            }

        var isHightlighted: Boolean = false
            set(value) {
                field = value
                if (value) {
                    headerButton.foreground = threadHighlightColor
                } else {
                    headerButton.foreground = UIManager.getColor("Button.foreground")
                }
            }

        private val headerButton = object : JButton(prefix, if (collapsed) expandIcon else collapseIcon) {
            init {
                horizontalTextPosition = LEFT
                horizontalAlignment = LEFT

                addActionListener { isCollapsed = !isCollapsed }
            }

            // This effectively right-aligns the icon
            override fun getIconTextGap(): Int {
                val fm = getFontMetrics(font)
                return size.width - insets.left - insets.right - icon.iconWidth - fm.stringWidth(text)
            }
        }

        private val textArea = JTextPane().apply {
            isEditable = false
            contentType = "text/html"

            addHyperlinkListener { event ->
                if (event.eventType == HyperlinkEvent.EventType.ACTIVATED) {
                    HyperlinkStrategy.currentValue.handleEvent(event)
                }
            }
        }

        private val scrollPane = FlatScrollPane(textArea) {
            horizontalScrollBar.preferredSize = Dimension(0, 10)
            verticalScrollBar.preferredSize = Dimension(10, 0)

            isVisible = !collapsed
        }

        var text: String?
            get() = textArea.text
            set(value) {
                textArea.text = value
            }

        init {
            add(headerButton, "growx, top, wmax 100%")
            add(scrollPane, "push, grow, top, wmax 100%")
        }

        companion object {
            private val collapseIcon = FlatSVGIcon("icons/bx-chevrons-up.svg")
            private val expandIcon = FlatSVGIcon("icons/bx-chevrons-down.svg")
        }
    }

    private class ThreadContainer(
        private val version: String
    ) : JPanel(MigLayout("fill, flowy, hidemode 3, gapy 5, ins 0")) {
        var thread: Thread? by Delegates.observable(null) { _, _, _ ->
            updateThreadInfo()
        }

        var highlightCpu: Boolean = false
        var highlightStacktrace: Boolean = true

        val isViewable: Boolean
            get() = thread != null || ShowNullThreads.currentValue

        var isSelected: Boolean = true
            set(value) {
                field = value
                isVisible = isViewable && value
            }

        private val titleLabel = FlatLabel()
        private val detailsButton = FlatButton().apply {
            icon = detailIcon
            toolTipText = "Open in details popup"
            addActionListener {
                thread?.let {
                    jFrame("Thread ${it.id} Details", 900, 500) {
                        contentPane = DetailsPane(listOf(it.toDetail(version)))
                    }
                }
            }
        }

        val blockerButton = BlockerButton()

        val diffCheckBox = JCheckBox("Diff").apply { isVisible = false }

        private val monitors = DetailContainer("Locked Monitors")
        private val synchronizers = DetailContainer("Synchronizers")
        private val stacktrace = DetailContainer("Stacktrace", false)

        init {
            add(
                JPanel(MigLayout("fill, ins 5, hidemode 3")).apply {
                    add(detailsButton)
                    add(titleLabel, "push, grow, gapleft 8")
                    add(diffCheckBox)
                    add(blockerButton)
                }, "wmax 100%"
            )

            // Ensure that the top two don't get pushed below their preferred size.
            add(monitors, "grow, h pref:pref:300, top, wmax 100%")
            add(synchronizers, "grow, h pref:pref:300, top, wmax 100%")
            add(stacktrace, "push, grow, top, wmax 100%")
        }

        fun updateThreadInfo() {
            isVisible = isViewable && isSelected

            titleLabel.text = buildString {
                tag("html") {
                    tag("div") {
                        tag("b", content = (thread?.state?.toString() ?: "NO THREAD"))
                        append(" - ")
                        append(percent.format(thread?.cpuUsage ?: 0.0))
                    }
                }
            }

            titleLabel.foreground = if (highlightCpu) {
                threadHighlightColor
            } else {
                UIManager.getColor("Label.foreground")
            }

            blockerButton.blocker = thread?.blocker?.owner
            detailsButton.isVisible = thread != null

            monitors.apply {
                isVisible = ShowEmptyValues.currentValue || thread?.lockedMonitors?.isNotEmpty() == true
                if (isVisible) {
                    itemCount = thread?.lockedMonitors?.size ?: 0
                    text = thread?.lockedMonitors
                        ?.joinToString(
                            separator = "\n",
                            prefix = "<html><pre>",
                            postfix = "</pre></html>",
                        ) { monitor ->
                            if (monitor.frame == null) {
                                "lock: ${monitor.lock}"
                            } else {
                                "lock: ${monitor.lock}\n${monitor.frame}"
                            }
                        }
                }
            }

            synchronizers.apply {
                isVisible = ShowEmptyValues.currentValue || thread?.lockedSynchronizers?.isNotEmpty() == true
                if (isVisible) {
                    itemCount = thread?.lockedSynchronizers?.size ?: 0
                    text = thread?.lockedSynchronizers
                        ?.joinToString(
                            separator = "\n",
                            prefix = "<html><pre>",
                            postfix = "</pre></html>",
                            transform = String::escapeHtml,
                        )
                }
            }

            stacktrace.apply {
                isVisible = ShowEmptyValues.currentValue || thread?.stacktrace?.isNotEmpty() == true
                if (isVisible) {
                    itemCount = thread?.stacktrace?.size ?: 0
                    text = thread?.stacktrace
                        ?.joinToString(
                            separator = "\n",
                            prefix = "<html><pre>",
                            postfix = "</pre></html>",
                        ) { stackLine ->
                            if (UseHyperlinks.currentValue) {
                                stackLine.toBodyLine(version).let { (text, link) ->
                                    if (link != null) {
                                        """<a href="$link">$text</a>"""
                                    } else {
                                        text
                                    }
                                }
                            } else {
                                stackLine
                            }
                        }
                    isHightlighted = highlightStacktrace
                }
            }

            diffCheckBox.apply {
                isSelected = false
                isVisible = !thread?.stacktrace.isNullOrEmpty()
            }
        }
    }

    companion object {
        private val blockedIcon = FlatSVGIcon("icons/bx-block.svg").derive(12, 12)
        private val detailIcon = FlatSVGIcon("icons/bx-link-external.svg").derive(12, 12)

        private val percent = DecimalFormat("0.000'%'")

        private val threadHighlightColor: Color
            get() = UIManager.getColor("Component.warning.focusedBorderColor")

        private val nextIcon = FlatSVGIcon("icons/bx-chevron-right.svg")
        private val previousIcon = FlatSVGIcon("icons/bx-chevron-left.svg")
    }
}
