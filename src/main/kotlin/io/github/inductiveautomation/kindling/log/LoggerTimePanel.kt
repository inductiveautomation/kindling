package io.github.inductiveautomation.kindling.log

import com.formdev.flatlaf.ui.FlatSpinnerUI
import io.github.inductiveautomation.kindling.core.Kindling.Preferences.UI.Theme
import io.github.inductiveautomation.kindling.utils.Action
import io.github.inductiveautomation.kindling.utils.add
import io.github.inductiveautomation.kindling.utils.getAll
import net.miginfocom.swing.MigLayout
import org.jdesktop.swingx.JXDatePicker
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoField
import java.util.Date
import javax.swing.BorderFactory
import javax.swing.BorderFactory.createEmptyBorder
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JSpinner
import javax.swing.JTextField
import javax.swing.SpinnerNumberModel
import javax.swing.SwingConstants
import javax.swing.UIManager
import javax.swing.border.LineBorder
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

class LoggerTimePanel(
    private val lowerBound: Instant,
    private val upperBound: Instant,
) : JPanel(MigLayout("ins 0, fill")), LogFilterPanel {
    private val enabledCheckBox = JCheckBox(
        Action("Enabled", selected = true) {
            fireTimeUpdateEvent()
        },
    )
    private var startTime = lowerBound
    private var endTime = upperBound

    private var filterStartTime: Instant? = startTime
    private var filterEndTime: Instant? = endTime

    private val startSelector = DateTimeSelector("From", startTime.toLocalDateTime()).apply {
        addTimeChangeActionListener(::fireTimeUpdateEvent)
    }
    private val endSelector = DateTimeSelector("To", endTime.toLocalDateTime()).apply {
        addTimeChangeActionListener(::fireTimeUpdateEvent)
    }
    private val resetButton = JButton(
        Action("Reset") {
            startTime = lowerBound
            endTime = upperBound
            startSelector.time = startTime.toLocalDateTime()
            endSelector.time = endTime.toLocalDateTime()
            fireTimeUpdateEvent()
        },
    )

    init {
        add(enabledCheckBox, "left, wrap")
        add(startSelector, "spanx 2, pushx, growx, wrap")
        add(endSelector, "spanx 2, push, grow, wrap")
        add(resetButton, "right")
    }

    private fun fireTimeUpdateEvent() {
        startSelector.updateTime()
        endSelector.updateTime()
        startTime = startSelector.time.atZone(LogViewer.SelectedTimeZone.currentValue).toInstant()
        endTime = endSelector.time.atZone(LogViewer.SelectedTimeZone.currentValue).toInstant()

        listenerList.getAll<FilterChangeListener>().forEach(FilterChangeListener::filterChanged)
    }

    override val isFilterApplied: Boolean
        get() = enabledCheckBox.isSelected && (lowerBound != startTime || upperBound != endTime)

    override val tabName: String = "Time"

    override val component: JComponent = this

    override fun filter(event: LogEvent): Boolean {
        return !enabledCheckBox.isSelected || (filterStartTime?.isBefore(event.timestamp) == true && filterEndTime?.isAfter(event.timestamp) == true)
    }

    override fun addFilterChangeListener(listener: FilterChangeListener) {
        listenerList.add(listener)
    }
}

private fun Instant.toLocalDateTime(): LocalDateTime {
    return this.atZone(LogViewer.SelectedTimeZone.currentValue).toLocalDateTime()
}

private var JXDatePicker.dateTime: LocalDateTime?
    get() = date?.toInstant()?.atZone(LogViewer.SelectedTimeZone.currentValue)?.toLocalDateTime()
    set(value) {
        date = value?.atOffset(LogViewer.SelectedTimeZone.currentValue.rules.getOffset(value))?.toInstant().let(Date::from)
    }

