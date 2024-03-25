package io.github.inductiveautomation.kindling.zip.views.gwbk

import com.jidesoft.swing.StyledLabelBuilder
import io.github.inductiveautomation.kindling.statistics.categories.MetaStatistics
import java.awt.BorderLayout
import java.awt.Font
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JPanel

class MetaStatisticsRenderer : StatisticRenderer<MetaStatistics> {
    override var title: String = "Meta"
    override val icon: Icon? = null

    override fun MetaStatistics.render(): JComponent {
        title = "Gateway: $gatewayName"

        return JPanel(BorderLayout()).apply {
            add(
                displayedStatistics.fold(StyledLabelBuilder()) { acc, (field, suffix, value) ->
                    acc.add("$field: ", Font.BOLD)
                    acc.add(value(this@render))
                    acc.add(suffix)
                    acc.add("\n")
                    acc
                }.createLabel(),
                BorderLayout.NORTH,
            )
        }
    }

    private data class MetaStatistic(
        val label: String,
        val suffix: String = "",
        val value: (stats: MetaStatistics) -> String,
    )

    companion object {
        private val displayedStatistics: List<MetaStatistic> = listOf(
            MetaStatistic("Version") { it.version },
            MetaStatistic("Role") { it.role },
            MetaStatistic("Edition") { it.edition },
            MetaStatistic("UUID") { it.uuid.orEmpty() },
            MetaStatistic("Init Memory", suffix = "mB") { it.initMemory.toString() },
            MetaStatistic("Max Memory", suffix = "mB") { it.maxMemory.toString() },
        )
    }
}
