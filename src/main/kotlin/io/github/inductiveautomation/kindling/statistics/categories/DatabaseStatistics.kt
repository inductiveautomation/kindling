package io.github.inductiveautomation.kindling.statistics.categories

import com.inductiveautomation.ignition.common.datasource.DatabaseVendor
import io.github.inductiveautomation.kindling.statistics.GatewayBackup
import io.github.inductiveautomation.kindling.statistics.Statistic
import io.github.inductiveautomation.kindling.statistics.StatisticCalculator
import io.github.inductiveautomation.kindling.utils.executeQuery
import io.github.inductiveautomation.kindling.utils.get
import io.github.inductiveautomation.kindling.utils.toList

data class DatabaseStatistics(
    val connections: List<Connection>,
) : Statistic {
    val enabled: Int = connections.count { it.enabled }

    data class Connection(
        val name: String,
        val description: String?,
        val vendor: DatabaseVendor,
        val enabled: Boolean,
        val sfEnabled: Boolean,
        val bufferSize: Long,
        val cacheSize: Long,
    )

    @Suppress("SqlResolve")
    companion object Calculator : StatisticCalculator<DatabaseStatistics> {
        private val DATABASE_STATS =
            """
            SELECT
                ds.name,
                ds.description,
                jdbc.dbtype,
                ds.enabled,
                sf.enablediskstore,
                sf.buffersize,
                sf.storemaxrecords
            FROM
                datasources ds
                JOIN storeandforwardsyssettings sf ON ds.datasources_id = sf.storeandforwardsyssettings_id
                JOIN jdbcdrivers jdbc ON ds.driverid = jdbc.jdbcdrivers_id
            """.trimIndent()

        override suspend fun calculate(backup: GatewayBackup): DatabaseStatistics? {
            val connections =
                backup.configDb.executeQuery(DATABASE_STATS).toList { rs ->
                    Connection(
                        name = rs[1],
                        description = rs[2],
                        vendor = DatabaseVendor.valueOf(rs[3]),
                        enabled = rs[4],
                        sfEnabled = rs[5],
                        bufferSize = rs[6],
                        cacheSize = rs[7],
                    )
                }

            if (connections.isEmpty()) {
                return null
            }

            return DatabaseStatistics(connections)
        }
    }
}
