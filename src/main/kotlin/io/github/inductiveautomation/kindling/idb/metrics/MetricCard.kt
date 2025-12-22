package io.github.inductiveautomation.kindling.idb.metrics

import io.github.inductiveautomation.kindling.core.Timezone
import io.github.inductiveautomation.kindling.idb.metrics.MetricCard.Companion.MetricPresentation.Cpu
import io.github.inductiveautomation.kindling.idb.metrics.MetricCard.Companion.MetricPresentation.Default
import io.github.inductiveautomation.kindling.idb.metrics.MetricCard.Companion.MetricPresentation.Heap
import io.github.inductiveautomation.kindling.idb.metrics.MetricCard.Companion.MetricPresentation.Queue
import io.github.inductiveautomation.kindling.idb.metrics.MetricCard.Companion.MetricPresentation.Throughput
import io.github.inductiveautomation.kindling.utils.Action
import io.github.inductiveautomation.kindling.utils.jFrame
import net.miginfocom.swing.MigLayout
import org.jfree.chart.ChartPanel
import org.jfree.chart.annotations.XYLineAnnotation
import org.jfree.data.statistics.Regression
import org.jfree.data.xy.XYDataset
import java.awt.BasicStroke
import java.text.DecimalFormat
import java.text.FieldPosition
import java.text.NumberFormat
import java.text.ParsePosition
import java.util.Date
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants.CENTER
import javax.swing.UIManager
import javax.swing.border.LineBorder

class MetricCard(val metric: Metric, data: List<MetricData>) : JPanel(MigLayout("fill, ins 10")) {
    private val presentation = metric.presentation

    private val sparkLine = ChartPanel(
        sparkline(data, presentation.formatter),
        false,
        false,
        false,
        true,
        true,
    ).apply {
        popupMenu.addSeparator()
        popupMenu.add(
            Action("Popout") {
                jFrame(
                    title = metric.name,
                    width = 800,
                    height = 600,
                ) {
                    add(
                        ChartPanel(
                            sparkline(
                                data,
                                presentation.formatter,
                            ),
                        ),
                    )
                }
            },
        )
    }

    init {
        add(
            JLabel(metric.name, CENTER).apply {
                putClientProperty("FlatLaf.styleClass", "h3")
            },
            "span, pushx, growx",
        )

        val aggregateData = DoubleArray(data.size) { i -> data[i].value }
        add(
            JLabel("Min: ${presentation.formatter.format(aggregateData.min())}", CENTER),
            "pushx, growx",
        )
        add(
            JLabel("Avg: ${presentation.formatter.format(aggregateData.average())}", CENTER),
            "pushx, growx",
        )
        add(
            JLabel("Max: ${presentation.formatter.format(aggregateData.max())}", CENTER),
            "pushx, growx, wrap",
        )

        val minTimestamp = data.first().timestamp
        val maxTimestamp = data.last().timestamp

        if (presentation.isShowTrend) {
            val regression = regressionFunction(sparkLine.chart.xyPlot.dataset, 0)
            val minTimeDouble = minTimestamp.time.toDouble()
            val maxTimeDouble = maxTimestamp.time.toDouble()

            sparkLine.chart.xyPlot.addAnnotation(
                XYLineAnnotation(
                    minTimeDouble,
                    regression(minTimeDouble),
                    maxTimeDouble,
                    regression(maxTimeDouble),
                    BasicStroke(1.0f),
                    UIManager.getColor("Actions.Yellow"),
                ),
            )
        }

        add(sparkLine, "span, w 300, h 170, pushx, growx")

        val timeLabel = JLabel(
            buildTimeLabelString(minTimestamp, maxTimestamp),
            CENTER,
        )
        add(timeLabel, "pushx, growx, span")

        Timezone.Default.addChangeListener {
            timeLabel.text =
                buildTimeLabelString(minTimestamp, maxTimestamp)
        }

        border = LineBorder(UIManager.getColor("Component.borderColor"), 3, true)
    }

    private fun buildTimeLabelString(minTimestamp: Date, maxTimestamp: Date): String = buildString {
        append(Timezone.Default.format(minTimestamp))
        append(" - ")
        append(Timezone.Default.format(maxTimestamp))
    }

    companion object {

        private val mbFormatter = DecimalFormat("0.0 'mB'")
        private val heapFormatter = object : NumberFormat() {
            override fun format(
                number: Double,
                toAppendTo: StringBuffer,
                pos: FieldPosition,
            ): StringBuffer = mbFormatter.format(number / 1_000_000, toAppendTo, pos)

            override fun format(
                number: Long,
                toAppendTo: StringBuffer,
                pos: FieldPosition,
            ): StringBuffer = mbFormatter.format(number, toAppendTo, pos)

            override fun parse(source: String, parsePosition: ParsePosition): Number = mbFormatter.parse(source, parsePosition)
        }

        enum class MetricPresentation(val formatter: NumberFormat, val isShowTrend: Boolean) {
            Heap(heapFormatter, true),
            Queue(NumberFormat.getIntegerInstance(), false),
            Throughput(DecimalFormat("0.00 'dp/s'"), true),
            Cpu(
                DecimalFormat("0.00%").apply {
                    multiplier = 1
                },
                true,
            ),
            Default(NumberFormat.getInstance(), false),
        }

        private val Metric.presentation: MetricPresentation
            get() = when {
                name.contains("heap", true) -> Heap
                name.contains("queue", true) -> Queue
                name.contains("throughput", true) -> Throughput
                name.contains("cpu", true) -> Cpu
                else -> Default
            }

        fun regressionFunction(dataset: XYDataset, series: Int): (Double) -> Double {
            val (a, b) = Regression.getOLSRegression(dataset, series)
            return { x ->
                a + b * x
            }
        }
    }
}
