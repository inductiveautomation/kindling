package io.github.inductiveautomation.kindling.xml

import com.formdev.flatlaf.extras.FlatSVGIcon
import io.github.inductiveautomation.kindling.core.ToolPanel
import io.github.inductiveautomation.kindling.utils.NumericEntryField
import io.github.inductiveautomation.kindling.utils.chooseFiles
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import net.miginfocom.swing.MigLayout
import org.jdesktop.swingx.autocomplete.AutoCompleteDecorator
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import java.nio.file.Path
import javax.swing.BorderFactory
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JFileChooser
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JSplitPane
import javax.swing.JTextArea
import javax.swing.JTextField
import javax.swing.SwingConstants
import javax.swing.SwingUtilities
import javax.swing.UIManager
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.filechooser.FileNameExtensionFilter
import kotlin.io.path.name

class LogbackEditor(path: Path) : ToolPanel() {
    private val configsFromXml = LogbackConfigDeserializer().getObjectFromXML(path.toString()) ?: LogbackConfigData()
    private val logbackConfigManager = LogbackConfigManager(configsFromXml)

    private val selectedLoggersList = logbackConfigManager.getLoggerConfigs()

    private val directorySelectorPanel = DirectorySelectorPanel()
    private val scanForChangesPanel = ScanForChangesPanel()
    private val generalConfigPanel =
        JPanel(MigLayout("fill, ins 0")).apply {
            add(directorySelectorPanel, "growx, wrap")
            add(scanForChangesPanel, "growx, wrap")
        }
    private val loggerConfigPanel = LoggerSelectorPanel()
    private val clearAllButton =
        JButton("Clear all selected loggers").apply {
            addActionListener {
                loggerConfigPanel.clearAll()
                updateData()
            }
        }
    private val editorPanel =
        JPanel(MigLayout("fill, ins 10")).apply {
            add(generalConfigPanel, "growx, wrap")
            add(loggerConfigPanel, "push, grow, wrap")
            add(clearAllButton, "growx")
        }

    private val xmlOutputPreview =
        JTextArea().apply {
            lineWrap = true
            isEditable = false
            font = UIManager.getFont("monospaced.font")
            text = logbackConfigManager.getXmlString()
            caretPosition = 0
        }
    private val copyXmlButton =
        JButton("Copy to clipboard").apply {
            addActionListener {
                val clipboard = Toolkit.getDefaultToolkit().systemClipboard
                clipboard.setContents(StringSelection(xmlOutputPreview.text), null)
            }
        }
    private val saveXmlButton =
        JButton("Save XML file").apply {
            addActionListener {
                updateData()
                JFileChooser().apply {
                    fileFilter = FileNameExtensionFilter("XML file", "xml")
                    selectedFile = File("logback.xml")
                    val save = showSaveDialog(null)
                    if (save == JFileChooser.APPROVE_OPTION) {
                        logbackConfigManager.writeXmlFile(selectedFile.absolutePath)
                    }
                }
            }
        }

    private val scrollPane =
        JScrollPane(xmlOutputPreview).apply {
            verticalScrollBar.unitIncrement = 16
        }
    private val previewPanel =
        JPanel(MigLayout("fill, ins 10")).apply {
            add(JLabel("XML Output Preview"), "north, growx, wrap")
            add(scrollPane, "push, grow, wrap")
            add(copyXmlButton, "growx, wrap")
            add(saveXmlButton, "growx")
        }

