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
import io.github.inductiveautomation.kindling.utils.FileFilter
import io.github.inductiveautomation.kindling.utils.ReifiedJXTable
import io.github.inductiveautomation.kindling.utils.VerticalSplitPane
import java.awt.Color
import java.nio.file.Path
import javax.swing.JScrollPane
import kotlin.io.path.inputStream
import kotlin.io.path.name
import org.jdesktop.swingx.decorator.ColorHighlighter

class AlarmCacheView(path: Path) : ToolPanel() {
    override val icon = FlatSVGIcon("icons/bx-bell.svg")

    private val alarmStates: Map<AlarmState, List<AlarmEvent>> = try {
        val info = AliasingObjectInputStream(path.inputStream()) {
            put(
                "com.inductiveautomation.ignition.gateway.alarming.status.AlarmStateModel\$PersistedAlarmInfo",
                PersistedAlarmInfo::class.java,
            )
        }.readObject() as PersistedAlarmInfo

        info.data.mapValues { (_, array) -> array.toList() }
    } catch (e: Exception) {
        throw IllegalArgumentException("Error deserializing alarm cache. Only caches from Ignition 8.1.20+ are supported.")
    }

    private val table = run {
        val initialModel = AlarmEventModel(alarmStates.values.flatten())
        ReifiedJXTable(initialModel).apply {
            columnFactory = AlarmEventModel.AlarmEventColumnList.toColumnFactory()
            createDefaultColumnsFromModel()

            alarmStateColors.entries.forEach { (state, color) ->
                addHighlighter(
                    ColorHighlighter(
                        { _, adapter ->
                            val viewRow = convertRowIndexToModel(adapter.row)
                            state == model[viewRow].state
                        },
                        color,
                        if (state == AlarmState.ClearAcked) Color.BLACK else Color.WHITE,
                    ),
                )
            }

            selectionModel.apply {
                addListSelectionListener {
                    val alarmEvents = selectedIndices.map {
                        val viewIndex = convertRowIndexToModel(it)
                        model[viewIndex]
                    }
                    detailsPane.events = alarmEvents.map { it.toDetail() }
                }
            }
        }
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

        add(
            VerticalSplitPane(
                top = JScrollPane(table),
                bottom = detailsPane,
                resizeWeight = 0.5,
            ),
            "push, grow, span",
        )
    }

    companion object {
        private val alarmStateColors = mapOf(
            AlarmState.ActiveAcked to Color(171, 0, 0),
            AlarmState.ActiveUnacked to Color(236, 34, 21),
            AlarmState.ClearAcked to Color(220, 220, 254),
            AlarmState.ClearUnacked to Color(73, 171, 171),
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
