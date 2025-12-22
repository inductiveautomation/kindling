package io.github.inductiveautomation.kindling.log

import com.formdev.flatlaf.extras.FlatSVGIcon
import com.formdev.flatlaf.extras.components.FlatButton
import io.github.inductiveautomation.kindling.core.FilterChangeListener
import io.github.inductiveautomation.kindling.core.FilterPanel
import io.github.inductiveautomation.kindling.core.Kindling.Preferences.UI.Theme
import io.github.inductiveautomation.kindling.core.Timezone
import io.github.inductiveautomation.kindling.utils.Action
import io.github.inductiveautomation.kindling.utils.Column
import io.github.inductiveautomation.kindling.utils.ColumnList
import io.github.inductiveautomation.kindling.utils.EmptyBorder
import io.github.inductiveautomation.kindling.utils.FileFilterResponsive
import io.github.inductiveautomation.kindling.utils.FlatScrollPane
import io.github.inductiveautomation.kindling.utils.ReifiedJXTable
import io.github.inductiveautomation.kindling.utils.ReifiedListTableModel
import io.github.inductiveautomation.kindling.utils.getAll
import io.github.inductiveautomation.kindling.utils.getAncestorOfClass
import net.miginfocom.swing.MigLayout
import org.jdesktop.swingx.JXDatePicker
import org.jdesktop.swingx.renderer.DefaultTableRenderer
import java.awt.Cursor
import java.awt.EventQueue
import java.awt.Insets
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoField
import java.time.temporal.ChronoField.HOUR_OF_DAY
import java.time.temporal.ChronoField.MILLI_OF_SECOND
import java.time.temporal.ChronoField.MINUTE_OF_HOUR
import java.time.temporal.ChronoField.SECOND_OF_MINUTE
import java.time.temporal.ChronoUnit
import java.time.temporal.WeekFields
import java.util.Date
import java.util.Locale
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.JSeparator
import javax.swing.JSpinner
import javax.swing.ListSelectionModel
import javax.swing.SortOrder
import javax.swing.SpinnerModel
import javax.swing.SpinnerNumberModel
import javax.swing.SwingConstants
import javax.swing.UIManager
import javax.swing.border.LineBorder
import kotlin.math.absoluteValue

internal class TimePanel<T : LogEvent>(
    data: List<T>,
) : FilterPanel<T>(),
    FileFilterResponsive<T> {
    override val icon = FlatSVGIcon("icons/bx-time-five.svg")

    private var lowerBound: Instant = data.first().timestamp
    private var upperBound: Instant = data.last().timestamp

    private var coveredRange: ClosedRange<Instant> = lowerBound..upperBound
    private var totalCurrentRange = coveredRange

    private val startSelector = DateTimeSelector(lowerBound, totalCurrentRange)
    private val endSelector = DateTimeSelector(upperBound, totalCurrentRange)

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
            add(
                JLabel("To").apply {
                    horizontalAlignment = SwingConstants.CENTER
                },
                "align center, growx",
            )
            add(endSelector, "pushx, growx")

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
            updateCoveredRange()
        }
        endSelector.addPropertyChangeListener("time") {
            updateCoveredRange()
        }
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

private var JXDatePicker.localDate: LocalDate?
    get() = date?.toInstant()?.let { LocalDate.ofInstant(it, Timezone.Default.zoneId) }
    set(value) {
        date =
            value?.atStartOfDay()
                ?.atOffset(Timezone.Default.zoneId.rules.getOffset(value.atStartOfDay()))
                ?.toInstant()
                .let(Date::from)
    }

class DateTimeSelector(
    var defaultValue: Instant,
    initialRange: ClosedRange<Instant>,
) : JPanel(MigLayout("ins 0")) {
    var range: ClosedRange<Instant> = initialRange
        set(value) {
            field = value
            datePicker.monthView.apply {
                lowerBound = Date.from(value.start)
                upperBound = Date.from(value.endInclusive)
            }
        }

    private val initialZonedTime: ZonedDateTime
        get() = defaultValue.atZone(Timezone.Default.zoneId)

    private val datePicker =
        JXDatePicker().apply {
            localDate = initialZonedTime.toLocalDate()
            editor.horizontalAlignment = SwingConstants.CENTER
            monthView.apply {
                // adjust calendar from java.time to java.util weekday numbering
                firstDayOfWeek = WeekFields.of(Locale.getDefault()).firstDayOfWeek.value % 7 + 1

                lowerBound = Date.from(range.start)
                upperBound = Date.from(range.endInclusive)
            }
            linkPanel = JPanel() // can't be null or BasicDatePickerUI throws an NPE on theme change

            addActionListener {
                if (date == null) { // out of range selection sets null in JXDatePicker - we'll be nicer and reset
                    localDate = initialZonedTime.toLocalDate()
                } else {
                    firePropertyChange("time", null, time)
                }
            }
        }

    private val timeSelector = TimeSelector().apply {
        localTime = initialZonedTime.toLocalTime()
        addPropertyChangeListener("localTime") {
            firePropertyChange("time", null, time)
        }
    }

    var time: Instant
        get() {
            val localDate = datePicker.localDate
            return if (localDate == null) {
                defaultValue
            } else {
                ZonedDateTime.of(
                    localDate,
                    timeSelector.localTime,
                    Timezone.Default.zoneId,
                ).toInstant() ?: defaultValue
            }
        }
        set(value) {
            val zonedDateTime = value.atZone(Timezone.Default.zoneId)
            datePicker.localDate = zonedDateTime.toLocalDate()
            timeSelector.localTime = zonedDateTime.toLocalTime()
        }

    init {
        add(datePicker, "growx, spanx, pushx, wrap")
        add(timeSelector, "growx, spanx, pushx, wrap")

        for (amount in listOf(-30L, -15, -5, -1, 1L, 5, 15, 30)) {
            add(
                FlatButton().apply {
                    action = Action(
                        name = "%+d".format(amount),
                        description = buildString {
                            append(if (amount > 0) "Add" else "Subtract")
                            append(" ")
                            append(amount.absoluteValue)
                            append(" minute")
                            if (amount.absoluteValue > 1) {
                                append("s")
                            }
                        },
                    ) {
                        time = time.plusSeconds(amount * 60)
                    }
                    margin = Insets(1, 1, 1, 1)
                },
                "w 12.5%, sgx",
            )
        }
    }
}

