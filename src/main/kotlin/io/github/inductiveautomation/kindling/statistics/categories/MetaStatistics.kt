package io.github.inductiveautomation.kindling.statistics.categories

import io.github.inductiveautomation.kindling.statistics.GatewayBackup
import io.github.inductiveautomation.kindling.statistics.Statistic
import io.github.inductiveautomation.kindling.statistics.StatisticCalculator
import io.github.inductiveautomation.kindling.utils.asScalarMap
import io.github.inductiveautomation.kindling.utils.executeQuery

data class MetaStatistics(
    val uuid: String?,
    val gatewayName: String,
    val edition: String,
    val role: String,
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

        override suspend fun calculate(backup: GatewayBackup): MetaStatistics {
            val sysPropsMap = backup.configDb.executeQuery(SYS_PROPS).asScalarMap()

            val edition = backup.info.getElementsByTagName("edition").item(0)?.textContent
            val version = backup.info.getElementsByTagName("version").item(0).textContent

            return MetaStatistics(
                uuid = sysPropsMap["SYSTEMUID"] as String?,
                gatewayName = sysPropsMap.getValue("SYSTEMNAME") as String,
                edition = edition.takeUnless { it.isNullOrEmpty() } ?: "Standard",
                role = backup.redundancyInfo.getProperty("redundancy.noderole"),
                version = version,
                initMemory = backup.ignitionConf.getProperty("wrapper.java.initmemory").takeWhile { it.isDigit() }.toInt(),
                maxMemory = backup.ignitionConf.getProperty("wrapper.java.maxmemory").takeWhile { it.isDigit() }.toInt(),
            )
        }
    }
}
