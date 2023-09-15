package io.github.inductiveautomation.kindling.log

import io.github.inductiveautomation.kindling.core.FilterChangeListener
import io.github.inductiveautomation.kindling.core.FilterPanel
import io.github.inductiveautomation.kindling.core.Kindling.Preferences.UI.Theme
import io.github.inductiveautomation.kindling.log.LogViewer.TimeStampFormatter
import io.github.inductiveautomation.kindling.utils.Action
import io.github.inductiveautomation.kindling.utils.Column
import io.github.inductiveautomation.kindling.utils.EmptyBorder
import io.github.inductiveautomation.kindling.utils.getAll
import net.miginfocom.swing.MigLayout
import org.jdesktop.swingx.JXDatePicker
import java.awt.Cursor
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.temporal.ChronoField
import java.time.temporal.ChronoField.HOUR_OF_DAY
import java.time.temporal.ChronoField.MILLI_OF_SECOND
import java.time.temporal.ChronoField.MINUTE_OF_HOUR
import java.time.temporal.ChronoField.SECOND_OF_MINUTE
import java.time.temporal.WeekFields
import java.util.Date
import java.util.Locale
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.JSpinner
import javax.swing.SpinnerModel
import javax.swing.SpinnerNumberModel
import javax.swing.SwingConstants
import javax.swing.UIManager
import javax.swing.border.LineBorder

internal class TimePanel(
    private val lowerBound: Instant,
    private val upperBound: Instant,
) : FilterPanel<LogEvent>() {
    private var coveredRange: ClosedRange<Instant> = lowerBound..upperBound
    private val initialRange = coveredRange

    private val startSelector = DateTimeSelector(lowerBound, initialRange)
    private val endSelector = DateTimeSelector(upperBound, initialRange)

    private val resetRange = Action("Reset") {
        reset()
    }

    override val tabName: String = "Time"

    override val component = JPanel(MigLayout("ins 0, fill, wrap 1"))

    init {
        component.apply {
            add(startSelector, "pushx, growx")
            add(
                JLabel("To").apply {
                    horizontalAlignment = SwingConstants.CENTER
                },
                "align center, growx",
            )
            add(endSelector, "pushx, growx")
            add(JButton(resetRange), "top, right, pushy")
        }

        startSelector.addPropertyChangeListener("time") {
            updateCoveredRange()
        }
        endSelector.addPropertyChangeListener("time") {
            updateCoveredRange()
        }
    }

    override fun isFilterApplied(): Boolean = coveredRange != initialRange

    private fun updateCoveredRange() {
        coveredRange = startSelector.time..endSelector.time

        listeners.getAll<FilterChangeListener>().forEach(FilterChangeListener::filterChanged)
    }

    override fun filter(item: LogEvent): Boolean = item.timestamp in coveredRange
    override fun customizePopupMenu(
        menu: JPopupMenu,
        column: Column<out LogEvent, *>,
        event: LogEvent,
    ) {
        if (column == WrapperLogColumns.Timestamp || column == SystemLogColumns.Timestamp) {
            menu.add(
                Action("Show only events after ${TimeStampFormatter.format(event.timestamp)}") {
                    startSelector.time = event.timestamp
                },
            )
            menu.add(
                Action("Show only events before ${TimeStampFormatter.format(event.timestamp)}") {
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
    get() = date?.toInstant()?.let { LocalDate.ofInstant(it, LogViewer.SelectedTimeZone.currentValue) }
    set(value) {
        date = value?.atStartOfDay()
            ?.atOffset(LogViewer.SelectedTimeZone.currentValue.rules.getOffset(value.atStartOfDay()))
            ?.toInstant()
            .let(Date::from)
    }

class DateTimeSelector(
    private val initialValue: Instant,
    private val range: ClosedRange<Instant>,
) : JPanel(MigLayout("ins 0, fillx")) {
    private val initialZonedTime = initialValue.atZone(LogViewer.SelectedTimeZone.currentValue)

    private var datePicker = JXDatePicker().apply {
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
                initialValue
            } else {
                ZonedDateTime.of(
                    localDate,
                    timeSelector.localTime,
                    LogViewer.SelectedTimeZone.currentValue,
                ).toInstant() ?: initialValue
            }
        }
        set(value) {
            val zonedDateTime = value.atZone(LogViewer.SelectedTimeZone.currentValue)
            datePicker.localDate = zonedDateTime.toLocalDate()
            timeSelector.localTime = zonedDateTime.toLocalTime()
        }

    init {
        add(datePicker, "growx, pushx, wrap")
        add(timeSelector, "growx, pushx")
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
        get() = LocalTime.of(
            hourSelector.value.toInt(),
            minuteSelector.value.toInt(),
            secondSelector.value.toInt(),
            (milliSelector.value * 1_000_000).toInt(), // milli-of-second to nano-of-second
        )
        set(value) {
            hourSelector.value = value.hour.toLong()
            minuteSelector.value = value.minute.toLong()
            secondSelector.value = value.second.toLong()
            milliSelector.value = (value.nano / 1_000_000).toLong() // milli-of-second to nano-of-second
        }
}

private class TimePartSpinner(
    model: ChronoSpinnerModel,
    pixelsPerValueChange: Int,
) : JSpinner(model) {
    var isSelection = true

    private val dragListener = object : MouseAdapter() {
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
