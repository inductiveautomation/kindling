package io.github.inductiveautomation.kindling.statistics.categories

import io.github.inductiveautomation.kindling.statistics.CalculatorSupport
import io.github.inductiveautomation.kindling.statistics.GatewayBackup
import io.github.inductiveautomation.kindling.statistics.Statistic
import io.github.inductiveautomation.kindling.statistics.StatisticCalculator
import io.github.inductiveautomation.kindling.statistics.config83.PlatformResourceCategory
import io.github.inductiveautomation.kindling.utils.MajorVersion
import io.github.inductiveautomation.kindling.utils.asScalarMap
import io.github.inductiveautomation.kindling.utils.executeQuery
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
    companion object Calculators : CalculatorSupport<MetaStatistics>(
        mapOf(
            MajorVersion.SevenNine to Calculator,
            MajorVersion.EightZero to Calculator,
            MajorVersion.EightOne to Calculator,
            MajorVersion.EightThree to Calculator83,
        ),
    )

    @Suppress("SqlResolve")
    object Calculator : StatisticCalculator<MetaStatistics> {
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

    object Calculator83 : StatisticCalculator<MetaStatistics> {
        override suspend fun calculate(backup: GatewayBackup): MetaStatistics {
            val sysProps = backup.resources.getPlatformCategory(
                PlatformResourceCategory.SYSTEM_PROPERTIES,
            ).resources.single().config

            val localSysProps = backup.resources.withDeploymentId("local") {
                getPlatformCategory(PlatformResourceCategory.LOCAL_SYSTEM_PROPERTIES)
            }.resources.single().config

            val edition = backup.info.getElementsByTagName("edition").item(0)?.textContent
            val version = backup.info.getElementsByTagName("version").item(0).textContent

            return MetaStatistics(
                uuid = localSysProps["systemUID"]?.jsonPrimitive?.content,
                gatewayName = sysProps["systemName"]!!.jsonPrimitive.content,
                edition = edition.takeUnless { it.isNullOrEmpty() } ?: "Standard",
                role = backup.redundancyInfo.getProperty("redundancy.noderole"),
                version = version,
                initMemory = backup.ignitionConf.getProperty("wrapper.java.initmemory").takeWhile { it.isDigit() }.toInt(),
                maxMemory = backup.ignitionConf.getProperty("wrapper.java.maxmemory").takeWhile { it.isDigit() }.toInt(),
            )
        }
    }
}
