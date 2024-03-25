package io.github.inductiveautomation.kindling.statistics.categories

import io.github.inductiveautomation.kindling.statistics.GatewayBackup
import io.github.inductiveautomation.kindling.statistics.Statistic
import io.github.inductiveautomation.kindling.statistics.StatisticCalculator
import io.github.inductiveautomation.kindling.utils.executeQuery
import io.github.inductiveautomation.kindling.utils.get
import io.github.inductiveautomation.kindling.utils.toList

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

    @Suppress("SqlResolve")
    companion object Calculator : StatisticCalculator<DeviceStatistics> {
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
}
