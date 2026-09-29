package io.github.inductiveautomation.kindling.statistics.categories

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
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive

data class GatewayNetworkStatistics(
    val outgoing: List<OutgoingConnection>,
    val incoming: List<IncomingConnection>,
) : Statistic {
    data class OutgoingConnection(
        val host: String,
        val port: Int,
        val enabled: Boolean,
    )

    data class IncomingConnection(
        val uuid: String,
    )

    @Suppress("SqlResolve")
    companion object Calculator : StatisticCalculator<GatewayNetworkStatistics> {
        private val OUTGOING_CONNECTIONS =
            """
            SELECT
                host,
                port,
                enabled
            FROM
                wsconnectionsettings
            """.trimIndent()

        private val INCOMING_CONNECTIONS =
            """
            SELECT
                connectionid
            FROM
                wsincomingconnection
            """.trimIndent()

        override suspend fun calculate(backup: InternalDatabase): GatewayNetworkStatistics? {
            val outgoing =
                backup.configDb.executeQuery(OUTGOING_CONNECTIONS)
                    .toList { rs ->
                        OutgoingConnection(
                            host = rs[1],
                            port = rs[2],
                            enabled = rs[3],
                        )
                    }

            val incoming =
                backup.configDb.executeQuery(INCOMING_CONNECTIONS)
                    .toList { rs ->
                        IncomingConnection(rs[1])
                    }

            if (outgoing.isEmpty() && incoming.isEmpty()) {
                return null
            }

            return GatewayNetworkStatistics(outgoing, incoming)
        }

        private val OUTGOING = ResourceType(PLATFORM_MODULE_ID, "gateway-network-outgoing")
        private val INCOMING = ResourceType(PLATFORM_MODULE_ID, "gateway-network-incoming")

        override suspend fun calculate(backup: Filesystem): GatewayNetworkStatistics? {
            val outgoing = backup.core.resourcesOfType(OUTGOING).map { resource ->
                OutgoingConnection(
                    host = resource.config.getValue("host").jsonPrimitive.content,
                    port = resource.config.getValue("port").jsonPrimitive.int,
                    enabled = resource.enabled,
                )
            }

            val incoming = backup.core.resourcesOfType(INCOMING).map { resource ->
                IncomingConnection(resource.config.getValue("connectionId").jsonPrimitive.content)
            }

            if (outgoing.isEmpty() && incoming.isEmpty()) {
                return null
            }

            return GatewayNetworkStatistics(outgoing, incoming)
        }
    }
}
