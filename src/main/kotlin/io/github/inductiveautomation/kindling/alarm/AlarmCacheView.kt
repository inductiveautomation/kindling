package io.github.inductiveautomation.kindling.alarm

import com.formdev.flatlaf.extras.FlatSVGIcon
import com.inductiveautomation.ignition.common.alarming.AlarmEvent
import com.inductiveautomation.ignition.common.alarming.AlarmState
import com.inductiveautomation.ignition.common.alarming.EventData
import io.github.inductiveautomation.kindling.alarm.model.AlarmEventModel
import io.github.inductiveautomation.kindling.alarm.model.PersistedAlarmInfo
import io.github.inductiveautomation.kindling.cache.AliasingObjectInputStream
import io.github.inductiveautomation.kindling.core.Detail
import io.github.inductiveautomation.kindling.core.DetailsPane
import io.github.inductiveautomation.kindling.core.Tool
import io.github.inductiveautomation.kindling.core.ToolPanel
import io.github.inductiveautomation.kindling.utils.EDT_SCOPE
import io.github.inductiveautomation.kindling.utils.FileFilter
import io.github.inductiveautomation.kindling.utils.ReifiedJXTable
import io.github.inductiveautomation.kindling.utils.VerticalSplitPane
import java.awt.Color
import java.nio.file.Path
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import kotlin.io.path.inputStream
import kotlin.io.path.name
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.miginfocom.swing.MigLayout
import org.jdesktop.swingx.JXSearchField
import org.jdesktop.swingx.decorator.ColorHighlighter

class AlarmCacheView(path: Path) : ToolPanel() {
    override val icon = FlatSVGIcon("icons/bx-bell.svg")

    private val events: List<AlarmEvent> = try {
        val info = AliasingObjectInputStream(path.inputStream()) {
            put(
                "com.inductiveautomation.ignition.gateway.alarming.status.AlarmStateModel\$PersistedAlarmInfo",
                PersistedAlarmInfo::class.java,
            )
        }.readObject() as PersistedAlarmInfo

        info.data.values.flatMap { it.toList() }
    } catch (e: Exception) {
        throw IllegalArgumentException("Error deserializing alarm cache. Only caches from Ignition 8.1.20+ are supported.")
    }

    private val table = run {
        val initialModel = AlarmEventModel(events)

        ReifiedJXTable(initialModel).apply {
            columnFactory = AlarmEventModel.AlarmEventColumnList.toColumnFactory()
            createDefaultColumnsFromModel()

            alarmStateColors.entries.forEach { (state, colorPalette) ->
                addHighlighter(
                    ColorHighlighter(
                        { _, adapter ->
                            val viewRow = convertRowIndexToModel(adapter.row)
                            state == model[viewRow].state
                        },
                        colorPalette.background,
                        colorPalette.foreground,
                    ),
                )
            }
        }
    }

    private val countLabelText: String
        get() = "Showing ${table.rowCount} of ${events.size} alarms"

    private val alarmCountLabel = JLabel(countLabelText)

    private val search = JXSearchField("Search")

    private val header = JPanel(MigLayout("fill, ins 4")).apply {
        add(alarmCountLabel, "west")
        add(search, "east, wmin 300")
    }

    private val detailsPane = DetailsPane()

    private fun EventData?.toDetailBody(name: String): List<Detail.BodyLine>? {
        return this?.run {
            buildList {
                add(Detail.BodyLine("***$name***"))
                addAll(
                    values.map {
                        Detail.BodyLine("${it.property.name}: ${it.value}")
                    },
                )
                add(Detail.BodyLine(""))
            }
        }
    }

    private fun AlarmEvent.toDetail(): Detail = Detail(
        title = "Event Data for $name ($id)",
        message = if (notes.isNullOrEmpty()) null else "Notes: $notes",
        body = listOfNotNull(
            activeData.toDetailBody("Active Data"),
            ackData.toDetailBody("Ack Data"),
            clearedData.toDetailBody("Clear Data"),
        ).flatten(),
    )

    init {
        name = "Alarm Cache"
        toolTipText = path.toString()

        add(header, "growx, spanx")

        add(
            VerticalSplitPane(
                top = JScrollPane(table),
                bottom = detailsPane,
                resizeWeight = 0.5,
            ),
            "push, grow, span",
        )

        table.selectionModel.addListSelectionListener {
            EDT_SCOPE.launch {
                val details = withContext(Dispatchers.Default) {
                    table.selectionModel.selectedIndices.map {
                        table.model[table.convertRowIndexToModel(it)].toDetail()
                    }
                }
                detailsPane.events = details
            }
        }

        search.addActionListener { event ->
            val searchField = event.source as JXSearchField
            EDT_SCOPE.launch {
                val filteredAlarms = withContext(Dispatchers.Default) {
                    events.filter { alarmEvent ->
                        val asString = AlarmEventModel.AlarmEventColumnList.joinToString { column ->
                            column.getValue(alarmEvent).toString().lowercase()
                        }
                        searchField.text.lowercase() in asString
                    }
                }
                table.model = AlarmEventModel(filteredAlarms)
                alarmCountLabel.text = countLabelText
            }
        }
    }

    private data class AlarmStateColorPalette(
        val background: Color,
        val foreground: Color,
    )

    companion object {
        private val alarmStateColors = mapOf(
            AlarmState.ActiveAcked to AlarmStateColorPalette(Color(171, 0, 0), Color.WHITE),
            AlarmState.ActiveUnacked to AlarmStateColorPalette(Color(236, 34, 21), Color.WHITE),
            AlarmState.ClearAcked to AlarmStateColorPalette(Color(220, 220, 254), Color.BLACK),
            AlarmState.ClearUnacked to AlarmStateColorPalette(Color(73, 171, 171), Color.BLACK),
        )
    }
}

object AlarmViewer : Tool {
    override val title: String = "Alarm Cache"
    override val description: String = ".alarms files from the Ignition data dir."
    override val icon = FlatSVGIcon("icons/bx-data.svg")

    override fun open(path: Path): ToolPanel = AlarmCacheView(path)

    override val filter: FileFilter = FileFilter(".alarms files") {
        fileNameRegex.matches(it.name)
    }

    private val fileNameRegex = """\.?alarms_.*""".toRegex()
}
