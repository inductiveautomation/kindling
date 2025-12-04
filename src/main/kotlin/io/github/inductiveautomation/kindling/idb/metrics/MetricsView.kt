package io.github.inductiveautomation.kindling.idb.metrics

import io.github.inductiveautomation.kindling.core.ToolPanel
import io.github.inductiveautomation.kindling.utils.EDT_SCOPE
import io.github.inductiveautomation.kindling.utils.FlatScrollPane
import io.github.inductiveautomation.kindling.utils.toList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.miginfocom.swing.MigLayout
import java.sql.Connection
import javax.swing.Icon
import javax.swing.JPanel

class MetricsView(connection: Connection) : ToolPanel("ins 0, fill, hidemode 3") {
    @Suppress("SqlResolve")
    private val metrics: List<Metric> =
        connection.createStatement().executeQuery(
            //language=sql
            """
            SELECT DISTINCT
                metric_name
            FROM
                system_metrics
            """.trimIndent(),
        ).toList { rs ->
            Metric(rs.getString(1))
        }

    private val metricTree = MetricTree(metrics)

    @Suppress("SqlResolve")
    private val metricDataQuery =
        connection.prepareStatement(
            //language=sql
            """
            SELECT DISTINCT
                value,
                timestamp
            FROM
                system_metrics
            WHERE
                metric_name = ?
            ORDER BY
                timestamp
            """.trimIndent(),
        )

    private val metricCards: List<MetricCard> =
        metrics.map { metric ->
            val metricData =
                metricDataQuery.apply {
                    setString(1, metric.name)
                }
                    .executeQuery()
                    .toList { rs ->
                        MetricData(rs.getDouble(1), rs.getTimestamp(2))
                    }

            MetricCard(metric, metricData)
        }

    private val cardPanel =
        JPanel(MigLayout("wrap 2, fillx, gap 20, hidemode 3, ins 6")).apply {
            for (card in metricCards) {
                add(card, "pushx, growx")
            }
        }

    init {
        add(FlatScrollPane(metricTree), "grow, w 200::20%")
        add(FlatScrollPane(cardPanel), "push, grow, span")

        metricTree.checkBoxTreeSelectionModel.addTreeSelectionListener { updateData() }
    }

    private fun updateData() {
        BACKGROUND.launch {
            val selectedMetricNames = metricTree.selectedLeafNodes.map { it.name }
            EDT_SCOPE.launch {
                for (card in metricCards) {
                    card.isVisible = card.metric.name in selectedMetricNames
                }
            }
        }
    }

    override val icon: Icon? = null

    companion object {
        private val BACKGROUND = CoroutineScope(Dispatchers.Default)
    }
}
