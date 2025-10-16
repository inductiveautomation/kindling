package io.github.inductiveautomation.kindling.statistics.categories

import io.github.inductiveautomation.kindling.statistics.CalculatorSupport
import io.github.inductiveautomation.kindling.statistics.GatewayBackup
import io.github.inductiveautomation.kindling.statistics.Statistic
import io.github.inductiveautomation.kindling.statistics.StatisticCalculator
import io.github.inductiveautomation.kindling.utils.MajorVersion
import io.github.inductiveautomation.kindling.utils.executeQuery
import io.github.inductiveautomation.kindling.utils.get
import io.github.inductiveautomation.kindling.utils.toList
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class DeviceStatistics(
    val devices: List<Device>,
) : Statistic {
    data class Device(
        val name: String,
        val type: String,
        val description: String?,
        val enabled: Boolean,
    )

    val total = devices.size
    val enabled = devices.count { it.enabled }

    companion object Calculators : CalculatorSupport<DeviceStatistics>(
        mapOf(
            MajorVersion.SevenNine to Calculator,
            MajorVersion.EightZero to Calculator,
            MajorVersion.EightOne to Calculator,
            MajorVersion.EightThree to Calculator83,
        ),
    )

    @Suppress("SqlResolve")
    object Calculator : StatisticCalculator<DeviceStatistics> {
        private val DEVICES =
            """
            SELECT
                name,
                type,
                description,
                enabled
            FROM
                devicesettings
            """.trimIndent()

        override suspend fun calculate(backup: GatewayBackup): DeviceStatistics? {
            val devices =
                backup.configDb.executeQuery(DEVICES)
                    .toList { rs ->
                        Device(
                            name = rs[1],
                            type = rs[2],
                            description = rs[3],
                            enabled = rs[4],
                        )
                    }

            if (devices.isEmpty()) {
                return null
            }

            return DeviceStatistics(devices)
        }
    }

    object Calculator83 : StatisticCalculator<DeviceStatistics> {
        override suspend fun calculate(backup: GatewayBackup): DeviceStatistics? = coroutineScope {
            val devicesCategory = backup.resources.getModuleCategory("com.inductiveautomation.opcua", "device")

            val devices: List<Device> = devicesCategory.resources.map {
                async {
                    Device(
                        name = it.name,
                        type = it.config["profile"]!!.jsonObject["type"]!!.jsonPrimitive.content,
                        description = it.data.description,
                        enabled = it.data.attributes.enabled,
                    )
                }
            }.toList().awaitAll()

            if (devices.isEmpty()) return@coroutineScope null
            DeviceStatistics(devices)
        }
    }
}