    fun updateData() {
        val temp = xmlOutputPreview.caretPosition

        logbackConfigManager.configs.logHomeDir =
            LogHomeDirectory(
                "LOG_HOME",
                directorySelectorPanel.logHomeField.text.replace("\\", "\\\\"),
            )
        logbackConfigManager.configs.scan = if (scanForChangesPanel.scanForChangesCheckbox.isSelected) true else null
        logbackConfigManager.configs.scanPeriod =
            if (scanForChangesPanel.scanForChangesCheckbox.isSelected) {
                "${scanForChangesPanel.scanPeriodField.text} seconds"
            } else {
                null
            }

        loggerConfigPanel.selectedLoggersPanel.components.forEachIndexed { index, selectedLoggerCard ->
            selectedLoggersList[index].level = (selectedLoggerCard as SelectedLoggerCard).loggerLevelSelector.selectedItem as String
            selectedLoggersList[index].separateOutput = selectedLoggerCard.loggerSeparateOutput.isSelected
            selectedLoggersList[index].outputFolder = selectedLoggerCard.loggerOutputFolder.text
            selectedLoggersList[index].filenamePattern = selectedLoggerCard.loggerFilenamePattern.text
            selectedLoggersList[index].maxFileSize = selectedLoggerCard.maxFileSize.textField.value as Long
            selectedLoggersList[index].totalSizeCap = selectedLoggerCard.totalSizeCap.textField.value as Long
            selectedLoggersList[index].maxDaysHistory = selectedLoggerCard.maxDays.textField.value as Long
        }
        logbackConfigManager.updateLoggerConfigs(selectedLoggersList)

        xmlOutputPreview.text = logbackConfigManager.getXmlString()
        xmlOutputPreview.caretPosition = if (temp > xmlOutputPreview.text.length) xmlOutputPreview.text.length else temp
    }

