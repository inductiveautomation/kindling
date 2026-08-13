package io.github.inductiveautomation.kindling.log

import com.formdev.flatlaf.extras.FlatSVGIcon
import io.github.inductiveautomation.kindling.core.FilterChangeListener
import io.github.inductiveautomation.kindling.core.FilterPanel
import io.github.inductiveautomation.kindling.core.Timezone
import io.github.inductiveautomation.kindling.utils.Action
import io.github.inductiveautomation.kindling.utils.Column
import io.github.inductiveautomation.kindling.utils.ColumnList
import io.github.inductiveautomation.kindling.utils.DateTimeSelector
import io.github.inductiveautomation.kindling.utils.FileFilterResponsive
import io.github.inductiveautomation.kindling.utils.FlatScrollPane
import io.github.inductiveautomation.kindling.utils.ReifiedJXTable
import io.github.inductiveautomation.kindling.utils.ReifiedListTableModel
import io.github.inductiveautomation.kindling.utils.getAll
import io.github.inductiveautomation.kindling.utils.getAncestorOfClass
import net.miginfocom.swing.MigLayout
import org.jdesktop.swingx.renderer.DefaultTableRenderer
import java.awt.EventQueue
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.JSeparator
import javax.swing.ListSelectionModel
import javax.swing.SortOrder

