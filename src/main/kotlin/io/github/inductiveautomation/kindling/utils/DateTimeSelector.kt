package io.github.inductiveautomation.kindling.utils

import com.formdev.flatlaf.FlatClientProperties
import com.formdev.flatlaf.extras.FlatSVGIcon
import com.formdev.flatlaf.extras.components.FlatButton
import com.formdev.flatlaf.extras.components.FlatTextField
import io.github.inductiveautomation.kindling.core.Kindling.Preferences.UI.Theme
import io.github.inductiveautomation.kindling.core.Timezone
import net.miginfocom.swing.MigLayout
import org.jdesktop.swingx.JXMonthView
import org.jdesktop.swingx.calendar.DateSelectionModel
import java.awt.Dimension
import java.awt.EventQueue
import java.awt.Insets
import java.awt.Point
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.WeekFields
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.KeyStroke
import javax.swing.ListSelectionModel
import javax.swing.ScrollPaneConstants
import javax.swing.SwingConstants
import javax.swing.SwingUtilities
import javax.swing.UIManager
import javax.swing.event.PopupMenuEvent
import javax.swing.event.PopupMenuListener
import kotlin.math.absoluteValue

/**
 * A timestamp field backed by a popup containing a calendar and a list of times, in increments of [step].
 * Values are clamped into [range]; changes fire a `time` property change.
 */
