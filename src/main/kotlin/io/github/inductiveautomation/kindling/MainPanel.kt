package io.github.inductiveautomation.kindling

import com.formdev.flatlaf.FlatLaf
import com.formdev.flatlaf.extras.FlatAnimatedLafChange
import com.formdev.flatlaf.extras.FlatSVGIcon
import com.formdev.flatlaf.extras.FlatUIDefaultsInspector
import com.formdev.flatlaf.extras.components.FlatTextArea
import com.formdev.flatlaf.fonts.roboto.FlatRobotoFont
import com.formdev.flatlaf.fonts.roboto_mono.FlatRobotoMonoFont
import com.formdev.flatlaf.util.SystemInfo
import com.jidesoft.swing.StyleRange.STYLE_UNDERLINED
import io.github.inductiveautomation.kindling.core.ClipboardTool
import io.github.inductiveautomation.kindling.core.CustomIconView
import io.github.inductiveautomation.kindling.core.Kindling
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
import io.github.inductiveautomation.kindling.utils.StyledLabel
import io.github.inductiveautomation.kindling.utils.TabStrip
import io.github.inductiveautomation.kindling.utils.chooseFiles
import io.github.inductiveautomation.kindling.utils.getLogger
import io.github.inductiveautomation.kindling.utils.jFrame
import io.github.inductiveautomation.kindling.utils.menuShortcutKeyMaskEx
import io.github.inductiveautomation.kindling.utils.render
import io.github.inductiveautomation.kindling.utils.traverseChildren
import net.miginfocom.layout.PlatformDefaults
import net.miginfocom.layout.UnitValue
import net.miginfocom.swing.MigLayout
import java.awt.BorderLayout
import java.awt.Cursor
import java.awt.Cursor.HAND_CURSOR
import java.awt.Desktop
import java.awt.Dimension
import java.awt.EventQueue
import java.awt.Font.PLAIN
import java.awt.Menu
import java.awt.MenuItem
import java.awt.PopupMenu
import java.awt.Taskbar
import java.awt.Toolkit
import java.awt.Window
import java.awt.datatransfer.DataFlavor
import java.awt.desktop.QuitStrategy
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.image.BufferedImage
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
import javax.swing.JMenuItem
import javax.swing.JPanel
import javax.swing.KeyStroke
import javax.swing.SwingConstants
import javax.swing.SwingConstants.CENTER
import javax.swing.UIManager
import javax.swing.filechooser.FileFilter

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
            isEnabled = DefaultTool.currentValue.respectsEncoding
        }

        traverseChildren().filterIsInstance<JPanel>().last().apply {
            add(encodingSelector, 0)
            add(
                JLabel("Encoding: ", SwingConstants.RIGHT).apply {
                    verticalAlignment = SwingConstants.BOTTOM
                },
                0,
            )
        }

        Tool.byFilter.keys.forEach(this::addChoosableFileFilter)
        fileFilter = DefaultTool.currentValue.filter
        addPropertyChangeListener(JFileChooser.FILE_FILTER_CHANGED_PROPERTY) { e ->
            val relevantTool = Tool.byFilter[e.newValue as FileFilter]
            encodingSelector.isEnabled = relevantTool?.respectsEncoding != false // null = 'all files', so enabled
        }

        addActionListener {
            if (selectedFile != null) {
                HomeLocation.currentValue = selectedFile.parentFile.toPath()
            }
        }

        Theme.addChangeListener {
            updateUI()
        }
    }

    private val openAction = Action(
        name = "Open...",
        accelerator = KeyStroke.getKeyStroke(KeyEvent.VK_O, menuShortcutKeyMaskEx),
    ) {
        fileChooser.chooseFiles(this@MainPanel)?.let { selectedFiles ->
            val selectedTool: Tool? = Tool.byFilter[fileChooser.fileFilter]
            openFiles(selectedFiles, selectedTool)
        }
    }

    private val tabs = TabStrip().apply {
        if (SystemInfo.isMacFullWindowContentSupported) {
            // add padding component for macOS window controls
            leadingComponent = Box.createHorizontalStrut(70)
        }

        trailingComponent = JPanel(BorderLayout()).apply {
            add(
                JButton(openAction).apply {
                    hideActionText = true
                    icon = FlatSVGIcon("icons/bx-plus.svg")
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

    private val fileMenu = JMenu("File").apply {
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
        if (!SystemInfo.isMacOS) {
            addSeparator()
            add(
                Action("Preferences") {
                    preferencesEditor.isVisible = true
                    preferencesEditor.toFront()
                },
            )
        }
    }

    private val pasteMenu = JMenu("Paste").apply {
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
    }

    private val menuBar = JMenuBar().apply {
        add(fileMenu)
        add(pasteMenu)
        add(
            JMenu("Help").apply {
                add(debugMenu)
                add(
                    Action("Forum") {
                        Desktop.getDesktop().browse(Kindling.forumThread)
                    },
                )
                if (!SystemInfo.isMacOS) {
                    add(
                        Action("About") {
                            aboutDialog.isVisible = true
                            aboutDialog.toFront()
                        },
                    )
                }
            },
        )
    }

    private val aboutDialog by lazy {
        jFrame(
            title = "About Kindling",
            width = 300,
            height = 200,
            embedContentIntoTitleBar = true,
            initiallyVisible = false,
        ) {
            defaultCloseOperation = JFrame.HIDE_ON_CLOSE
            isResizable = false
            isUndecorated
            type = Window.Type.UTILITY

            contentPane = JPanel(MigLayout("ins 6, fill, wrap 1", "align center")).apply {
                add(JLabel(FlatSVGIcon("logo.svg").derive(64, 64), CENTER))
                add(
                    JLabel("Kindling", CENTER).apply {
                        font = UIManager.getFont("h1.font")
                    },
                )
                add(JLabel("Version ${System.getProperty("app.version") ?: "(Dev)"}", CENTER))
                add(
                    StyledLabel {
                        add("Homepage", PLAIN, UIManager.getColor("Hyperlink.linkColor"), STYLE_UNDERLINED)
                    }.apply {
                        cursor = Cursor.getPredefinedCursor(HAND_CURSOR)
                        addMouseListener(
                            object : MouseAdapter() {
                                override fun mouseClicked(e: MouseEvent) {
                                    Desktop.getDesktop().browse(Kindling.homepage)
                                }
                            },
                        )
                    },
                )
            }
        }
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

                    mainPanel.macOsSetup()

                    transferHandler = FileTransferHandler(mainPanel::openFiles)
                }
            }
        }

        private fun lafSetup() {
            FlatRobotoFont.install()
            FlatRobotoMonoFont.install()
            FlatLaf.setPreferredFontFamily(FlatRobotoFont.FAMILY)
            FlatLaf.setPreferredLightFontFamily(FlatRobotoFont.FAMILY_LIGHT)
            FlatLaf.setPreferredSemiboldFontFamily(FlatRobotoFont.FAMILY_SEMIBOLD)
            FlatLaf.setPreferredMonospacedFontFamily(FlatRobotoMonoFont.FAMILY)
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

        private fun MainPanel.macOsSetup() {
            if (Taskbar.isTaskbarSupported()) {
                Taskbar.getTaskbar().apply {
                    if (isSupported(Taskbar.Feature.ICON_IMAGE)) {
                        val image = Kindling.logo.render(1024, 1024)
                        val padding = 128

                        val paddedImage = BufferedImage(
                            image.width + 2 * padding,
                            image.height + 2 * padding,
                            image.type,
                        ).apply {
                            createGraphics().apply {
                                drawImage(image, padding, padding, null)
                                dispose()
                            }
                        }

                        setIconImage(paddedImage)
                    }
                    if (isSupported(Taskbar.Feature.MENU)) {
                        menu = PopupMenu().apply {
                            add(fileMenu.toAwtMenu())
                            add(pasteMenu.toAwtMenu())
                        }
                    }
                }
            }
            Desktop.getDesktop().run {
                if (isSupported(Desktop.Action.APP_SUDDEN_TERMINATION)) {
                    disableSuddenTermination()
                }
                if (isSupported(Desktop.Action.APP_QUIT_STRATEGY)) {
                    setQuitStrategy(QuitStrategy.CLOSE_ALL_WINDOWS)
                }
                if (isSupported(Desktop.Action.APP_OPEN_FILE)) {
                    setOpenFileHandler { event ->
                        openFiles(event.files)
                    }
                }
                if (isSupported(Desktop.Action.APP_PREFERENCES)) {
                    setPreferencesHandler {
                        preferencesEditor.isVisible = true
                        preferencesEditor.toFront()
                    }
                }
                if (isSupported(Desktop.Action.APP_ABOUT)) {
                    setAboutHandler {
                        aboutDialog.isVisible = true
                        aboutDialog.toFront()
                    }
                }
            }
        }
    }
}

private fun JMenu.toAwtMenu(): Menu {
    return Menu(text).apply {
        for (menuComponent in menuComponents) {
            (menuComponent as? JMenuItem)?.toAwtMenuItem()?.let(::add)
        }
    }
}

private fun JMenuItem.toAwtMenuItem(): MenuItem {
    return MenuItem(text).apply {
        addActionListener(action)
    }
}
