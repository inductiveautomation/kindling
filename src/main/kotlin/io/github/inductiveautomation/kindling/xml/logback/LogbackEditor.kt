package io.github.inductiveautomation.kindling.xml.logback

import com.formdev.flatlaf.extras.FlatSVGIcon
import io.github.inductiveautomation.kindling.core.Kindling.Preferences.General.HomeLocation
import io.github.inductiveautomation.kindling.core.Kindling.Preferences.UI.Theme
import io.github.inductiveautomation.kindling.core.Theme.Companion.theme
import io.github.inductiveautomation.kindling.core.ToolPanel.Companion.exportFileChooser
import io.github.inductiveautomation.kindling.utils.Action
import io.github.inductiveautomation.kindling.utils.DocumentAdapter
import io.github.inductiveautomation.kindling.utils.EDT_SCOPE
import io.github.inductiveautomation.kindling.utils.EmptyBorder
import io.github.inductiveautomation.kindling.utils.FlatScrollPane
import io.github.inductiveautomation.kindling.utils.HorizontalSplitPane
import io.github.inductiveautomation.kindling.utils.chooseFiles
import io.github.inductiveautomation.kindling.utils.debounce
import net.miginfocom.swing.MigLayout
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea
import org.fife.ui.rsyntaxtextarea.SyntaxConstants
import org.fife.ui.rtextarea.RTextScrollPane
import org.jdesktop.swingx.autocomplete.AutoCompleteDecorator
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.awt.event.ItemEvent
import java.io.File
import java.util.Vector
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JFileChooser
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextField
import javax.swing.filechooser.FileNameExtensionFilter

class LogbackEditor(file: List<String>) : JPanel(MigLayout("ins 6, fill, hidemode 3")) {
    private var configData = LogbackConfigData.fromXml(file)

    private val scanEnabled = configData.scan ?: false
    private val scanPeriod = configData.scanPeriod?.filter(Char::isDigit)?.toLong()

    private val scanPeriodField = sizeEntryField(scanPeriod, "Sec", ::updateData).apply {
        isEditable = scanEnabled
    }

    private val scanForChangesCheckbox = JCheckBox("Scan for config changes?", scanEnabled)

    private val loggerItems = javaClass.getResourceAsStream("/loggers.txt")
        ?.bufferedReader()
        ?.useLines { lines ->
            lines.flatMap { line ->
                line.splitToSequence('.').runningReduce { acc, next ->
                    "$acc.$next"
                }
            }.toSet()
        }
        .orEmpty()

    private val loggerComboBox = JComboBox(Vector(loggerItems)).apply {
        isEditable = true
        insertItemAt("", 0)
        selectedIndex = -1
        AutoCompleteDecorator.decorate(this)
        setPrototypeDisplayValue("X".repeat(50))
    }

    private fun SelectedLoggerCard(selectedLogger: SelectedLogger): SelectedLoggerCard {
        return SelectedLoggerCard(selectedLogger, ::updateData).apply {
            closeButton.addActionListener {
                selectedLoggersPanel.remove(this)
                updateData()
                selectedLoggersPanel.revalidate()
            }
        }
    }

    private val addAction = Action("Add logger") {
        val selectedItem = loggerComboBox.selectedItem as String?
        val selectedLoggerNames = selectedLoggersPanel.components.map { it.name }
        if (!selectedItem.isNullOrEmpty() && selectedItem !in selectedLoggerNames) {
            selectedLoggersPanel.add(
                SelectedLoggerCard(SelectedLogger(selectedItem)),
                "growx, shrinkx",
            )
            revalidate()
            updateData()
            loggerComboBox.selectedIndex = -1
        }
    }

    private val selectedLoggersPanel = JPanel(MigLayout("ins 0, fillx, gap 5 5 3 3, wrap 1")).apply {
        for (logger in configData.toSelectedLoggers()) {
            add(SelectedLoggerCard(logger), "growx, shrinkx")
        }
    }

    private val logHomeDir = configData.logHomeDir?.value
    private val logHomeField = JTextField(
        logHomeDir?.replace("\\\\", "\\") ?: HomeLocation.currentValue.toString(),
    )

    private val fileChooser = JFileChooser(HomeLocation.currentValue.toFile()).apply {
        fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
    }

    private val logHomeBrowseAction = Action("Browse") {
        logHomeField.text = fileChooser.chooseFiles(this@LogbackEditor)?.singleOrNull()?.absolutePath
        updateData()
    }

