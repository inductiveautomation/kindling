package io.github.inductiveautomation.kindling.log

import com.formdev.flatlaf.ui.FlatSpinnerUI
import io.github.inductiveautomation.kindling.core.Kindling
import io.github.inductiveautomation.kindling.utils.Action
import io.github.inductiveautomation.kindling.utils.add
import io.github.inductiveautomation.kindling.utils.getAll
import net.miginfocom.swing.MigLayout
import org.jdesktop.swingx.JXDatePicker
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseMotionAdapter
import java.util.Calendar
import java.util.Date
import java.util.EventListener
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JSpinner
import javax.swing.JTextField
import javax.swing.SpinnerNumberModel
import javax.swing.SwingConstants
import javax.swing.UIManager
import javax.swing.event.EventListenerList
import javax.swing.plaf.SpinnerUI

class LoggerTimesPanel(
    private val lowerBound: Long,
    private val upperBound: Long,
) : JPanel(MigLayout("ins 0, fill")), LogFilterPanel {

    private val listeners = EventListenerList()
    private val enabledCheckBox = JCheckBox(
        Action("Enabled") {
            fireTimeUpdateEvent()
        },
    ).apply { isSelected = true }
    private var startTime = lowerBound
    private var endTime = upperBound

    private val startSelector = DateTimeSelector("From", startTime).apply {
        addTimeChangeActionListener(this@LoggerTimesPanel::fireTimeUpdateEvent)
    }
    private val endSelector = DateTimeSelector("To", endTime).apply {
        addTimeChangeActionListener(this@LoggerTimesPanel::fireTimeUpdateEvent)
    }
    private val resetButton = JButton(
        Action("Reset") {
            startTime = lowerBound
            endTime = upperBound
            startSelector.updateDisplay(startTime)
            endSelector.updateDisplay(endTime)
            fireTimeUpdateEvent()
        },
    )

    init {
        add(enabledCheckBox, "left, wrap")
        add(startSelector, "spanx 2, pushx, growx, wrap")
        add(endSelector, "spanx 2, push, grow, wrap")
        add(resetButton, "right")
    }

    fun isValidLogEvent(event: LogEvent): Boolean {
        return !enabledCheckBox.isSelected || event.timestamp.toEpochMilli() in startTime..endTime
    }

    fun interface TimeUpdateEventListener : EventListener {
        fun onTimeUpdate()
    }

    fun addTimeUpdateEventListener(listener: TimeUpdateEventListener) {
        listeners.add(listener)
    }

    private fun fireTimeUpdateEvent() {
        startSelector.updateTime()
        endSelector.updateTime()
        startTime = startSelector.time
        endTime = endSelector.time
        for (listener in listeners.getAll<TimeUpdateEventListener>()) {
            listener.onTimeUpdate()
        }
    }

    override val isFilterApplied: Boolean
        get() = enabledCheckBox.isSelected && (lowerBound != startTime || upperBound != endTime)
}

class DateTimeSelector(label: String, initialTime: Long) : JPanel(MigLayout("ins 0, fillx")) {

    var time = initialTime
    private var datePicker = JXDatePicker(Date(initialTime)).apply {
        editor.horizontalAlignment = SwingConstants.CENTER
    }

    fun addTimeChangeActionListener(action: () -> Unit) {
        datePicker.addActionListener {
            action()
        }
        timeSelector.addTimeChangeActionListener(action)
    }

    private val timeSelector = TimeSelector(initialTime)

    fun updateDisplay(latchedTime: Long) {
        datePicker.date = Date(latchedTime)
        timeSelector.updateDisplay(latchedTime)
    }

    fun updateTime() {
        time = datePicker.date.time +
            ((timeSelector.hourSelector.value as Int) * MILLIS_PER_HOUR) +
            ((timeSelector.minuteSelector.value as Int) * MILLIS_PER_MINUTE) +
            ((timeSelector.secondSelector.value as Int) * MILLIS_PER_SECOND) +
            (timeSelector.milliSelector.value as Int)
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

    companion object {
        const val MILLIS_PER_HOUR = 3600000
        const val MILLIS_PER_MINUTE = 60000
        const val MILLIS_PER_SECOND = 1000
    }
}

class TimeSelector(initialTime: Long) : JPanel(MigLayout("fill, ins 0")) {

    val hourSelector = CustomSpinner(23, 10)
    val minuteSelector = CustomSpinner(59, 6)
    val secondSelector = CustomSpinner(59, 6)
    val milliSelector = CustomSpinner(999, 1)
    private var time: Calendar = Calendar.getInstance()

    init {
        background = UIManager.getColor("ComboBox.background")
        border = BorderFactory.createLineBorder(UIManager.getColor("Button.borderColor"))
        add(hourSelector, "wmin 45, growx")
        add(minuteSelector, "wmin 45, growx")
        add(secondSelector, "wmin 45, growx")
        add(milliSelector, "wmin 55, growx")
        updateDisplay(initialTime)
        Kindling.addThemeChangeListener {
            background = UIManager.getColor("ComboBox.background")
            border = BorderFactory.createLineBorder(UIManager.getColor("Button.borderColor"))
        }
    }

    fun updateDisplay(newTime: Long) {
        time.timeInMillis = newTime
        hourSelector.value = time.get(Calendar.HOUR_OF_DAY)
        minuteSelector.value = time.get(Calendar.MINUTE)
        secondSelector.value = time.get(Calendar.SECOND)
        milliSelector.value = time.get(Calendar.MILLISECOND)
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

class CustomSpinner(max: Int, pixelsPerValueChange: Int) : JSpinner(SpinnerNumberModel(0, 0, max, 1)) {
    var isSelection = true
    private var previousY = 0

    init {
        (editor as DefaultEditor).textField.apply {
            border = BorderFactory.createEmptyBorder()
            horizontalAlignment = JTextField.CENTER
            addMouseMotionListener(
                object : MouseMotionAdapter() {
                    override fun mouseDragged(e: MouseEvent?) {
                        if (e != null) {
                            if (e.y < 0 || e.y > this@CustomSpinner.height) {
                                val deltaY = previousY - (e.y / pixelsPerValueChange)
                                var currentValue = (this@CustomSpinner.value as Int) + deltaY
                                if (deltaY < 0 && previousY == 0) {
                                    currentValue += this@CustomSpinner.height
                                }
                                if (currentValue < 0) {
                                    this@CustomSpinner.value = 0
                                } else if (currentValue > max) {
                                    this@CustomSpinner.value = max
                                } else {
                                    this@CustomSpinner.value = currentValue
                                }
                            }
                            previousY = e.y / pixelsPerValueChange
                        }
                    }
                },
            )
            addMouseListener(
                object : MouseAdapter() {
                    override fun mouseReleased(e: MouseEvent?) {
                        isSelection = true
                        previousY = 0
                        fireStateChanged()
                    }

                    override fun mousePressed(e: MouseEvent?) {
                        isSelection = false
                        super.mousePressed(e)
                    }
                },
            )
        }
        border = BorderFactory.createEmptyBorder()
        isOpaque = false
    }

    override fun setUI(ui: SpinnerUI?) {
        super.setUI(SpinnerUI())
    }
}

class SpinnerUI : FlatSpinnerUI() {
    override fun installDefaults() {
        super.installDefaults()
        buttonBackground = null
        buttonSeparatorColor = null
    }
}
