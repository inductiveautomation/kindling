package io.github.inductiveautomation.kindling.zip.views.gwbk

import com.formdev.flatlaf.extras.FlatSVGIcon
import com.inductiveautomation.ignition.common.datasource.DatabaseVendor.DB2
import com.inductiveautomation.ignition.common.datasource.DatabaseVendor.FIREBIRD
import com.inductiveautomation.ignition.common.datasource.DatabaseVendor.GENERIC
import com.inductiveautomation.ignition.common.datasource.DatabaseVendor.MSSQL
import com.inductiveautomation.ignition.common.datasource.DatabaseVendor.MYSQL
import com.inductiveautomation.ignition.common.datasource.DatabaseVendor.ORACLE
import com.inductiveautomation.ignition.common.datasource.DatabaseVendor.POSTGRES
import com.inductiveautomation.ignition.common.datasource.DatabaseVendor.SQLITE
import io.github.inductiveautomation.kindling.statistics.categories.DatabaseStatistics
import io.github.inductiveautomation.kindling.utils.ColumnList
import io.github.inductiveautomation.kindling.utils.FlatScrollPane
import io.github.inductiveautomation.kindling.utils.ReifiedJXTable
import io.github.inductiveautomation.kindling.utils.ReifiedLabelProvider.Companion.setDefaultRenderer
import io.github.inductiveautomation.kindling.utils.ReifiedListTableModel
import io.github.inductiveautomation.kindling.utils.asActionIcon
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.SortOrder

class DatabaseStatisticsRenderer : StatisticRenderer<DatabaseStatistics> {
    override val title: String = "Databases"
    override val icon: Icon = FlatSVGIcon("icons/bx-data.svg").asActionIcon()

    override fun DatabaseStatistics.subtitle(): String {
        return "$enabled enabled, ${connections.size} total"
    }

    override fun DatabaseStatistics.render(): JComponent {
        return FlatScrollPane(
            ReifiedJXTable(ReifiedListTableModel(connections, ConnectionColumns)).apply {
                setDefaultRenderer<DatabaseStatistics.Connection>(
                    getText = { it?.name },
                    getTooltip = { it?.description },
                )
                setSortOrder(Name, SortOrder.ASCENDING)
            },
        )
    }

    @Suppress("unused")
    companion object ConnectionColumns : ColumnList<DatabaseStatistics.Connection>() {
        val Name by column { it }
        val Vendor by column { conn ->
            when (conn.vendor) {
                MYSQL -> "MySQL/MariaDB"
                POSTGRES -> "PostgreSQL"
                MSSQL -> "SQL Server"
                ORACLE -> "Oracle"
                DB2 -> "DB2"
                FIREBIRD -> "Firebird"
                SQLITE -> "SQLite"
                GENERIC -> "Other"
            }
        }
        val Enabled by column { it.enabled }
        val sfEnabled by column("S+F") { it.sfEnabled }
        val bufferSize by column("Memory Buffer") { it.bufferSize }
        val cacheSize by column("Disk Cache") { it.cacheSize }
    }
}
