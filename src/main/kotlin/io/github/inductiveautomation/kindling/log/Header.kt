package io.github.inductiveautomation.kindling.log

import com.formdev.flatlaf.extras.FlatSVGIcon
import com.jidesoft.swing.JideButton
import com.jidesoft.swing.JidePopupMenu
import net.miginfocom.swing.MigLayout
import org.jdesktop.swingx.JXSearchField
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.time.ZoneId
import java.time.zone.ZoneRulesProvider
import javax.swing.ButtonGroup
import javax.swing.JCheckBoxMenuItem
import javax.swing.JLabel
import javax.swing.JMenu
import javax.swing.JPanel
import kotlin.properties.Delegates

class Header(private val totalRows: Int) : JPanel(MigLayout("ins 0, fill")) {
    private val events = JLabel("$totalRows (of $totalRows) events")

    val search = JXSearchField("Search")

    var isShowFullLoggerName: Boolean by Delegates.observable(false) { property, oldValue, newValue ->
        firePropertyChange(property.name, oldValue, newValue)
    }

    var selectedTimeZone: String by Delegates.observable(ZoneId.systemDefault().id) { property, oldValue, newValue ->
        firePropertyChange(property.name, oldValue, newValue)
    }

    var isOnlyShowMarkedLogs: Boolean by Delegates.observable(false) { property, oldValue, newValue ->
        firePropertyChange(property.name, oldValue, newValue)
    }

    private val settingsMenu = JidePopupMenu().apply {
        add(
            JCheckBoxMenuItem("Show Full Logger Names").apply {
                addActionListener {
                    isShowFullLoggerName = !isShowFullLoggerName
                }
            },
        )
        add(
            JCheckBoxMenuItem("Only Show Marked Logs").apply {
                addActionListener {
                    isOnlyShowMarkedLogs = !isOnlyShowMarkedLogs
                }
            },
        )
        val tzGroup = ButtonGroup()
        add(
            JMenu("Timezone").apply {
                for (timezone in ZoneRulesProvider.getAvailableZoneIds().sorted()) {
                    add(JCheckBoxMenuItem(timezone, timezone == selectedTimeZone)).also { timezoneItem ->
                        tzGroup.add(timezoneItem)
                        timezoneItem.addActionListener {
                            selectedTimeZone = timezone
                        }
                    }
                }
            },
        )
    }

    private val settings = JideButton(FlatSVGIcon("icons/bx-cog.svg")).apply {
        addMouseListener(
            object : MouseAdapter() {
                override fun mousePressed(e: MouseEvent) {
                    settingsMenu.show(this@apply, e.x, e.y)
                }
            },
        )
    }

    init {
        add(events, "pushx")
        add(search, "width 300, gap unrelated")
        add(settings)
    }

    var displayedRows by Delegates.observable(totalRows) { _, _, newValue ->
        events.text = "$newValue (of $totalRows) events"
    }
}
