package io.github.inductiveautomation.kindling.thread.comparison

import com.formdev.flatlaf.extras.FlatSVGIcon
import com.jidesoft.swing.StyledLabel
import io.github.inductiveautomation.kindling.core.Kindling
import io.github.inductiveautomation.kindling.thread.MultiThreadViewer
import io.github.inductiveautomation.kindling.thread.model.Thread
import io.github.inductiveautomation.kindling.thread.model.ThreadLifespan
import io.github.inductiveautomation.kindling.utils.Action
import io.github.inductiveautomation.kindling.utils.add
import io.github.inductiveautomation.kindling.utils.attachPopupMenu
import io.github.inductiveautomation.kindling.utils.dismissOnEscape
import io.github.inductiveautomation.kindling.utils.getAll
import io.github.inductiveautomation.kindling.utils.jFrame
import io.github.inductiveautomation.kindling.utils.style
import net.miginfocom.swing.MigLayout
import java.awt.Color
import java.awt.Font
import java.util.EventListener
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.UIManager
import javax.swing.event.EventListenerList
import kotlin.properties.Delegates

class ThreadComparisonPane(
    totalThreadDumps: Int,
    version: String,
) : JPanel(MigLayout("fill, ins 0")) {
    private val listeners = EventListenerList()

    var threads: ThreadLifespan by Delegates.observable(emptyList()) { _, _, _ ->
        updateData()
    }

    private val header = HeaderPanel()
    private val body = JPanel((MigLayout("fill, ins 0, hidemode 3")))
    private val footer = if (totalThreadDumps > 3) FooterPanel() else null

    private val threadContainers: List<ThreadContainer> = List(totalThreadDumps) { i ->
        ThreadContainer(i, version).apply {
            blockerButton.addActionListener {
                blockerButton.blocker?.let {
                    fireBlockerSelectedEvent(it)
                }
            }

            if (i + 1 < totalThreadDumps) {
                attachPopupMenu {
                    val next = threads.getOrNull(i + 1) ?: return@attachPopupMenu null
                    JPopupMenu().apply {
                        add(
                            Action("Compare with next trace") {
                                jFrame(
                                    title = "Stacktrace Diff",
                                    width = 1000,
                                    height = 600,
                                ) {
                                    add(ThreadDiffView(threads[i]!!, next))
                                    dismissOnEscape()
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    private var selectionModel = ThreadSelectionModel()

    init {
        for (threadContainer in threadContainers) {
            threadContainer.addPropertyChangeListener("selected") { e ->
                if (e.newValue == true) {
                    selectionModel.add(threadContainer)
                } else {
                    selectionModel.remove(threadContainer)
                }
            }
        }

        MultiThreadViewer.ShowNullThreads.addChangeListener {
            for (container in threadContainers) {
                container.updateThreadInfo()
            }

            footer?.reset()
        }
        MultiThreadViewer.ShowEmptyValues.addChangeListener {
            for (container in threadContainers) {
                container.updateThreadInfo()
            }
        }
        Kindling.Preferences.General.UseHyperlinks.addChangeListener {
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

        selectionModel = ThreadSelectionModel()

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

        val diffSelection = JButton(
            Action("Compare Diffs") {
                val (thread1, thread2) = selectionModel.asList()

                jFrame(
                    title = "Comparing ${thread1.name} from thread dumps ${thread1.index + 1} and ${thread2.index + 1}",
                    width = 1000,
                    height = 600,
                ) {
                    add(ThreadDiffView(thread1.thread!!, thread2.thread!!))
                    dismissOnEscape()
                }
            },
        ).apply {
            isEnabled = false
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

    private inner class FooterPanel : JPanel(MigLayout("fill, ins 3")) {
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

    private inner class ThreadSelectionModel {
        private val internal = ArrayDeque<ThreadContainer>(2)

        init {
            updateEnabled()
        }

        fun add(container: ThreadContainer) {
            if (internal.size == 2) {
                val toDeselect = internal.removeFirst()
                toDeselect.diffCheckBox.isSelected = false
            }
            internal.addLast(container)
            updateEnabled()
        }

        fun remove(container: ThreadContainer) {
            internal.remove(container)
            updateEnabled()
        }

        private fun updateEnabled() {
            header.diffSelection.isEnabled = internal.size == 2
        }

        fun asList(): List<ThreadContainer> = internal.sortedBy { it.index }
    }

    companion object {
        private val nextIcon = FlatSVGIcon("icons/bx-chevron-right.svg")
        private val previousIcon = FlatSVGIcon("icons/bx-chevron-left.svg")

        internal val threadHighlightColor: Color
            get() = UIManager.getColor("Component.warning.focusedBorderColor")
    }
}
