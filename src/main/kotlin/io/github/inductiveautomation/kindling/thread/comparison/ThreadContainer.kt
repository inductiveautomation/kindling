package io.github.inductiveautomation.kindling.thread.comparison

import com.formdev.flatlaf.extras.FlatSVGIcon
import com.formdev.flatlaf.extras.components.FlatButton
import com.formdev.flatlaf.extras.components.FlatLabel
import io.github.inductiveautomation.kindling.core.DetailsPane
import io.github.inductiveautomation.kindling.core.Kindling.Preferences.General.UseHyperlinks
import io.github.inductiveautomation.kindling.thread.MultiThreadView.Companion.toDetail
import io.github.inductiveautomation.kindling.thread.MultiThreadViewer.ShowEmptyValues
import io.github.inductiveautomation.kindling.thread.MultiThreadViewer.ShowNullThreads
import io.github.inductiveautomation.kindling.thread.comparison.ThreadComparisonPane.Companion.threadHighlightColor
import io.github.inductiveautomation.kindling.thread.model.Thread
import io.github.inductiveautomation.kindling.utils.dismissOnEscape
import io.github.inductiveautomation.kindling.utils.escapeHtml
import io.github.inductiveautomation.kindling.utils.jFrame
import io.github.inductiveautomation.kindling.utils.tag
import io.github.inductiveautomation.kindling.utils.toBodyLine
import java.text.DecimalFormat
import javax.swing.Icon
import javax.swing.JCheckBox
import javax.swing.JPanel
import javax.swing.UIManager
import kotlin.properties.Delegates
import net.miginfocom.swing.MigLayout

internal class ThreadContainer(
    val index: Int,
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
                jFrame("Thread ${it.name} Details", 900, 500) {
                    contentPane = DetailsPane(listOf(it.toDetail(version)))
                    dismissOnEscape()
                }
            }
        }
    }

    val blockerButton = BlockerButton()

    val diffCheckBox = JCheckBox("Diff").apply {
        isVisible = false
        addActionListener {
            this@ThreadContainer.firePropertyChange("selected", !isSelected, isSelected)
        }
    }

    private val monitors = DetailContainer("Locked Monitors")
    private val synchronizers = DetailContainer("Synchronizers")
    private val stacktrace = DetailContainer("Stacktrace", false)

    init {
        add(
            JPanel(MigLayout("fill, ins 5, hidemode 3")).apply {
                add(detailsButton)
                add(titleLabel, "push, grow, gapleft 8")
                add(diffCheckBox)
                add(blockerButton, "gapleft 8")
            }, "wmax 100%, top, growx"
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

    private companion object {
        val detailIcon: Icon = FlatSVGIcon("icons/bx-link-external.svg").derive(12, 12)
        val percent = DecimalFormat("0.000'%'")
    }
}