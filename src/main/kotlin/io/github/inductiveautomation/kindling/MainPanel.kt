package io.github.inductiveautomation.kindling

import com.formdev.flatlaf.FlatLaf
import com.formdev.flatlaf.extras.FlatAnimatedLafChange
import com.formdev.flatlaf.extras.FlatSVGIcon
import com.formdev.flatlaf.extras.FlatUIDefaultsInspector
import com.formdev.flatlaf.extras.components.FlatTextArea
import com.formdev.flatlaf.fonts.roboto.FlatRobotoFont
import com.formdev.flatlaf.util.SystemInfo
import io.github.inductiveautomation.kindling.core.ClipboardTool
import io.github.inductiveautomation.kindling.core.CustomIconView
import io.github.inductiveautomation.kindling.core.Kindling.Preferences.Advanced.Debug
import io.github.inductiveautomation.kindling.core.Kindling.Preferences.General.ChoosableEncodings
import io.github.inductiveautomation.kindling.core.Kindling.Preferences.General.DefaultEncoding
import io.github.inductiveautomation.kindling.core.Kindling.Preferences.General.DefaultTool
import io.github.inductiveautomation.kindling.core.Kindling.Preferences.General.HomeLocation
import io.github.inductiveautomation.kindling.core.Kindling.Preferences.UI.ScaleFactor
import io.github.inductiveautomation.kindling.core.Kindling.Preferences.UI.Theme
import io.github.inductiveautomation.kindling.core.MultiTool
import io.github.inductiveautomation.kindling.core.Tool
import io.github.inductiveautomation.kindling.core.ToolOpeningException
import io.github.inductiveautomation.kindling.core.ToolPanel
import io.github.inductiveautomation.kindling.core.preferencesEditor
import io.github.inductiveautomation.kindling.internal.FileTransferHandler
import io.github.inductiveautomation.kindling.utils.Action
import io.github.inductiveautomation.kindling.utils.FlatScrollPane
import io.github.inductiveautomation.kindling.utils.TabStrip
import io.github.inductiveautomation.kindling.utils.chooseFiles
import io.github.inductiveautomation.kindling.utils.getLogger
import io.github.inductiveautomation.kindling.utils.jFrame
import net.miginfocom.layout.PlatformDefaults
import net.miginfocom.layout.UnitValue
import net.miginfocom.swing.MigLayout
import java.awt.BorderLayout
import java.awt.Container
import java.awt.Desktop
import java.awt.Dimension
import java.awt.EventQueue
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.desktop.OpenFilesHandler
import java.awt.desktop.QuitStrategy
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.io.File
import java.nio.charset.Charset
import javax.swing.Box
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JFileChooser
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JMenu
import javax.swing.JMenuBar
import javax.swing.JPanel
import javax.swing.KeyStroke
import javax.swing.UIManager

class MainPanel : JPanel(MigLayout("ins 6, fill")) {
    private val fileChooser = JFileChooser(HomeLocation.currentValue.toFile()).apply {
        isMultiSelectionEnabled = true
        fileView = CustomIconView()

        val encodingSelector = JComboBox(ChoosableEncodings).apply {
            toolTipText = "Charset Encoding for Wrapper Logs"
            selectedItem = DefaultEncoding.currentValue
            addActionListener {
                DefaultEncoding.currentValue = selectedItem as Charset
            }
        }

        val encodingPanel = JPanel(MigLayout("ins 0, fillx")).apply {
            add(JLabel("Encoding: "))
            add(encodingSelector, "growx, pushx")
        }

        (((components[0] as Container).components[3] as Container).components[3] as Container).apply {
            layout = MigLayout("fillx, ins 5 0, hidemode 0")
            add(encodingPanel, "growx, pushx", 0)
        }

        Tool.byFilter.keys.forEach(this::addChoosableFileFilter)
        fileFilter = DefaultTool.currentValue.filter

        addActionListener {
            if (selectedFile != null) {
                HomeLocation.currentValue = selectedFile.parentFile.toPath()
            }
        }

        Theme.addChangeListener {
            updateUI()
        }
    }

