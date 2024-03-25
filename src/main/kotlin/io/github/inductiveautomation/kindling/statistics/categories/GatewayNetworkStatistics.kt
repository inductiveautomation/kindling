package io.github.inductiveautomation.kindling.statistics.categories

import io.github.inductiveautomation.kindling.statistics.GatewayBackup
import io.github.inductiveautomation.kindling.statistics.Statistic
import io.github.inductiveautomation.kindling.statistics.StatisticCalculator
import io.github.inductiveautomation.kindling.utils.executeQuery
import io.github.inductiveautomation.kindling.utils.get
import io.github.inductiveautomation.kindling.utils.toList

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

        override suspend fun calculate(backup: GatewayBackup): GatewayNetworkStatistics? {
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
    }
}
