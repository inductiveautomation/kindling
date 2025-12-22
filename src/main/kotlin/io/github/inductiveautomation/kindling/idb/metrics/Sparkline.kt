package io.github.inductiveautomation.kindling.idb.metrics

import io.github.inductiveautomation.kindling.core.Kindling.Preferences.UI.Theme
import io.github.inductiveautomation.kindling.core.Theme.Companion.theme
import io.github.inductiveautomation.kindling.core.Timezone
import org.jfree.chart.ChartFactory
import org.jfree.chart.JFreeChart
import org.jfree.chart.axis.NumberAxis
import org.jfree.chart.ui.RectangleInsets
import org.jfree.data.time.FixedMillisecond
import org.jfree.data.time.TimeSeries
import org.jfree.data.time.TimeSeriesCollection
import java.text.NumberFormat
import java.time.Instant

fun sparkline(data: List<MetricData>, formatter: NumberFormat): JFreeChart = ChartFactory.createTimeSeriesChart(
    /* title = */
    null,
    /* timeAxisLabel = */
    null,
    /* valueAxisLabel = */
    null,
    /* dataset = */
    TimeSeriesCollection(
        TimeSeries("Series").apply {
            for ((value, timestamp) in data) {
                add(FixedMillisecond(timestamp), value, false)
            }
        },
    ),
    /* legend = */
    false,
    /* tooltips = */
    true,
    /* urls = */
    false,
).apply {
    xyPlot.apply {
        domainAxis.isPositiveArrowVisible = true
        rangeAxis.apply {
            isPositiveArrowVisible = true
            (this as NumberAxis).numberFormatOverride = formatter
        }
        val updateTooltipGenerator = {
            renderer.setDefaultToolTipGenerator { dataset, series, item ->
                val time = Instant.ofEpochMilli(dataset.getXValue(series, item).toLong())
                "${Timezone.Default.format(time)} - ${formatter.format(dataset.getYValue(series, item))}"
            }
        }

        updateTooltipGenerator()

        Timezone.Default.addChangeListener {
            updateTooltipGenerator()
        }

        isDomainGridlinesVisible = false
        isRangeGridlinesVisible = false
        isOutlineVisible = false
    }

    padding = RectangleInsets(10.0, 10.0, 10.0, 10.0)
    isBorderVisible = false

    theme = Theme.currentValue
    Theme.addChangeListener { newTheme ->
        theme = newTheme
    }
}
