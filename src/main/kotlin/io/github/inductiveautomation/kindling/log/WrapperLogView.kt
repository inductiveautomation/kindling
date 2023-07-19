package io.github.inductiveautomation.kindling.log

import com.formdev.flatlaf.extras.FlatSVGIcon
import io.github.inductiveautomation.kindling.core.ClipboardTool
import io.github.inductiveautomation.kindling.core.Kindling.Preferences.General.DefaultEncoding
import io.github.inductiveautomation.kindling.core.MultiTool
import io.github.inductiveautomation.kindling.core.Preference
import io.github.inductiveautomation.kindling.core.Preference.Companion.PreferenceCheckbox
import io.github.inductiveautomation.kindling.core.Preference.Companion.preference
import io.github.inductiveautomation.kindling.core.PreferenceCategory
import io.github.inductiveautomation.kindling.core.ToolPanel
import io.github.inductiveautomation.kindling.utils.Action
import io.github.inductiveautomation.kindling.utils.ZoneIdSerializer
import java.awt.Desktop
import java.io.File
import java.nio.file.Path
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.zone.ZoneRulesProvider
import java.util.Vector
import javax.swing.Icon
import javax.swing.JComboBox
import javax.swing.JPopupMenu
import kotlin.io.path.name
import kotlin.io.path.useLines

class WrapperLogView(
    events: List<WrapperLogEvent>,
    tabName: String,
    private val fromFile: Boolean,
) : ToolPanel() {
    private val logPanel = LogPanel(events)

    init {
        name = tabName
        toolTipText = tabName

        add(logPanel, "push, grow")
    }

    override val icon: Icon = LogViewer.icon

    override fun customizePopupMenu(menu: JPopupMenu) {
        menu.add(
            exportMenu { logPanel.table.model },
        )
        if (fromFile) {
            menu.addSeparator()
            menu.add(
                Action(name = "Open in External Editor") {
                    Desktop.getDesktop().open(File(tabName))
                },
            )
        }
    }
}

data object LogViewer : MultiTool, ClipboardTool, PreferenceCategory {
    override val title = "Wrapper Log"
    override val description = "wrapper.log(.n) files"
    override val icon = FlatSVGIcon("icons/bx-file.svg")
    override val extensions = List(21) { i ->
        if (i == 0) {
            "log"
        } else {
            i.toString()
        }
    }

    override fun open(paths: List<Path>): ToolPanel {
        require(paths.isNotEmpty()) { "Must provide at least one path" }
        val events = paths.flatMap { path ->
            path.useLines(DefaultEncoding.currentValue) { lines -> LogPanel.parseLogs(lines) }
        }
        return WrapperLogView(
            events = events,
            tabName = paths.first().name,
            fromFile = true,
        )
    }

    override fun open(data: String): ToolPanel {
        return WrapperLogView(
            events = LogPanel.parseLogs(data.lineSequence()),
            tabName = "Paste at ${LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))}",
            fromFile = false,
        )
    }

    val SelectedTimeZone = preference(
        name = "Timezone",
        description = "Timezone to use when displaying logs",
        default = ZoneId.systemDefault(),
        serializer = ZoneIdSerializer,
        editor = {
            JComboBox(Vector(ZoneRulesProvider.getAvailableZoneIds().sorted())).apply {
                selectedItem = currentValue.id
                addActionListener {
                    currentValue = ZoneId.of(selectedItem as String)
                }
            }
        },
    )

    private lateinit var _formatter: DateTimeFormatter
    val TimeStampFormatter: DateTimeFormatter
        get() {
            if (!this::_formatter.isInitialized) {
                _formatter = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss:SSS").withZone(SelectedTimeZone.currentValue)
            }
            if (_formatter.zone != SelectedTimeZone.currentValue) {
                _formatter = _formatter.withZone(SelectedTimeZone.currentValue)
            }
            return _formatter
        }

    val ShowDensity = preference(
        name = "Density Display",
        default = true,
        editor = {
            PreferenceCheckbox("Show 'minimap' of log events in scrollbar")
        },
    )

    val ShowOnlyMarked = preference(
        name = "Show Marked Only",
        default = false,
        editor = {
            PreferenceCheckbox("Show only marked logs")
        },
    )

    override val displayName: String = "Log View"
    override val key: String = "logview"
    override val preferences: List<Preference<*>> = listOf(SelectedTimeZone, ShowDensity, ShowOnlyMarked)
}
