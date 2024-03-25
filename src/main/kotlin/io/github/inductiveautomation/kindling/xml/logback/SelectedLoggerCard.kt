package io.github.inductiveautomation.kindling.xml.logback

import com.formdev.flatlaf.extras.FlatSVGIcon
import net.miginfocom.swing.MigLayout
import java.awt.Font
import java.awt.event.ItemEvent
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextField
import javax.swing.SwingConstants.RIGHT
import javax.swing.UIManager
import javax.swing.border.LineBorder

internal class SelectedLoggerCard(
    val logger: SelectedLogger,
    private val callback: () -> Unit,
) : JPanel(MigLayout("fill, ins 5, hidemode 3")) {
    val loggerLevelSelector = JComboBox(loggingLevels).apply {
        selectedItem = logger.level
    }

    val loggerSeparateOutput = JCheckBox("Output to separate location?").apply {
        isSelected = logger.separateOutput
    }

    val closeButton = JButton(FlatSVGIcon("icons/bx-x.svg")).apply {
        border = null
        background = null
    }

    val loggerOutputFolder = JTextField(logger.outputFolder).apply {
        addActionListener { callback() }
    }

    val loggerFilenamePattern = JTextField(logger.filenamePattern).apply {
        addActionListener { callback() }
    }

    val maxFileSize = sizeEntryField(logger.maxFileSize, "MB", callback)
    val totalSizeCap = sizeEntryField(logger.totalSizeCap, "MB", callback)
    val maxDays = sizeEntryField(logger.maxDaysHistory, "Days", callback)

    private val redirectOutputPanel = JPanel(MigLayout("ins 0, fill")).apply {
        isVisible = loggerSeparateOutput.isSelected
        loggerSeparateOutput.addItemListener {
            isVisible = it.stateChange == ItemEvent.SELECTED
        }

        add(JLabel("Output Folder", RIGHT), "split 2, spanx, sgx a")
        add(loggerOutputFolder, "growx")
        add(JLabel("Filename Pattern", RIGHT), "split 2, spanx, sgx a")
        add(loggerFilenamePattern, "growx")

        add(JLabel("Max File Size", RIGHT), "sgx a")
        add(maxFileSize, "sgx e, growx")
        add(JLabel("Total Size Cap", RIGHT), "sgx a")
        add(totalSizeCap, "sgx e, growx")
        add(JLabel("Max Days", RIGHT), "sgx a")
        add(maxDays, "sgx e, growx")
    }

    init {
        name = logger.name
        border = LineBorder(UIManager.getColor("Component.borderColor"), 3, true)

        loggerLevelSelector.addActionListener { callback() }
        loggerSeparateOutput.addActionListener { callback() }

        add(
            JLabel(logger.name).apply {
                font = font.deriveFont(Font.BOLD, 14F)
            },
        )
        add(closeButton, "right, wrap")
        add(loggerLevelSelector)
        add(loggerSeparateOutput, "right, wrap")
        add(redirectOutputPanel, "growx, span")
    }

    companion object {
        private val loggingLevels =
            arrayOf(
                "OFF",
                "ERROR",
                "WARN",
                "INFO",
                "DEBUG",
                "TRACE",
                "ALL",
            )
    }
}