class TimeSelector : JPanel(MigLayout("fill, ins 0")) {
    private val hourSelector = timePartSpinner(HOUR_OF_DAY, "00", 10)
    private val minuteSelector = timePartSpinner(MINUTE_OF_HOUR, ":00", 6)
    private val secondSelector = timePartSpinner(SECOND_OF_MINUTE, ":00", 6)
    private val milliSelector = timePartSpinner(MILLI_OF_SECOND, "'.'000", 1)

    private fun timePartSpinner(
        field: ChronoField,
        pattern: String,
        pixelsPerValueChange: Int,
    ) = TimePartSpinner(ChronoSpinnerModel(field, pattern), pixelsPerValueChange).apply {
        addChangeListener {
            firePropertyChange("localTime", null, localTime)
        }
    }

    init {
        background = UIManager.getColor("ComboBox.background")
        border = LineBorder(UIManager.getColor("Button.borderColor"))
        add(hourSelector, "wmin 45, growx")
        add(minuteSelector, "wmin 45, growx")
        add(secondSelector, "wmin 45, growx")
        add(milliSelector, "wmin 55, growx")

        Theme.addChangeListener {
            background = UIManager.getColor("ComboBox.background")
            border = LineBorder(UIManager.getColor("Button.borderColor"))
        }
    }

    var localTime: LocalTime
        get() =
            LocalTime.of(
                hourSelector.value.toInt(),
                minuteSelector.value.toInt(),
                secondSelector.value.toInt(),
                // milli-of-second to nano-of-second
                (milliSelector.value * 1_000_000).toInt(),
            )
        set(value) {
            hourSelector.value = value.hour.toLong()
            minuteSelector.value = value.minute.toLong()
            secondSelector.value = value.second.toLong()
            // milli-of-second to nano-of-second
            milliSelector.value = (value.nano / 1_000_000).toLong()
        }
}

private class TimePartSpinner(
    model: ChronoSpinnerModel,
    pixelsPerValueChange: Int,
) : JSpinner(model) {
    var isSelection = true

    private val dragListener =
        object : MouseAdapter() {
            private var previousY = 0

            override fun mouseDragged(e: MouseEvent) {
                if (e.y < 0 || e.y > height) {
                    val deltaY = previousY - (e.y / pixelsPerValueChange)
                    var currentValue = value + deltaY
                    if (deltaY < 0 && previousY == 0) {
                        currentValue += height
                    }
                    value = currentValue.coerceIn(0, model.maximum)
                }
                previousY = e.y / pixelsPerValueChange
            }

            override fun mouseReleased(e: MouseEvent) {
                isSelection = true
                previousY = 0
                fireStateChanged()
            }

            override fun mousePressed(e: MouseEvent) {
                isSelection = false
            }
        }

    override fun createEditor(model: SpinnerModel): JComponent {
        check(model is ChronoSpinnerModel)
        return NumberEditor(this, model.pattern).apply {
            textField.apply {
                border = EmptyBorder()
                cursor = Cursor.getPredefinedCursor(Cursor.S_RESIZE_CURSOR)
            }
        }
    }

    init {
        border = EmptyBorder()
        isOpaque = false
        (editor as DefaultEditor).textField.apply {
            addMouseMotionListener(dragListener)
            addMouseListener(dragListener)
        }
    }

    override fun getValue(): Long = super.getValue() as Long

    override fun getModel(): ChronoSpinnerModel = super.getModel() as ChronoSpinnerModel
}

private class ChronoSpinnerModel(
    field: ChronoField,
    val pattern: String,
) : SpinnerNumberModel(0L, field.range().minimum, field.range().maximum, 1L) {
    override fun getMaximum(): Long = super.getMaximum() as Long
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
