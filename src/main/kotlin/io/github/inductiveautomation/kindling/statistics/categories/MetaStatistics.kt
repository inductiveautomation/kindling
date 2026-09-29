package io.github.inductiveautomation.kindling.statistics.categories

import io.github.inductiveautomation.kindling.resources.ResourceType
import io.github.inductiveautomation.kindling.resources.ResourceType.Companion.PLATFORM_MODULE_ID
import io.github.inductiveautomation.kindling.statistics.GatewayBackup
import io.github.inductiveautomation.kindling.statistics.GatewayBackup.Filesystem
import io.github.inductiveautomation.kindling.statistics.GatewayBackup.InternalDatabase
import io.github.inductiveautomation.kindling.statistics.Statistic
import io.github.inductiveautomation.kindling.statistics.StatisticCalculator
import io.github.inductiveautomation.kindling.utils.asScalarMap
import io.github.inductiveautomation.kindling.utils.executeQuery
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

data class MetaStatistics(
    val uuid: String?,
    val gatewayName: String,
    val edition: String,
    val role: String?,
    val version: String,
    val initMemory: Int,
    val maxMemory: Int,
) : Statistic {
    @Suppress("SqlResolve")
    companion object Calculator : StatisticCalculator<MetaStatistics> {
        private val SYS_PROPS =
            """
            SELECT *
            FROM
                sysprops
            """.trimIndent()

        private val SYSTEM_PROPERTIES = ResourceType(PLATFORM_MODULE_ID, "system-properties")
        private val LOCAL_SYSTEM_PROPERTIES = ResourceType(PLATFORM_MODULE_ID, "local-system-properties")

        override suspend fun calculate(backup: InternalDatabase): MetaStatistics {
            val sysPropsMap = backup.configDb.executeQuery(SYS_PROPS).asScalarMap()

            return backup.toMetaStatistics(
                uuid = sysPropsMap["SYSTEMUID"] as String?,
                gatewayName = sysPropsMap.getValue("SYSTEMNAME") as String,
            )
        }

        override suspend fun calculate(backup: Filesystem): MetaStatistics {
            val systemProperties = checkNotNull(backup.core[SYSTEM_PROPERTIES]) { "No system properties" }.config
            // the system UID is unique to each node, so it's only ever defined in the local collection
            val localSystemProperties = backup.local?.get(LOCAL_SYSTEM_PROPERTIES)?.config

            return backup.toMetaStatistics(
                uuid = localSystemProperties?.get("systemUID")?.jsonPrimitive?.contentOrNull,
                gatewayName = systemProperties.getValue("systemName").jsonPrimitive.content,
            )
        }

        private fun GatewayBackup.toMetaStatistics(uuid: String?, gatewayName: String) = MetaStatistics(
            uuid = uuid,
            gatewayName = gatewayName,
            edition = edition ?: "Standard",
            role = redundancyInfo.getProperty("redundancy.noderole"),
            version = version,
            initMemory = ignitionConf.getProperty("wrapper.java.initmemory").takeWhile { it.isDigit() }.toInt(),
            maxMemory = ignitionConf.getProperty("wrapper.java.maxmemory").takeWhile { it.isDigit() }.toInt(),
        )
    }
}
