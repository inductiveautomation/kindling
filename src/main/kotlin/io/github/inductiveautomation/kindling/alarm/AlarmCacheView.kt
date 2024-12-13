package io.github.inductiveautomation.kindling.alarm

import com.formdev.flatlaf.extras.FlatSVGIcon
import com.inductiveautomation.ignition.common.alarming.AlarmEvent
import com.inductiveautomation.ignition.common.alarming.AlarmState
import com.inductiveautomation.ignition.common.alarming.EventData
import io.github.inductiveautomation.kindling.alarm.model.AlarmEventColumnList
import io.github.inductiveautomation.kindling.alarm.model.PersistedAlarmInfo
import io.github.inductiveautomation.kindling.cache.AliasingObjectInputStream
import io.github.inductiveautomation.kindling.core.Detail
import io.github.inductiveautomation.kindling.core.DetailsPane
import io.github.inductiveautomation.kindling.core.Tool
import io.github.inductiveautomation.kindling.core.ToolOpeningException
import io.github.inductiveautomation.kindling.core.ToolPanel
import io.github.inductiveautomation.kindling.utils.ColorPalette
import io.github.inductiveautomation.kindling.utils.EDT_SCOPE
import io.github.inductiveautomation.kindling.utils.FileFilter
import io.github.inductiveautomation.kindling.utils.ReifiedJXTable
import io.github.inductiveautomation.kindling.utils.ReifiedListTableModel
import io.github.inductiveautomation.kindling.utils.VerticalSplitPane
import io.github.inductiveautomation.kindling.utils.selectedRowIndices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.miginfocom.swing.MigLayout
import org.jdesktop.swingx.JXSearchField
import java.awt.Color
import java.nio.file.Path
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import kotlin.io.path.inputStream
import kotlin.io.path.name

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
        throw ToolOpeningException("Error deserializing alarm cache. Only caches from Ignition 8.1.20+ are supported.", e)
    }

    private val table = ReifiedJXTable(
        ReifiedListTableModel(events, AlarmEventColumnList),
    ).apply {
        alarmStateColors.forEach { (state, colorPalette) ->
            addHighlighter(
                colorPalette.toHighLighter { _, adapter ->
                    val viewRow = convertRowIndexToModel(adapter.row)
                    state == model[viewRow].state
                },
            )
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

    private fun EventData.asBodyLine(name: String): Sequence<Detail.BodyLine> {
        return sequence {
            yield(Detail.BodyLine("***$name***"))
            values.forEach { pv ->
                yield(Detail.BodyLine("${pv.property.name}: ${pv.value}"))
            }
            yield(Detail.EMPTY_LINE)
        }
    }

    private fun AlarmEvent.toDetail(): Detail = Detail(
        title = "Event Data for $name ($displayPathOrSource)",
        message = if (notes.isNullOrEmpty()) null else "Notes: $notes",
        body = sequence {
            yieldAll(activeData?.asBodyLine("Active Data").orEmpty())
            yieldAll(ackData?.asBodyLine("Ack Data").orEmpty())
            yieldAll(clearedData?.asBodyLine("Clear Data").orEmpty())
        }.toList(),
        details = mapOf(
            "id" to id.toString(),
        ),
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
                    table.selectedRowIndices().map { table.model[it].toDetail() }
                }
                detailsPane.events = details
            }
        }

        search.addActionListener { event ->
            val searchField = event.source as JXSearchField
            EDT_SCOPE.launch {
                val filteredAlarms = withContext(Dispatchers.Default) {
                    events.filter { alarmEvent ->
                        val foundInColumns = AlarmEventColumnList.any { column ->
                            column.getValue(alarmEvent).toString().contains(searchField.text, ignoreCase = true)
                        }
                        if (foundInColumns) {
                            true
                        } else {
                            listOfNotNull(
                                alarmEvent.ackData,
                                alarmEvent.clearedData,
                                alarmEvent.activeData,
                            ).any { eventData ->
                                eventData.toString().contains(searchField.text, ignoreCase = true)
                            }
                        }
                    }
                }
                table.model = ReifiedListTableModel(filteredAlarms, AlarmEventColumnList)
                alarmCountLabel.text = countLabelText
            }
        }
    }

    companion object {
        private val alarmStateColors: Map<AlarmState, ColorPalette> = mapOf(
            AlarmState.ActiveAcked to ColorPalette(Color(0xAB0000), Color(0xD0D0D0)),
            AlarmState.ActiveUnacked to ColorPalette(Color(0xEC2215), Color(0xD0D0D0)),
            AlarmState.ClearAcked to ColorPalette(Color(0xDCDCFE), Color(0x262626)),
            AlarmState.ClearUnacked to ColorPalette(Color(0x49ABAB), Color(0x262626)),
        )
    }
}

data object AlarmViewer : Tool {
    override val serialKey = "alarm-cache"
    override val title = "Alarm Cache"
    override val description = "Persistent Alarms Cache (.alarms_[TIMESTAMP])"
    override val icon = FlatSVGIcon("icons/bx-bell.svg")
    override val requiresHiddenFiles = true

    override fun open(path: Path): ToolPanel = AlarmCacheView(path)

    override val filter = FileFilter(description) {
        fileNameRegex.matches(it.name)
    }

    private val fileNameRegex = """\.?alarms_.*""".toRegex()
}