class DateTimeSelector(
    initialValue: Instant,
    initialRange: ClosedRange<Instant>,
    title: String? = null,
    private val step: Duration = Duration.ofMinutes(5),
) : JPanel(MigLayout("ins 0, fillx, wrap 1, gapy 1")) {
    private val zoneId: ZoneId
        get() = Timezone.Default.zoneId

    /** The value [reset] returns to. */
    var defaultValue: Instant = initialValue

    /** Times outside this range are dimmed in the popup's list. */
    var range: ClosedRange<Instant> = initialRange
        set(value) {
            field = value
            time = time.coerceIn(value)
            updateDisplay()
        }

    var time: Instant = initialValue.coerceIn(initialRange)
        set(value) {
            val coerced = value.coerceIn(range)
            val old = field
            if (coerced == old) return
            field = coerced
            updateDisplay()
            firePropertyChange("time", old, coerced)
        }

    /** Days to flag in the calendar. */
    var highlightedDates: Set<LocalDate> = emptySet()
        set(value) {
            field = value
            val dates = value.map { Date.from(it.atStartOfDay(zoneId).toInstant()) }
            monthView.setFlaggedDates(*dates.toTypedArray())
        }

    // set while updateDisplay pushes values into the calendar and the time list
    private var adjusting = false

    // the popup isn't in the frame's component tree, so it doesn't get theme updates on its own
    private var themeChangedWhileHidden = false

    private var lastHiddenAt = 0L

    private val timesOfDay: List<LocalTime> = run {
        require(!step.isZero && !step.isNegative && step <= Duration.ofDays(1)) {
            "step must be a positive duration of at most one day, was $step"
        }
        val nanos = step.toNanos()
        List((Duration.ofDays(1).toNanos() / nanos).toInt()) { LocalTime.ofNanoOfDay(it * nanos) }
    }

    private val monthView = JXMonthView().apply {
        isTraversable = true
        selectionMode = DateSelectionModel.SelectionMode.SINGLE_SELECTION
        // adjust calendar from java.time to java.util weekday numbering
        firstDayOfWeek = WeekFields.of(Locale.getDefault()).firstDayOfWeek.value % 7 + 1
        boxPaddingX = 4
        boxPaddingY = 3

        selectionModel.addDateSelectionListener {
            if (adjusting) return@addDateSelectionListener
            val selectedDay = selectionDate ?: return@addDateSelectionListener
            time = ZonedDateTime.of(
                LocalDate.ofInstant(selectedDay.toInstant(), zoneId),
                time.atZone(zoneId).toLocalTime(),
                zoneId,
            ).toInstant()
        }
    }

    private val timeList = JList(timesOfDay.toTypedArray()).apply {
        selectionMode = ListSelectionModel.SINGLE_SELECTION
        cellRenderer = listCellRenderer<LocalTime> { _, value, _, _, _ ->
            horizontalAlignment = SwingConstants.CENTER
            text = TIME_FORMAT.format(value)
            if (!isSelectable(value)) {
                foreground = UIManager.getColor("Label.disabledForeground")
            }
        }

        addListSelectionListener { event ->
            // don't fire while dragging through the list
            if (adjusting || event.valueIsAdjusting) return@addListSelectionListener
            val selectedTime = selectedValue ?: return@addListSelectionListener
            time = ZonedDateTime.of(time.atZone(zoneId).toLocalDate(), selectedTime, zoneId).toInstant()
        }
    }

    private val timeScrollPane = FlatScrollPane(timeList) {
        horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
        verticalScrollBar.unitIncrement = 16
    }

    private val timeHeader = JLabel("", SwingConstants.CENTER)

    private val editor = FlatTextField().apply {
        horizontalAlignment = SwingConstants.CENTER
        toolTipText = "Type a timestamp, scroll to nudge it, or press Down for the calendar"

        trailingComponent = FlatButton().apply {
            icon = FlatSVGIcon("icons/bx-time-five.svg").derive(14, 14)
            buttonType = FlatButton.ButtonType.toolBarButton
            toolTipText = "Pick a date and time"
            addActionListener { togglePopup() }
        }

        // Enter commits; losing focus reverts
        addActionListener { commitEditor(revertIfInvalid = false) }
        addFocusListener(
            object : FocusAdapter() {
                override fun focusLost(e: FocusEvent) = commitEditor(revertIfInvalid = true)
            },
        )

        addMouseListener(
            object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    if (e.clickCount >= 2) {
                        showPopup()
                    }
                }
            },
        )

        addMouseWheelListener { e ->
            if (isFocusOwner) {
                val unit = when {
                    e.isShiftDown -> ChronoUnit.HOURS
                    e.isControlDown -> ChronoUnit.SECONDS
                    else -> ChronoUnit.MINUTES
                }
                // wheel rotation is negative when scrolling up
                time = time.minus(e.wheelRotation.toLong(), unit)
                e.consume()
            } else {
                parent?.dispatchEvent(SwingUtilities.convertMouseEvent(this, e, parent))
            }
        }

        getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), SHOW_POPUP)
        actionMap.put(SHOW_POPUP, Action { showPopup() })
    }

    private val popup = JPopupMenu().apply {
        layout = MigLayout("ins 4, fillx, gapx 6")

        add(monthView, "aligny top")
        add(
            JPanel(MigLayout("ins 0, fill, wrap 1, gapy 2")).apply {
                add(timeHeader, "growx")
                add(timeScrollPane, "grow, push")
            },
            "aligny top, growy",
        )
        add(footer(), "newline, spanx, growx, gaptop 4")

        addPopupMenuListener(
            object : PopupMenuListener {
                override fun popupMenuWillBecomeVisible(e: PopupMenuEvent) = Unit

                override fun popupMenuWillBecomeInvisible(e: PopupMenuEvent) {
                    lastHiddenAt = System.currentTimeMillis()
                }

                override fun popupMenuCanceled(e: PopupMenuEvent) = Unit
            },
        )
    }

    init {
        if (title != null) {
            add(JLabel(title, SwingConstants.CENTER), "growx")
        }
        add(editor, "growx, pushx")

        applyColors()
        updateDisplay()

        Timezone.Default.addChangeListener {
            updateDisplay()
        }

        Theme.addChangeListener {
            applyColors()
            themeChangedWhileHidden = true
        }
    }

    fun reset() {
        time = defaultValue
    }

    // Actions are inlined into their buttons since each is only used in one place
    private fun footer() = JPanel(MigLayout("ins 0, fillx, wrap 4, gapx 2, gapy 2")).apply {
        add(
            footerButton("Earliest", "Jump to the start of the available data") {
                time = range.start
            },
            "sgx jump, growx",
        )
        add(
            footerButton("Day Start", "Jump to midnight of the selected day") {
                time = time.atZone(zoneId).toLocalDate().atStartOfDay(zoneId).toInstant()
            },
            "sgx jump, growx",
        )
        add(
            footerButton("Day End", "Jump to the last millisecond of the selected day") {
                time = time.atZone(zoneId).toLocalDate().atTime(END_OF_DAY).atZone(zoneId).toInstant()
            },
            "sgx jump, growx",
        )
        add(
            footerButton("Latest", "Jump to the end of the available data") {
                time = range.endInclusive
            },
            "sgx jump, growx",
        )

        for (amount in listOf(-30L, -15, -5, -1, 1L, 5, 15, 30)) {
            add(
                footerButton(
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
                },
                "sgx nudge, growx",
            )
        }
    }

    private fun footerButton(name: String, description: String, onClick: () -> Unit) = FlatButton().apply {
        action = Action(name = name, description = description) { onClick() }
        margin = Insets(1, 1, 1, 1)
    }

    private fun isSelectable(localTime: LocalTime): Boolean {
        val candidate = ZonedDateTime.of(time.atZone(zoneId).toLocalDate(), localTime, zoneId)
        return candidate.toInstant() in range
    }

    private fun applyColors() {
        UIManager.getColor("Actions.Yellow")?.let { weekend ->
            monthView.setDayForeground(Calendar.SATURDAY, weekend)
            monthView.setDayForeground(Calendar.SUNDAY, weekend)
        }
        UIManager.getColor("Actions.Blue")?.let { monthView.flaggedDayForeground = it }
        UIManager.getColor("Label.foreground")?.let { monthView.monthStringForeground = it }
        UIManager.getColor("Panel.background")?.let {
            monthView.monthStringBackground = it
            monthView.background = it
        }
        UIManager.getColor("List.selectionBackground")?.let { monthView.selectionBackground = it }
        UIManager.getColor("List.selectionForeground")?.let { monthView.selectionForeground = it }
    }

    private fun updateDisplay() {
        adjusting = true
        try {
            val zoned = time.atZone(zoneId)
            editor.text = DISPLAY_FORMAT.format(zoned)
            editor.outline = null

            val startOfDay = Date.from(zoned.toLocalDate().atStartOfDay(zoneId).toInstant())
            monthView.timeZone = TimeZone.getTimeZone(zoneId)
            monthView.selectionDate = startOfDay
            monthView.ensureDateVisible(startOfDay)

            timeHeader.text = TIME_HEADER_FORMAT.format(zoned)
            timeList.selectedIndex = timesOfDay.indexOfLast { it <= zoned.toLocalTime() }
            timeList.repaint()
        } finally {
            adjusting = false
        }
    }

    private fun commitEditor(revertIfInvalid: Boolean) {
        val parsed = parse(editor.text)
        if (parsed == null && !revertIfInvalid) {
            editor.outline = FlatClientProperties.OUTLINE_ERROR
            return
        }
        if (parsed != null) {
            time = parsed
        }
        // the value may have been clamped or rejected
        updateDisplay()
    }

    private fun parse(text: String): Instant? {
        val trimmed = text.trim()
        for (format in DATE_TIME_FORMATS) {
            val parsed = runCatching { LocalDateTime.parse(trimmed, format) }.getOrNull()
            if (parsed != null) {
                return parsed.atZone(zoneId).toInstant()
            }
        }
        return runCatching { LocalDate.parse(trimmed, DATE_FORMAT) }
            .getOrNull()
            ?.atStartOfDay(zoneId)
            ?.toInstant()
    }

    private fun togglePopup() {
        // the popup is already hidden by the time the button fires, so don't reopen it right away
        if (System.currentTimeMillis() - lastHiddenAt > POPUP_REOPEN_DELAY) {
            showPopup()
        }
    }

    private fun showPopup() {
        if (popup.isVisible) return

        if (themeChangedWhileHidden) {
            SwingUtilities.updateComponentTreeUI(popup)
            applyColors()
            themeChangedWhileHidden = false
        }

        updateDisplay()
        timeScrollPane.preferredSize = Dimension(
            TIME_COLUMN_WIDTH,
            (monthView.preferredSize.height - timeHeader.preferredSize.height).coerceAtLeast(MIN_TIME_LIST_HEIGHT),
        )

        popup.show(this, 0, height)
        EventQueue.invokeLater(::centerTimeListOnSelection)
    }

    private fun centerTimeListOnSelection() {
        val index = timeList.selectedIndex
        if (index < 0) return
        val cell = timeList.getCellBounds(index, index) ?: return
        val viewport = timeScrollPane.viewport
        val maxY = (timeList.height - viewport.height).coerceAtLeast(0)
        viewport.viewPosition = Point(0, (cell.y + cell.height / 2 - viewport.height / 2).coerceIn(0, maxY))
    }

    private companion object {
        const val SHOW_POPUP = "showDateTimePopup"
        const val POPUP_REOPEN_DELAY = 150L
        const val TIME_COLUMN_WIDTH = 88
        const val MIN_TIME_LIST_HEIGHT = 120

        val END_OF_DAY: LocalTime = LocalTime.MAX.truncatedTo(ChronoUnit.MILLIS)

        val DISPLAY_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss.SSS")
        val TIME_HEADER_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
        val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
        val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("uuuu-MM-dd")

        val DATE_TIME_FORMATS: List<DateTimeFormatter> = listOf(
            "uuuu-MM-dd HH:mm:ss.SSS",
            // the format used elsewhere in Kindling, so pasted values work
            "uuuu-MM-dd HH:mm:ss:SSS",
            "uuuu-MM-dd HH:mm:ss",
            "uuuu-MM-dd HH:mm",
        ).map(DateTimeFormatter::ofPattern)
    }
}
