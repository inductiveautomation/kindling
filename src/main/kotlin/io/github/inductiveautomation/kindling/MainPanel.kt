package io.github.inductiveautomation.kindling

import com.formdev.flatlaf.FlatLaf
import com.formdev.flatlaf.extras.FlatAnimatedLafChange
import com.formdev.flatlaf.extras.FlatSVGIcon
import com.formdev.flatlaf.extras.FlatUIDefaultsInspector
import com.formdev.flatlaf.extras.components.FlatTextArea
import com.formdev.flatlaf.util.SystemInfo
import io.github.inductiveautomation.kindling.core.ClipboardTool
import io.github.inductiveautomation.kindling.core.CustomIconView
import io.github.inductiveautomation.kindling.core.Kindling.Advanced.Debug
import io.github.inductiveautomation.kindling.core.Kindling.General.HomeLocation
import io.github.inductiveautomation.kindling.core.Kindling.UI.ScaleFactor
import io.github.inductiveautomation.kindling.core.Kindling.UI.Theme
import io.github.inductiveautomation.kindling.core.MultiTool
import io.github.inductiveautomation.kindling.core.Tool
import io.github.inductiveautomation.kindling.core.ToolOpeningException
import io.github.inductiveautomation.kindling.core.ToolPanel
import io.github.inductiveautomation.kindling.core.preferencesFrame
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
import java.awt.Desktop
import java.awt.Dimension
import java.awt.EventQueue
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.desktop.OpenFilesHandler
import java.awt.desktop.QuitStrategy
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.io.File
import javax.swing.JButton
import javax.swing.JFileChooser
import javax.swing.JFrame
import javax.swing.JMenu
import javax.swing.JMenuBar
import javax.swing.JPanel
import javax.swing.UIManager

class MainPanel(empty: Boolean) : JPanel(MigLayout("ins 6, fill")) {
    private val fileChooser = JFileChooser(HomeLocation.currentValue.toFile()).apply {
        isMultiSelectionEnabled = true
        fileView = CustomIconView()

        Tool.byFilter.keys.forEach(this::addChoosableFileFilter)
        fileFilter = Tool.tools.first().filter

        Theme.addChangeListener {
            updateUI()
        }
    }

    private val openAction = Action(
        name = "Open...",
    ) {
        fileChooser.chooseFiles(this)?.let { selectedFiles ->
            val selectedTool: Tool? = Tool.byFilter[fileChooser.fileFilter]
            openFiles(selectedFiles, selectedTool)
        }
    }

    private val tabs = TabStrip()
    private val openButton = JButton(openAction)

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
                add(openAction)
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
                                println("No string data found on clipboard")
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
                                preferencesFrame.isVisible = !preferencesFrame.isVisible
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
        synchronized(treeLock) {
            val child = getComponent(0)
            if (child == openButton) {
                remove(openButton)
                add(tabs, "dock center")
            }
        }
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
        if (empty) {
            add(openButton, "dock center")
        } else {
            add(tabs, "dock center")
        }

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
            System.setProperty("flatlaf.uiScale", ScaleFactor.currentValue.toString())

            EventQueue.invokeLater {
                lafSetup()

                jFrame("Kindling", 1280, 800) {
                    defaultCloseOperation = JFrame.EXIT_ON_CLOSE

                    val mainPanel = MainPanel(args.isEmpty())
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
            applyTheme(false)

            UIManager.getDefaults().apply {
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
                    preferencesFrame.isVisible = true
                }
            }
        }
    }
}