    private val clearAll = Action(
        name = "Clear All",
        description = "Clear all configured loggers",
        icon = FlatSVGIcon("icons/bx-x.svg"),
    ) {
        clearAll()
    }

    private val editorPanel = JPanel(MigLayout("ins 0, fill, wrap 1, hidemode 3")).apply {
        add(JLabel("Log Home Directory"), "growx")
        add(logHomeField, "growx, split 2")
        add(JButton(logHomeBrowseAction), "sgx b")
        add(scanForChangesCheckbox, "split 2")
        add(scanPeriodField, "right, sgx b")

        add(JLabel("Logger Selection"), "growx")
        add(loggerComboBox, "growx, split 2")
        add(JButton(addAction), "sgx b")
        add(JLabel("Configured Loggers"))

        val loggerScrollPane = FlatScrollPane(selectedLoggersPanel) {
            verticalScrollBar.unitIncrement = 16
            border = EmptyBorder()
        }
        add(loggerScrollPane, "grow, push")

        add(JButton(clearAll), "pushx, align right, sgx b")
    }

    private val xmlOutputPreview = RSyntaxTextArea(configData.toXml()).apply {
        lineWrap = true
        isEditable = false
        caretPosition = 0
        syntaxEditingStyle = SyntaxConstants.SYNTAX_STYLE_XML
        theme = Theme.currentValue

        Theme.addChangeListener { newTheme ->
            theme = newTheme
        }
    }

    private val copyXmlAction = Action(
        description = "Copy to Clipboard",
        icon = FlatSVGIcon("icons/bx-clipboard.svg"),
    ) {
        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        clipboard.setContents(StringSelection(configData.toXml()), null)
    }

    private val saveXmlAction = Action(
        description = "Save to file",
        icon = FlatSVGIcon("icons/bx-save.svg"),
    ) {
        updateData()
        exportFileChooser.apply {
            resetChoosableFileFilters()
            fileFilter = FileNameExtensionFilter("XML file", "xml")
            selectedFile = File("logback.xml")
            if (showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
                configData.writeTo(selectedFile.outputStream())
            }
        }
    }

    private val previewPanel = JPanel(MigLayout("ins 0, fill")).apply {
        add(RTextScrollPane(xmlOutputPreview), "push, grow, wrap, span")
        add(JButton(copyXmlAction), "pushx, align right")
        add(JButton(saveXmlAction), "align right")
    }

    private val recreateData = debounce(coroutineScope = EDT_SCOPE) {
        val selectedLoggers = selectedLoggersPanel.components
            .filterIsInstance<SelectedLoggerCard>()
            .map { card ->
                SelectedLogger(
                    name = card.name,
                    level = card.loggerLevelSelector.selectedItem as String,
                    separateOutput = card.loggerSeparateOutput.isSelected,
                    outputFolder = card.loggerOutputFolder.text,
                    filenamePattern = card.loggerFilenamePattern.text,
                    maxFileSize = card.maxFileSize.value as Long,
                    totalSizeCap = card.totalSizeCap.value as Long,
                    maxDaysHistory = card.maxDays.value as Long,
                )
            }

        configData = configData.update(
            logHomeDirectory = LogHomeDirectory(
                "LOG_HOME",
                logHomeField.text.replace("\\", "\\\\"),
            ),
            scan = scanForChangesCheckbox.isSelected.takeIf { it },
            scanPeriod = if (scanForChangesCheckbox.isSelected) {
                "${scanPeriodField.text} seconds"
            } else {
                null
            },
            selectedLoggers,
        )

        val caretPosition = xmlOutputPreview.caretPosition
        xmlOutputPreview.text = configData.toXml()
        xmlOutputPreview.caretPosition = caretPosition.coerceAtMost(xmlOutputPreview.text.length)
    }

    private fun updateData() {
        recreateData.invoke()
    }

    init {
        scanPeriodField.addPropertyChangeListener("value") {
            updateData()
        }
        scanForChangesCheckbox.addItemListener {
            scanPeriodField.isEditable = it.stateChange == ItemEvent.SELECTED
            if (scanPeriodField.isEditable && scanPeriodField.text.isNullOrEmpty()) {
                scanPeriodField.text = "30"
            }
            revalidate()
            updateData()
        }
        logHomeField.document.addDocumentListener(DocumentAdapter { updateData() })

        add(HorizontalSplitPane(editorPanel, previewPanel), "push, grow")
    }

    private fun clearAll() {
        selectedLoggersPanel.apply {
            removeAll()
            revalidate()
            repaint()
            updateData()
        }
    }
}
