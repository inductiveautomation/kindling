package io.github.inductiveautomation.kindling.zip.views.gwbk

import com.formdev.flatlaf.extras.FlatSVGIcon
import com.formdev.flatlaf.extras.components.FlatTabbedPane
import io.github.inductiveautomation.kindling.statistics.categories.GatewayNetworkStatistics
import io.github.inductiveautomation.kindling.statistics.categories.GatewayNetworkStatistics.IncomingConnection
import io.github.inductiveautomation.kindling.statistics.categories.GatewayNetworkStatistics.OutgoingConnection
import io.github.inductiveautomation.kindling.utils.ColumnList
import io.github.inductiveautomation.kindling.utils.FlatScrollPane
import io.github.inductiveautomation.kindling.utils.ReifiedJXTable
import io.github.inductiveautomation.kindling.utils.ReifiedListTableModel
import io.github.inductiveautomation.kindling.utils.asActionIcon
import javax.swing.Icon
import javax.swing.JTabbedPane
import javax.swing.SortOrder

class GatewayNetworkStatisticsRenderer : StatisticRenderer<GatewayNetworkStatistics> {
    override val title: String = "Gateway Network"
    override val icon: Icon = FlatSVGIcon("icons/bx-sitemap.svg").asActionIcon()

    override fun GatewayNetworkStatistics.render() = FlatTabbedPane().apply {
        tabLayoutPolicy = JTabbedPane.SCROLL_TAB_LAYOUT
        tabType = FlatTabbedPane.TabType.underlined

        addTab(
            "${outgoing.size} Outgoing",
            FlatScrollPane(
                ReifiedJXTable(ReifiedListTableModel(outgoing, OutgoingColumns)).apply {
                    setSortOrder(OutgoingColumns.Identifier, SortOrder.ASCENDING)
                },
            ),
        )
        addTab(
            "${incoming.size} Incoming",
            FlatScrollPane(
                ReifiedJXTable(ReifiedListTableModel(incoming, IncomingColumns)).apply {
                    setSortOrder(IncomingColumns.Identifier, SortOrder.ASCENDING)
                },
            ),
        )
    }

    object IncomingColumns : ColumnList<IncomingConnection>() {
        val Identifier by column(value = IncomingConnection::uuid)
    }

    @Suppress("unused")
    object OutgoingColumns : ColumnList<OutgoingConnection>() {
        val Identifier by column {
            "${it.host}:${it.port}"
        }
        val Enabled by column { it.enabled }
    }
}