    private val openFunction: () -> Unit = {
        fileChooser.chooseFiles(this)?.let { selectedFiles ->
            val selectedTool: Tool? = Tool.byFilter[fileChooser.fileFilter]
            openFiles(selectedFiles, selectedTool)
        }
    }

    private val tabs = TabStrip().apply {
        if (SystemInfo.isMacFullWindowContentSupported) {
            // add padding component for MacOS window controls
            leadingComponent = Box.createHorizontalStrut(70)
        }

        trailingComponent = JPanel(BorderLayout()).apply {
            add(
                JButton(
                    Action(
                        icon = FlatSVGIcon("icons/bx-plus.svg"),
                    ) { openFunction() },
                ).apply {
                    isFocusable = false
                },
                BorderLayout.WEST,
            )
        }
    }

    private val debugMenu = JMenu("Debug").apply {
        add(
            Action("UI Inspector") {
                FlatUIDefaultsInspector.show()
            },
        )

        isVisible = Debug.currentValue
    }

    private val menuBar = JMenuBar().apply {
        add(
            JMenu("File").apply {
                add(
                    Action(
                        name = "Open...",
                        accelerator = KeyStroke.getKeyStroke(KeyEvent.VK_O, Toolkit.getDefaultToolkit().menuShortcutKeyMaskEx),
                    ) {
                        openFunction()
                    },
                )
                for (tool in Tool.tools) {
                    add(
                        Action(
                            name = "Open ${tool.title}",
                        ) {
                            fileChooser.fileFilter = tool.filter
                            fileChooser.chooseFiles(this@MainPanel)?.let { selectedFiles ->
                                openFiles(selectedFiles, tool)
                            }
                        },
                    )
                }
            },
        )
        add(
            JMenu("Paste").apply {
                for (clipboardTool in Tool.tools.filterIsInstance<ClipboardTool>()) {
                    add(
                        Action(
                            name = "Paste ${clipboardTool.title}",
                        ) {
                            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
                            if (clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor)) {
                                val clipString = clipboard.getData(DataFlavor.stringFlavor) as String
                                openOrError(clipboardTool.title, "clipboard data") {
                                    clipboardTool.open(clipString)
                                }
                            } else {
                                LOGGER.info("No string data found on clipboard")
                            }
                        },
                    )
                }
            },
        )
        if (!SystemInfo.isMacOS) {
            add(
                JMenu("Preferences").apply {
                    addMouseListener(
                        object : MouseAdapter() {
                            override fun mouseClicked(e: MouseEvent?) {
                                preferencesEditor.isVisible = !preferencesEditor.isVisible
                            }
                        },
                    )
                },
            )
        }
        add(debugMenu)
    }

    /**
     * Opens a path in a tool (blocking). In the event of any error, opens an 'Error' tab instead.
     */
    private fun openOrError(title: String, description: String, openFunction: () -> ToolPanel) {
        runCatching {
            val toolPanel = openFunction()
            tabs.addTab(component = toolPanel, select = true)
        }.getOrElse { ex ->
            LOGGER.error("Failed to open $description as a $title", ex)
            tabs.addTab(
                "ERROR",
                FlatSVGIcon("icons/bx-error.svg"),
                FlatScrollPane(
                    FlatTextArea().apply {
                        isEditable = false
                        text = buildString {
                            if (ex is ToolOpeningException) {
                                appendLine(ex.message)
                            } else {
                                appendLine("Error opening $description: ${ex.message}")
                            }
                            append((ex.cause ?: ex).stackTraceToString())
                        }
                    },
                ),
            )
            tabs.selectedIndex = tabs.indices.last
        }
    }

    fun openFiles(files: List<File>, tool: Tool? = null) {
        if (tool is MultiTool) {
            openOrError(tool.title, files.joinToString()) {
                tool.open(files.map(File::toPath))
            }
        } else {
            files.groupBy { tool ?: Tool[it] }.forEach { (tool, filesByTool) ->
                if (tool is MultiTool) {
                    openOrError(tool.title, filesByTool.joinToString()) {
                        tool.open(filesByTool.map(File::toPath))
                    }
                } else {
                    filesByTool.forEach { file ->
                        openOrError(tool.title, file.toString()) {
                            tool.open(file.toPath())
                        }
                    }
                }
            }
        }
    }

    init {
        add(tabs, "dock center")

        Debug.addChangeListener { newValue ->
            debugMenu.isVisible = newValue
        }
    }

    companion object {
        val LOGGER = getLogger<MainPanel>()

        @JvmStatic
        fun main(args: Array<String>) {
            System.setProperty("apple.awt.application.name", "Kindling")
            System.setProperty("apple.laf.useScreenMenuBar", "true")
            System.setProperty("apple.awt.application.appearance", "system")
            System.setProperty("flatlaf.uiScale", ScaleFactor.currentValue.toString())

            EventQueue.invokeLater {
                lafSetup()

                jFrame(
                    title = "Kindling",
                    width = 1280,
                    height = 800,
                    embedContentIntoTitleBar = true,
                ) {
                    defaultCloseOperation = JFrame.EXIT_ON_CLOSE

                    val mainPanel = MainPanel()
                    add(mainPanel)
                    jMenuBar = mainPanel.menuBar

                    if (args.isNotEmpty()) {
                        args.map(::File).let(mainPanel::openFiles)
                    }

                    macOsSetup(
                        openFilesHandler = { event ->
                            mainPanel.openFiles(event.files)
                        },
                    )

                    transferHandler = FileTransferHandler(mainPanel::openFiles)
                }
            }
        }

        private fun lafSetup() {
            FlatRobotoFont.install()
            FlatLaf.setPreferredFontFamily(FlatRobotoFont.FAMILY)
            FlatLaf.setPreferredLightFontFamily(FlatRobotoFont.FAMILY_LIGHT)
            FlatLaf.setPreferredSemiboldFontFamily(FlatRobotoFont.FAMILY_SEMIBOLD)
            applyTheme(false)

            UIManager.getDefaults().apply {
                put("Component.focusWidth", 0)
                put("Component.innerfocusWidth", 1)
                put("ScrollBar.width", 16)
                put("TabbedPane.tabType", "card")
                put("MenuItem.minimumIconSize", Dimension()) // https://github.com/JFormDesigner/FlatLaf/issues/328
                put("Tree.showDefaultIcons", true)
            }

            PlatformDefaults.setGridCellGap(UnitValue(2.0F), UnitValue(2.0F))

            Theme.addChangeListener {
                applyTheme(true)
            }
        }

        private fun applyTheme(animate: Boolean) {
            try {
                if (animate) {
                    FlatAnimatedLafChange.showSnapshot()
                }
                UIManager.setLookAndFeel(Theme.currentValue.lookAndFeelClassname)
                FlatLaf.updateUI()
            } finally {
                // Will no-op if not animated
                FlatAnimatedLafChange.hideSnapshotWithAnimation()
            }
        }

        private fun macOsSetup(openFilesHandler: OpenFilesHandler) = Desktop.getDesktop().run {
            // MacOS specific stuff
            if (isSupported(Desktop.Action.APP_SUDDEN_TERMINATION)) {
                disableSuddenTermination()
            }
            if (isSupported(Desktop.Action.APP_QUIT_STRATEGY)) {
                setQuitStrategy(QuitStrategy.CLOSE_ALL_WINDOWS)
            }
            if (isSupported(Desktop.Action.APP_OPEN_FILE)) {
                setOpenFileHandler(openFilesHandler)
            }
            if (isSupported(Desktop.Action.APP_PREFERENCES)) {
                setPreferencesHandler {
                    preferencesEditor.isVisible = true
                }
            }
        }
    }
}
