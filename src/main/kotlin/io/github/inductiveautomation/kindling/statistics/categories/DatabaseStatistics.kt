package io.github.inductiveautomation.kindling.statistics.categories

import com.inductiveautomation.ignition.common.datasource.DatabaseVendor
import io.github.inductiveautomation.kindling.resources.ResourceType
import io.github.inductiveautomation.kindling.resources.ResourceType.Companion.PLATFORM_MODULE_ID
import io.github.inductiveautomation.kindling.statistics.GatewayBackup
import io.github.inductiveautomation.kindling.statistics.GatewayBackup.Filesystem
import io.github.inductiveautomation.kindling.statistics.GatewayBackup.InternalDatabase
import io.github.inductiveautomation.kindling.statistics.Statistic
import io.github.inductiveautomation.kindling.statistics.StatisticCalculator
import io.github.inductiveautomation.kindling.utils.executeQuery
import io.github.inductiveautomation.kindling.utils.get
import io.github.inductiveautomation.kindling.utils.toList
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

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

        override suspend fun calculate(backup: InternalDatabase): DatabaseStatistics? {
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

        private val DATABASE_CONNECTION = ResourceType(PLATFORM_MODULE_ID, "database-connection")
        private val DATABASE_DRIVER = ResourceType(PLATFORM_MODULE_ID, "database-driver")
        private val STORE_AND_FORWARD_ENGINE = ResourceType(PLATFORM_MODULE_ID, "store-and-forward-engine")

        // StoreAndForwardEngineSettings defaults
        private const val DEFAULT_DATA_THRESHOLD = 10_000L

        override suspend fun calculate(backup: Filesystem): DatabaseStatistics? {
            val core = backup.core
            val drivers = core.resourcesOfType(DATABASE_DRIVER).associateBy { it.name }
            // each database connection owns a store and forward engine of the same name
            val engines = core.resourcesOfType(STORE_AND_FORWARD_ENGINE).associateBy { it.name }

            val connections = core.resourcesOfType(DATABASE_CONNECTION).map { resource ->
                val name = checkNotNull(resource.name)
                val driverName = resource.config.getValue("driver").jsonPrimitive.content
                val driverType = drivers[driverName]?.config?.get("type")?.jsonPrimitive?.contentOrNull
                val engine = engines[name]
                val engineConfig = engine?.config

                Connection(
                    name = name,
                    description = resource.description,
                    vendor = DatabaseVendor.entries.find { it.name == driverType } ?: DatabaseVendor.GENERIC,
                    enabled = resource.enabled,
                    sfEnabled = engine?.enabled == true,
                    bufferSize = engineConfig?.get("dataThreshold")?.jsonPrimitive?.long ?: DEFAULT_DATA_THRESHOLD,
                    cacheSize = engineConfig?.secondaryCountLimit() ?: 0,
                )
            }

            if (connections.isEmpty()) {
                return null
            }

            return DatabaseStatistics(connections)
        }

        // 8.1's disk cache max records most closely corresponds to a count limit on the secondary maintenance policy
        private fun JsonObject.secondaryCountLimit(): Long? {
            val limit = (get("secondaryMaintenancePolicy") as? JsonObject)?.get("limit")?.jsonObject ?: return null
            if (limit["limitType"]?.jsonPrimitive?.contentOrNull != "COUNT") return null
            return limit["value"]?.jsonPrimitive?.long
        }
    }
}
