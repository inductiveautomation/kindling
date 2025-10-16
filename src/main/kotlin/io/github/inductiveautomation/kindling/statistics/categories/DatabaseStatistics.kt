package io.github.inductiveautomation.kindling.statistics.categories

import com.inductiveautomation.ignition.common.datasource.DatabaseVendor
import io.github.inductiveautomation.kindling.statistics.CalculatorSupport
import io.github.inductiveautomation.kindling.statistics.GatewayBackup
import io.github.inductiveautomation.kindling.statistics.Statistic
import io.github.inductiveautomation.kindling.statistics.StatisticCalculator
import io.github.inductiveautomation.kindling.statistics.config83.PlatformResourceCategory
import io.github.inductiveautomation.kindling.utils.MajorVersion
import io.github.inductiveautomation.kindling.utils.executeQuery
import io.github.inductiveautomation.kindling.utils.get
import io.github.inductiveautomation.kindling.utils.toList
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

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

    companion object Calculators : CalculatorSupport<DatabaseStatistics>(
        mapOf(
            MajorVersion.SevenNine to Calculator,
            MajorVersion.EightZero to Calculator,
            MajorVersion.EightOne to Calculator,
            MajorVersion.EightThree to Calculator83,
        ),
    )

    @Suppress("SqlResolve")
    object Calculator : StatisticCalculator<DatabaseStatistics> {
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

    object Calculator83 : StatisticCalculator<DatabaseStatistics> {
        @OptIn(ExperimentalSerializationApi::class)
        override suspend fun calculate(backup: GatewayBackup): DatabaseStatistics? = coroutineScope {
            val dbConnections = backup.resources.getPlatformCategory(PlatformResourceCategory.DATABASE_CONNECTION)
            val sfEngine = backup.resources.getPlatformCategory(PlatformResourceCategory.STORE_AND_FORWARD_ENGINE)

            val connections = dbConnections.resources.map { connectionResource ->
                async {
                    val sfResource = sfEngine[connectionResource.name] ?: return@async null
                    Connection(
                        name = connectionResource.name,
                        description = connectionResource.data.description,
                        vendor = DatabaseVendor.valueOf(connectionResource.config["translator"]!!.jsonPrimitive.content),
                        enabled = connectionResource.data.attributes.enabled,
                        sfEnabled = sfResource.data.attributes.enabled,
                        bufferSize = sfResource.config["dataThreshold"]!!.jsonPrimitive.content.toLong(),
                        cacheSize = sfResource.config["secondaryMaintenancePolicy"]!!.jsonObject["limit"]!!.jsonObject["value"]!!.jsonPrimitive.content.toLong(),
                    )
                }
            }.toList().awaitAll().filterNotNull()

            if (connections.isEmpty()) return@coroutineScope null
            DatabaseStatistics(connections)
        }
    }
}