    init {
        name = path.name
        toolTipText = path.toString()
        add(
            JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT,
                editorPanel,
                previewPanel,
            ).apply {
                isOneTouchExpandable = true
                resizeWeight = 0.5
            },
            "push, grow",
        )
        updateData()
    }

    inner class DirectorySelectorPanel : JPanel(MigLayout("fill, ins 0")) {
        private val logHomeDir = logbackConfigManager.configs.logHomeDir?.value
        private var logHomePath = logHomeDir?.replace("\\\\", "\\") ?: System.getProperty("user.home")
        val logHomeField = JTextField(logHomePath)

        private val fileChooser =
            JFileChooser().apply {
                fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                addActionListener {
                    if (selectedFile != null) {
                        logHomePath = selectedFile.absolutePath
                    }
                }
            }

        private val logHomeBrowseButton =
            JButton("Browse").apply {
                addActionListener {
                    fileChooser.chooseFiles(this@DirectorySelectorPanel)
                    this@DirectorySelectorPanel.logHomeField.text = logHomePath
                    updateData()
                }
            }

        init {
            add(JLabel("Log Home Directory"), "growx, wrap")
            add(logHomeField, "growx, push, split 2")
            add(logHomeBrowseButton, "w 100!")
        }
    }

    override fun updateUI() {
        super.updateUI()
        SwingUtilities.invokeLater {
            xmlOutputPreview.font = UIManager.getFont("monospaced.font")
            loggerConfigPanel.scrollPane.border = null
        }
    }

    inner class ScanForChangesPanel : JPanel(MigLayout("fill, hidemode 3, ins 0")) {
        private val scanEnabled = logbackConfigManager.configs.scan ?: false
        private val scanPeriod = logbackConfigManager.configs.scanPeriod?.filter(Char::isDigit)?.toLong() ?: 30

        val scanForChangesCheckbox =
            JCheckBox("Scan for config changes?").apply {
                isSelected = scanEnabled
                addActionListener {
                    this@ScanForChangesPanel.customEntryPanel.isVisible = this.isSelected
                    updateData()
                }
            }

        val scanPeriodField =
            NumericEntryField(scanPeriod).apply {
                addNumericChangeListener(::updateData)
            }

        private val customEntryPanel =
            JPanel(MigLayout("fill, ins 0")).apply {
                add(JLabel("Scan period (sec):"), "cell 0 0")
                add(scanPeriodField, "cell 0 0, w 100")
                isVisible = scanEnabled
            }

        init {
            add(scanForChangesCheckbox, "growx, split 2, wrap")
            add(customEntryPanel, "growx")
        }
    }

    inner class LoggerSelectorPanel : JPanel(MigLayout("fill, ins 0")) {
        private val loggerItems = getLoggerList()

        @OptIn(ExperimentalSerializationApi::class)
        private fun getLoggerList(): Array<String> {
            val filename = "/loggers.json"
            val stream = javaClass.getResourceAsStream(filename)!!
            val loggerList = Json.decodeFromStream<List<IgnitionLogger>>(stream)
            return loggerList.map { it.name }.toTypedArray()
        }

        private val loggerComboBox =
            JComboBox(loggerItems).apply {
                isEditable = true
                insertItemAt("", 0)
                selectedIndex = -1
                AutoCompleteDecorator.decorate(this)
                setPrototypeDisplayValue("X".repeat(50))
            }

        private val addButton =
            JButton("Add logger").apply {
                addActionListener {
                    if (loggerComboBox.selectedItem != null &&
                        loggerComboBox.selectedItem != "" &&
                        (loggerComboBox.selectedItem as String) !in selectedLoggersList.map { logger -> logger.name }
                    ) {
                        selectedLoggersList.add(SelectedLogger((loggerComboBox.selectedItem as String)))
                        selectedLoggersPanel.add(
                            SelectedLoggerCard(selectedLoggersList.last()),
                            "north, growx, shrinkx, wrap, gap 5 5 3 3",
                        )
                        revalidate()
                        updateData()
                        loggerComboBox.selectedIndex = -1
                    }
                }
            }

        val selectedLoggersPanel =
            JPanel(MigLayout("fill, ins 5, hidemode 0")).apply {
                selectedLoggersList.forEach { selectedLogger ->
                    add(SelectedLoggerCard(selectedLogger), "north, growx, shrinkx, wrap, gap 5 5 3 3")
                }
            }

        val scrollPane =
            JScrollPane(selectedLoggersPanel).apply {
                verticalScrollBar.unitIncrement = 16
            }

        fun clearAll() {
            selectedLoggersList.removeAll(selectedLoggersList)
            selectedLoggersPanel.apply {
                removeAll()
                revalidate()
                repaint()
                updateData()
            }
        }

        init {
            add(JLabel("Logger Selection"), "growx, wrap")
            add(loggerComboBox, "growx, split 2")
            add(addButton, "w 100!, wrap")
            add(JLabel("Selected loggers:"), "wrap")
            add(
                JPanel(MigLayout("fill, hidemode 0"))
                    .apply {
                        add(scrollPane, "north, grow, push, wrap")
                        border = BorderFactory.createLineBorder(UIManager.getColor("Component.borderColor"))
                    },
                "grow, push",
            )
        }
    }

    inner class SizeEntryField(
        label: String,
        inputValue: Long,
        unitValue: String,
    ) : JPanel(MigLayout("fill, ins 0")) {
        val textField =
            NumericEntryField(inputValue).apply {
                addNumericChangeListener(::updateData)
            }

        private val unit =
            JTextField(unitValue).apply {
                isEditable = false
                horizontalAlignment = SwingConstants.CENTER
            }

        init {
            add(
                JLabel(label).apply {
                    horizontalAlignment = SwingConstants.CENTER
                },
                "center, grow, shrinkx, wrap",
            )
            add(
                JPanel(MigLayout("fill, ins 0")).apply {
                    border = BorderFactory.createLineBorder(UIManager.getColor("Component.borderColor"))
                    add(textField, "wmin 60, grow, gap 0")
                    add(unit, "w 40!, growy, gap 0")
                },
                "grow, shrinkx",
            )
        }

        override fun updateUI() {
            super.updateUI()
            SwingUtilities.invokeLater {
                textField.border = null
                unit.border = null
                unit.foreground = UIManager.getColor("TextArea.inactiveForeground")
            }
        }
    }

    inner class SelectedLoggerCard(logger: SelectedLogger) : JPanel(MigLayout("fill, ins 5, hidemode 3")) {
        private val loggingLevels = arrayOf("OFF", "ERROR", "WARN", "INFO", "DEBUG", "TRACE", "ALL")
        val loggerLevelSelector =
            JComboBox(loggingLevels).apply {
                selectedItem = logger.level
                addActionListener {
                    updateData()
                }
            }

        val loggerSeparateOutput =
            JCheckBox("Output to separate location?").apply {
                this.isSelected = logger.separateOutput
                addActionListener {
                    this@SelectedLoggerCard.separateOutputOptions.isVisible = this.isSelected
                    updateData()
                }
            }

        private val closeButton =
            JButton(FlatSVGIcon("icons/bx-x.svg")).apply {
                addActionListener {
                    selectedLoggersList.remove(logger)
                    loggerConfigPanel.selectedLoggersPanel.components.forEachIndexed { index, component ->
                        if ((component as SelectedLoggerCard).name == logger.name) {
                            loggerConfigPanel.selectedLoggersPanel.remove(index)
                        }
                    }
                    updateData()
                }
            }

        val loggerOutputFolder =
            JTextField(logger.outputFolder).apply {
                document.addDocumentListener(
                    object : DocumentListener {
                        override fun insertUpdate(e: DocumentEvent?) = updateData()

                        override fun removeUpdate(e: DocumentEvent?) = updateData()

                        override fun changedUpdate(e: DocumentEvent?) = updateData()
                    },
                )
            }

        val loggerFilenamePattern =
            JTextField(logger.filenamePattern).apply {
                document.addDocumentListener(
                    object : DocumentListener {
                        override fun insertUpdate(e: DocumentEvent?) = updateData()

                        override fun removeUpdate(e: DocumentEvent?) = updateData()

                        override fun changedUpdate(e: DocumentEvent?) = updateData()
                    },
                )
            }

        val maxFileSize = SizeEntryField("Max File Size", logger.maxFileSize, "MB")
        val totalSizeCap = SizeEntryField("Total Size Cap", logger.totalSizeCap, "MB")
        val maxDays = SizeEntryField("Max Days", logger.maxDaysHistory, "Days")

        private val separateOutputOptions =
            JPanel(MigLayout("fillx, ins 0")).apply {
                add(
                    JPanel(MigLayout("fill, ins 0")).apply {
                        add(JLabel("Output Folder:"), "cell 0 0")
                        add(loggerOutputFolder, "cell 1 0 2 0, push, growx, shrinkx")
                        add(JLabel("Filename Pattern:"), "cell 0 1")
                        add(loggerFilenamePattern, "cell 1 1 2 1, push, growx, shrinkx")
                    },
                    "grow, push, shrinkx",
                )

                add(maxFileSize, "grow, shrinkx")
                add(totalSizeCap, "grow, shrinkx")
                add(maxDays, "grow, shrinkx")
                isVisible = logger.separateOutput
            }

        init {
            name = logger.name
            border = BorderFactory.createTitledBorder(logger.name)
            add(loggerLevelSelector, "w 100")
            add(closeButton, "right, wrap, gap 5 5")
            add(loggerSeparateOutput, "growx, span 3, wrap")
            add(separateOutputOptions, "growx, span 3")
        }

        override fun updateUI() {
            super.updateUI()
            SwingUtilities.invokeLater {
                closeButton.border = null
                closeButton.background = null
            }
        }
    }

    override val icon: Icon = XMLTool.icon
}

data class SelectedLogger(
    val name: String = "Logger name",
    var level: String = "INFO",
    var separateOutput: Boolean = false,
    var outputFolder: String = "\${LOG_HOME}\\\\AdditionalLogs\\\\",
    var filenamePattern: String = "${name.replace(".", "")}.%d{yyyy-MM-dd}.%i.log",
    var maxFileSize: Long = 10,
    var totalSizeCap: Long = 1000,
    var maxDaysHistory: Long = 5,
)