internal class TimePanel<T : LogEvent>(
    data: List<T>,
) : FilterPanel<T>(),
    FileFilterResponsive<T> {
    override val icon = FlatSVGIcon("icons/bx-time-five.svg")

    private var lowerBound: Instant = data.first().timestamp
    private var upperBound: Instant = data.last().timestamp

    private var coveredRange: ClosedRange<Instant> = lowerBound..upperBound
    private var totalCurrentRange = coveredRange

    private val startSelector = DateTimeSelector(lowerBound, totalCurrentRange, "Start Time")
    private val endSelector = DateTimeSelector(upperBound, totalCurrentRange, "End Time")

    private val denseMinutesTable =
        ReifiedJXTable(
            ReifiedListTableModel(
                data.groupingBy { it.timestamp.truncatedTo(ChronoUnit.MINUTES) }
                    .eachCount()
                    .entries
                    .filter { it.value > 60 }
                    .map { entry ->
                        DenseTime(entry.key, entry.value)
                    },
                DensityColumns,
            ),
        ).apply {
            isColumnControlVisible = false
            tableHeader.apply {
                reorderingAllowed = false
            }
            setSortOrder(DensityColumns.Count, SortOrder.DESCENDING)

            selectionMode = ListSelectionModel.SINGLE_SELECTION

            addMouseListener(
                object : MouseAdapter() {
                    override fun mousePressed(e: MouseEvent) {
                        if (e.clickCount >= 2) {
                            val rowAtPoint = rowAtPoint(e.point)
                            if (rowAtPoint != -1) {
                                e.consume()
                                selectTime(model[convertRowIndexToModel(rowAtPoint), DensityColumns.Minute])
                            }
                            return
                        }
                        maybeShowPopup(e)
                    }

                    override fun mouseReleased(e: MouseEvent) {
                        maybeShowPopup(e)
                    }

                    private fun maybeShowPopup(e: MouseEvent) {
                        if (e.isPopupTrigger) {
                            e.consume()
                            val rowAtPoint = rowAtPoint(e.point)
                            if (rowAtPoint != -1) {
                                setRowSelectionInterval(rowAtPoint, rowAtPoint)

                                val timeToSelect = model[convertRowIndexToModel(rowAtPoint), DensityColumns.Minute]
                                JPopupMenu().apply {
                                    add(
                                        Action("Select") {
                                            selectTime(timeToSelect)
                                        },
                                    )
                                }.show(this@apply, e.x, e.y)
                            }
                        }
                    }
                },
            )
        }

    private fun selectTime(timeToSelect: Instant) {
        val table = containingLogPanel?.table ?: return

        val modelIndex = table.model.data
            .indexOfFirst { it.timestamp.truncatedTo(ChronoUnit.MINUTES) == timeToSelect }
        if (modelIndex == -1) return

        val viewIndex = table.convertRowIndexToView(modelIndex)
        table.setRowSelectionInterval(viewIndex, viewIndex)
        table.scrollRectToVisible(table.getCellRect(viewIndex, 0, true))
    }

    private val resetRange =
        Action("Reset") {
            reset()
        }

    private val captureRange =
        Action(
            name = "Capture Range",
            description = "Sets the time filter to the current visible range and resets all other filters",
        ) {
            val logPanel = containingLogPanel ?: return@Action

            val events = logPanel.table.model.data
            logPanel.reset()

            EventQueue.invokeLater {
                startSelector.time = events.first().timestamp
                endSelector.time = events.last().timestamp
            }
        }

    override val tabName: String = "Time"

    override val component =
        JPanel(MigLayout("ins 2 0, fill, wrap 1")).apply {
            add(startSelector, "pushx, growx")
            add(endSelector, "pushx, growx, gaptop 4")

            add(
                JSeparator(JSeparator.HORIZONTAL),
                "growx, spanx, h 10!, gap 10 10 10 10",
            )

            add(JButton(captureRange), "split 2, gapright push")
            add(JButton(resetRange))

            add(
                JSeparator(JSeparator.HORIZONTAL),
                "growx, spanx, h 10!, gap 10 10 10 10",
            )

            add(
                JLabel("Dense Times").apply {
                    toolTipText = "Dense times are minutes with more than 60 logged events"
                },
            )
            add(FlatScrollPane(denseMinutesTable), "pushx, growx")
        }

    private val containingLogPanel: LogPanel<*>?
        get() = component.getAncestorOfClass<LogPanel<*>>()

    init {
        startSelector.addPropertyChangeListener("time") {
            // push the other bound along rather than letting the two cross
            if (startSelector.time > endSelector.time) {
                endSelector.time = startSelector.time
            }
            updateCoveredRange()
        }
        endSelector.addPropertyChangeListener("time") {
            if (endSelector.time < startSelector.time) {
                startSelector.time = endSelector.time
            }
            updateCoveredRange()
        }

        updateHighlightedDates(data)

        Timezone.Default.addChangeListener {
            lowerBound = data.minOf { it.timestamp }
            upperBound = data.maxOf { it.timestamp }
            totalCurrentRange = lowerBound..upperBound
            startSelector.range = totalCurrentRange
            endSelector.range = totalCurrentRange
            updateHighlightedDates(data)
            reset()
        }
    }

    /** Flags the days which contain events in both selectors' calendars. */
    private fun updateHighlightedDates(data: List<T>) {
        val daysWithData = data.mapTo(mutableSetOf()) { LocalDate.ofInstant(it.timestamp, Timezone.Default.zoneId) }
        startSelector.highlightedDates = daysWithData
        endSelector.highlightedDates = daysWithData
    }

    override fun isFilterApplied(): Boolean = coveredRange != totalCurrentRange

    private fun updateCoveredRange() {
        coveredRange = startSelector.time..endSelector.time

        listeners.getAll<FilterChangeListener>().forEach(FilterChangeListener::filterChanged)
    }

    override fun filter(item: T): Boolean = item.timestamp in coveredRange

    override fun setModelData(data: List<T>) {
        val isFilterApplied = isFilterApplied()
        lowerBound = data.minOf { it.timestamp }
        upperBound = data.maxOf { it.timestamp }
        totalCurrentRange = lowerBound..upperBound

        denseMinutesTable.model = ReifiedListTableModel(
            data.groupingBy { it.timestamp.truncatedTo(ChronoUnit.MINUTES) }
                .eachCount()
                .entries
                .filter { it.value > 60 }
                .map { entry ->
                    DenseTime(entry.key, entry.value)
                },
            DensityColumns,
        )

        startSelector.range = totalCurrentRange
        startSelector.defaultValue = lowerBound

        endSelector.range = totalCurrentRange
        endSelector.defaultValue = upperBound

        if (!isFilterApplied) {
            reset()
            return
        }

        if (lowerBound > startSelector.time) {
            startSelector.time = lowerBound
        }

        if (upperBound < startSelector.time) {
            startSelector.time = upperBound
        }

        updateCoveredRange()
    }

    override fun customizePopupMenu(
        menu: JPopupMenu,
        column: Column<out T, *>,
        event: T,
    ) {
        if (column == WrapperLogColumns.Timestamp || column == SystemLogColumns.Timestamp) {
            menu.add(
                Action("Show only events after ${Timezone.Default.format(event.timestamp)}") {
                    startSelector.time = event.timestamp
                },
            )
            menu.add(
                Action("Show only events before ${Timezone.Default.format(event.timestamp)}") {
                    endSelector.time = event.timestamp
                },
            )
        }
    }

    override fun reset() {
        startSelector.time = lowerBound
        endSelector.time = upperBound
        updateCoveredRange()
    }
}

private data class DenseTime(
    val time: Instant,
    val count: Int,
)

private object DensityColumns : ColumnList<DenseTime>() {
    @Suppress("PropertyName", "RedundantSuppression")
    private lateinit var _formatter: DateTimeFormatter

    private val minuteFormatter: DateTimeFormatter
        get() {
            if (!this::_formatter.isInitialized) {
                _formatter = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm")
                    .withZone(Timezone.Default.zoneId)
            }
            if (_formatter.zone != Timezone.Default.zoneId) {
                _formatter = _formatter.withZone(Timezone.Default.zoneId)
            }
            return _formatter
        }

    val Minute by column(
        column = {
            cellRenderer =
                DefaultTableRenderer {
                    (it as? Instant)?.let(minuteFormatter::format)
                }
        },
        value = DenseTime::time,
    )

    val Count by column(
        name = "Event Count",
        value = DenseTime::count,
    )
}
