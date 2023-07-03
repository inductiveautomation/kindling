package io.github.inductiveautomation.kindling.thread

import com.formdev.flatlaf.extras.FlatSVGIcon
import com.formdev.flatlaf.extras.components.FlatButton
import com.formdev.flatlaf.extras.components.FlatLabel
import com.formdev.flatlaf.extras.components.FlatTextPane
import io.github.inductiveautomation.kindling.core.DetailsPane
import io.github.inductiveautomation.kindling.core.Kindling.Preferences.Advanced.HyperlinkStrategy
import io.github.inductiveautomation.kindling.core.Kindling.Preferences.General.UseHyperlinks
import io.github.inductiveautomation.kindling.thread.MultiThreadView.Companion.toDetail
import io.github.inductiveautomation.kindling.thread.MultiThreadViewer.ShowEmptyValues
import io.github.inductiveautomation.kindling.thread.MultiThreadViewer.ShowNullThreads
import io.github.inductiveautomation.kindling.thread.model.Thread
import io.github.inductiveautomation.kindling.thread.model.ThreadLifespan
import io.github.inductiveautomation.kindling.utils.FlatScrollPane
import io.github.inductiveautomation.kindling.utils.ScrollingTextPane
import io.github.inductiveautomation.kindling.utils.add
import io.github.inductiveautomation.kindling.utils.escapeHtml
import io.github.inductiveautomation.kindling.utils.getAll
import io.github.inductiveautomation.kindling.utils.jFrame
import io.github.inductiveautomation.kindling.utils.tag
import io.github.inductiveautomation.kindling.utils.toBodyLine
import net.miginfocom.swing.MigLayout
import org.jdesktop.swingx.JXTaskPane
import org.jdesktop.swingx.JXTaskPaneContainer
import java.awt.Color
import java.text.DecimalFormat
import java.util.EventListener
import javax.swing.JPanel
import javax.swing.UIManager
import javax.swing.event.EventListenerList
import javax.swing.event.HyperlinkEvent
import javax.swing.text.html.HTMLEditorKit
import kotlin.properties.Delegates

class ThreadComparisonPane(
    totalThreadDumps: Int,
    private val version: String,
) : JPanel(MigLayout("fill, ins 0")) {
    private val listeners = EventListenerList()

    var threads: ThreadLifespan by Delegates.observable(emptyList()) { _, _, _ ->
        updateData()
    }

    private val threadContainers: List<ThreadContainer> = List(totalThreadDumps) {
        ThreadContainer(version).apply {
            blockerButton.addActionListener {
                val blocker = blockerButton.blocker
                if (blocker != null) {
                    fireBlockerSelectedEvent(blocker)
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
            FlatScrollPane(
                JPanel(MigLayout("fill, hidemode 3, ins 0")).apply {
                    for (container in threadContainers) {
                        add(container, "push, grow, sizegroup")
                    }
                },
            ),
            "push, grow",
        )
    }

    private fun updateData() {
        threads.firstOrNull { it != null }?.let {
            header.setText(it)
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

    private class ComparisonEditorKit : HTMLEditorKit() {
        init {
            styleSheet.apply {
                //language=CSS
                addRule(
                    """
                b {
                    font-size: larger;
                }
                pre { 
                    font-size: 10px; 
                }
                object { 
                    padding-left: 16px; 
                }
                    """.trimIndent(),
                )
            }
        }
    }

    private class HeaderPanel : JPanel(MigLayout("fill, ins 3")) {
        private val nameLabel = FlatTextPane().apply {
            editorKit = ComparisonEditorKit()
            isEditable = false
        }

        init {
            add(nameLabel, "pushx, growx")
        }

        fun setText(thread: Thread) {
            nameLabel.text = buildString {
                tag("html") {
                    tag("b", thread.name)
                    append("<br>")

                    tag("b", "ID: ")
                    append(thread.id)
                    tag("b", " | ")

                    tag("b", "Daemon: ")
                    append(thread.isDaemon)
                    tag("b", " | ")

                    if (thread.system != null) {
                        tag("b", "System: ")
                        append(thread.system)
                        tag("b", " | ")
                    }
                    if (thread.scope != null) {
                        tag("b", "Scope: ")
                        append(thread.scope)
                    }
                }
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

    private class DetailContainer(val prefix: String) : JXTaskPane() {
        var itemCount = 0
            set(value) {
                title = "$prefix: $value"
                field = value
            }

        private val scrollingTextPane = ScrollingTextPane().apply {
            addHyperlinkListener { event ->
                if (event.eventType == HyperlinkEvent.EventType.ACTIVATED) {
                    HyperlinkStrategy.currentValue.handleEvent(event)
                }
            }
        }

        var text: String? by scrollingTextPane::text

        init {
            isCollapsed = true
            isAnimated = false

            add(scrollingTextPane)
        }
    }

    private class ThreadContainer(private val version: String) : JXTaskPaneContainer() {
        var thread: Thread? by Delegates.observable(null) { _, _, _ ->
            updateThreadInfo()
        }

        var highlightCpu: Boolean = false
        var highlightStacktrace: Boolean = true

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

        private val monitors = DetailContainer("Locked Monitors")
        private val synchronizers = DetailContainer("Synchronizers")
        private val stacktrace = DetailContainer("Stacktrace").apply {
            isCollapsed = false
        }

        init {
            add(
                JPanel(MigLayout("fill, ins 5, hidemode 3")).apply {
                    add(detailsButton)
                    add(titleLabel, "push, grow, gapleft 8")
                    add(blockerButton)
                },
            )

            add(monitors)
            add(synchronizers)
            add(stacktrace)
        }

        fun updateThreadInfo() {
            isVisible = thread != null || ShowNullThreads.currentValue

            titleLabel.text = buildString {
                tag("html") {
                    tag("div") {
                        tag("b", (thread?.state?.toString() ?: "NO THREAD"))
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
                    isSpecial = highlightStacktrace
                }
            }
        }
    }

    companion object {
        private val blockedIcon = FlatSVGIcon("icons/bx-block.svg").derive(12, 12)
        private val detailIcon = FlatSVGIcon("icons/bx-link-external.svg").derive(12, 12)

        private val percent = DecimalFormat("0.000'%'")

        private val threadHighlightColor: Color
            get() = UIManager.getColor("Component.warning.focusedBorderColor")
    }
}