class DateTimeSelector(
    label: String,
    time: LocalDateTime,
) : JPanel(MigLayout("ins 0, fillx")) {
    private var datePicker = JXDatePicker().apply {
        dateTime = time
        editor.horizontalAlignment = SwingConstants.CENTER
    }

    fun addTimeChangeActionListener(action: () -> Unit) {
        datePicker.addActionListener {
            action()
        }
        timeSelector.addTimeChangeActionListener(action)
    }

    private val timeSelector = TimeSelector(time.toLocalTime())

    var time: LocalDateTime = time
        set(value) {
            field = value
            datePicker.dateTime = value
            timeSelector.time = value.toLocalTime()
        }

    fun updateTime() {
        time = datePicker.date.toInstant().atZone(LogViewer.SelectedTimeZone.currentValue).toLocalDateTime() +
            (timeSelector.hourSelector.value as Int).hours.toJavaDuration() +
            (timeSelector.minuteSelector.value as Int).minutes.toJavaDuration() +
            (timeSelector.secondSelector.value as Int).seconds.toJavaDuration() +
            (timeSelector.milliSelector.value as Int).milliseconds.toJavaDuration()
    }

    init {
        add(
            JLabel(label).apply {
                horizontalAlignment = SwingConstants.CENTER
            },
            "cell 1 0, align center, growx, wrap",
        )
        add(JLabel("Date   "))
        add(datePicker, "growx, pushx, wrap")
        add(JLabel("Time   "))
        add(timeSelector, "growx, pushx")
    }
}

class TimeSelector(time: LocalTime) : JPanel(MigLayout("fill, ins 0")) {
    val hourSelector = CustomSpinner(23, 10)
    val minuteSelector = CustomSpinner(59, 6)
    val secondSelector = CustomSpinner(59, 6)
    val milliSelector = CustomSpinner(999, 1)

    init {
        background = UIManager.getColor("ComboBox.background")
        border = BorderFactory.createLineBorder(UIManager.getColor("Button.borderColor"))
        add(hourSelector, "wmin 45, growx")
        add(minuteSelector, "wmin 45, growx")
        add(secondSelector, "wmin 45, growx")
        add(milliSelector, "wmin 55, growx")

        Theme.addChangeListener {
            background = UIManager.getColor("ComboBox.background")
            border = LineBorder(UIManager.getColor("Button.borderColor"))
        }
    }

    var time: LocalTime = time
        set(value) {
            hourSelector.value = value.get(ChronoField.HOUR_OF_DAY)
            minuteSelector.value = value.get(ChronoField.MINUTE_OF_HOUR)
            secondSelector.value = value.get(ChronoField.SECOND_OF_MINUTE)
            milliSelector.value = value.get(ChronoField.MILLI_OF_SECOND)
            field = value
        }

    fun addTimeChangeActionListener(action: () -> Unit) {
        listOf(hourSelector, minuteSelector, secondSelector, milliSelector).forEach { spinner ->
            spinner.addChangeListener {
                if (spinner.isSelection) {
                    action()
                }
            }
        }
    }
}

class CustomSpinner(
    max: Int,
    pixelsPerValueChange: Int,
) : JSpinner(SpinnerNumberModel(0, 0, max, 1)) {
    var isSelection = true

    private val dragListener = object : MouseAdapter() {
        private var previousY = 0

        override fun mouseDragged(e: MouseEvent?) {
            if (e != null) {
                if (e.y < 0 || e.y > this@CustomSpinner.height) {
                    val deltaY = previousY - (e.y / pixelsPerValueChange)
                    var currentValue = value as Int + deltaY
                    if (deltaY < 0 && previousY == 0) {
                        currentValue += this@CustomSpinner.height
                    }
                    this@CustomSpinner.value = currentValue.coerceIn(0, max)
                }
                previousY = e.y / pixelsPerValueChange
            }
        }

        override fun mouseReleased(e: MouseEvent?) {
            isSelection = true
            previousY = 0
            fireStateChanged()
        }

        override fun mousePressed(e: MouseEvent?) {
            isSelection = false
            super.mousePressed(e)
        }
    }

    init {
        border = createEmptyBorder()
        isOpaque = false

        (super.getEditor() as NumberEditor).textField.apply {
            border = createEmptyBorder()
            horizontalAlignment = JTextField.CENTER
            addMouseMotionListener(dragListener)
            addMouseListener(dragListener)
        }

        setUI(SpinnerUI())
    }
}

class SpinnerUI : FlatSpinnerUI() {
    override fun installDefaults() {
        super.installDefaults()
        buttonBackground = null
        buttonSeparatorColor = null
    }
}
